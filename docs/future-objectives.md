# App objectives and current scope

Updated 19 October 2026. The owner explicitly authorized the v1.2 floating control-bar source after the earlier investigation.

## Implemented in v1.2 source; device acceptance pending

- Small draggable overlay with remembered position and foreground notification.
- Native receiver previous/play-pause/stop/next controls, enabled according to the session action mask.
- Joying Bluetooth Music source on/off via main-module command/update 0, with previous-source restore and readback confirmation.
- Android Bluetooth adapter off and confirmed enable/recovery prompt.
- Native settings access, playback/status text, explicit close and service reconnect on a long press of the status strip.

## Future investigation

- Fix the underlying stock Bluetooth toggle fault if evidence justifies a system/framework patch.
- Establish an authorized app-accessible helper for property writes/process restart, or retain USB configuration; ordinary app/overlay permission is insufficient.
- Enable receive/transmit profiles together if feasible; headphone volume integration remains lower priority.
- Optional collapse/expand behavior and automatic source selection on sender connection, only if requested.

No system patch, native-mode property writer, headphone-volume fix, automatic source selection or boot launch is part of v1.2.
