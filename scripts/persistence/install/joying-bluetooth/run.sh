#!/system/bin/sh
# Native Bluetooth receiving setup r1. Run through the offline lsec updater.
umask 077
JBT_MODE=$1
case "$JBT_MODE" in check|install|restore) ;; *) exit 2;; esac
JBT_USB=/storage/sdcard1
JBT_TARGET=/oem/app/config.txt
JBT_OEM=/oem
JBT_BACKUP=$JBT_USB/joying-bluetooth-backup-r1
JBT_RECEIPT=$JBT_BACKUP/receipt.txt
JBT_STAGE=/oem/app/.config.bluetooth-receiver-r1
JBT_RESTORECON=/system/bin/restorecon
JBT_CHANGED=0
JBT_STAGE_OWNED=0
JBT_REMOUNTED=0
JBT_RESULT=FAILED
[ ! -L "$JBT_USB/joying-bluetooth-$JBT_MODE.log" ] || exit 1
exec >>"$JBT_USB/joying-bluetooth-$JBT_MODE.log" 2>&1 || exit 1
fail() { echo "ERROR: $*"; exit 1; }
hash_file() { JBT_H=$(sha256sum "$1") || return 1; echo "${JBT_H%% *}"; }
valid_hash() { [ "${#1}" = 64 ] && case "$1" in *[!0-9a-f]*) return 1;; *) return 0;; esac; }
safe_file() { [ ! -L "$1" ] && { [ ! -e "$1" ] || [ -f "$1" ]; }; }
check_hash() { [ -f "$1" ] && [ ! -L "$1" ] && [ "$(hash_file "$1")" = "$2" ]; }
permissions() {
    chown "$JBT_UID.$JBT_GID" "$1" && chmod "$JBT_PERM" "$1"
}
writable() {
    if [ ! -w "$JBT_OEM/app" ]; then
        mount -o remount,rw "$JBT_OEM" || fail "Cannot make OEM partition writable."
        JBT_REMOUNTED=1
    fi
}
stage_original() {
    check_hash "$JBT_BACKUP/$JBT_ORIGINAL.config.txt" "$JBT_ORIGINAL" || return 1
    cp "$JBT_BACKUP/$JBT_ORIGINAL.config.txt" "$JBT_STAGE" || return 1
    JBT_STAGE_OWNED=1
    permissions "$JBT_STAGE" && check_hash "$JBT_STAGE" "$JBT_ORIGINAL" || return 1
    mv -f "$JBT_STAGE" "$JBT_TARGET" || return 1
    permissions "$JBT_TARGET" && "$JBT_RESTORECON" "$JBT_TARGET" && check_hash "$JBT_TARGET" "$JBT_ORIGINAL"
}
finish() {
    JBT_EXIT=$?; trap - 0 1 2 15
    if [ "$JBT_EXIT" != 0 ] && [ "$JBT_CHANGED" = 1 ]; then
        if stage_original; then echo "Pre-operation configuration restored."
        else echo "ROLLBACK FAILED: retain the USB, backup and log."; fi
    fi
    if [ "$JBT_STAGE_OWNED" = 1 ]; then rm -f "$JBT_STAGE"; fi
    sync
    if [ "$JBT_REMOUNTED" = 1 ]; then
        if ! mount -o remount,ro "$JBT_OEM"; then echo "ERROR: could not return OEM to read-only."; JBT_EXIT=1; fi
    fi
    echo "RESULT: $JBT_RESULT; exit $JBT_EXIT"
    exit "$JBT_EXIT"
}
trap finish 0
trap 'exit 130' 1 2 15
echo "Joying native Bluetooth receiving setup r1: $JBT_MODE"
[ "$(id -u)" = 0 ] || fail "Use the root offline USB updater."
for JBT_CMD in sha256sum cp mv rm mkdir chown chmod sync pidof getprop stat sed grep tr mount; do
    command -v "$JBT_CMD" >/dev/null 2>&1 || fail "Missing command: $JBT_CMD"
