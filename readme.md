# Joying Bluetooth Fix

Native Android Bluetooth settings and audio-streaming investigation for a Joying/FYT SC9853i head unit running Android 8.1.

**Confirmed:** a Mac can stream music into the Joying's native Bluetooth receiver. The native stack was bypassed by vendor settings; it was not necessary to replace the Bluetooth APK or libraries to obtain playback.

## Contents

| Folder | Contents |
|---|---|
| [app/](app/) | Bluetooth Settings shortcut: Android/Java project and original smali activity source. |
| [scripts/persistence/](scripts/persistence/) | Configuration-only USB CHECK/INSTALL/RESTORE scripts and host checks. |
| [docs/](docs/discovery-and-reproduction.md) | Findings, commands and reproducible receiving-mode procedure. |

No APKs, vendor binaries, signing keys or personal diagnostic logs are included.

## Quick start

Prerequisite: an already-authorized ADB connection to the head unit. From Android platform-tools on the Mac:

```sh
./adb connect HEAD_UNIT_IP:5555
./adb shell 'setprop sys.fyt.systemobd 1 && setprop persist.lsec.enable_a2dp 0 && am force-stop com.android.bluetooth && am start -a android.settings.BLUETOOTH_SETTINGS'
```

Replace `HEAD_UNIT_IP` with the Joying's Wi-Fi address. Enable Bluetooth if needed, pair the Mac/phone, select the Joying as its media output, then open **Joying's original Bluetooth Music screen**. The default native name was `lsec-android`; use the current name if renamed.

The [shortcut app](app/readme.md) adds a launcher icon for the same settings screen. It does not set properties, enable native mode, connect devices or select the Joying audio source.

If connected but silent, pause the Mac player, select the Mac speakers as output, reselect the Joying, and resume playback with Bluetooth Music open. This recovered playback after the receive/transmit role switch on the tested unit.

## Reboot persistence and limits

`sys.fyt.systemobd=1` is volatile. The [USB procedure](scripts/persistence/readme.md) retains it through `/oem/app/config.txt`, with a verified original backup and guarded restore. It requires the existing compatible offline `lsec6521update` tool; no updater is supplied.

Tested device: `sp9853i_1h10`, Android API 27, Joying firmware dated 2020-04-07. Receiving playback and manual recovery are confirmed. The persistence scripts passed seven host checks, but their first head-unit installation/reboot acceptance remains pending. Shortcut APK structure/signature checks passed; installation of the new icon on the unit has not yet been reported. The Java/Gradle project has not been assembled in the preparation environment.

Headphone output also produced audio and media-button control, but the normal Joying volume buttons did not control its level. That work is deferred. Automatic source switching/reconnection is not implemented.
