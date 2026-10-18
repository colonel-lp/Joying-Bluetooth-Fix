# Native-mode persistence r1

Configuration-only scripts for the tested `sp9853i_1h10`, Android 8.1 / API 27. They add or replace `sys.fyt.systemobd=1` in `/oem/app/config.txt`; they do not replace an APK, Bluetooth library or module type.

**Status:** eight host checks pass; actual USB installation and reboot behaviour still require acceptance on the head unit. Confirm the [temporary receiving setup](../../docs/discovery-and-reproduction.md#2-enable-native-receiving) works before making it persistent.

## USB layout

Each action has a complete script layout, with `lsec.sh` already named correctly and its runner included:

| Folder to copy from | Action | Log at USB root |
|---|---|---|
| [Check/](Check/) | Read the current configuration. | `joying-bluetooth-check.log` |
| [install/](install/) | Back up and enable native mode in the boot config. | `joying-bluetooth-install.log` |
| [restore/](restore/) | Restore the verified original configuration. | `joying-bluetooth-restore.log` |

Copy the **contents of one action folder** to the USB root, replacing the previous `lsec.sh` and `joying-bluetooth/run.sh`. Do not copy the enclosing `Check`, `install` or `restore` folder. No renaming is required.

Keep the exact compatible **`lsec6521update` at the USB root**, using the offline procedure already proven on your unit. This vendor binary is not supplied in this repository; retain your existing copy. The resulting USB contains `/lsec6521update`, `/lsec.sh` and `/joying-bluetooth/run.sh`.

**Keep `joying-bluetooth-backup-r1/` on the USB when changing actions.** Restore needs the original backup and receipt created by install. If using another USB, copy that backup folder onto it too. Never replace the backup with an empty folder.

Run the known **offline** USB update, remove the USB and allow a full boot. Installation/restoration refuse to run while Android/ADB is active. No APK is needed on the USB.

Every action appends to its own log at the USB root, preserving earlier runs. The launcher logs missing-runner errors and the runner's exit status too. A writable USB is required for logging. For install, expect `INSTALLED`, `ALREADY_INSTALLED` or `ALREADY_CONFIGURED`, exit 0. Retain `/joying-bluetooth-backup-r1/`: it holds the verified original config and installation receipt.

The receiving profile must already have been selected with `persist.lsec.enable_a2dp=0` in the temporary setup. This persistent property is separate from the config edit.

## Check and restore

- **CHECK:** copy the contents of `Check/` to the USB root, run the offline updater and inspect `joying-bluetooth-check.log`. It reads the config without changing it or creating a backup.
- **RESTORE:** copy the contents of `restore/` to the USB root, retain the original backup folder, run the offline updater and inspect `joying-bluetooth-restore.log`.

Restore verifies the original backup and refuses to overwrite a config edited since installation. It restores the exact original bytes and recorded ownership/mode. If the original already contained a native-mode setting, that original value returns. Restore does not undo the separately selected receiving profile or uninstall the shortcut.

## Reboot acceptance

With ADB reconnected, check:

```sh
./adb shell getprop sys.fyt.systemobd
./adb shell getprop persist.lsec.enable_a2dp
```

Expected: `1`, then `0`. Open native Bluetooth Settings, connect the sender, choose the Joying as its audio output, and open Joying Bluetooth Music. Record both the values and audible playback; a successful USB script alone does not prove boot persistence or audio operation.

For Mac-side log collection, keep files on the Desktop:

```sh
./adb pull /storage/sdcard1/joying-bluetooth-install.log ~/Desktop/joying-bluetooth-install.log
```

## Local checks

```sh
sh -n scripts/persistence/install/joying-bluetooth/run.sh
python3 scripts/persistence/test_installer.py
```

The host harness redirects device paths into temporary fixtures. It covers installation/idempotence/exact restore, duplicate values, read-only check/wrong device, later owner edits, corrupt backups, rollback on labelling failure, and symlink/running-Android rejection. It also checks all three USB layouts, matching runners, launcher logging, missing runners, preserved previous logs and nonzero exit propagation. It does not emulate the updater, OEM mount or actual boot order.
