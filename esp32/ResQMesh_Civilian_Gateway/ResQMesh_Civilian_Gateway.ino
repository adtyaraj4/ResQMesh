/*
 * ResQMesh_Civilian_Gateway.ino
 *
 * BLE <-> LoRa gateway for the civilian side of ResQMesh.
 *
 * Flow:
 *   Phone (BLE central) --write--> RX characteristic --> validate/dedup --> LoRa TX
 *   LoRa RX --> validate/dedup --> TX characteristic --notify--> Phone (BLE central)
 *
 * Hardware: ESP32 DevKit V1 / ESP32-WROOM-32 + Ai-Thinker Ra-02 (SX1278)
 *
 * Required Arduino libraries (install via Library Manager):
 *   - "LoRa" by Sandeep Mistry
 *   - "ArduinoJson" by Benoit Blanchon (v6.x)
 *   - ESP32 BLE Arduino (bundled with the ESP32 board package, no separate install)
 *
 * Board package: esp32 by Espressif Systems (installed via Boards Manager)
 *
 * NOT YET FLASHED/TESTED ON HARDWARE BY ME — I have no ESP32/Ra-02 in
 * this environment. This follows the standard Arduino-ESP32 BLE
 * peripheral pattern and the documented "LoRa" library API, but you
 * should watch the Serial Monitor (115200 baud) on first flash to
 * confirm LoRa.begin() succeeds and the BLE service starts advertising
 * before assuming the radio config is correct.
 *
 * ===========================================================
 *                  CONFIGURATION SECTION
 * ===========================================================
 * Every pin and radio parameter that might need to change for your
 * physical wiring lives here. Nothing below this section should need
 * editing for a rewiring.
 */

#include <SPI.h>
#include <LoRa.h>
#include <ArduinoJson.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// ---- BLE identity ----
#define BLE_DEVICE_NAME        "RESQMESH-GW"
#define SERVICE_UUID            "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
#define CHARACTERISTIC_UUID_RX  "6e400002-b5a3-f393-e0a9-e50e24dcca9e" // phone writes here
#define CHARACTERISTIC_UUID_TX  "6e400003-b5a3-f393-e0a9-e50e24dcca9e" // ESP32 notifies here

// ---- Node identity (NOT phone IMEI/number — a fixed gateway id) ----
#define GATEWAY_NODE_ID         "GW-CIVILIAN-001"

// ---- Ra-02 (SX1278) SPI pin mapping — conventional VSPI on ESP32 DevKit V1 ----
// Change these if your physical wiring differs. Nothing else in this
// file needs to change for a rewiring.
#define LORA_SCK   18
#define LORA_MISO  19
#define LORA_MOSI  23
#define LORA_SS    5
#define LORA_RST   14
#define LORA_DIO0  26

// ---- LoRa radio settings ----
// LORA_FREQUENCY confirmed as 433 MHz for these Ra-02 modules.
// Both the civilian and rescue gateways MUST use this identical value.
#define LORA_FREQUENCY           433E6
#define LORA_BANDWIDTH           125E3
#define LORA_SPREADING_FACTOR    7
#define LORA_CODING_RATE         5      // 4/5
#define LORA_SYNC_WORD           0x12   // private-network sync word, not the LoRaWAN public default
#define LORA_TX_POWER            17     // dBm

// ---- Packet handling ----
#define MAX_PACKET_JSON_BYTES    512
#define SEEN_CACHE_SIZE          32     // recently-seen messageIds, ring buffer

// ===========================================================
//                    STATE
// ===========================================================

BLECharacteristic *txCharacteristic;
bool bleDeviceConnected = false;
bool oldBleDeviceConnected = false;

String seenMessageIds[SEEN_CACHE_SIZE];
int seenCacheIndex = 0;

unsigned long packetsSentLora = 0;
unsigned long packetsReceivedLora = 0;
unsigned long packetsFromPhone = 0;
unsigned long duplicatesDropped = 0;

// ===========================================================
//                 DUPLICATE CACHE
// ===========================================================

