# Bluetooth Settings shortcut source

An ordinary launcher app that opens `android.settings.BLUETOOTH_SETTINGS` and exits. It has no requested permissions, service, network access, root access, stored state or Bluetooth property writes.

- Package: `com.eqdsp.bluetoothsettings`; label: **Bluetooth Settings**.
- Minimum Android API 21; target API 27; compile SDK 35.
- `src/main/` is the conventional Java/Android project.
- `smali/` preserves the exact activity source used for the initial shortcut APK. It is a reference input, not part of the Gradle Java source set.

## Build locally

Open this `app/` directory in Android Studio, or use Gradle 8.13, JDK 17 and Android SDK Platform 35:

```sh
cd app
gradle assembleDebug
```

The root project itself is the application module; there is no nested `app/app/` directory. Supply the SDK location locally through Android Studio or `local.properties`. No Gradle wrapper binary is committed. The pinned AGP 8.13.2 toolchain requirements are documented in the [official release notes](https://developer.android.com/build/releases/agp-8-13-0-release-notes).

Install your locally generated debug APK:

```sh
adb install -r build/outputs/apk/debug/BluetoothSettings-debug.apk
```

An existing installation signed with the original shortcut certificate must be uninstalled before installing a build signed with another key. The shortcut has no saved data to lose:

```sh
adb uninstall com.eqdsp.bluetoothsettings
```

No APK, signing key or debug keystore is stored in this repository. The initial smali-built APK passed manifest/DEX and v1/v2-signature checks. This conventional Java/Gradle project has not been assembled in the preparation environment; installation of the new shortcut on the head unit has not yet been reported. Its underlying settings action has already been tested through ADB.
