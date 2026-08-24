# ResQMesh Phase 2

Phase 2 introduces the gateway-to-phone BLE Classic bridge.

Data path:

People ESP32 → BLE → Gateway ESP32 → Bluetooth Classic → ResQ Team App

The gateway receives messages from sender ESP32 nodes, formats them as ResQ JSON when necessary, and forwards them line-by-line to the connected ResQ Team phone.
