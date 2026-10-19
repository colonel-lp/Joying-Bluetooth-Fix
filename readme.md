# Joying Bluetooth Fix

## Objective and goals

Enable native Android Bluetooth on Joying/FYT SC9853i head units and provide compact controls for receiving music, audio-source selection and Bluetooth settings. Transmitting audio and broader device support remain investigation goals.

## Repository folders

| Folder | Purpose |
|---|---|
| [app/](app/) | Bluetooth Controls Android source and build instructions. |
| [scripts/persistence/](scripts/persistence/) | USB Check, install and restore layouts, with per-action logs. |
| [docs/](docs/) | Bluetooth findings, commands, limitations and validation records. |

## Joying Bluetooth structure

- Joying's original Bluetooth app controls a vendor Bluetooth stack/module.
- The firmware also contains native Android Bluetooth, normally bypassed by vendor configuration.
- Native received audio uses Android alarm stream **4**. Joying's sound service selects Bluetooth Music source **3** and sets that stream to **7/7**; normal speaker volume remains under Joying control.
- The native media session provides remote playback commands independently of the vendor app's buttons.

## Enable native Bluetooth with ADB

From Android platform-tools, using an already-authorized connection:

```sh
./adb connect HEAD_UNIT_IP:5555
./adb shell 'setprop sys.fyt.systemobd 1 && setprop persist.lsec.enable_a2dp 0 && am force-stop com.android.bluetooth && am start -a android.settings.BLUETOOTH_SETTINGS'
```

Enable Bluetooth, pair the sender and select the Joying as its sound output. Select **Source ON** in Bluetooth Controls, or open Joying Bluetooth Music. The default native device name is `lsec-android`; use its current name if renamed.

If Bluetooth remains off/BLE-only, request Android's enable confirmation:

```sh
./adb shell am start -a android.bluetooth.adapter.action.REQUEST_ENABLE
./adb shell dumpsys bluetooth_manager | head -n 4
```

Full operation requires `enabled: true` and `state: ON`. If connected but silent, switch the sender's audio output away from the Joying and back, then resume playback. A Bluetooth process restart is needed when changing its startup properties, not for routine connections.

## Surviving reboots

`sys.fyt.systemobd` resets on reboot. The [persistence scripts](scripts/persistence/readme.md) set `sys.fyt.systemobd=1` in `/oem/app/config.txt`, retaining a verified backup for restore. Use the compatible offline USB updater and keep the backup folder. Each action writes a USB log.

`persist.lsec.enable_a2dp=0` is the separate persistent receiving-mode setting. After reboot, check both values with `getprop`. The floating app and its overlay permission do not provide system-property write access. The app does not launch automatically at boot.

## Capabilities and limitations

- Laptop/phone music can reach the native receiver through Joying's Bluetooth source.
- Receive mode is `persist.lsec.enable_a2dp=0`; transmit mode is `1`. Changing roles currently requires restarting Bluetooth; simultaneous operation is not established.
- Headphone output works separately, but Joying's volume buttons do not provide suitable headphone-volume control.
- The stock settings toggle can remain off/BLE-only. App state checks and a bounded enable retry address recovery; they do not patch the underlying firmware.
- Android 8.1 permits direct adapter control with the app's declared `BLUETOOTH_ADMIN` permission. A system confirmation is retained as a fallback if direct enablement is denied.
- Remote players can ignore commands, including Stop. Pairing, a connection label or command dispatch alone does not establish audio or playback control.

## App status

**Bluetooth Controls v1.3** provides a draggable floating bar with remembered position, previous/play-pause/stop/next, source on/off, adapter on/off, Settings, status and close. Source OFF restores the previous source, or Android when no previous source is known. Close releases a source acquired by the app and leaves the adapter enabled.

Receiving playback and the main floating controls are working on the target unit. The latest source adds direct enablement, transition handling and access to the native Stop handler; these changes need device confirmation. Native-mode setup remains a prerequisite. The app does not write system properties, restart Bluetooth, switch audio roles or select a source automatically.
