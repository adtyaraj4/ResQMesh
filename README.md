# ResQMesh — Phase 1

Milestone: **Phone A ↔ Phone B, offline, no internet, no backend, no LoRa.**

## What's in this build

| File | Purpose |
|---|---|
| `data/NodeIdManager.kt` | Generates a random `NODE-XXXXXX` ID on first launch, persists it in SharedPreferences. Never uses phone number/IMEI. |
| `nearby/NearbyTransport.kt` | Wraps Google Nearby Connections (`P2P_CLUSTER` strategy): advertising, discovery, auto-connect, and raw byte send/receive. This is **transport only** — no routing/TTL/dedup logic lives here on purpose, so it can be reused unchanged once multi-hop routing is added on top in Phase 3+. |
| `MainActivity.kt` | Requests runtime permissions (location + Bluetooth + Nearby Wi-Fi Devices depending on API level), then starts advertising + discovery. |
| `ui/HomeScreen.kt` | Temporary test screen — **not** the final emergency-button UI from the spec. Shows node ID, discovered/connected peer counts, a send-test-packet button, and a log of received packets. |

## Why P2P_CLUSTER and not P2P_STAR

`P2P_STAR` limits a device to a single connection, which would cap us at
two-phone topologies forever. `P2P_CLUSTER` allows a device to hold
multiple simultaneous connections, which is required for Phase 3
(three-phone multi-hop: A → B → C). Choosing it now avoids a transport
rewrite later.

## How to test (2 physical Android devices, API 26+)

1. Open the project in Android Studio (Koala+ recommended), let Gradle sync.
2. Turn OFF Wi-Fi internet and mobile data on both phones (Bluetooth and
   Wi-Fi *radios* should stay on — Nearby Connections needs them; you're
   only disabling their internet uplink, e.g. airplane mode + re-enable
   Wi-Fi/Bluetooth, or just no SIM/hotspot).
3. Install the app (`Run ▶`) on both phones.
4. Grant all permission prompts on both phones.
5. Watch the "Transport status" card — it should move from
   `Advertising as NODE-XXXX` → `Connecting to NODE-YYYY...` →
   `Connected to <endpointId>` on both devices, usually within a few
   seconds to ~30s depending on radio conditions.
6. On Phone A, tap **"Send test packet to connected peers"**.
7. On Phone B, a new card should appear under "Received packets" showing
   `From: <endpointId>` and the text `Hello from NODE-A7F2 at <timestamp>`.
8. Repeat in the other direction.

### Expected output
- Both phones show each other's endpoint ID as connected.
- Sending on either device produces a received card on the other within
  ~1 second.
- No internet permission is exercised for this — you can confirm by
  checking neither phone's data usage indicator moves.

### Common issues
- **Stuck on "Discovering peers..."**: one or both phones denied a
  permission. Check Settings → Apps → ResQMesh → Permissions; Location
  and Nearby devices must be granted, not just Bluetooth.
- **Connects then immediately disconnects**: happens if both apps were
  freshly reinstalled and Bluetooth cache is stale — toggle Bluetooth
  off/on on both phones.
- **Works in the same room but not two rooms apart**: expected — this
  phase uses BLE/Wi-Fi Direct range only (tens of meters). Long range is
  what the LoRa gateway (Phase 10+) is for.
- **Emulators**: don't use them for this test — Nearby Connections needs
  real BLE/Wi-Fi radios, per the project spec.

## What's deliberately NOT here yet

No multi-hop routing, no message IDs/dedup, no TTL, no GPS, no priority
queue, no store-and-forward, no ESP32/LoRa, no backend, no encryption.
Those are Phases 2–17. This build only proves the raw transport works
between two phones with zero infrastructure.

## Next step (Phase 2/3)

Once you've confirmed the above on real hardware, the next step is
introducing a structured message envelope (message ID + sender + hop
count) so a third phone can relay what it receives instead of just
echoing raw text — that's the seed of the mesh routing engine.