done
[ "$(getprop ro.product.device)" = sp9853i_1h10 ] || fail "Requires the tested sp9853i_1h10 device."
[ "$(getprop ro.build.version.sdk)" = 27 ] || fail "Requires Android 8.1 / SDK 27."
[ -x "$JBT_RESTORECON" ] || fail "SELinux labelling tool missing."
[ -f "$JBT_TARGET" ] && [ ! -L "$JBT_TARGET" ] || fail "Missing/unsafe OEM config."
JBT_CURRENT=$(hash_file "$JBT_TARGET") || fail "Cannot hash config."
valid_hash "$JBT_CURRENT" || fail "Invalid config hash."
echo "Current config SHA-256: $JBT_CURRENT"
grep '^[[:space:]]*sys[.]fyt[.]systemobd[[:space:]]*=' "$JBT_TARGET" || echo "Native override: not set in config."
if [ "$JBT_MODE" = check ]; then JBT_RESULT=CHECK_COMPLETE; exit 0; fi
if pidof system_server audioserver adbd >/dev/null 2>&1; then fail "Android is running; use the offline updater."; fi
[ ! -L "$JBT_OEM" ] && [ ! -L "$JBT_OEM/app" ] || fail "Unexpected OEM symlink."
[ ! -e "$JBT_STAGE" ] && [ ! -L "$JBT_STAGE" ] || fail "Staging residue: inspect before continuing."
[ ! -L "$JBT_BACKUP" ] || fail "Unsafe backup directory."
[ ! -e "$JBT_BACKUP" ] || [ -d "$JBT_BACKUP" ] || fail "Backup path is not a directory."
safe_file "$JBT_RECEIPT" || fail "Unsafe receipt."
JBT_HAS_RECEIPT=0
if [ -f "$JBT_RECEIPT" ]; then
    IFS=' ' read -r JBT_VERSION JBT_ORIGINAL JBT_INSTALLED JBT_PERM JBT_UID JBT_GID JBT_EXTRA <"$JBT_RECEIPT" || fail "Unreadable receipt."
    [ "$JBT_VERSION" = 1 ] && [ -z "$JBT_EXTRA" ] || fail "Invalid receipt version/fields."
    valid_hash "$JBT_ORIGINAL" && valid_hash "$JBT_INSTALLED" || fail "Invalid receipt hashes."
    case "$JBT_PERM" in ''|*[!0-7]*) fail "Invalid permission receipt.";; esac
    case "$JBT_UID:$JBT_GID" in *[!0-9:]*) fail "Invalid owner receipt.";; esac
    [ -n "$JBT_UID" ] && [ -n "$JBT_GID" ] || fail "Missing owner receipt."
    check_hash "$JBT_BACKUP/$JBT_ORIGINAL.config.txt" "$JBT_ORIGINAL" || fail "Original backup hash mismatch."
    JBT_HAS_RECEIPT=1
    if [ "$JBT_CURRENT" != "$JBT_INSTALLED" ] && [ "$JBT_CURRENT" != "$JBT_ORIGINAL" ]; then
        fail "Config changed after installation; automatic overwrite refused. Keep backups and merge deliberately."
    fi
fi
if [ "$JBT_MODE" = restore ]; then
    [ "$JBT_HAS_RECEIPT" = 1 ] || fail "No installation receipt: nothing safe to restore."
    if [ "$JBT_CURRENT" = "$JBT_ORIGINAL" ]; then JBT_RESULT=ALREADY_RESTORED; exit 0; fi
    writable
    # Restoration failure leaves either the installed file or the verified original.
    stage_original || fail "Restore failed."
    JBT_RESULT=RESTORED
    exit 0
fi
if [ "$JBT_HAS_RECEIPT" = 1 ] && [ "$JBT_CURRENT" = "$JBT_INSTALLED" ]; then
    JBT_RESULT=ALREADY_INSTALLED; exit 0
