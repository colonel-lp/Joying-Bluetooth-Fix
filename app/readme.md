# Bluetooth Controls

**v1.10 / code 11** · `com.joying.bluetoothsettings`

A draggable floating bar for native Bluetooth reception on Joying/FYT Android 8.1 head units. The [native-mode setup](../readme.md#enable-native-bluetooth-with-adb) is required first.

## Source layout

- `src/main/`: current Android application.
- `gradle/wrapper/`: Gradle distribution settings.
- `tests/`: source and lifecycle checks.
- `smali/`: historical shortcut source, excluded from the build.

## Build

Open this `app/` directory in Android Studio. Use the `gradle-wrapper.properties` distribution, **Gradle JVM 25**, Android SDK **35**, Gradle **9.3.1** and AGP **8.13.2**. Minimum SDK is 21; target SDK is 27. There is no nested `app/app/` module.

With an installed Gradle 9.3.1:

```sh
gradle assembleDebug
adb install -r build/outputs/apk/debug/Bluetooth-Controls-v1.10.apk
```

The command-line wrapper launchers/JAR are not included. Use the same signing key for updates. Local SDK paths and signing material remain outside the repository.

Versions advance as 1.10, 1.11, 1.12. APKs are named `Bluetooth-Controls-v<version>.apk` in their build-type output folder.

## Controls

Allow **display over other apps** on first launch. The title bar shows the native audio sender when available, with the installed version at the right. Drag the status strip to move the bar; long-press it to reconnect its service clients.

- **Previous / play-pause / Stop / next:** control the native sender. Stop uses the firmware's handler even though its advertised control list omits Stop; the remote player may ignore it.
- **Audio:** ON takes permanent media focus and selects Bluetooth Music, pausing cooperating Android players. OFF pauses the native sender (or sends Stop if Pause is unavailable), releases focus and restores the previous source. If none was captured, it selects Android. Earlier players are not restarted.
- **BT:** directly request adapter on/off using the declared Android 8.1 permission. State is confirmed before the transition finishes. One enable retry is allowed after three seconds in a stable off/BLE-only state; transitions are bounded to twelve seconds. A confirmation dialog is used only if direct enablement is denied.
- **BT Settings:** open native Bluetooth settings in a separate app-owned task; press again to close it. The button highlights while that task remains open and resets on Back/close. A settings window opened elsewhere is not tracked or closed.
- **Floating ×:** hide the bar without changing Bluetooth, playback or focus. A hidden app exits when both are confirmed off.
- **Notification:** previous, play/pause, Stop, next, Audio, BT and BT Settings work directly from its compact row of equal-width, 36 dp-high buttons. On states highlight; unavailable controls are dimmed. Tap its status text to reopen the bar. Its **×** pauses the sender, releases focus, restores the source and disables Bluetooth, waiting for confirmation before exiting. Android’s app header remains; the repeated content title is removed.

EQ & DSP 1.77 can toggle the bar open/closed over its current screen and theme its shortcut from confirmed window presence; see the [companion link](../docs/eq-dsp-link.md). Audio selection remains explicit through the Audio button or notification shutdown action.

Opening the icon again restores/reuses the bar. Hiding also updates the companion button to OFF. Failed shutdown keeps the notification available for retry; the return source is retained if restoration is not confirmed.

## Status and limitations

The floating controls work with native receiving mode. The notification control layout awaits device confirmation. Android 8.1 relies on players respecting focus changes. The stock Bluetooth settings fault remains a firmware limitation.

No root access, system patch, property writer, audio-role switching, automatic device connection or boot launch is included. The overlay grant is separate from system privileges. Headphone-volume integration remains outside the current app.

Technical details and validation records are in [docs/](../docs/).
