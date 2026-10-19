# Joying Bluetooth Fix

Floating Bluetooth controls, native Android settings and audio-streaming investigation for a Joying/FYT SC9853i head unit running Android 8.1.

**Confirmed:** a Mac can stream music into the Joying's native Bluetooth receiver. The native stack was bypassed by vendor settings; it was not necessary to replace the Bluetooth APK or libraries to obtain playback.

## Contents

| Folder | Contents |
|---|---|
| [app/](app/) | Bluetooth Controls v1.2: draggable overlay, native playback, source and adapter controls; historical shortcut source. |
| [scripts/persistence/](scripts/persistence/) | Copy-to-USB Check/install/restore folders, per-action logs and host checks. |
| [docs/](docs/discovery-and-reproduction.md) | Findings, commands and reproducible receiving-mode procedure. |

No APKs, vendor binaries, signing keys or personal diagnostic logs are included.

## Quick start

Prerequisite: an already-authorized ADB connection to the head unit. From Android platform-tools on the Mac:

```sh
./adb connect HEAD_UNIT_IP:5555
./adb shell 'setprop sys.fyt.systemobd 1 && setprop persist.lsec.enable_a2dp 0 && am force-stop com.android.bluetooth && am start -a android.settings.BLUETOOTH_SETTINGS'
```

Replace `HEAD_UNIT_IP` with the Joying's Wi-Fi address. Enable Bluetooth if needed, pair the Mac/phone, select the Joying as its media output, then open **Joying's original Bluetooth Music screen**. The default native name was `lsec-android`; use the current name if renamed.

The [Bluetooth Controls app v1.2](app/readme.md) provides a small draggable bar above other apps: previous, play/pause, stop (when advertised), next, Joying Bluetooth source on/off, adapter on/off, Settings and close. It remembers its position, shows native playback status, confirms source changes through Joying readback, and restores a source acquired by the app on close. First launch requests display-over-other-apps permission. Enabling Bluetooth uses Android's recovery confirmation; launch itself does not force Bluetooth on.

Current native-mode setup remains a prerequisite. The app does not write system properties, restart the Bluetooth process or switch receive/transmit roles.

If connected but silent, pause the Mac player, select the Mac speakers as output, reselect the Joying, and resume playback with Bluetooth Music open. This recovered playback after the receive/transmit role switch on the tested unit.

## Reboot persistence and limits

`sys.fyt.systemobd=1` is volatile. The [USB procedure](scripts/persistence/readme.md) retains it through `/oem/app/config.txt`, with a verified original backup and guarded restore. It requires the existing compatible offline `lsec6521update` tool; no updater is supplied.

Tested device: `sp9853i_1h10`, Android API 27, Joying firmware dated 2020-04-07. Native receiving playback, source 3/stream-4 level 7 and the native media session are confirmed. Persistence scripts passed nine host checks; first unit installation/reboot acceptance remains pending. The v1.2 production Java compiled against Android SDK 35 and passed 26 focused host scenarios for routing and launcher/playback flows. No v1.2 APK was assembled here; full Gradle packaging and floating-bar/audio acceptance on the unit remain pending.

Headphone output also produced audio and media-button control, but the normal Joying volume buttons did not control its level. That work is deferred. Automatic source selection and device reconnection are not implemented; the floating bar retries its native media-service connection while Bluetooth is enabled.
