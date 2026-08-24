# ResQTeam

> **The rescue-side application of ResQMesh.**

ResQTeam is the Android application designed for **rescue teams** receiving emergency messages through the ResQMesh offline communication network.

It is the receiving and operational side of the system:

```text
Civilian Phone
      │
      │ Offline mesh
      ▼
Civilian / Relay Nodes
      │
      │ BLE / LoRa gateway
      ▼
Rescue Gateway ESP32
      │
      │ Bluetooth Classic
      ▼
ResQTeam Android App
      │
      ├── Emergency alerts
      ├── Priority
      ├── Victim details
      ├── Location
      └── Navigation
```

---

## 🚧 Current Status

**Prototype — Working, but still under development**

The current ResQTeam prototype can communicate with the dedicated rescue-side ESP32 gateway and receive distress messages from the gateway.

The application is **not production-ready** and should not be relied upon as the sole emergency communication or dispatch system.

The current prototype still needs work in areas such as:

- More robust message parsing
- Reliable reconnection
- Persistent emergency storage
- Delivery acknowledgements
- Better error handling
- Background Bluetooth reliability
- Authentication and encryption
- Production-grade rescue workflow
- Real-world hardware and range testing
- Integration hardening with the full ResQMesh network

---

# 🧩 Rescue-Side Architecture

The rescue side currently uses a dedicated ESP32 receiver.

```text
Sender ESP32
     │
     │ BLE
     ▼
┌───────────────────────┐
│ RESQMESH-RECEIVER     │
│ Rescue Receiver ESP32 │
└───────────┬───────────┘
            │
            │ Bluetooth Classic
            ▼
┌───────────────────────┐
│ ResQTeam Android App  │
└───────────────────────┘
```

The receiver ESP32 exposes:

### BLE

```text
Device:
RESQMESH-RECEIVER
```

Service:

```text
6e400001-b5a3-f393-e0a9-e50e24dcca9e
```

RX:

```text
6e400002-b5a3-f393-e0a9-e50e24dcca9e
```

TX:

```text
6e400003-b5a3-f393-e0a9-e50e24dcca9e
```

### Bluetooth Classic

```text
Device:
ResQTeam-ESP32
```

The receiver uses Bluetooth Classic SPP to send newline-delimited messages to the Android application.

---

# 📱 What Currently Works

The current prototype supports the following flow:

```text
Sender ESP32
      ↓
     BLE
      ↓
Receiver ESP32
      ↓
Bluetooth Classic
      ↓
ResQTeam App
```

When the receiver ESP32 receives a message from the sender:

1. It receives the BLE write.
2. Reads the message.
3. Prints the received packet to Serial.
4. Checks whether the rescue application is connected.
5. Sends the message through Bluetooth Classic.
6. The Android application receives the message.

The ESP32 uses:

```cpp
SerialBT.println(message);
```

so that the Android application can process newline-delimited messages.

---

# 🚨 Rescue Team Workflow

The intended rescue workflow is:

```text
NEW EMERGENCY
      ↓
Priority classification
      ↓
View emergency details
      ↓
View victim location
      ↓
Navigate to victim
      ↓
Respond / acknowledge
```

Emergency messages can eventually contain information such as:

- Emergency type
- Priority
- Source node
- Timestamp
- Latitude
- Longitude
- Battery
- Number of people
- Medical information
- Hop count
- TTL
- Message ID

The current prototype is focused on establishing the **gateway → rescue application communication path** before hardening the complete rescue workflow.

---

# 🔌 ESP32 Receiver

The rescue receiver firmware is located in the ESP32 portion of this project.

Its role is deliberately simple:

```text
BLE Receiver
     +
Bluetooth Classic Sender
```

It does not perform the main mesh-routing logic.

Its job is to bridge the incoming rescue-side communication to the ResQTeam Android application.

### Device names

BLE:

```text
RESQMESH-RECEIVER
```

Bluetooth Classic:

