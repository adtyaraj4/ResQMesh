# ResQMesh — Mesh Core Increment (on top of Phase 1)

Builds on the working Phase 1 two-phone Nearby Connections test. **Nothing
in `NearbyTransport.kt`'s existing behavior was changed** — only additive
`SharedFlow` event streams were appended so a wrapper could consume events
cleanly instead of diffing an accumulating list.

## 1. Files created/modified

**Created** (`app/src/main/java/com/resqmesh/app/mesh/`):
- `MeshPacket.kt` — the single packet format shared across every future
  transport (Nearby now; BLE/LoRa next). Compact JSON via kotlinx.serialization,
  deliberately kept to primitives/enums so a later binary/CBOR codec is a
  one-file swap (only `MeshPacketCodec` would change, not callers).
- `TransportType.kt` — `TransportType` enum (`NEARBY`, `BLE_GATEWAY`,
  `LORA_GATEWAY`), `TransportConnectionState`, `TransportPeer`.
- `MeshTransport.kt` — the interface every transport implements, exactly
  as specified: `start()`, `stop()`, `send()`, `broadcast()`,
  `observeIncomingPackets()`, `observeConnections()`.
- `NearbyMeshTransport.kt` — adapts the **existing, untouched**
  `NearbyTransport` to `MeshTransport`. Pure translation layer: encodes
  `MeshPacket` → JSON text for `sendText`/`broadcastText`, decodes incoming
  text → `MeshPacket` (dropping anything that doesn't parse, so a stray
  non-mesh payload can't crash routing).
- `MeshManager.kt` — the routing engine: dedup by `messageId` with a
  10-minute expiry, TTL decrement/hop increment on every forward, stops
  forwarding at `ttl <= 0`, delivers packets addressed to this node (or
  broadcasts) to the app layer.
- `MeshViewModel.kt` — thin ViewModel seam so future UI work doesn't put
  networking calls inside composables. **Not wired into `MainActivity`
  yet** — see "Deferred" below.

**Modified**:
- `nearby/NearbyTransport.kt` — added two additive `SharedFlow`s
  (`messageEvents`, `connectionEvents`) alongside the existing
  `StateFlow`s, which are untouched. Nothing existing was removed or
  changed in behavior.
- `app/build.gradle.kts` — added the `kotlinx-serialization` plugin,
  `kotlinx-serialization-json:1.6.3`, and
  `androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4`. JDK 17 /
  Kotlin 1.9.24 / AGP 8.5.2 are unchanged, per your build requirements.

## 2. What this increment deliberately does NOT include

Going straight to the full spec (BLE gateway, ESP32 firmware, LoRa
bridging, ACK protocol, persistent store-and-forward, topology screen,
Demo Mode, signed APK) in one pass isn't something I can respomsibly hand
you as "done" — I have no network access in this environment to resolve
Gradle dependencies or compile, and no ESP32/LoRa hardware to test
against, so any firmware or BLE code written blind is unverified. Per
your own original phased plan (do not generate everything at once), this
increment is scoped to the part that's pure logic and provably correct by
inspection:

- **No BLE / ESP32 code yet.** `Esp32BleManager`, `GatewayNode`,
  `Esp32BleTransport`, and the ESP32-side firmware are the next increment.
  `MeshManager` already takes `transports: List<MeshTransport>`, so adding
  `Esp32BleTransport` later means one line at the `MeshManager(...)`
  construction site — no changes to `MeshManager` itself.
- **No ACK packets / delivery confirmation UI.** `MeshPacket.ack()` exists
  as a factory function but nothing calls it yet — wiring "SOS DELIVERED ✓"
  needs a second real transport to round-trip through first.
- **No persistent store-and-forward.** Undeliverable packets aren't
  currently queued to disk; only in-memory dedup state exists.
- **Loop prevention is dedup-only for now**, not dedup + explicit
  previous-hop exclusion. `MeshTransport.observeIncomingPackets()` returns
  `Flow<MeshPacket>` with no peer-id attached, so `MeshManager` can't yet
  say "don't echo this back to the exact peer it came from" — it relies on
  the seen-message cache instead, which is one of the mechanisms your spec
  names as valid on its own. Tightening this to explicit peer exclusion is
  called out as a TODO in `MeshManager`'s forwarding code and is a natural
  fit for when `Esp32BleTransport` is added (two real transports make the
  echo case something you can actually trigger and watch get suppressed).
- **UI is untouched.** The polished SOS/mesh-status home screen, gateway
  status card, topology screen, and Demo Mode are all next — they should
  land together with the BLE work so the UI has something real to show
  besides Nearby peers.
- **No APK artifact.** I can't compile in this environment (no network,
  no Android SDK here). Build it yourself via `./gradlew assembleDebug` in
  Android Studio — see below.

## 3. How to verify this increment

This is source-only; verify it the same way you verified Phase 1 — open
in Android Studio, let Gradle sync (it needs network for the new
`kotlinx-serialization-json` and `lifecycle-viewmodel-compose` deps), and
confirm `assembleDebug` succeeds. Since `MeshViewModel`/`MeshManager`
aren't wired into any Activity yet, there's no new on-device behavior to
test in this increment — the goal here is "this compiles and the wiring
is correct," which a build + a quick unit test would confirm faster than
a phone test. If you want, I can write a JVM unit test for
`MeshManager`'s dedup/TTL logic using a fake in-memory `MeshTransport` —
that's real, run-today verification, unlike anything BLE/LoRa-related
which needs hardware.

## 4. Next step

Tell me when you want to proceed, and the next increment is:
`GatewayNode.kt`, `Esp32BleManager.kt` (scan/connect/reconnect against the
Nordic UART-style UUIDs you specified), `Esp32BleTransport.kt` implementing
`MeshTransport`, registering it alongside `NearbyMeshTransport` in
`MeshManager`, and the ESP32-side Arduino/PlatformIO firmware for the
BLE↔SPI↔LoRa bridge — plus the UI overhaul (SOS screen, gateway status,
topology view) once there's a second transport for it to actually display.
