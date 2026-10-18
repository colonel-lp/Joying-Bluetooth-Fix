# Bluetooth Settings shortcut source

An ordinary launcher app that opens `android.settings.BLUETOOTH_SETTINGS` and exits. It has no requested permissions, service, network access, root access, stored state or Bluetooth property writes.

- Package: `com.joying.bluetoothsettings`; label: **Bluetooth Settings**.
- Minimum Android API 21; target API 27; compile SDK 35.
- `src/main/` is the conventional Java/Android project.
- `smali/` preserves the initial shortcut activity implementation, with its package renamed to match this project. It is a reference input, not part of the Gradle Java source set.

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

No APK, signing key or debug keystore is stored in this repository. The initial smali-built APK passed manifest/DEX and v1/v2-signature checks. This conventional Java/Gradle project has not been assembled in the preparation environment; installation of the new shortcut on the head unit has not yet been reported. Its underlying settings action has already been tested through ADB.
