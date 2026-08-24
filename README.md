# ResQMesh — Phase 2: BLE Gateway Integration

## Project Progress

ResQMesh has progressed from the initial mesh/gateway foundation into **Phase 2**, where the gateway is now connected to the **ResQ Team Android application** using Bluetooth Classic.

### Phase 1 — Mesh / Gateway Foundation

The earlier stage established the core ResQMesh communication path:

**People ESP32 → BLE → Gateway/Receiver ESP32**

The gateway receives emergency messages from sender ESP32 nodes over BLE and processes them as the central relay point.

### Phase 2 — BLE Gateway → ResQ Team App

Phase 2 extends that working gateway by adding the phone-facing communication path:

**People ESP32 → BLE → Gateway ESP32 → Bluetooth Classic → ResQ Team App**

The gateway ESP32 now:
- Advertises a BLE service for incoming sender ESP32 nodes.
- Receives emergency messages through the BLE RX characteristic.
- Uses Bluetooth Classic with the device name `ResQTeam-ESP32`.
- Forwards received emergency data to the ResQ Team phone.
- Sends each forwarded message as a newline-terminated packet so the Android application can read it as a stream of messages.
- Converts non-JSON incoming messages into a structured ResQ emergency JSON packet for the app.

## Communication Architecture

```text
┌─────────────────────┐
│   People ESP32      │
│  Emergency Sender   │
└──────────┬──────────┘
           │
           │ BLE
           ▼
┌─────────────────────┐
│  Gateway ESP32      │
│   RESQMESH-RECEIVER │
│                     │
│ BLE RX              │
│        ↓            │
│ Message Processing  │
│        ↓            │
│ JSON Formatting     │
│        ↓            │
│ Bluetooth Classic   │
└──────────┬──────────┘
           │
           │ Bluetooth Classic / SPP
           ▼
┌─────────────────────┐
│   ResQ Team App     │
│      Android        │
└─────────────────────┘
```

## Gateway BLE Configuration

The receiver uses:

- Device name: `RESQMESH-RECEIVER`
- Service UUID:
  `6e400001-b5a3-f393-e0a9-e50e24dcca9e`
- RX characteristic:
  `6e400002-b5a3-f393-e0a9-e50e24dcca9e`
- TX characteristic:
  `6e400003-b5a3-f393-e0a9-e50e24dcca9e`

The sender ESP32 writes emergency messages to the gateway RX characteristic.

## Phone Gateway Configuration

The receiver ESP32 exposes Bluetooth Classic using:

- Bluetooth device name: `ResQTeam-ESP32`
- Android connection type: Bluetooth Classic / SPP

The gateway checks whether a phone client is connected before forwarding an emergency packet.

## Message Forwarding

A received message follows this path:

1. Gateway receives the message from the sender ESP32.
2. The BLE callback captures the message.
3. The gateway passes it to the phone-forwarding function.
4. If the incoming message is already JSON, it is forwarded.
5. If it is plain text, the gateway wraps it into a structured ResQ JSON packet.
6. The JSON packet is sent using `SerialBT.println()`.
7. The newline allows the Android app to process each emergency as a separate incoming line.

Example structured packet:

```json
{
  "messageId": "ESP32-12345",
  "sourceNodeId": "SENDER-ESP32",
  "type": "EMERGENCY",
  "priority": 5,
  "latitude": 13.0827,
  "longitude": 80.2707,
  "timestamp": 12345,
  "battery": 90,
  "ttl": 10,
  "hopCount": 1,
  "peopleCount": 1,
  "injuredCount": 0,
  "message": "HELP"
}
```

## Current Phase 2 Status

### Completed

- [x] Sender ESP32 → Gateway ESP32 BLE communication
- [x] Gateway BLE server
- [x] Gateway advertising
- [x] Gateway message reception callback
- [x] Bluetooth Classic phone interface
- [x] `ResQTeam-ESP32` Bluetooth device
- [x] Gateway-to-phone forwarding
- [x] Newline-delimited phone messages
- [x] Structured JSON forwarding for non-JSON emergency messages
- [x] Gateway serial logging for connection and forwarding status

### Next Development

The next stage can build on this gateway-to-app link to improve the complete rescue workflow, including richer emergency information, priority handling, location presentation, navigation, acknowledgement/status handling, and more robust multi-node operation.

## Hardware Communication

### Sender ESP32

The sender node is responsible for transmitting emergency information into the ResQMesh BLE network.

### Gateway ESP32

The gateway acts as the bridge between the local ESP32 mesh and the ResQ Team Android application.

### ResQ Team Phone

The phone connects to the gateway using Bluetooth Classic and receives the forwarded emergency packets.

## Notes

This repository represents **Phase 2** of the ResQMesh development process. LoRa support remains optional/future work in the gateway firmware and is not required for the current BLE-to-phone communication path.

---

**ResQMesh — Phase 2**

Emergency communication from the field node to the rescue team's phone is now bridged through the gateway.