fi
JBT_METADATA=$(stat -c '%a %u %g' "$JBT_TARGET") || fail "Cannot preserve config metadata."
IFS=' ' read -r JBT_PERM JBT_UID JBT_GID JBT_EXTRA <<JBT_METADATA_END
$JBT_METADATA
JBT_METADATA_END
[ -z "$JBT_EXTRA" ] || fail "Unexpected config metadata."
case "$JBT_PERM" in ''|*[!0-7]*) fail "Unexpected config permissions.";; esac
case "$JBT_UID:$JBT_GID" in *[!0-9:]*) fail "Unexpected config ownership.";; esac
[ -n "$JBT_UID" ] && [ -n "$JBT_GID" ] || fail "Missing config ownership."
writable
JBT_STAGE_OWNED=1
sed 's/^[[:space:]]*sys[.]fyt[.]systemobd[[:space:]]*=.*$/sys.fyt.systemobd=1/' "$JBT_TARGET" >"$JBT_STAGE" || fail "Cannot stage config."
if ! grep -q '^[[:space:]]*sys[.]fyt[.]systemobd[[:space:]]*=' "$JBT_TARGET"; then
    printf '\nsys.fyt.systemobd=1\n' >>"$JBT_STAGE" || fail "Cannot append native setting."
fi
permissions "$JBT_STAGE" || fail "Cannot preserve config metadata."
JBT_NEW=$(hash_file "$JBT_STAGE") || fail "Cannot hash staged config."
if [ "$JBT_NEW" = "$JBT_CURRENT" ]; then JBT_RESULT=ALREADY_CONFIGURED; exit 0; fi
if [ "$JBT_HAS_RECEIPT" = 1 ]; then
    [ "$JBT_NEW" = "$JBT_INSTALLED" ] || fail "Reinstall differs from recorded setup."
else
    mkdir -p "$JBT_BACKUP" || fail "Cannot create backup directory."
    JBT_ORIGINAL=$JBT_CURRENT
    JBT_INSTALLED=$JBT_NEW
    JBT_SAVE=$JBT_BACKUP/$JBT_ORIGINAL.config.txt
    safe_file "$JBT_SAVE" && safe_file "$JBT_SAVE.new" || fail "Unsafe backup file."
    [ ! -e "$JBT_SAVE.new" ] || fail "Backup staging residue."
    if [ ! -e "$JBT_SAVE" ]; then
        cp "$JBT_TARGET" "$JBT_SAVE.new" || fail "Cannot back up config."
        check_hash "$JBT_SAVE.new" "$JBT_ORIGINAL" || fail "Backup verification failed."
        mv "$JBT_SAVE.new" "$JBT_SAVE" || fail "Cannot commit backup."
    fi
    check_hash "$JBT_SAVE" "$JBT_ORIGINAL" || fail "Backup hash mismatch."
    safe_file "$JBT_RECEIPT.new" && [ ! -e "$JBT_RECEIPT.new" ] || fail "Receipt staging residue."
    printf '1 %s %s %s %s %s\n' "$JBT_ORIGINAL" "$JBT_INSTALLED" "$JBT_PERM" "$JBT_UID" "$JBT_GID" >"$JBT_RECEIPT.new" || fail "Cannot record backup."
    mv "$JBT_RECEIPT.new" "$JBT_RECEIPT" || fail "Cannot commit receipt."
    sync
fi
check_hash "$JBT_TARGET" "$JBT_ORIGINAL" || fail "Config changed during setup."
JBT_CHANGED=1
mv -f "$JBT_STAGE" "$JBT_TARGET" || fail "Cannot apply config."
permissions "$JBT_TARGET" && "$JBT_RESTORECON" "$JBT_TARGET" || fail "Cannot restore permissions/context."
check_hash "$JBT_TARGET" "$JBT_INSTALLED" || fail "Installed config verification failed."
JBT_CHANGED=0
JBT_RESULT=INSTALLED
echo "Native mode configured for reboot. Receiving profile remains the ADB-selected persistent setting."
exit 0
