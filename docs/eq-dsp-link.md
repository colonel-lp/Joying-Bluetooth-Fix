# EQ & DSP overlay link

- Bluetooth Controls **1.7 / code 8** exposes `com.joying.bluetoothsettings.ControlLinkService` for EQ & DSP **1.77 / build 98**.
- EQ & DSP binds explicitly while its screen is started. Watching does not open the bar, enable Bluetooth or select an audio source. An open request starts/reuses the private foreground overlay service directly; a close request hides the window without changing source/focus/Bluetooth state or starting another service; the normal granted path opens no Activity.
- The companion's window retains its existing non-focusable overlay flags. Its Audio/BT/media controls and previous-source ownership are unchanged. Hiding is separate from shutting down the foreground service.
- Initial subscription reports current presence. Window attachment reports ON; removal reports OFF. The controller also clears ON after Binder loss and ignores replies from earlier connections. Stop observing does not close the bar. No running-app scans, visibility polling or global broadcasts.
- Only messages whose Android-supplied `sendingUid` resolves to `com.eqdsp.controller` are accepted. Both manifests declare the other package for visibility on newer Android versions; the floating bar service remains unexported.

## Messenger protocol 1

Every message uses `arg1=1`. Client requests carry `replyTo`; the companion keys each subscription by that Binder and removes it on unsubscribe/death.

| `what` | Direction | Meaning |
|---|---|---|
| 1 WATCH | Controller → companion | Subscribe and receive initial STATE. |
| 2 OPEN | Controller → companion | Open/reuse the overlay; requires an existing subscription. |
| 3 STATE | Companion → controller | `arg2=1` attached, `arg2=0` absent. |
| 4 ERROR | Companion → controller | `arg2=1` overlay permission needed; `arg2=2` requested overlay action failed. |
| 5 UNWATCH | Controller → companion | Remove this subscription; leave the overlay running. |
| 6 CLOSE | Controller → companion | Hide the attached bar; an absent bar reports OFF. Added in 1.5; 1.7 retains active audio/radio. |

- The controller does not mark ON optimistically. Opening/closing has an eight-second confirmation timeout; missing/older companions report an update/install requirement.
- If the overlay grant is missing, the controller opens the companion's existing permission trampoline following the user's tap. This recovery may temporarily leave the underlying screen; the granted launch does not. No source is selected as part of permission recovery.
- Protocol 1 remains compatible with the earlier 1.76 controller for opening/status. Remote close requires the updated 1.5 companion; version 1.4 ignores CLOSE.
- Normal hide reports OFF when the window is removed. The notification may remain while BT or Audio is on; protocol STATE reports window presence, not radio or service lifetime. If the companion process stops abruptly, the controller detects Binder disconnection; a fresh process starts with absent presence.
- This protocol controls window launch/status only. Radio state and Bluetooth Audio ON/OFF are separate controls.

Build and install both app sources with their respective existing signing keys. No system/service audio patch is required for this link. Source/host checks and remaining device checks are in [validation](validation.md).

