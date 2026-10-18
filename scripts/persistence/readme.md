# Native-mode persistence r1

Configuration-only scripts for the tested `sp9853i_1h10`, Android 8.1 / API 27. They add or replace `sys.fyt.systemobd=1` in `/oem/app/config.txt`; they do not replace an APK, Bluetooth library or module type.

**Status:** seven host checks pass; actual USB installation and reboot behaviour still require acceptance on the head unit. Confirm the [temporary receiving setup](../../docs/discovery-and-reproduction.md#2-enable-native-receiving) works before making it persistent.

## USB layout

Reuse the exact compatible `lsec6521update` and offline procedure already proven on your unit. The binary is not provided. Use one launcher at a time:

| USB path | Input |
|---|---|
| `/lsec6521update` | Your known-compatible updater. |
| `/lsec.sh` | Copy `INSTALL-lsec.sh` and rename the copy to `lsec.sh`. |
| `/joying-bluetooth/run.sh` | `run.sh` from this folder. |

Run the known **offline** USB update, remove the USB and allow a full boot. Installation/restoration refuse to run while Android/ADB is active. No APK is needed on the USB.

Read `/joying-bluetooth-install.log` on the USB. Expected result: `INSTALLED`, `ALREADY_INSTALLED` or `ALREADY_CONFIGURED`, exit 0. Retain `/joying-bluetooth-backup-r1/`: it holds the verified original config and installation receipt.

The receiving profile must already have been selected with `persist.lsec.enable_a2dp=0` in the temporary setup. This persistent property is separate from the config edit.

## Check and restore

- **CHECK:** substitute `CHECK-lsec.sh`, renamed to `lsec.sh`; run the offline updater and inspect `joying-bluetooth-check.log`. It reads the config without changing it or creating a backup.
- **RESTORE:** substitute `RESTORE-lsec.sh`, renamed to `lsec.sh`; retain `joying-bluetooth/run.sh` and the original backup folder. Inspect `joying-bluetooth-restore.log`.

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
sh -n scripts/persistence/run.sh
python3 scripts/persistence/test_installer.py
```

The host harness redirects device paths into temporary fixtures. It covers installation/idempotence/exact restore, duplicate values, read-only check/wrong device, later owner edits, corrupt backups, rollback on labelling failure, and symlink/running-Android rejection. It does not emulate the updater, OEM mount or actual boot order.
