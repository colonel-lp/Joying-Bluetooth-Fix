# Source, playback and adapter interfaces

Applies to the Joying SC9853i Android 8.1 firmware. Native-mode setup and audio-role selection are described in the [project README](../readme.md).

## Joying audio source

Bind explicitly to `com.syu.ms/app.ModuleService` with action `com.syu.ms.main`. This returns `com.syu.ipc.IRemoteModule` for the main module.

| Operation | Wire format |
|---|---|
| Select source | Transaction 1, command 0, integer array containing the source ID, null float/string arrays; one-way. |
| Register source callback | Transaction 3, callback Binder, update 0, flush 1; one-way. |
| Unregister callback | Transaction 4, callback Binder, update 0; one-way. |
| Source update | `com.syu.ipc.IModuleCallback`, transaction 1: update ID, integer/float/string arrays. |

Interface tokens must match the descriptors. Main module ID, command ID and source ID are distinct values. Bluetooth Music is source **3**; Android is source **0**. The service also supports source get operation 0 through its ModuleObject interface, but the app uses callback readback instead.

The command follows the existing main-module/MCU/audio-device path. Power, ACC and call handling can prevent or override a selection. Dispatch acceptance alone does not confirm routing. The app confirms update 0 and retains the previous source before acquisition. Release restores it only while Bluetooth remains selected; an unrelated later source change is respected. Explicit Audio OFF falls back to Android when no previous source was captured.

Received native audio uses alarm stream **4**, normally set to its maximum **7** for Bluetooth Music. Joying's sound service applies this policy and can overwrite direct volume changes. Source selection, remote playback and adapter enablement remain separate operations.

## Audio focus

Audio ON requests permanent `AUDIOFOCUS_GAIN` with media/music attributes before selecting the source. A denied request leaves the source unchanged. This sends permanent focus loss to the earlier Android player, so abandoning focus does not invite it to resume. Audio ON does not send Play to the Bluetooth sender. The focus request sets `setWillPauseWhenDucked(true)` so brief interruptions reach the pause handler; automatic ducking of this app cannot lower audio played by the separate Bluetooth process.

Audio OFF pauses the native sender, falling back to Stop if Pause is unavailable, then abandons focus and restores the source. No Play command is sent to an earlier player. Source acquisition failure, an external source change, adapter shutdown, focus loss and full service shutdown also release app-held focus. Hiding the floating window retains focus and leaves playback/source selection unchanged. Opening the bar or observing an already-selected Bluetooth source does not take focus; explicitly pressing Play on that source does.

Android 8.1 focus is cooperative: a player that ignores focus loss can continue playing. No global media keys, active-session enumeration or force-stop permission is used. The firmware receiver builds a focus request without submitting it; the floating app supplies the request for its own audio selection.

## Native playback

Bind `MediaBrowser` to:

`com.android.bluetooth/com.android.bluetooth.a2dpsink.mbs.A2dpMediaBrowserService`

The service is exported without a service permission gate and accepts browser clients. Obtain its session token and use `MediaController.TransportControls`; no global session enumeration is required. The native service sends AVRCP press/release commands to its current connected device.

| Control | AVRCP operation |
|---|---|
| Play | 0x44 |
| Pause | 0x46 |
| Stop | 0x45 |
| Next | 0x4B |
| Previous | 0x4C |
| Fast forward | 0x49 |
| Rewind | 0x48 |

These values are AVRCP operations, not Android key-event codes. The captured session advertises action mask **54**: play, pause, previous and next. Its code also implements Stop despite omitting the Stop action bit. The app allows that specific handler; the sender may still ignore the command. Other buttons follow the action mask. Playback state and metadata come from the same native session.

Joying's `com.syu.ms.bt` module dispatches to its separate vendor driver. Those playback buttons do not control the native Mac connection. Generic system media keys can reach another local player. `media monitor A2dpMediaBrowserService` was rejected for missing media-control permission; its trailing “No session found” does not establish that the session disappeared.

## Adapter control

On Android 8.1, declared `BLUETOOTH_ADMIN` permits direct `BluetoothAdapter.enable()`/`disable()` calls following a button press. The app already declares that normal permission. Routine enablement does not need Android's repeated confirmation dialog; the public confirmation remains a fallback if direct enablement throws a permission denial.

