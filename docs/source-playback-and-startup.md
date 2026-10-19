# Source, playback controls and startup investigation

19 October 2026. Scope: findings only; no app, Bluetooth package or DSP service changes. Applies to the supplied Joying SC9853i Android 8.1 firmware. See [confirmed receiving procedure](discovery-and-reproduction.md).

## Source selection and level 7

The native receiver uses Android **STREAM_ALARM (4)**. The observed **7** is its volume level, not a stream number. Joying's sound service owns this policy; directly setting the alarm volume can be overwritten.

The existing service exposes the main module through:

- Package/component: `com.syu.ms/app.ModuleService`.
- Intent action: `com.syu.ms.main` (retain this action when binding explicitly).
- Interface: `com.syu.ipc.IRemoteModule`.
- Source request: `cmd(0, new int[]{sourceId}, null, null)`.
- Current-source readback: `get(0, null, null, null)` returning a `ModuleObject` containing the source value. Update ID 0 also publishes source changes.

The alternative toolkit service exposes module 0 as the same main module. Main module ID, command ID and audio source ID are separate numbers.

Static trace: `ModuleService.onBind` -> main module `module.j.al`; command 0 -> `module.j.e.b.a(sourceId)` -> `module.j.a.a(int)` -> `module.j.h.aj(int)` -> current-source state/`sys.app_id`, MCU command and `AudioDevice.appId`. It is a service call, not a broadcast or a direct `sys.app_id` property write. The service checks power/ACC conditions and can override the source for a call; acknowledge actual readback rather than treating dispatch as success.

`AudioDevice.a()` applies per-source Android stream policy. Source IDs 3 and 15 select the stream-4 path through `f()` -> `app.u.j()` -> the alarm stream's maximum volume; other source paths mute it. Calls, mute and navigation can modify this policy. The owner's `joying-bt-controls.txt` capture confirms **source 3** while native Mac music plays through the original Joying Bluetooth Music screen, with stream 4 at **7/7**. The original Joying Bluetooth UI APK has not been traced in this investigation; the service endpoint and observed sound policy have. Direct source selection by our own app remains untested.

The supplied service manifest has no application/service permission gate on this endpoint; its intent filter makes the service exported under its legacy target. The inspected binding and main command path contain no caller-signature check. This is a viable ordinary-app integration candidate, with device acceptance still required.

To leave Bluetooth audio, remember the previous source and request it through the same main-module command. Restore only while Bluetooth remains the source selected by our app, so an explicit later user source change is respected. Pausing the sender, changing source and disabling Bluetooth are separate operations. Do not force stream 4 to zero as a substitute for releasing the source.

## Native playback controls

The supplied native Bluetooth package contains an exported service:

`com.android.bluetooth/com.android.bluetooth.a2dpsink.mbs.A2dpMediaBrowserService`

Its manifest declares the `android.media.browse.MediaBrowserService` action without a service permission gate. `onGetRoot()` accepts the client and returns a browser root. `onCreate()` publishes an active `MediaSession` and session token.

An ordinary app can attempt an explicit `MediaBrowser` connection, obtain its session token, construct a `MediaController`, then use `getTransportControls()`. This avoids selecting an unrelated local player's session and does not require global media-session enumeration or notification-listener access.

| Transport control | Native AVRCP operation |
|---|---|
| Play | 68 / 0x44 |
| Pause | 70 / 0x46 |
| Stop | 69 / 0x45 |
| Next | 75 / 0x4B |
| Previous | 76 / 0x4C |
| Fast forward | 73 / 0x49 |
| Rewind | 72 / 0x48 |

These are AVRCP operation IDs, not Android key-event codes. The service sends a press followed by a release to its current A2DP device. Its AVRCP controller requires a matching connected device and BLUETOOTH permission. Controls should be disabled when the browser/device is unavailable; a sender/player may support only some commands. Session playback state and metadata can also be observed.

This is distinct from `com.syu.ms.bt`, which dispatches to Joying's vendor Bluetooth driver. Native reception was enabled alongside that separate external stack; vendor playback commands must not be assumed to control the native Mac connection. The owner confirms that the original Joying app's playback buttons have no effect on the Mac. That fits the separate command paths; it does not establish that native AVRCP commands fail. No Bluetooth patch appears necessary for an initial browser/control test. End-to-end commands from our own app remain untested.