```text
ResQTeam-ESP32
```

---

# 🧪 How to Test

## Android

Requirements:

- Android Studio
- JDK 17
- Compatible Android device
- Bluetooth enabled
- Required Bluetooth permissions granted

Build the application:

```bash
./gradlew assembleDebug
```

Windows:

```powershell
.\gradlew.bat assembleDebug
```

---

## ESP32

Upload the receiver firmware to an ESP32 board.

Open the Serial Monitor at:

```text
115200 baud
```

The receiver should display:

```text
RESQMESH RECEIVER ESP32
BLE RECEIVER READY
STARTING BLUETOOTH CLASSIC
RECEIVER FULLY READY
```

It will then wait for:

```text
Sender ESP32
```

and:

```text
ResQTeam App
```

---

# 🔄 Connection Sequence

### Step 1 — Start receiver

Power the ESP32.

It begins BLE advertising and Bluetooth Classic.

### Step 2 — Connect sender

The sender ESP32 connects to:

```text
RESQMESH-RECEIVER
```

### Step 3 — Connect rescue phone

The ResQTeam Android application connects to:

```text
ResQTeam-ESP32
```

### Step 4 — Send emergency

The sender sends:

```text
Emergency message
```

through BLE.

### Step 5 — Gateway forwards

The receiver ESP32 forwards the message:

```text
BLE → Bluetooth Classic
```

### Step 6 — ResQTeam receives

The Android application displays the received emergency.

---

# 📁 Project Structure

```text
ResQTeam/
│
├── app/
│   └── src/
│       └── main/
│           ├── java/
│           └── res/
│
├── esp32/
│   └── receiver firmware
│
├── gradle/
│
├── README.md
├── .gitignore
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

# 🔐 Security

This is an experimental prototype.

The current implementation does **not** provide production-grade:

- End-to-end encryption
- Device authentication
- Message signatures
- Anti-spoofing
- Secure key management
- Guaranteed delivery

These must be implemented before deployment in real emergency operations.

---

# 🛠️ Known Limitations

The prototype currently depends on a specific communication chain:

```text
Sender ESP32
      ↓ BLE
Receiver ESP32
      ↓ Bluetooth Classic
Android
```

It does not yet provide a fully autonomous production rescue network.

Other limitations include:

- Single rescue gateway architecture
- No persistent message database
- Limited reconnection logic
- No complete ACK protocol
- No authenticated emergency packets
- No production dispatch backend
- Limited hardware failure handling
- No comprehensive automated tests

---

# 🗺️ Next Development Steps

### Phase 1 — Gateway communication

**Current**

```text
Sender ESP32 → Receiver ESP32 → ResQTeam
```

### Phase 2 — Robust packet processing

- Structured JSON / MeshPacket parsing
- Message IDs
- Duplicate protection
- Validation
- TTL handling

### Phase 3 — Rescue dashboard

- Priority sorting
- Emergency queue
- Victim details
- Map
- Navigation
- Status updates

### Phase 4 — Reliability

- Persistent database
- Reconnection
- Retry queues
- ACKs
- Delivery status

### Phase 5 — Security

- Encryption
- Authentication
- Signed messages
- Secure gateway pairing

### Phase 6 — Full ResQMesh integration

```text
Civilian Phones
      ↓
Phone Mesh
      ↓
Civilian Gateway
      ↓
LoRa
      ↓
Rescue Gateway
      ↓
ResQTeam
```

---

# ⚠️ Prototype Disclaimer

ResQTeam is currently a **working engineering prototype**.

It demonstrates the intended rescue-side communication path, but it still requires substantial development and testing before it can be considered suitable for real emergency operations.

**Do not use this prototype as the sole emergency communication system in a real disaster.**

---

# ❤️ ResQTeam

ResQTeam is one part of the larger ResQMesh ecosystem.

The goal is to connect people in distress with the people trying to rescue them — even when conventional communication infrastructure is unavailable.

> **When the network dies, we become the network.**
