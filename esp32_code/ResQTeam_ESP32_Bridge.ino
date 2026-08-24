/*
 * ResQTeam ESP32 Bridge — Milestone 1 & 2
 * ----------------------------------------
 * Scope of THIS sketch (per the project's prototype-first rule): prove the
 * Bluetooth Classic leg — ESP32 -> ResQTeam Android app — works end to end,
 * using canned test packets typed at the Serial Monitor. This intentionally
 * does NOT touch LoRa/SX1278 yet; that's the next milestone once this leg is
 * verified on real hardware (spec sections 46, 49-51).
 *
 * Bluetooth device name MUST be exactly "ResQTeam-ESP32" (spec section 19) —
 * the Android app looks up a paired device with this exact name.
 *
 * HOW TO USE
 * 1. Flash this to an ESP32 dev board.
 * 2. Pair "ResQTeam-ESP32" from the rescue phone's Bluetooth settings once
 *    (classic SPP pairing, no PIN needed on most stacks).
 * 3. Open the Arduino Serial Monitor at 115200 baud, line ending "Newline".
 * 4. Type one of:
 *      hello   -> sends the milestone-1 sanity string
 *      trapped -> sends a sample TRAPPED/CRITICAL packet
 *      medical -> sends a sample MEDICAL/CRITICAL packet
 *      evac    -> sends a sample EVACUATION/HIGH packet
 *      supply  -> sends a sample SUPPLIES/MEDIUM packet
 *    and press Enter. The app should show a "Test Packet Received" banner
 *    for `hello`, and a new incident card for the others.
 *
 * WHEN LORA IS ADDED (next milestone)
 * Keep this Bluetooth-forwarding logic completely separate from the LoRa
 * receive code (spec section 41: "clearly separate the LoRa transport code
 * from the Bluetooth bridge code"). The pin defines below are placeholders
 * for that milestone — do not wire hardware to them yet; verify against
 * your actual ESP32 board's silkscreen/datasheet first (spec section 42).
 */

#include "BluetoothSerial.h"

#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error Bluetooth is not enabled! Enable it in "Tools -> Board" configuration.
#endif

BluetoothSerial SerialBT;

// ---- Placeholders for the LoRa milestone — NOT wired/used in this sketch ----
// #define LORA_SCK   18
// #define LORA_MISO  19
// #define LORA_MOSI  23
// #define LORA_SS    5
// #define LORA_RST   14
// #define LORA_DIO0  2
// Confirm these against your specific board before the LoRa milestone.

unsigned long messageCounter = 0;

void setup() {
  Serial.begin(115200);
  SerialBT.begin("ResQTeam-ESP32"); // Classic SPP, discoverable + connectable
  Serial.println("[BOOT] ResQTeam-ESP32 bridge ready.");
  Serial.println("[BOOT] Type: hello | trapped | medical | evac | supply");
}

void loop() {
  if (Serial.available()) {
    String cmd = Serial.readStringUntil('\n');
    cmd.trim();
    handleCommand(cmd);
  }

  // Keep the classic BT link responsive even with nothing to send.
  if (SerialBT.available()) {
    // Drain anything the phone sends back (future two-way commands, spec 40).
    String fromPhone = SerialBT.readStringUntil('\n');
    Serial.print("[BT RX] ");
    Serial.println(fromPhone);
  }

  delay(20);
}

void handleCommand(const String &cmd) {
  if (cmd == "hello") {
    sendLine("HELLO FROM RESQMESH");
  } else if (cmd == "trapped") {
    sendLine(buildPacket("TRAPPED", 5, "NODE-A7F2", 13.0827, 80.2707, 61, 5, 3, 4, 2));
  } else if (cmd == "medical") {
    sendLine(buildPacket("MEDICAL", 5, "NODE-B821", 13.0790, 80.2660, 54, 4, 2, 1, 1));
  } else if (cmd == "evac") {
    sendLine(buildPacket("EVACUATION", 4, "NODE-C312", 13.0850, 80.2740, 72, 6, 1, 6, 0));
  } else if (cmd == "supply") {
    sendLine(buildPacket("SUPPLIES", 3, "NODE-D004", 13.0801, 80.2695, 40, 6, 4, 12, 0));
  } else if (cmd.length() > 0) {
    Serial.println("[BOOT] Unknown command. Type: hello | trapped | medical | evac | supply");
  }
}

void sendLine(const String &line) {
  SerialBT.println(line); // newline-delimited, per spec section 22
  Serial.print("[BT TX] ");
  Serial.println(line);
}

String buildPacket(
    const char *type,
    int priority,
    const char *sourceNodeId,
    double lat,
    double lon,
    int battery,
    int ttl,
    int hopCount,
    int peopleCount,
    int injuredCount) {

  messageCounter++;
  char messageId[24];
  snprintf(messageId, sizeof(messageId), "MSG-%06lX", messageCounter);

  char json[320];
  snprintf(
      json,
      sizeof(json),
      "{\"messageId\":\"%s\",\"sourceNodeId\":\"%s\",\"type\":\"%s\","
      "\"priority\":%d,\"latitude\":%.4f,\"longitude\":%.4f,"
      "\"timestamp\":%lu,\"battery\":%d,\"ttl\":%d,\"hopCount\":%d,"
      "\"peopleCount\":%d,\"injuredCount\":%d}",
      messageId, sourceNodeId, type, priority, lat, lon,
      (unsigned long)(millis() / 1000), battery, ttl, hopCount,
      peopleCount, injuredCount);

  return String(json);
}
