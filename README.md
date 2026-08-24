# ResQMesh

> **WHEN THE NETWORK DIES, WE BECOME THE NETWORK.**

ResQMesh is an **offline-first emergency communication ecosystem** designed to keep distress messages moving when cellular or internet infrastructure is unavailable.

Instead of depending on a central server, ResQMesh explores a distributed architecture in which nearby phones, ESP32 gateways and LoRa links cooperate to carry emergency information across a disaster area.

---

# 🌐 System Overview

The complete system is divided into two major sides.

## Civilian Side

```text
Civilian Phone
      │
      │ Nearby Connections
      ▼
Nearby Phones
      │
      │ Multi-hop mesh
      ▼
Civilian ESP32 Gateway
      │
      │ BLE
      ▼
ESP32
      │
      │ LoRa
      ▼
Remote Rescue Gateway
```

## Rescue Side

```text
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

The long-term architecture is:

```text
PERSON
  │
  ▼
PHONE A
  │
  ▼
PHONE B
  │
  ▼
PHONE C
  │
  ▼
CIVILIAN GATEWAY
  │
  │ LoRa
  ▼
RESCUE GATEWAY
  │
  ▼
RESQTEAM
  │
  ▼
RESCUE TEAM
```

---

# 📈 How ResQMesh Has Progressed

## Stage 1 — Direct Offline Phone Communication

The project started with a simple proof of concept:

```text
Phone A ↔ Phone B
```

Using Google Nearby Connections, two physical Android devices could discover one another and exchange data without relying on an internet backend.

The project uses:

```text
P2P_CLUSTER
```

because a future mesh node must be capable of communicating with multiple neighboring nodes.

---

# Stage 2 — Structured Mesh Packets

The system then moved from simple text transmission to a structured `MeshPacket`.

Packets can contain:

```text
messageId
sourceNodeId
destinationNodeId
type
payload
timestamp
ttl
hopCount
priority
origin
latitude
longitude
batteryLevel
peopleCount
medicalCondition
```

This creates a common protocol between different transports.

```text
Nearby
   │
BLE
   │
LoRa
   │
   ▼
MeshPacket
```

The message structure remains consistent regardless of how the packet physically travels.

---

# Stage 3 — Multi-Hop Mesh

ResQMesh now supports the concept of relay nodes.

For example:

```text
A → B → C
```

If A cannot directly reach C, B can receive and forward the packet.

The routing layer handles:

1. Packet validation
2. Duplicate detection
3. Local delivery
4. TTL decrement
5. Hop-count increment
6. Forwarding

This is the core of the ResQMesh mesh.

---

# Stage 4 — Duplicate Protection

A mesh can accidentally create loops:

```text
A → B → C → A → B → C
```

ResQMesh uses a message ID and recently-seen cache.

If a node receives a packet it has already processed, it does not forward it again.

This prevents uncontrolled packet duplication.

---

# Stage 5 — TTL and Hop Count

Packets contain a TTL.

Current default:

```text
TTL = 8
```

Each forward:

```text
TTL - 1
Hop Count + 1
```

When TTL reaches zero, the packet stops propagating.

This provides a basic protection against indefinite message circulation.

---

# Stage 6 — Store-and-Forward

A message should not disappear just because a node is temporarily disconnected.

The mesh therefore maintains an in-memory queue.

```text
Emergency
   │
   ▼
No peer
   │
   ▼
STORED
   │
   ▼
Peer appears
   │
   ▼