Requests are asynchronous. The app waits for state confirmation, defers requests during transitions, allows one enable retry after three seconds in a stable off/BLE-only state, and reports an unconfirmed transition after twelve seconds. Timers continue while the bar is hidden and are canceled when the service stops. On older Android, `getLeState()` is attempted to distinguish BLE transition states hidden by the public OFF value; public state is used if that interface is unavailable.

The original settings toggle can remain off/BLE-only after shutdown. A later enable request has recovered it. The new app handling does not patch the underlying framework fault and still needs device confirmation after the change.

## Window and notification lifetime

The foreground service owns source, focus, media and adapter clients independently of the floating window. × and companion CLOSE detach only the window. Actual removal reports overlay presence OFF; reopening reuses the service and restores its window without changing audio or radio state.

The ongoing notification uses a 56 dp custom RemoteViews layout for both collapsed and expanded content: a status line and a control row containing previous, play/pause, Stop, next, Audio, BT, BT Settings and ×. Android 7+ decorates it with the system app header; no duplicate content title or standard action buttons are added. Older Android uses the custom-content fallback. Status/body taps use the permission trampoline to reopen the bar.

Each button has a distinct immutable explicit service PendingIntent on Android 6+. Service delivery rechecks the same enabled-state rules used by the floating bar, so stale buttons cannot bypass transition or capability checks. Button actions do not create a floating window or require an overlay grant. BT Settings opens/closes the owned settings task; notification × invokes confirmed shutdown. Playback state, capabilities, sender, source and settings state participate in the notification update key; unchanged content is not reposted and updates are silent.

A hidden service stops when Bluetooth and Audio are confirmed off with no pending transitions. Explicit shutdown pauses the sender, abandons focus, restores the source and then disables Bluetooth, waiting for in-flight requests and readback. Rejected or unconfirmed shutdown keeps the notification and retry action. Hiding does not restart earlier players or release an active audio route. Boot launch is not included.

## Settings task and sender name

BT Settings starts a private translucent root activity with a separate task affinity. That root launches native Bluetooth settings for a result, without a new-task flag on the native child. Pressing the button again finishes/removes the root task; Back/result and root destruction clear the open indicator. The button stays highlighted while closing is pending. A non-root fallback finishes only the child it launched and itself. Launch failure/timeout allows retry; recreation does not launch a duplicate child.

This tracks only the native settings task opened by the app. It does not inspect or close unrelated Android settings tasks and needs no additional permission. The Android 8.1 reference manifest uses a standard Bluetooth settings activity; firmware task behavior still requires device confirmation.

The title bar reads its version from installed package metadata. Sender names come from `BluetoothProfile` receiving profile **11** (`A2DP_SINK` on Android 8.1), not the paired-device list or HID connections. The supplied firmware returns its single current connected sender from that profile. Names refresh on profile connection, name and adapter events; disconnect/ambiguity clears the label. Unsupported profile access leaves the name absent. The native media session continues to provide playback state and track title separately.

## Privilege limits

- Checking Bluetooth state is available to the app. Reading native-mode properties can be attempted with suitable error handling.
- Property writes are controlled by the property service/SELinux. ADB-shell access does not establish ordinary-app access.
- Force-stopping another package requires signature/privileged access. Overlay permission does not provide it.
- Native-mode flags are cached during Bluetooth process initialization. Adapter off/on is not equivalent to reloading changed startup properties.
- Receiving/transmitting roles remain selected at process startup. No property writer, privileged helper or system patch is included in the app.

## References

- [Android audio focus](https://developer.android.com/media/optimize/audio-focus)
- [Activity task lifecycle](https://developer.android.com/reference/android/app/Activity#finishAndRemoveTask())
- [BluetoothAdapter](https://developer.android.com/reference/android/bluetooth/BluetoothAdapter)
- [MediaBrowser](https://developer.android.com/reference/android/media/browse/MediaBrowser)
- [MediaController](https://developer.android.com/reference/android/media/session/MediaController)
- [Android 8.1 permission definitions](https://android.googlesource.com/platform/frameworks/base/+/android-8.1.0_r1/core/res/AndroidManifest.xml)
- [Android 8.1 property service](https://android.googlesource.com/platform/system/core/+/android-8.1.0_r1/init/property_service.cpp)

Static interface findings come from the supplied service/Bluetooth binaries. Private decompilations and personal diagnostic captures are excluded from the repository. Validation and device-check records are kept in [validation.md](validation.md).
