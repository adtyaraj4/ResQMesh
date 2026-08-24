#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>


// ============================================================
// OPTIONAL LORA
// Currently disabled
// ============================================================

// #include <SPI.h>
// #include <LoRa.h>

#define LORA_SCK        18
#define LORA_MISO       19
#define LORA_MOSI       23
#define LORA_SS         5
#define LORA_RST        14
#define LORA_DIO0       2

#define LORA_FREQUENCY  433E6

#define USE_LORA        false


// ============================================================
// PEOPLE PHONE BLE
// ============================================================

#define PEOPLE_DEVICE_NAME "RESQMESH-GW"

#define SERVICE_UUID \
    "6e400001-b5a3-f393-e0a9-e50e24dcca9e"

#define RX_CHARACTERISTIC_UUID \
    "6e400002-b5a3-f393-e0a9-e50e24dcca9e"

#define TX_CHARACTERISTIC_UUID \
    "6e400003-b5a3-f393-e0a9-e50e24dcca9e"


// ============================================================
// RECEIVER ESP32
// ============================================================

#define RECEIVER_DEVICE_NAME "RESQMESH-RECEIVER"


// ============================================================
// GLOBALS
// ============================================================

BLECharacteristic* phoneTxCharacteristic = nullptr;

BLEClient* receiverClient = nullptr;

BLERemoteCharacteristic* receiverRxCharacteristic = nullptr;


// Phone connection
bool phoneConnected = false;

// Receiver connection
bool receiverConnected = false;


// ============================================================
// MESSAGE QUEUE
// ============================================================

// Message received from the phone.
// We store it first and forward it from loop().
String pendingMessage = "";

bool messagePending = false;


// ============================================================
// LORA SETUP
// ============================================================

void setupLoRa() {

    if (!USE_LORA) {

        Serial.println("[LoRa] Disabled.");

        return;
    }


    /*
    SPI.begin(
        LORA_SCK,
        LORA_MISO,
        LORA_MOSI,
        LORA_SS
    );


    LoRa.setPins(
        LORA_SS,
        LORA_RST,
        LORA_DIO0
    );


    if (!LoRa.begin(LORA_FREQUENCY)) {

        Serial.println("[LoRa] FAILED!");

        return;
    }


    Serial.println("[LoRa] Started.");
    */
}


// ============================================================
// CONNECT TO RECEIVER
// ============================================================

bool connectToReceiver() {

    Serial.println();
    Serial.println("========================================");
    Serial.println("       SEARCHING FOR RECEIVER");
    Serial.println("========================================");


    BLEScan* scan =
        BLEDevice::getScan();


    scan->setActiveScan(true);


    BLEScanResults* results =
        scan->start(5, false);


    BLEAdvertisedDevice* receiverDevice =
        nullptr;


    // --------------------------------------------------------
    // SEARCH SCAN RESULTS
    // --------------------------------------------------------

    for (int i = 0; i < results->getCount(); i++) {

        BLEAdvertisedDevice device =
            results->getDevice(i);


        if (!device.haveName()) {
            continue;
        }


        String name =
            device.getName().c_str();


        Serial.print("[BLE] Found device: ");
        Serial.println(name);


        if (name == RECEIVER_DEVICE_NAME) {

            receiverDevice =
                new BLEAdvertisedDevice(device);

            break;
        }
    }


    // --------------------------------------------------------
    // RECEIVER NOT FOUND
    // --------------------------------------------------------

    if (receiverDevice == nullptr) {

        Serial.println(
            "[BLE] Receiver NOT found."
        );

        scan->clearResults();

        return false;
    }


    Serial.println(
        "[BLE] Receiver found!"
    );


    // --------------------------------------------------------
    // CREATE BLE CLIENT
    // --------------------------------------------------------

    if (receiverClient != nullptr) {

        if (receiverClient->isConnected()) {

            receiverClient->disconnect();
        }

        delete receiverClient;

        receiverClient = nullptr;
    }


    receiverClient =
        BLEDevice::createClient();


    // --------------------------------------------------------
    // CONNECT
    // --------------------------------------------------------

    Serial.println(
        "[BLE] Connecting to receiver..."
    );


    if (!receiverClient->connect(receiverDevice)) {

        Serial.println(
            "[BLE] FAILED to connect to receiver."
        );


        delete receiverDevice;

        scan->clearResults();

        receiverConnected = false;

        return false;
    }


    Serial.println(
        "[BLE] Connected to receiver!"
    );


    // --------------------------------------------------------
    // FIND SERVICE
    // --------------------------------------------------------

    BLERemoteService* service =
        receiverClient->getService(
            SERVICE_UUID
        );


    if (service == nullptr) {

        Serial.println(
            "[BLE] Receiver service NOT found."
        );


        receiverClient->disconnect();

        delete receiverDevice;

        scan->clearResults();

        receiverConnected = false;

        return false;
    }


    Serial.println(
        "[BLE] Receiver service found."
    );


    // --------------------------------------------------------
    // FIND RX CHARACTERISTIC
    // --------------------------------------------------------

    receiverRxCharacteristic =
        service->getCharacteristic(
            RX_CHARACTERISTIC_UUID
        );


    if (receiverRxCharacteristic == nullptr) {

        Serial.println(
            "[BLE] Receiver RX characteristic NOT found."
        );


        receiverClient->disconnect();

        delete receiverDevice;

        scan->clearResults();

        receiverConnected = false;

        return false;
    }


    Serial.println(
        "[BLE] Receiver RX characteristic found."
    );


    // --------------------------------------------------------
    // CHECK WRITE SUPPORT
    // --------------------------------------------------------

    Serial.print(
        "[BLE] Can write: "
    );

    Serial.println(
        receiverRxCharacteristic->canWrite()
            ? "YES"
            : "NO"
    );


    Serial.print(
        "[BLE] Can write without response: "
    );

    Serial.println(
        receiverRxCharacteristic->canWriteNoResponse()
            ? "YES"
            : "NO"
    );


    if (!receiverRxCharacteristic->canWrite() &&
        !receiverRxCharacteristic->canWriteNoResponse()) {

        Serial.println(
            "[BLE] ERROR: RX characteristic is not writable!"
        );


        receiverClient->disconnect();

        delete receiverDevice;

        scan->clearResults();

        receiverConnected = false;

        return false;
    }


    // --------------------------------------------------------
    // READY
    // --------------------------------------------------------

    receiverConnected = true;


    Serial.println();
    Serial.println("========================================");
    Serial.println("       RECEIVER COMMUNICATION READY");
    Serial.println("========================================");


    delete receiverDevice;

    scan->clearResults();


    return true;
}


