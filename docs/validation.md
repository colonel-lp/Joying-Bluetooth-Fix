# Validation

## Bluetooth Controls 1.4 / code 5 — 19 October 2026

- Production Java compiles against SDK 35 with Java 8 target/generated R. Existing 14 source-routing, 13 adapter-state and 12 launcher/native-playback scenarios pass; 7 new overlay-presence checks pass. The controller's 11 link scenarios use host Binder stand-ins. No Gradle/Java 25 execution, APK or actual window/IPC/radio test.
- The exported link accepts only the EQ & DSP package UID, subscribes without opening the bar, starts/reuses its foreground overlay service without Activity navigation, and reports actual attachment/removal. The overlay service remains private. See [protocol](eq-dsp-link.md).
- Unit acceptance: direct launch from EQ & DSP, existing-open query, repeated tap, ×/notification close, service/process stop, client rotation/rebind and stale replies. Confirm underlying screen stays visible; radio/audio controls and previous-source ownership/restoration remain unchanged. Check missing overlay grant recovery. Retain previous adapter/Stop/device acceptance below.

## Source checks

From the repository root, with Java 17 or newer and `jdk.compiler`:

```sh
python3 app/tests/run_checks.py --android-jar /path/to/platforms/android-35/android.jar
python3 app/tests/check_flows.py
python3 scripts/persistence/test_installer.py
```

The SDK argument compiles production Java against Android SDK classes with a generated R stand-in. Host stand-ins exercise source routing, adapter requests, launcher permission flow and native media-session callbacks. These checks do not reproduce Android services, radio hardware or audio output.

## Recorded results

- v1.2: production Java compiled against SDK 35; 14 source-routing and 12 launcher/media scenarios passed.
- Java 25 configuration: official Gradle compatibility and distribution checksum checked. Gradle 9.3.1 is pinned with AGP 8.13.2. Java 25 execution was not available in the preparation environment.
- Device report for v1.2: floating controls work except the disabled Stop button; the first enable after shutdown can leave settings off, while another attempt recovers it.
- Persistence: nine host checks passed previously; device USB/reboot acceptance remains unreported.
- v1.3: production Java compiled against SDK 35; 14 source-routing, 13 adapter-state and 12 launcher/media scenarios passed. Direct adapter control, transition handling, retry and Stop need device confirmation.

No APK is assembled as part of source-only checks.

## Device checks

- Confirm the native-mode properties after boot, then pair/connect a sender.
- Check source on/off, original-source restore and closing after another app changes the source.
- Check play/pause, previous, next and Stop against the sender's player.
- Cycle Bluetooth using the bar. Confirm no normal enable prompt, full ON state and source/playback recovery after shutdown.
- Close during a pending request and reopen; check that old retries do not run.
- Verify overlay permission denial, dragging, saved position and reopening the existing bar.

For a diagnostic capture on the Mac Desktop:

```sh
./adb logcat -d -v threadtime > ~/Desktop/joying-bt-floating.txt
./adb shell dumpsys bluetooth_manager >> ~/Desktop/joying-bt-floating.txt
```

App messages use the `JoyingBluetooth` tag. A permission rejection does not establish that a playback or enable operation reached Bluetooth.
