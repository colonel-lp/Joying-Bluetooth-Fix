# Bluetooth Controls source

**v1.2 / code 3** adds a draggable floating control bar for the tested Joying SC9853i, Android 8.1. Package remains `com.joying.bluetoothsettings`, independent of the EQ/DSP app. Build current releases from `src/main/`; the original `smali/` shortcut is retained only as a historical v1.0 reference.

## Use

1. Build/install, open **Bluetooth Controls**, and allow **display over other apps** when Android asks. Return to the app. The foreground notification keeps the bar running over other apps.
2. Drag the status strip to move the bar. Its position is remembered and constrained to the screen after orientation changes. Opening the icon again reuses the existing bar.
3. Use **Settings** to open Android Bluetooth settings, pair/connect the sender and select the Joying as the Mac's sound output.
4. **BT OFF** means the adapter is currently off; tap to show Android's enable confirmation. **BT ON** means it is currently on; tap to release the Bluetooth audio source and request adapter shutdown. The label follows actual state, not merely request acceptance. During a transition the button waits. Canceling enable does not repeat the prompt.
5. **Source OFF** means another Joying source is selected; tap to select Bluetooth Music source **3**. **Source ON** means Bluetooth Music is selected; tap to restore the source remembered before acquisition. If Bluetooth was already the source when this app opened, explicit Source OFF returns to Android source **0**. Source state is confirmed through Joying's main-module update 0; an unconfirmed change produces an error.
6. Use **previous, play/pause, stop, next** for the native receiver's remote sender. Buttons are dimmed when the native session does not advertise support. In the supplied capture, play/pause/previous/next are advertised; Stop is not. Commands target the native Bluetooth session, not BBC Sounds or another local player.
7. **×** closes the bar and notification, releases a source acquired by this app, and leaves the Bluetooth adapter enabled. If the user already selected another source, closing respects that selection. Use BT OFF first when the adapter should also be turned off.

The status strip shows adapter state, native session availability/playback and a track title when supplied. Long-press it without dragging to reconnect the source and media-service clients. Settings and × remain available when the native media session is missing.

## Existing setup and limits

The existing [native Bluetooth setup](../docs/discovery-and-reproduction.md) is still required. This ordinary app does not set `sys.fyt.systemobd`, switch receive/transmit roles, force-stop Bluetooth or modify the sound/Bluetooth system packages. Granting overlay permission does not grant those privileges. Receiving mode remains the current objective.

BT ON uses the confirmed Android enable prompt, including when the adapter reports off in the stuck BLE-only state. BT OFF uses the Android 8.1 adapter API with BLUETOOTH_ADMIN; it waits for state broadcasts and reports an unconfirmed shutdown after ten seconds. It does not repair the underlying vendor toggle fault. No automatic boot launch, auto connection or automatic source selection is enabled.

Source on/off and remote playback remain separate: selecting a source does not automatically start the sender. Source release does not send pause to the Mac, and returning to a local source does not send play to another app. Joying's existing service applies stream-4 volume policy; the floating app does not fight it with direct alarm-volume writes.

The bar uses a foreground service and stops on explicit close; the system can still terminate it. The source it acquired is remembered so reopening can restore it. If a release cannot be verified, the error is shown and the return-source record is retained. If necessary, select another source using Joying's original apps. Overlay behavior, binder access, remote controls and adapter cycling need acceptance on the actual unit.

## Build in Android Studio

Open the repository's **`app/`** directory. The root project is the application module; there is no nested `app/app/` directory. Use:

- Gradle **8.13**, selected from `gradle/wrapper/gradle-wrapper.properties`.
- Android Gradle Plugin **8.13.2**.
- Gradle JDK **17** and Android SDK Platform **35**.
- Minimum SDK **21**, target SDK **27**; intended device is Android 8.1/API 27.

Sync, then build the debug APK. With an installed Gradle 8.13:

```sh
cd app
gradle assembleDebug
adb install -r build/outputs/apk/debug/BluetoothControls-debug.apk
```

The distribution properties are included; command-line wrapper launchers/JAR are not. Use Android Studio or an installed Gradle 8.13 for terminal builds. Keep local SDK paths in ignored `local.properties`. Existing installs with the same package and signing key can be updated in place. A differently signed installation must first be uninstalled, which also clears saved bar position/return source; leave Bluetooth source before doing that.

No APKs, signing keys, vendor libraries or binary payloads are published. There are no external application dependencies.

## Validation

```sh
python3 app/tests/run_checks.py --android-jar /path/to/platforms/android-35/android.jar
python3 app/tests/check_flows.py
```

Java 17 with `jdk.compiler` is required. The first command checks 14 source-routing scenarios, manifest permissions/lifecycle declarations, and optionally compiles all production Java against the SDK (using a generated R stand-in). The second checks 12 launcher/native-session scenarios with host Android stand-ins, including overlay permission denial, canceled/recreated enable prompts, unsupported actions and stale connection callbacks. These checks do not emulate a head unit, prove Binder/audio access, or assemble an APK.

Completed here: Android SDK Java compilation and 26 focused host scenarios. Full Gradle/resource packaging and on-unit visual/audio acceptance remain pending; the owner builds the APK.

On-unit acceptance: grant/deny overlay permission; drag and reopen; connect the Mac; Source ON then OFF; each supported media button; BT OFF then ON including BLE-only recovery; × while source acquisition is pending; close after another app selects a different source. Confirm that other apps' media controls are unaffected.

For a diagnostic capture on the Mac Desktop:

```sh
./adb logcat -d -v threadtime > ~/Desktop/joying-bt-floating.txt
```

App diagnostics use the `JoyingBluetooth` log tag. The [interface investigation](../docs/source-playback-and-startup.md) records the underlying commands and observed restrictions.
