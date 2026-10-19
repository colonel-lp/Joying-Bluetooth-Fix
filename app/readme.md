# Bluetooth Settings shortcut source

Version **1.1 / code 2** is a small launcher for the Joying's Android Bluetooth settings:

1. If Bluetooth is fully on, open settings immediately.
2. If Bluetooth is off or stuck in BLE-only mode, show Android's normal enable confirmation.
3. After a successful result, confirm the adapter is enabled and open settings. Cancel exits without another prompt.

Reopen this app's icon to recover after the stock settings switch gets stuck. No background monitoring, fixed delay, silent enable, service, network access, root access or Bluetooth property writes are used. The app requests the normal `BLUETOOTH` permission to read adapter state and use the enable confirmation on Android 8.1. It keeps only transient request state across activity recreation.

The activity uses a translucent window and remains in history while waiting for the enable result. A no-display/no-history activity cannot safely wait for that result. It closes after settings opens or the request is canceled.

- Package: `com.joying.bluetoothsettings`; label: **Bluetooth Settings**.
- Minimum Android API 21; target API 27; compile SDK 35.
- `src/main/` is the conventional Java/Android project.
- `smali/` preserves the initial shortcut activity implementation, with its package renamed to match this project. It is a historical v1.0 reference, not part of the Gradle Java source set and does not contain the v1.1 recovery flow. Build current releases from `src/main/`.

## Build locally

Open this `app/` directory in Android Studio. Under **Settings → Build, Execution, Deployment → Build Tools → Gradle**, select:

- **Distribution / Use Gradle from:** `gradle-wrapper.properties` file.
- **Gradle JDK:** JDK 17.

Then sync the project. `gradle/wrapper/gradle-wrapper.properties` selects **Gradle 8.13** for the IDE. AGP remains **8.13.2**; Gradle 9.6 cannot load that plugin because it removed an internal API the plugin uses. The settings file rejects a different Gradle version before the Android plugin loads.

For a terminal build, use an installed **Gradle 8.13**, JDK 17 and Android SDK Platform 35:

```sh
cd app
gradle assembleDebug
```

The root project itself is the application module; there is no nested `app/app/` directory. Supply the SDK location locally through Android Studio or `local.properties`. The IDE distribution file is included; command-line wrapper launchers/JAR are not included, so use Android Studio or an installed Gradle 8.13 for terminal builds. The pinned AGP 8.13.2 toolchain requirements are documented in the [official release notes](https://developer.android.com/build/releases/agp-8-13-0-release-notes).

Install your locally generated debug APK:

```sh
adb install -r build/outputs/apk/debug/BluetoothSettings-debug.apk
```

The package is now independent of the EQ/DSP project. Remove the earlier shortcut installation through Android Settings to avoid duplicate icons. For later updates of this package, an installation signed with a different key must be uninstalled first. The shortcut has no saved data to lose:

```sh
adb uninstall com.joying.bluetoothsettings
```

No APK, signing key or debug keystore is stored in this repository. The initial smali-built APK passed manifest/DEX and v1/v2-signature checks. This conventional Java/Gradle project has not been assembled in the preparation environment; installation of the new shortcut on the head unit has not yet been reported. Its settings action and enable-confirmation recovery have been tested through ADB. The owner confirmed `enabled: true` and `state: ON` after the enable prompt recovered BLE-only mode; this does not establish that the stock toggle fault is fixed. Current activity lifecycle/decision checks use host Android stand-ins; on-unit app acceptance remains required.


## Flow checks and head-unit acceptance

Run `python3 app/tests/run_checks.py` from the repository root with a JDK containing `jdk.compiler`. It compiles the actual activity with host Android stand-ins and checks 13 flows: already enabled, confirmed enable, cancel, false-success state, unavailable adapter/request/settings, state-access failure, recreation while waiting, duplicate/unrelated results and a fresh launch after cancellation. The manifest check ensures the waiting activity uses a visible-capable theme, retains history and requests only `BLUETOOTH`. These checks do not emulate Android services or radio/audio hardware.

After building/installing v1.1 on the Joying, verify: ON opens settings without a prompt; OFF/BLE-only prompts once and opens settings after acceptance; cancel closes without another prompt; reopening the icon can retry. The stock settings toggle fault itself remains unresolved. Current receiver operation still needs the existing native-mode setup and Joying Bluetooth Music source selection.

Future routing/mixing and media controls are recorded separately in [future objectives](../docs/future-objectives.md); they are not implemented by this launcher.
