# Native-mode persistence

Enable `sys.fyt.systemobd=1` at boot through `/oem/app/config.txt`, with a verified original backup and guarded restore. Intended for the Joying SC9853i Android 8.1 setup described in the [project README](../../readme.md).

## USB layouts

| Copy contents from | Action | USB log |
|---|---|---|
| [Check/](Check/) | Read the current configuration. | `joying-bluetooth-check.log` |
| [install/](install/) | Back up the config and enable native mode. | `joying-bluetooth-install.log` |
| [restore/](restore/) | Restore the verified original config. | `joying-bluetooth-restore.log` |

Copy the **contents** of one action folder to the USB root. Use the compatible `lsec6521update` at the USB root. The required paths are:

- `/lsec6521update`
- `/lsec_updatesh/lsec.sh`
- `/joying-bluetooth/run.sh`

Keep `/joying-bluetooth-backup-r1/` when changing actions or moving to another USB. Restore requires its original backup and receipt. Use the offline USB procedure; install and restore refuse to run while Android/ADB is active. Remove the USB before normal boot.

Each action appends its result and exit status to its own log. Copy the complete layout: a missing runner cannot create a log. A writable USB is required.

## Compatibility and restore

The launcher contains an interpreter line and one child-shell command. Keep variables, functions and logging in `run.sh`; the updater processes launcher lines independently. Use `lsec_updatesh/lsec.sh`, not a USB-root `lsec.sh`.

Install preserves unrelated config content and records the original ownership/mode. Restore verifies the backup and refuses to overwrite later unrelated edits. It restores the original bytes and metadata, including any original native-mode setting. It does not change the separate receiving-mode property or uninstall the app.

## Reboot checks

Set receiving mode with `persist.lsec.enable_a2dp=0` using the [ADB procedure](../../readme.md#enable-native-bluetooth-with-adb). After a full reboot:

```sh
./adb shell getprop sys.fyt.systemobd
./adb shell getprop persist.lsec.enable_a2dp
```

Expected values are `1`, then `0`. Connect a sender and select Bluetooth Music source to check playback. A script result alone does not establish boot persistence.

Copy an install log to the Mac Desktop if needed:

```sh
./adb pull /storage/sdcard1/joying-bluetooth-install.log ~/Desktop/joying-bluetooth-install.log
```
