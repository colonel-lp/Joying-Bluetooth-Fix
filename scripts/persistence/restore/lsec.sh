#!/system/bin/sh
JBT_USB=/storage/sdcard1
JBT_MODE=restore
JBT_LOG=$JBT_USB/joying-bluetooth-$JBT_MODE.log
[ ! -L "$JBT_LOG" ] || exit 1
exec >>"$JBT_LOG" 2>&1 || exit 1
echo "USB launcher: $JBT_MODE"
if [ ! -f "$JBT_USB/joying-bluetooth/run.sh" ]; then
    echo "ERROR: missing /joying-bluetooth/run.sh on USB. RESULT: FAILED; exit 1"
    exit 1
fi
/system/bin/sh "$JBT_USB/joying-bluetooth/run.sh" "$JBT_MODE"
JBT_EXIT=$?
echo "USB launcher finished: $JBT_MODE; exit $JBT_EXIT"
exit "$JBT_EXIT"