bool isDuplicate(const String &messageId) {
  for (int i = 0; i < SEEN_CACHE_SIZE; i++) {
    if (seenMessageIds[i] == messageId) return true;
  }
  return false;
}

void markSeen(const String &messageId) {
  seenMessageIds[seenCacheIndex] = messageId;
  seenCacheIndex = (seenCacheIndex + 1) % SEEN_CACHE_SIZE;
}

// ===========================================================
//                 PACKET VALIDATION
// ===========================================================

// Returns true if the JSON document has the minimum required fields
// with sane types. Does not mutate doc.
bool isValidPacket(JsonDocument &doc) {
  if (!doc.containsKey("messageId") || !doc["messageId"].is<const char*>()) return false;
  if (!doc.containsKey("sourceNodeId") || !doc["sourceNodeId"].is<const char*>()) return false;
  if (!doc.containsKey("type") || !doc["type"].is<const char*>()) return false;
  if (!doc.containsKey("ttl") || !doc["ttl"].is<int>()) return false;
  if (doc["ttl"].as<int>() <= 0) return false;
  if (!doc.containsKey("version") || doc["version"].as<int>() != 1) return false;
  return true;
}

// ===========================================================
//                 LORA SEND
// ===========================================================

void sendOverLora(const String &json) {
  if (json.length() > MAX_PACKET_JSON_BYTES) {
    Serial.println("[LoRa TX] Packet too large, dropping: " + String(json.length()) + " bytes");
    return;
  }
  LoRa.beginPacket();
  LoRa.print(json);
  LoRa.endPacket();
  packetsSentLora++;
  Serial.println("[LoRa TX] " + json);
}

// ===========================================================
//                 BLE NOTIFY (ESP32 -> Phone)
// ===========================================================

void notifyPhone(const String &json) {
  if (!bleDeviceConnected) {
    Serial.println("[BLE TX] No phone connected, dropping notification");
    return;
  }
  txCharacteristic->setValue((uint8_t *)json.c_str(), json.length());
  txCharacteristic->notify();
  Serial.println("[BLE TX] " + json);
}

// ===========================================================
//         INCOMING FROM PHONE (BLE write to RX)
// ===========================================================

class RxCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *characteristic) override {
    String value = String(characteristic->getValue().c_str());
    if (value.length() == 0) return;

    packetsFromPhone++;
    Serial.println("[BLE RX] " + value);

    StaticJsonDocument<MAX_PACKET_JSON_BYTES> doc;
    DeserializationError err = deserializeJson(doc, value);
    if (err) {
      Serial.println("[BLE RX] JSON parse error: " + String(err.c_str()));
      return;
    }
    if (!isValidPacket(doc)) {
      Serial.println("[BLE RX] Packet failed validation, dropping");
      return;
    }

    String messageId = doc["messageId"].as<String>();
    if (isDuplicate(messageId)) {
      duplicatesDropped++;
      Serial.println("[BLE RX] Duplicate messageId, dropping: " + messageId);
      return;
    }
    markSeen(messageId);

    // Forward exactly as received onto LoRa. TTL/hopCount are the
    // phone-side mesh's responsibility to have already decremented
    // before it reaches the gateway; the gateway does not re-decrement
    // here, it's a bridge, not another mesh hop in the phone-side sense.
    String reSerialized;
    serializeJson(doc, reSerialized);
    sendOverLora(reSerialized);
  }
};

// ===========================================================
//         INCOMING FROM LORA (Rescue gateway, eventually)
// ===========================================================

void pollLora() {
  int packetSize = LoRa.parsePacket();
  if (packetSize == 0) return;

  String received = "";
  while (LoRa.available()) {
    received += (char)LoRa.read();
  }
  packetsReceivedLora++;
  Serial.println("[LoRa RX] RSSI=" + String(LoRa.packetRssi()) + " " + received);

  StaticJsonDocument<MAX_PACKET_JSON_BYTES> doc;
  DeserializationError err = deserializeJson(doc, received);
  if (err) {
    Serial.println("[LoRa RX] JSON parse error: " + String(err.c_str()));
    return;
  }
  if (!isValidPacket(doc)) {
    Serial.println("[LoRa RX] Packet failed validation, dropping");
    return;
  }

  String messageId = doc["messageId"].as<String>();
  if (isDuplicate(messageId)) {
    duplicatesDropped++;
    Serial.println("[LoRa RX] Duplicate messageId, dropping: " + messageId);
    return;
  }
  markSeen(messageId);

  String reSerialized;
  serializeJson(doc, reSerialized);
  notifyPhone(reSerialized);
}