// ============================================================
// FORWARD MESSAGE TO RECEIVER
// ============================================================

bool forwardToReceiver(String message) {

    message.trim();


    if (message.length() == 0) {

        return false;
    }


    // --------------------------------------------------------
    // CHECK CONNECTION
    // --------------------------------------------------------

    if (receiverClient == nullptr ||
        !receiverClient->isConnected() ||
        receiverRxCharacteristic == nullptr) {

        Serial.println(
            "[BLE] Receiver is NOT connected."
        );

        receiverConnected = false;

        return false;
    }


    // --------------------------------------------------------
    // DISPLAY MESSAGE
    // --------------------------------------------------------

    Serial.println();
    Serial.println("========================================");
    Serial.println("       FORWARDING TO RECEIVER");
    Serial.println("========================================");

    Serial.print("Message length: ");
    Serial.println(message.length());

    Serial.println();

    Serial.println(message);

    Serial.println();


    // --------------------------------------------------------
    // WRITE TO RECEIVER
    // --------------------------------------------------------

    Serial.println(
        "[BLE] Writing message..."
    );


    bool success =
        receiverRxCharacteristic->writeValue(
            message,
            true
        );


    // --------------------------------------------------------
    // CHECK RESULT
    // --------------------------------------------------------

    if (success) {

        Serial.println(
            "[BLE] WRITE SUCCESS!"
        );

        Serial.println(
            "[BLE] Receiver acknowledged the message."
        );

    } else {

        Serial.println(
            "[BLE] WRITE FAILED!"
        );

        receiverConnected = false;
    }


    Serial.println(
        "========================================"
    );


    return success;
}


// ============================================================
// PEOPLE PHONE BLE CALLBACKS
// ============================================================

class ServerCallbacks :
    public BLEServerCallbacks {

public:

    void onConnect(
        BLEServer* server
    ) override {

        phoneConnected = true;


        Serial.println();
        Serial.println("========================================");
        Serial.println("       PEOPLE PHONE CONNECTED");
        Serial.println("========================================");
    }


    void onDisconnect(
        BLEServer* server
    ) override {

        phoneConnected = false;


        Serial.println();
        Serial.println(
            "       PEOPLE PHONE DISCONNECTED"
        );


        delay(200);


        server->getAdvertising()->start();
    }
};


// ============================================================
// PEOPLE PHONE → SENDER ESP32
// ============================================================

class PhoneRxCallbacks :
    public BLECharacteristicCallbacks {

public:

    void onWrite(
        BLECharacteristic* characteristic
    ) override {


        // ----------------------------------------------------
        // GET MESSAGE FROM PHONE
        // ----------------------------------------------------

        String message =
            characteristic->getValue().c_str();


        if (message.length() == 0) {

            return;
        }


        // ----------------------------------------------------
        // DISPLAY MESSAGE
        // ----------------------------------------------------

        Serial.println();
        Serial.println("========================================");
        Serial.println("       MESSAGE FROM PEOPLE PHONE");
        Serial.println("========================================");

        Serial.print("Message length: ");
        Serial.println(message.length());

        Serial.println();

        Serial.println(message);

        Serial.println();


        // ----------------------------------------------------
        // STORE MESSAGE
        //
        // IMPORTANT:
        // Do NOT perform another BLE write directly inside
        // this callback.
        // ----------------------------------------------------

        pendingMessage = message;

        messagePending = true;


        Serial.println(
            "[BLE] Message queued for receiver."
        );


        // ----------------------------------------------------
        // FUTURE LORA
        // ----------------------------------------------------

        if (USE_LORA) {

            /*
            LoRa.beginPacket();

            LoRa.print(message);

            LoRa.endPacket();
            */
        }
    }
};


