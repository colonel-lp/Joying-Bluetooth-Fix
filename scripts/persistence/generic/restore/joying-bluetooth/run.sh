#!/system/bin/sh
# Generic FYT native Bluetooth setup. Run through a compatible offline updater.
umask 077
JBT_MODE=$1
case "$JBT_MODE" in check|install|restore) ;; *) exit 2;; esac
JBT_USB=$(CDPATH= cd -P "${0%/*}/.." && pwd) || exit 1
JBT_TARGET=/oem/app/config.txt
JBT_OEM=/oem
JBT_BACKUP=$JBT_USB/joying-bluetooth-backup-generic-r1
JBT_RECEIPT=$JBT_BACKUP/receipt.txt
JBT_STAGE=/oem/app/.config.bluetooth-generic-r1
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
echo "Generic FYT native Bluetooth setup r1: $JBT_MODE"
for JBT_PROP in ro.product.manufacturer ro.product.model ro.product.device ro.product.board ro.hardware ro.build.version.release ro.build.version.sdk sys.fyt.bluetooth_type sys.fyt.systemobd persist.lsec.enable_a2dp bluetooth.mock_stack; do
    printf '%s=%s\n' "$JBT_PROP" "$(getprop "$JBT_PROP")"
done
[ "$(id -u)" = 0 ] || fail "Use the root offline USB updater."
for JBT_CMD in sha256sum cp mv rm mkdir chown chmod sync pidof getprop id ls mount; do
    command -v "$JBT_CMD" >/dev/null 2>&1 || fail "Missing command: $JBT_CMD"
done
[ -x "$JBT_RESTORECON" ] || fail "SELinux labelling tool missing."
if [ ! -f "$JBT_TARGET" ] || [ -L "$JBT_TARGET" ] || [ -L "$JBT_OEM" ] || [ -L "$JBT_OEM/app" ]; then
    echo "Boot config: unavailable or unsafe ($JBT_TARGET)."
    if [ "$JBT_MODE" = check ]; then JBT_RESULT=CHECK_COMPLETE; exit 0; fi
    fail "This firmware does not expose the supported FYT boot config."
fi
JBT_CURRENT=$(hash_file "$JBT_TARGET") || fail "Cannot hash config."
valid_hash "$JBT_CURRENT" || fail "Invalid config hash."
echo "Current config SHA-256: $JBT_CURRENT"
# Parse config keys with shell built-ins; the offline environment lacks sed/stat.
JBT_TAB=$(printf '\t')
JBT_CR=$(printf '\r')
native_key() {
    JBT_KEY=${1%%=*}
    while :; do case "$JBT_KEY" in ' '*|"$JBT_TAB"*) JBT_KEY=${JBT_KEY#?};; *) break;; esac; done
    while :; do case "$JBT_KEY" in *' '|*"$JBT_TAB") JBT_KEY=${JBT_KEY%?};; *) break;; esac; done
    [ "$JBT_KEY" = sys.fyt.systemobd ] && case "$1" in *=*) return 0;; esac
    return 1
}
JBT_FOUND=0
while IFS= read -r JBT_LINE || [ -n "$JBT_LINE" ]; do
    if native_key "$JBT_LINE"; then printf '%s\n' "$JBT_LINE"; JBT_FOUND=1; fi