FORWARDED
```

### Current limitation

The queue is currently in memory.

It does not yet survive:

- App process termination
- App restart
- Device reboot

Persistent storage is planned.

---

# Stage 7 — Emergency Message System

ResQMesh moved beyond generic text packets and introduced emergency types and priorities.

Current emergency categories include:

```text
TRAPPED
MEDICAL
EVACUATION
SUPPLIES
SAFE
ACK
```

Priorities include:

```text
CRITICAL
HIGH
MEDIUM
STATUS
```

This allows the rescue side to eventually prioritize emergencies instead of treating every packet equally.

---

# Stage 8 — Location and Battery

Emergency packets can contain:

- Latitude
- Longitude
- Accuracy
- Location timestamp
- Battery percentage
- Number of people
- Medical information

The emergency message can still be sent if a current GPS fix cannot be obtained.

The design principle is:

> **GPS should improve an emergency message, not prevent the emergency message from being sent.**

---

# Stage 9 — BLE Gateway

ResQMesh introduced an ESP32 BLE gateway.

The Android side can communicate with an ESP32 through BLE.

Architecture:

```text
Android
   │
   │ BLE
   ▼
ESP32
```

This provides a bridge between the Android mesh and external radio hardware.

---

# Stage 10 — LoRa Gateway

The ESP32 gateway was extended with an Ai-Thinker Ra-02 / SX1278 LoRa radio.

Architecture:

```text
Android
   │
   │ BLE
   ▼
ESP32
   │
   │ SPI
   ▼
Ra-02
   │
   │ LoRa
   ▼
Remote Gateway
```

The gateway can act as a BLE ↔ LoRa bridge.

---

# Stage 11 — Rescue Team System

The rescue-side application, **ResQTeam**, is now being developed as the operational receiver.

Current prototype chain:

```text
Sender ESP32
      │
      │ BLE
      ▼
Rescue Receiver ESP32
      │
      │ Bluetooth Classic
      ▼
ResQTeam Android App
```

The receiver ESP32 uses:

```text
BLE device:
RESQMESH-RECEIVER
```

and:

```text
Bluetooth Classic:
ResQTeam-ESP32
```

The receiver accepts messages from the sender and forwards them to the rescue phone.

The ResQTeam application is **working as a prototype**, but it is still under active development and needs reliability, security and production hardening.

---

# 🔄 Complete Communication Vision

The complete intended system is:

```text
┌─────────────────┐
│  Person Phone   │
└────────┬────────┘
         │
         │ Nearby
         ▼
┌─────────────────┐
│  Mesh Phone     │
└────────┬────────┘
         │
         │ Multi-hop
         ▼
┌─────────────────┐
│ Civilian Gateway│
│     ESP32       │
└────────┬────────┘
         │
         │ BLE
         ▼
┌─────────────────┐
│      ESP32      │
│      Ra-02      │
└────────┬────────┘
         │
         │ LoRa
         ▼
┌─────────────────┐
│ Rescue Gateway  │
│      ESP32      │
└────────┬────────┘
         │
         │ Bluetooth
         ▼
┌─────────────────┐
│   ResQTeam App  │
└────────┬────────┘
         │
         ▼
    Rescue Team