// ============================================================
// START PEOPLE PHONE BLE SERVER
// ============================================================

void setupPeopleBLE() {

    Serial.println(
        "[BLE] Starting People Phone BLE..."
    );


    // --------------------------------------------------------
    // INITIALIZE BLE
    // --------------------------------------------------------

    if (!BLEDevice::init(
            PEOPLE_DEVICE_NAME
        )) {

        Serial.println(
            "[BLE] BLE initialization FAILED!"
        );

        return;
    }


    // --------------------------------------------------------
    // CREATE SERVER
    // --------------------------------------------------------

    BLEServer* server =
        BLEDevice::createServer();


    server->setCallbacks(
        new ServerCallbacks()
    );


    // --------------------------------------------------------
    // CREATE SERVICE
    // --------------------------------------------------------

    BLEService* service =
        server->createService(
            SERVICE_UUID
        );


    // --------------------------------------------------------
    // RX CHARACTERISTIC
    //
    // Phone writes messages here.
    // --------------------------------------------------------

    BLECharacteristic* rx =
        service->createCharacteristic(

            RX_CHARACTERISTIC_UUID,

            BLECharacteristic::PROPERTY_WRITE |
            BLECharacteristic::PROPERTY_WRITE_NR
        );


    rx->setCallbacks(
        new PhoneRxCallbacks()
    );


    // --------------------------------------------------------
    // TX CHARACTERISTIC
    //
    // Used for future notifications.
    // --------------------------------------------------------

    phoneTxCharacteristic =
        service->createCharacteristic(

            TX_CHARACTERISTIC_UUID,

            BLECharacteristic::PROPERTY_NOTIFY
        );


    phoneTxCharacteristic->addDescriptor(
        new BLE2902()
    );


    // --------------------------------------------------------
    // START SERVICE
    // --------------------------------------------------------

    service->start();


    // --------------------------------------------------------
    // ADVERTISE
    // --------------------------------------------------------

    BLEAdvertising* advertising =
        BLEDevice::getAdvertising();


    advertising->addServiceUUID(
        SERVICE_UUID
    );


    advertising->setScanResponse(true);


    BLEDevice::startAdvertising();


    Serial.println(
        "[BLE] People BLE ready."
    );


    Serial.print(
        "[BLE] Device name: "
    );

    Serial.println(
        PEOPLE_DEVICE_NAME
    );
}


// ============================================================
// SETUP
// ============================================================

void setup() {

    Serial.begin(115200);


    delay(1000);


    Serial.println();
    Serial.println("========================================");
    Serial.println("       RESQMESH SENDER ESP32");
    Serial.println("========================================");


    // --------------------------------------------------------
    // LORA
    // --------------------------------------------------------

    setupLoRa();


    // --------------------------------------------------------
    // START PEOPLE PHONE BLE SERVER
    // --------------------------------------------------------

    setupPeopleBLE();


    // --------------------------------------------------------
    // GIVE BLE SERVER TIME TO START
    // --------------------------------------------------------

    delay(2000);


    // --------------------------------------------------------
    // CONNECT SENDER → RECEIVER
    // --------------------------------------------------------

    connectToReceiver();


    // --------------------------------------------------------
    // READY
    // --------------------------------------------------------

    Serial.println();
    Serial.println("========================================");
    Serial.println("       SENDER READY");
    Serial.println("========================================");

    Serial.println(
        "Waiting for People Phone..."
    );

    Serial.println(
        "Waiting for Receiver ESP32..."
    );

    Serial.println();
}


// ============================================================
// LOOP
// ============================================================

void loop() {


    // ========================================================
    // HANDLE PENDING PHONE MESSAGE
    // ========================================================

    if (messagePending) {

        // Copy message locally

        String messageToSend =
            pendingMessage;


        // Clear queue BEFORE sending

        pendingMessage = "";

        messagePending = false;


        // ----------------------------------------------------
        // Make sure receiver is connected
        // ----------------------------------------------------

        if (!receiverConnected) {

            Serial.println(
                "[BLE] Receiver disconnected."
            );

            Serial.println(
                "[BLE] Attempting reconnection..."
            );


            connectToReceiver();
        }


        // ----------------------------------------------------
        // Forward message
        // ----------------------------------------------------

        if (receiverConnected) {

            forwardToReceiver(
                messageToSend
            );

        } else {

            Serial.println(
                "[BLE] Cannot forward message."
            );

            Serial.println(
                "[BLE] Receiver unavailable."
            );
        }
    }


    // ========================================================
    // AUTOMATIC RECEIVER RECONNECTION
    // ========================================================

    static unsigned long lastAttempt = 0;


    if (!receiverConnected) {

        if (millis() - lastAttempt > 5000) {

            lastAttempt = millis();

            connectToReceiver();
        }
    }


    // ========================================================
    // SMALL LOOP DELAY
    // ========================================================

    delay(20);
}