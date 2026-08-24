# ResQMesh — Phase 3

## Development Progress

ResQMesh has now progressed from establishing the mesh/gateway communication path to building the **rescue-team side of the system**.

### Phase 1 — Mesh / Gateway Foundation

The first stage established the core field communication path:

**People ESP32 → BLE → Gateway ESP32**

The gateway acts as the bridge that receives emergency messages from field/sender nodes.

### Phase 2 — Gateway to Rescue Team Phone

Phase 2 extended the gateway:

**People ESP32 → BLE → Gateway ESP32 → Bluetooth Classic → ResQ Team App**

The gateway was given a Bluetooth Classic interface named `ResQTeam-ESP32` and forwards emergency packets to the Android application.

### Phase 3 — Rescue Team Application

Phase 3 focuses on turning the received gateway messages into a usable rescue-team workflow.

The application is designed around receiving structured emergency information from the gateway and presenting it to the rescue team so that incidents can be acted upon quickly.

## Phase 3 Communication Architecture

```text
┌─────────────────────┐
│   People ESP32      │
│ Emergency Sender    │
└──────────┬──────────┘
           │ BLE
           ▼
┌─────────────────────┐
│    Gateway ESP32    │
│  RESQMESH-RECEIVER  │
└──────────┬──────────┘
           │ Bluetooth Classic
           ▼
┌─────────────────────┐
│   ResQ Team App     │
│                     │
│ Emergency Feed      │
│ Priority            │
│ Incident Details    │
│ Location            │
│ Navigation          │
└─────────────────────┘
```

## What Has Been Added / Developed

- [x] Bluetooth Classic gateway connection
- [x] Emergency message parsing / JSON
- [x] Priority-based emergency display
- [x] Location and navigation support
- [x] Incident details

## Emergency Data

The gateway/app communication is based around structured emergency packets. Relevant fields include information such as:

- Message ID
- Source node ID
- Emergency type
- Priority
- Latitude / longitude
- Timestamp
- Battery level
- TTL / hop count
- Number of people
- Number of injured people

This allows the rescue team interface to move beyond a simple text message and treat each incoming transmission as an emergency incident.

## Current Development Path

### Completed progression

**Phase 1**
> Establish ESP32 mesh/gateway communication.

**Phase 2**
> Connect the gateway to the rescue team's phone.

**Phase 3**
> Build the rescue-team operational interface around those incoming emergency messages.

### Next stage

The next phase can focus on making the system more robust for a real disaster scenario: multi-node handling, acknowledgement/status updates, stronger message validation, persistent incident history, improved location handling, and eventual LoRa-based long-range communication.

## Important

This README describes the project as supplied in this Phase 3 package. Features are only marked as completed where they are represented in the supplied project files.

---

**ResQMesh — Phase 3**

**When the network dies, we become the network.**