// ===========================================================
//                 BLE CONNECTION CALLBACKS
// ===========================================================

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *server) override {
    bleDeviceConnected = true;
    Serial.println("[BLE] Phone connected");
  }
  void onDisconnect(BLEServer *server) override {
    bleDeviceConnected = false;
    Serial.println("[BLE] Phone disconnected, resuming advertising");
  }
};

// ===========================================================
//                       SETUP
// ===========================================================

void setup() {
  Serial.begin(115200);
  delay(300);
  Serial.println("\n=== ResQMesh Civilian Gateway ===");
  Serial.println("Node ID: " GATEWAY_NODE_ID);

  // ---- LoRa init ----
  SPI.begin(LORA_SCK, LORA_MISO, LORA_MOSI, LORA_SS);
  LoRa.setPins(LORA_SS, LORA_RST, LORA_DIO0);

  if (!LoRa.begin(LORA_FREQUENCY)) {
    Serial.println("[LoRa] begin() FAILED — check wiring/frequency, halting");
    while (true) { delay(1000); }
  }
  LoRa.setSignalBandwidth(LORA_BANDWIDTH);
  LoRa.setSpreadingFactor(LORA_SPREADING_FACTOR);
  LoRa.setCodingRate4(LORA_CODING_RATE);
  LoRa.setSyncWord(LORA_SYNC_WORD);
  LoRa.setTxPower(LORA_TX_POWER);
  Serial.println("[LoRa] Radio initialized OK");

  // ---- BLE init (peripheral/server role — the phone is central) ----
  BLEDevice::init(BLE_DEVICE_NAME);
  BLEServer *server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());

  BLEService *service = server->createService(SERVICE_UUID);

  BLECharacteristic *rxCharacteristic = service->createCharacteristic(
      CHARACTERISTIC_UUID_RX,
      BLECharacteristic::PROPERTY_WRITE
  );
  rxCharacteristic->setCallbacks(new RxCallbacks());

  txCharacteristic = service->createCharacteristic(
      CHARACTERISTIC_UUID_TX,
      BLECharacteristic::PROPERTY_NOTIFY
  );
  txCharacteristic->addDescriptor(new BLE2902()); // required for notifications (CCCD)

  service->start();

  BLEAdvertising *advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);
  advertising->setScanResponse(true);
  BLEDevice::startAdvertising();
  Serial.println("[BLE] Advertising as " BLE_DEVICE_NAME);
}

// ===========================================================
//                       LOOP
// ===========================================================

void loop() {
  pollLora();

  // Standard Arduino-ESP32 BLE re-advertise idiom: startAdvertising()
  // is not automatically resumed after a disconnect on some core
  // versions, so we restart it explicitly.
  if (!bleDeviceConnected && oldBleDeviceConnected) {
    delay(200);
    BLEDevice::startAdvertising();
    Serial.println("[BLE] Re-advertising after disconnect");
    oldBleDeviceConnected = bleDeviceConnected;
  }
  if (bleDeviceConnected && !oldBleDeviceConnected) {
    oldBleDeviceConnected = bleDeviceConnected;
  }

  // Lightweight periodic diagnostics — useful during bring-up, cheap to
  // leave in for the demo.
  static unsigned long lastStatsPrint = 0;
  if (millis() - lastStatsPrint > 10000) {
    lastStatsPrint = millis();
    Serial.printf(
      "[STATS] fromPhone=%lu loraTx=%lu loraRx=%lu duplicates=%lu bleConnected=%d\n",
      packetsFromPhone, packetsSentLora, packetsReceivedLora, duplicatesDropped, bleDeviceConnected
    );
  }
}
