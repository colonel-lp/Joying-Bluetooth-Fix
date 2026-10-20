# Generic native Bluetooth boot setting

Check, install or restore `sys.fyt.systemobd=1` on FYT firmware without a model or Android-version restriction. The firmware must read `/oem/app/config.txt` at boot and implement the native Bluetooth override. This is a trial package; finding the config file does not establish Bluetooth support. It cannot add a missing stack, profile or codec.

## Folders and use

| Folder | Action | USB log |
|---|---|---|
| `Check/` | Report device properties and the current boot setting; no config changes. | `joying-bluetooth-check.log` |
| `install/` | Back up the original config and set the native override. | `joying-bluetooth-install.log` |
| `restore/` | Restore the original config and its ownership/permissions. | `joying-bluetooth-restore.log` |

Copy the contents of one action folder to the USB root. Add the offline updater supplied for that device. The included launcher uses `/system/bin/sh` and `/storage/sdcard1/joying-bluetooth/run.sh`; adjust that one path if its updater mounts the USB elsewhere. The runner finds the USB from its own location. Use an updater that supports `lsec_updatesh/lsec.sh`; updater binaries are device-specific and are not included.

Run Check first, then Install through the offline updater. Remove the USB before normal boot. Install and Restore require root, the existing OEM config, supported shell commands and `restorecon`, and refuse to run while Android/ADB is active. Missing requirements are logged; the script does not create a replacement boot configuration.

Keep the complete `joying-bluetooth-backup-generic-r1/` folder on that device's USB when changing actions. Backups are grouped by a hash of device properties, including serial properties when available. Use a separate USB/backup for each physical device: some firmware omits or duplicates serials. Restore requires the matching original backup and receipt, and refuses to overwrite later config edits. This package uses separate backups from the SC9853i-specific package.

## Bluetooth structure and reboot

FYT firmware can use a vendor Bluetooth stack while retaining a native Android stack. On firmware implementing this override, `sys.fyt.systemobd=1` selects native startup. The meaning and effect must be established on each firmware. The script changes the boot setting only; it does not change module type, profile selection or app permissions.

After a full reboot, check:

```sh
./adb shell getprop sys.fyt.systemobd
```

The installed setting should return `1`. Test discovery, pairing, connection and the required profile. A successful install log confirms the config change, not that the firmware applied it.

On the documented SC9853i firmware, receiving audio also requires:

```sh
./adb shell setprop persist.lsec.enable_a2dp 0
```

That property is firmware-specific; it is not set by this package. Restore returns the boot config to its original bytes, including any original native-mode value, and leaves separate persistent properties and apps unchanged.