done <"$JBT_TARGET"
[ "$JBT_FOUND" = 1 ] || echo "Native override: not set in config."
echo "Config layout found; native-stack support is not established by this check."
if [ "$JBT_MODE" = check ]; then JBT_RESULT=CHECK_COMPLETE; exit 0; fi
# Keep each device's backups separate without writing its serial to the log.
JBT_IDENTITY=$(
    for JBT_PROP in ro.product.device ro.product.board ro.hardware ro.serialno ro.boot.serialno; do
        printf '%s=%s\n' "$JBT_PROP" "$(getprop "$JBT_PROP")"
    done
)
JBT_DEVICE=$(printf '%s\n' "$JBT_IDENTITY" | sha256sum) || fail "Cannot identify device."
JBT_DEVICE=${JBT_DEVICE%% *}
valid_hash "$JBT_DEVICE" || fail "Invalid device identity hash."
JBT_BACKUP=$JBT_BACKUP/$JBT_DEVICE
JBT_RECEIPT=$JBT_BACKUP/receipt.txt
if pidof system_server audioserver adbd >/dev/null 2>&1; then fail "Android is running; use the offline updater."; fi
[ ! -L "$JBT_OEM" ] && [ ! -L "$JBT_OEM/app" ] || fail "Unexpected OEM symlink."
[ ! -e "$JBT_STAGE" ] && [ ! -L "$JBT_STAGE" ] || fail "Staging residue: inspect before continuing."
[ ! -L "$JBT_USB/joying-bluetooth-backup-generic-r1" ] && [ ! -L "$JBT_BACKUP" ] || fail "Unsafe backup directory."
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
# Numeric ownership comes from the supported ls, permissions from its mode field.
JBT_METADATA=$(ls -ldn "$JBT_TARGET") || fail "Cannot read config metadata."
IFS=' ' read -r JBT_ATTRIBUTES JBT_LINKS JBT_UID JBT_GID JBT_REST <<JBT_METADATA_END
$JBT_METADATA
JBT_METADATA_END
case "$JBT_ATTRIBUTES" in -?????????) ;; *) fail "Unexpected config mode.";; esac
case "$JBT_UID:$JBT_GID" in *[!0-9:]*) fail "Unexpected config ownership.";; esac
[ -n "$JBT_UID" ] && [ -n "$JBT_GID" ] || fail "Missing config ownership."
JBT_CHARS=${JBT_ATTRIBUTES#?}
JBT_BITS=0
JBT_BIT=256
JBT_POS=1
while [ -n "$JBT_CHARS" ]; do
    JBT_CHAR=${JBT_CHARS%"${JBT_CHARS#?}"}
    JBT_CHARS=${JBT_CHARS#?}
    case "$JBT_POS:$JBT_CHAR" in
        1:r|4:r|7:r|2:w|5:w|8:w|3:x|6:x|9:x) JBT_BITS=$((JBT_BITS + JBT_BIT));;
        3:s) JBT_BITS=$((JBT_BITS + 2048 + JBT_BIT));;
        3:S) JBT_BITS=$((JBT_BITS + 2048));;
        6:s) JBT_BITS=$((JBT_BITS + 1024 + JBT_BIT));;
        6:S) JBT_BITS=$((JBT_BITS + 1024));;
        9:t) JBT_BITS=$((JBT_BITS + 512 + JBT_BIT));;
        9:T) JBT_BITS=$((JBT_BITS + 512));;
        *:-) ;;
        *) fail "Unsupported config permission field.";;
    esac
    JBT_BIT=$((JBT_BIT / 2))
    JBT_POS=$((JBT_POS + 1))
done
JBT_PERM=$(printf '%o' "$JBT_BITS")
writable
JBT_STAGE_OWNED=1
# Keep unrelated bytes, CRLF and missing final newlines intact.
{
    while :; do
        JBT_LINE=
        IFS= read -r JBT_LINE
        JBT_READ=$?
        [ "$JBT_READ" = 0 ] || [ -n "$JBT_LINE" ] || break
        if native_key "$JBT_LINE"; then
            printf 'sys.fyt.systemobd=1'
            case "$JBT_LINE" in *"$JBT_CR") printf '\r';; esac
        else
            printf '%s' "$JBT_LINE"
        fi
        [ "$JBT_READ" = 0 ] || break
        printf '\n'
    done
    if [ "$JBT_FOUND" = 0 ]; then printf '\nsys.fyt.systemobd=1\n'; fi
} <"$JBT_TARGET" >"$JBT_STAGE" || fail "Cannot stage config."
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
echo "Native override configured for reboot; verify the property and Bluetooth after boot."
echo "Receiving/transmitting profile selection is unchanged."
exit 0
