# Validation

## Bluetooth Controls 1.6 / code 7 — review, 19 October 2026

- Review found that automatic ducking could bypass the listener while native audio belongs to the Bluetooth process. The focus request now opts into pause callbacks for ducking interruptions.
- Production Java compiles against SDK 35; all 83 host scenarios pass, including 11 focus-API and 9 source/focus integration scenarios. No APK assembly or device run.
- Focus API checks include the pause-on-duck flag and ducking-loss callback. Integration checks cover focus loss and close during pending source acquisition, denied focus, failed dispatch, route timeout, external source changes and explicit OFF on an existing route. Device acceptance remains pending.

## Bluetooth Controls 1.6 / code 7 — 19 October 2026

- Production Java compiles against SDK 35 with Java 8 target/generated R. Host checks pass: 14 source-routing, 13 adapter-state, 10 overlay-presence, 11 audio-session, 9 focus-API and 15 launcher/native-playback scenarios. No APK assembly or device run.
- Focus checks cover permanent media focus, denial, request identity, stale callbacks, legacy fallback and no automatic resume. Session checks cover release ordering, failed acquisition, source changes and close. Native playback checks cover Pause, Stop fallback and an unavailable session.
- Device acceptance: start another Android player, select Audio ON and confirm it pauses. Select Audio OFF and confirm the Bluetooth sender pauses while the earlier player stays paused. Repeat with source changes, adapter shutdown, close and another app requesting focus. Confirm source restoration and the companion overlay link.

## Bluetooth Controls 1.5 / code 6 — 19 October 2026

- Production Java compiles against SDK 35 with Java 8 target/generated R; 14 source-routing, 13 adapter-state and 10 presence/callback checks pass. Controller checks cover 14 link scenarios with Binder stand-ins. No APK assembly or device run.
- New CLOSE uses the attached bar's existing close/source-release callback, without creating a service/window. Absence immediately reports OFF; an active bar reports OFF only after removal. The old callback is discarded on removal. Radio/source/media control logic is unchanged.
- Unit checks: EQ & DSP 1.77 open/close toggle, active/inactive audio release, previous source ownership, repeated taps and confirmed OFF indication. Retain earlier adapter/Stop and overlay acceptance below.

## Bluetooth Controls 1.4 / code 5 — 19 October 2026

- Production Java compiles against SDK 35 with Java 8 target/generated R. Existing 14 source-routing, 13 adapter-state and 12 launcher/native-playback scenarios pass; 7 new overlay-presence checks pass. The controller's 11 link scenarios use host Binder stand-ins. No Gradle/Java 25 execution, APK or actual window/IPC/radio test.
- The exported link accepts only the EQ & DSP package UID, subscribes without opening the bar, starts/reuses its foreground overlay service without Activity navigation, and reports actual attachment/removal. The overlay service remains private. See [protocol](eq-dsp-link.md).
- Unit acceptance: direct launch from EQ & DSP, existing-open query, repeated tap, ×/notification close, service/process stop, client rotation/rebind and stale replies. Confirm underlying screen stays visible; radio/audio controls and previous-source ownership/restoration remain unchanged. Check missing overlay grant recovery. Retain previous adapter/Stop/device acceptance below.

## Source checks

From the repository root, with Java 17 or newer and `jdk.compiler`:

```sh
python3 app/tests/run_checks.py --android-jar /path/to/platforms/android-35/android.jar
python3 app/tests/check_audio_focus.py
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