The same device capture confirms an active **A2dpMediaBrowserService** session owned by `com.android.bluetooth`, reporting playing state (3) and actions **54**: play, pause, previous and next. Stop/rewind/fast-forward have handlers in the inspected code but are not advertised in this snapshot. Metadata fields are currently empty. One controller is registered; this does not prove a successful remote-control operation. BBC Sounds is the system's selected media-button session, and Spotify has another session, so generic media-key dispatch would not reliably test Bluetooth.

## Startup permissions

| Action | Ordinary app on this Android 8.1 unit |
|---|---|
| Check full Bluetooth enablement/state | Public adapter APIs; current app declares BLUETOOTH. |
| Recover through Android enable confirmation | Already confirmed manually; v1.1 implements the flow, awaiting app acceptance. |
| Read `sys.fyt.systemobd` and role flag | Can attempt `getprop`; verify app-context access and handle an unreadable value explicitly. |
| Request ordinary adapter disable/enable | Android 8.1 allows this with BLUETOOTH_ADMIN; current app does not declare it. The vendor toggle fault still needs acceptance testing. |
| Set `sys.fyt.systemobd` / role flag | Property-service/SELinux access is required. ADB-shell success does not establish app access. No verified existing app-accessible setter was found. |
| Force-stop `com.android.bluetooth` | FORCE_STOP_PACKAGES is signature/privileged; an ordinary app cannot reproduce the ADB command merely by executing `am`. |

Android adapter off/on is different from restarting the Bluetooth process. The native-mode flag is cached in `AdapterService` class initialization, so a normal adapter toggle does not guarantee it is reread. Merely adding a privileged permission also does not automatically grant SELinux property-write access.

Preferred startup flow: read configuration and adapter state; if configuration is correct, use normal enable/recovery and avoid a process restart. If configuration is wrong, use the documented USB persistence procedure or a separately verified privileged helper. Only restart a process that needs to reload changed configuration, then wait for full ON/profile readiness before enabling source/playback controls. Never silently continue with an unreadable property or an unverified write.

## Device capture and next control test

While native Mac music is audible with the original Joying Bluetooth Music screen open, capture:

```sh
{
./adb shell getprop sys.app_id
./adb shell media volume --stream 4 --get
./adb shell dumpsys media_session
} > ~/Desktop/joying-bt-controls.txt
```

Completed: source 3, alarm volume 7/7 and the active native session are confirmed as above. Next, target that exact session while Mac music plays:

```sh
./adb shell media monitor A2dpMediaBrowserService
```

At its interactive prompt, enter `pause`, then `play`, each followed by Return, and check the Mac player. Enter `q` to exit. This targets the session by tag, rather than sending a generic system media key. If monitoring reports an error, preserve the exact output; do not interpret a denied command as an AVRCP failure. A successful ADB test would establish the remote control path, but ordinary-app browser access still needs its own test.

The subsequent app test should cover source acquire/readback, each sender control, source release and returning to local playback; permission checks for property writes must run as the app, not as ADB shell.

## References and evidence limits

Static evidence: the supplied factory `190000000_com.syu.ms.apk`, native Bluetooth APK manifest and checksum-validated unquickened Bluetooth DEX. Private decompiled code and personal logs are not published here. Observed playback/volume results are documented in the reproduction guide; the owner's source/session capture was reviewed as recorded above. The targeted remote-control test is pending. No APK build or patch was performed for this investigation.

- [MediaBrowser and session token](https://developer.android.com/reference/android/media/browse/MediaBrowser)
- [MediaController](https://developer.android.com/reference/android/media/session/MediaController)
- [AOSP media command: targeted session monitor and interactive controls](https://android.googlesource.com/platform/frameworks/base/+/f829b5df5ed8f80ef41fe54cc4deb70fd8ff914e/cmds/media/src/com/android/commands/media/Media.java)
- [Android 8.1 permission definitions](https://android.googlesource.com/platform/frameworks/base/+/android-8.1.0_r1/core/res/AndroidManifest.xml)
- [Android 8.1 property service access checks](https://android.googlesource.com/platform/system/core/+/android-8.1.0_r1/init/property_service.cpp)
