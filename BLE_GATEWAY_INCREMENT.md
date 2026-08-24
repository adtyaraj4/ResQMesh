# ResQMesh — BLE Gateway + Civilian ESP32 Firmware Increment

Builds on the previous Mesh Core increment. Nearby Connections is
untouched again this round.

## 1. LoRa frequency — confirmed

`LORA_FREQUENCY` is set to `433E6` (433 MHz), confirmed. Both the
civilian and rescue gateways will use this identical value.

## 2. What's new

**Android (`app/src/main/java/com/resqmesh/app/mesh/`):**
- `GatewayNode.kt` — UI-facing gateway model (no raw hardware IDs exposed
  beyond an internal `address` field used only for reconnect logic).
- `Esp32BleManager.kt` — real BLE central-role implementation: scans
  filtered by **service UUID only** (not device name, per spec), connects,
  discovers the RX/TX characteristics, enables notifications via the CCCD
  descriptor, writes packets, and auto-reconnects with a 3s backoff on
  disconnect. Handles both the legacy and API-33+ `writeCharacteristic`/
  `writeDescriptor` overloads.
- `Esp32BleTransport.kt` — adapts `Esp32BleManager` to the same
  `MeshTransport` interface `NearbyMeshTransport` implements.
- `MeshPacket.kt` — updated: `MeshPacketType` now matches your exact list
  (`MEDICAL, INJURED, FIRE, TRAPPED, ACCIDENT, FLOOD, OTHER, SOS, ACK`),
  added `locationAccuracy`/`locationTimestamp`, renamed the ack-reference
  field to `ackFor` to match the exact wire format the firmware parses.
- `MeshViewModel.kt` — now registers **both** transports
  (`[nearbyMeshTransport, bleTransport]`) with `MeshManager`, and exposes
  `gatewayState`/`gateway` flows. Still not wired into a visible UI.

**ESP32 (`esp32/ResQMesh_Civilian_Gateway/ResQMesh_Civilian_Gateway.ino`):**
BLE peripheral (phone is central, ESP32 is server) exposing the Nordic-
UART-style service, bridging validated/deduplicated packets to and from
LoRa. Re-advertises automatically on BLE disconnect. Prints periodic
stats to Serial for bring-up debugging.

## 3. Wiring table (Ra-02 ↔ ESP32 DevKit V1)

| Ra-02 pin | ESP32 GPIO | Notes |
|---|---|---|
| VCC | **3.3V** | Never 5V — the Ra-02 is 3.3V logic only |
| GND | GND | |
| MISO | GPIO 19 | |
| MOSI | GPIO 23 | |
| SCK | GPIO 18 | |
| NSS (CS) | GPIO 5 | |
| RESET | GPIO 14 | |
| DIO0 | GPIO 26 | |

All of these are `#define`d at the top of the `.ino` — if your physical
wiring differs, that's the only section to touch.

## 4. Required libraries

Arduino IDE → Tools → Manage Libraries:
- **LoRa** by Sandeep Mistry (SX127x driver)
- **ArduinoJson** by Benoit Blanchon, v6.x

Board support: Boards Manager → **esp32 by Espressif Systems** (this
bundles the ESP32 BLE Arduino library used for `BLEDevice`/`BLEServer` —
no separate install for that part).

## 5. Flashing instructions

1. Arduino IDE → Tools → Board → select your ESP32 DevKit V1 variant.
2. Tools → Port → select the ESP32's serial port.
3. Open `ResQMesh_Civilian_Gateway.ino`, verify/compile first (catches
   missing-library errors before you touch hardware).
4. Upload. Some ESP32 DevKit boards need the BOOT button held during the
   "Connecting..." phase of upload — if upload fails at that stage, try
   that.
5. Open Serial Monitor at **115200 baud**. You should see:
   ```
   === ResQMesh Civilian Gateway ===
   Node ID: GW-CIVILIAN-001
   [LoRa] Radio initialized OK
   [BLE] Advertising as RESQMESH-GW
   ```
   If you instead see `[LoRa] begin() FAILED`, it's almost always wiring
   (check 3.3V not 5V to Ra-02 VCC, and the SPI pin table above) rather
   than the frequency value.

## 6. What this does NOT include yet

- **Rescue ESP32 firmware** (`ResQMesh_Rescue_Gateway.ino`) — structurally
  it's the mirror of this file (LoRa RX → validate/dedup → BLE notify,
  plus BLE RX of ACK packets → LoRa TX), but I'm writing it once this
  one's confirmed working on your bench rather than shipping two unverified
  sketches at once.
- **Rescue Android app** — a second application module, separate UI,
  incident list, map, ACK button. Substantial enough to be its own
  increment.
- **ACK round-trip wiring end-to-end**, **persistent store-and-forward**
  (Room-backed, survives app restart), **push notifications** for incoming
  emergencies, **the civilian emergency-only home screen UI**, and the
  **diagnostics/demo screen** are all still pending — `MeshViewModel` now
  has everything they need to bind to (`connections`, `receivedPackets`,
  `gatewayState`, `gateway`, `sendEmergency()`), so wiring them is UI work
  from here, not new networking logic.
- **No APK.** Still no network/Android SDK in this environment to
  compile — build via `./gradlew assembleDebug` in Android Studio (needs
  network to resolve the two new dependencies added last increment).

## 7. How to verify this increment

Two independent things to check before spending bench time:
1. **Android side**: Gradle sync + `assembleDebug` succeeds (source-only
   change, no new native deps beyond what compiled last round).
2. **ESP32 side**: flash it alone (no phone needed yet) and confirm the
   Serial Monitor output above — this validates LoRa init and BLE
   advertising independent of the Android app.

Once both check out individually, the first real hardware test is: leave
the app's Phase-1-style test screen aside for a moment and instead use a
generic BLE scanner app (e.g. nRF Connect) to confirm `RESQMESH-GW` shows
up advertising the `6e400001...` service — that isolates "is the ESP32
BLE side working" from "does the Android BLE client work," which makes
debugging much faster if something's off.
