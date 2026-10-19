# Validation

## Bluetooth Controls 1.8 / code 9 — 19 October 2026

- Production Java compiles against SDK 35 with Java 8 target/generated R. Host checks pass: 14 source-routing, 13 adapter-state, 10 overlay-presence, 11 audio-session, 9 source/focus integration, 11 focus-API, 15 launcher/native-playback, 25 foreground-service, 11 settings-window and 10 receiver-name scenarios. No APK assembly or device run.
- New checks cover owned-task launch/close/result/recreation, stale callbacks, duplicate taps, failed launch/close, separate version metadata, toggle highlighting, receiver-profile selection, rename, ambiguous/missing names, disconnect/rebind and cleanup. Manifest checks retain unchanged permissions and verify the private separate settings task.
- Device acceptance: open/close native settings repeatedly from the bar; verify return to the prior screen, nested device settings, Back, rotation/recreation and button highlighting. Confirm unrelated Settings tasks are preserved. Verify the actual sender name, rename/disconnect/reconnect/radio cycling and a connected keyboard/mouse. Check v1.8 at the title bar right, dragging from either title area and long titles. Retain notification/audio acceptance below.

## Bluetooth Controls 1.7 / code 8 — 19 October 2026

- Production Java compiles against SDK 35 with Java 8 target/generated R. Host checks pass: 14 source-routing, 13 adapter-state, 10 overlay-presence, 11 audio-session, 9 source/focus integration, 11 focus-API, 15 launcher/native-playback and 22 foreground-service scenarios. No APK assembly or device run.
- Service checks exercise the production service with platform/transport stand-ins: silent ongoing status, hide/reopen, no audio changes on hide, pending transitions, companion CLOSE presence, hidden focus loss, confirmed exit, failed/late shutdown, permission-independent cold shutdown and notification update reuse.
- Device acceptance: hide with BT ON/Audio OFF and with both ON; confirm playback/focus continue and notification states update. Restore via notification, launcher and EQ & DSP. Confirm one window and correct companion theming. Turn both off from the notification, including during pending source/adapter changes; confirm no earlier player resumes and notification disappears only after shutdown. Check BT changes from Android settings while hidden and retry after a rejected/unconfirmed shutdown.

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
python3 app/tests/check_service.py
python3 app/tests/check_settings.py
python3 app/tests/check_receiver.py
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
- Check Audio on/off and original-source restore. Hiding must retain active audio; explicit shutdown must respect a source subsequently chosen by another app.
- Check play/pause, previous, next and Stop against the sender's player.
- Cycle Bluetooth using the bar. Confirm no normal enable prompt, full ON state and source/playback recovery after shutdown.
- Hide during a pending request and reopen; confirm the request continues once. Full shutdown must cancel old retries.
- Verify overlay permission denial, dragging, saved position and reopening the existing bar.

For a diagnostic capture on the Mac Desktop:

```sh
./adb logcat -d -v threadtime > ~/Desktop/joying-bt-floating.txt
./adb shell dumpsys bluetooth_manager >> ~/Desktop/joying-bt-floating.txt
```

App messages use the `JoyingBluetooth` tag. A permission rejection does not establish that a playback or enable operation reached Bluetooth.