```

---

# 📱 Applications

## ResQMesh — Civilian App

Responsible for:

- Node identity
- Nearby discovery
- Mesh communication
- Emergency generation
- Packet routing
- TTL
- Deduplication
- Store-and-forward
- GPS
- Battery information
- Gateway connectivity

## ResQTeam — Rescue App

Responsible for:

- Receiving emergencies
- Emergency prioritization
- Victim details
- Location display
- Navigation
- Rescue-side status

---

# 📡 Communication Technologies

| Layer | Technology |
|---|---|
| Civilian phone mesh | Google Nearby Connections |
| Phone strategy | `P2P_CLUSTER` |
| Android ↔ ESP32 | BLE |
| ESP32 ↔ ESP32 | LoRa |
| Rescue ESP32 ↔ phone | Bluetooth Classic |
| LoRa radio | SX1278 / Ra-02 |
| Packet format | MeshPacket |
| Serialization | JSON / Kotlin Serialization |

---

# 🧪 What Currently Works

### Civilian application

- [x] Persistent node ID
- [x] Nearby peer discovery
- [x] Multiple peers
- [x] Offline packet transmission
- [x] Structured mesh packets
- [x] Multi-hop forwarding
- [x] TTL
- [x] Hop count
- [x] Duplicate suppression
- [x] In-memory store-and-forward
- [x] Emergency types
- [x] Priority
- [x] GPS
- [x] Battery data
- [x] BLE gateway transport

### Gateway

- [x] ESP32 BLE
- [x] BLE packet reception
- [x] Packet validation
- [x] Duplicate filtering
- [x] LoRa initialization
- [x] LoRa transmission/reception
- [x] BLE ↔ LoRa bridge

### Rescue side

- [x] Rescue ESP32 BLE receiver
- [x] Rescue ESP32 Bluetooth Classic server
- [x] Sender ESP32 → rescue ESP32 communication
- [x] Rescue ESP32 → Android communication
- [x] ResQTeam prototype receiving path

---

# ⚠️ What Still Needs Work

ResQMesh is a **working prototype, not a production emergency network**.

Remaining work includes:

- [ ] Persistent store-and-forward
- [ ] Full delivery acknowledgements
- [ ] End-to-end encryption
- [ ] Packet authentication
- [ ] Secure gateway pairing
- [ ] Cryptographic signatures
- [ ] Better routing metrics
- [ ] Gateway redundancy
- [ ] Production background operation
- [ ] Robust reconnection
- [ ] Full rescue-side dashboard
- [ ] Map integration
- [ ] Rescue status/acknowledgement workflow
- [ ] Large-scale testing
- [ ] Real-world LoRa range testing
- [ ] Hardware fault handling
- [ ] Production security audit

---

# 🗺️ Roadmap

```text
[✓] Direct Phone Communication
          ↓
[✓] Mesh Packet
          ↓
[✓] Multi-Hop
          ↓
[✓] TTL + Deduplication
          ↓
[✓] Store-and-Forward
          ↓
[✓] BLE Gateway
          ↓
[✓] LoRa Gateway Prototype
          ↓
[✓] Rescue Communication Prototype
          ↓
[ ] Persistent Storage
          ↓
[ ] Secure Protocol
          ↓
[ ] Reliable ACK System
          ↓
[ ] Complete Rescue Dashboard
          ↓
[ ] Full Hardware Validation
          ↓
[ ] Production-Ready System
```

---

# 🛠️ Development

The project consists of Android and ESP32 components.

### Android

Requirements:

- Android Studio
- JDK 17
- Android SDK
- Physical Android devices for communication testing

Build:

```bash
./gradlew assembleDebug
```

Windows:

```powershell
.\gradlew.bat assembleDebug
```

### ESP32

The project contains separate gateway/receiver firmware.

Hardware validation should always be performed on the actual ESP32 + radio hardware before claiming successful LoRa deployment.

---

# 🔐 Security Disclaimer

This system is intended for research, development and demonstration.

It currently does not provide sufficient security or reliability guarantees for real emergency deployment.

**Do not use ResQMesh or ResQTeam as the sole communication method during a real emergency.**

---

# 📁 Repository Architecture

```text
ResQMesh/
│
├── civilian-app/
│
├── rescue-team-app/
│
├── esp32/
│   ├── civilian-gateway/
│   └── rescue-gateway/
│
├── docs/
│
├── README.md
├── LICENSE
└── .gitignore
```

---

# 🤝 Contribution

ResQMesh is a student-led engineering project exploring resilient communication during disasters.

Contributions are welcome in:

- Android development
- Embedded systems
- LoRa
- Networking
- Routing algorithms
- Security
- UI/UX
- Maps and navigation
- Hardware testing

---

# ❤️ The Vision

A disaster should not become a communication disaster.

When cellular towers go down, internet connectivity disappears, or infrastructure becomes inaccessible, nearby devices can still communicate.

ResQMesh is built around one principle:

> **Don't wait for the network to come back. Build a network that doesn't need it.**

**WHEN THE NETWORK DIES, WE BECOME THE NETWORK.**
