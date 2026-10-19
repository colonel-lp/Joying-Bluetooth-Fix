# Bluetooth findings and reproduction

Observed 18 October 2026 on a Joying SC9853i: Android 8.1 / API 27, `sp9853i_1h10`, firmware 2020-04-07. Other firmware is unverified. No Bluetooth APK/library or sound-service patch was needed for the receiving test.

## 1. Establish authorized ADB

Enable Wi-Fi ADB using the unit's tools, confirm its IP and connect from platform-tools:

```sh
./adb kill-server
./adb start-server
./adb connect HEAD_UNIT_IP:5555
./adb devices -l
```

The device must report `device`, not `unauthorized`. Port **5037** is the Mac's local ADB server; **5555** is the Joying's Wi-Fi ADB endpoint. Closing Android Studio stopped it conflicting with a foreground debugging server during this investigation.

If routing is uncertain on macOS:

```sh
ping -c 4 HEAD_UNIT_IP
nc -vz -G 3 HEAD_UNIT_IP 5555
```

A successful TCP connection followed by `unauthorized` is an authentication problem. This unit did not display an RSA approval prompt. Authorization was resolved separately by a guarded offline USB procedure adding the Mac's **public** `~/.android/adbkey.pub` to `/data/misc/adb/adb_keys`, preserving existing keys and applying owner/group `1000.2000`, mode `0640` and SELinux labelling. That personalized installer/key is not included here. Do not expose the private `adbkey`, replace the existing trusted-key list or mistake authorization for root: the resulting shell remained UID 2000.

## 2. Enable native receiving

Record the existing properties before changing them:

```sh
./adb shell getprop sys.fyt.bluetooth_type
./adb shell getprop sys.fyt.systemobd
./adb shell getprop persist.lsec.enable_a2dp
./adb shell getprop bluetooth.mock_stack
```

On the tested unit, `bluetooth_type=1` was set in `/oem/app/config.txt`; its factory provenance was uncertain because an earlier Bluetooth script may have been used. Leave it unchanged. Set the native override and receiving role:

```sh
./adb shell 'setprop sys.fyt.systemobd 1 && setprop persist.lsec.enable_a2dp 0 && am force-stop com.android.bluetooth && am start -a android.settings.BLUETOOTH_SETTINGS'
```

Enable Bluetooth in **Android's** settings. The adapter reached ON with a real address, initialized its Marlin2 radio, discovered devices and paired with the Mac. The default native name was `lsec-android`; a renamed device remains the same receiver. Historical “unexpected crash” entries at the force-stop times do not by themselves establish spontaneous stack crashes.

`sys.fyt.systemobd` is volatile; `persist.lsec.enable_a2dp` is persistent. The override is cached by the Bluetooth process, which is why its restart is necessary. For boot configuration and exact restore, use [the separate USB instructions](../scripts/persistence/readme.md).

## 3. Make received music audible

1. Select the Joying as the Mac/phone's media output and play audio.
2. Open **Joying's original Bluetooth Music screen** to select the audio source.
3. If connected but silent, pause playback, select the Mac speakers, reselect the Joying output, and resume playback with Bluetooth Music open.

Both source selection and this output re-selection were confirmed to restore audible playback. Pairing alone does not establish an active media stream.

The receiver uses Android **STREAM_ALARM (4)**. While the ordinary Android source was selected, Joying's service repeatedly set that stream to 0. Bluetooth Music selected the proper source and set it to 7/7. A direct ADB volume change could be overwritten, so it was not a lasting fix:

```sh
./adb shell media volume --stream 4 --get
```

The vendor receiver builds an AudioFocusRequest but does not call AudioManager.requestAudioFocus. This explains the observed lack of ordinary audio-focus takeover when BBC Sounds was already playing; it does not, alone, prove the cause of every silent connection.

## 4. What the vendor code showed

Inspection of the supplied Bluetooth VDEX, restored with vdexExtractor and validated against its original DEX checksum, established:

- Native discovery, RFCOMM data-connection handling and A2DP receive/transmit code are still present. The native/JNI libraries retain radio initialization and substantial profile implementations; code presence alone does not establish hardware or device compatibility.
- `AdapterService.useExternalBt()` selects the vendor/external path when `sys.fyt.bluetooth_type != 0` **and** `sys.fyt.systemobd` is false. Setting `systemobd=1` selects the retained native enable/profile path without changing module type.
- In the external path, `startCoreServices()` bypasses native profile startup. `AdapterState.PendingCommandState` handles the BLE-started event without calling `enableNative()` and posts message **500 after 3 ms**, while the expected enable-ready event is **3**. The wrong completion message leaves the original sequence stuck at `BLE_TURNING_ON`, explaining the unresponsive native switch in this configuration.
- For module type 1, the external bonded-device list is reconstructed from **`/data/blink/bt_conf.ini`**. This explains a phone appearing as paired even while Android's native radio has not started; a populated list does not prove native connectivity.
- JNI selection with **`bluetooth.mock_stack=1`** loads **`bluetooth_test.default.so`**. Despite its name, this library contains substantial discovery, RFCOMM, audio-profile and HCI initialization code. Native discovery and audio succeeded without changing the property; selecting a library named “test” did not mean the stack was an empty mock.

The existing **`sys.fyt.systemobd` override** bypasses FYT's external branch and enables native startup without changing `sys.fyt.bluetooth_type`. Its value is cached by the Bluetooth process, so a process restart is needed after changing it. It is volatile across reboot; the persistence procedure retains it in the boot configuration.

These findings apply to this firmware. They explain the original external-path `BLE_TURNING_ON` failure. The later `BLE_ON` off/on fault, seen after the override was already active, is a separate issue described in section 7. RFCOMM code availability is established; end-to-end RFCOMM data transfer has not been reported.

## 5. Diagnose a silent connection

With the sender playing and Bluetooth Music open, capture to the Mac Desktop:

```sh
{
./adb shell getprop sys.fyt.systemobd
./adb shell getprop persist.lsec.enable_a2dp
./adb shell dumpsys bluetooth_manager
./adb shell dumpsys media.audio_flinger
./adb shell dumpsys audio
} > ~/Desktop/joying-bt-receive.txt
```

Expected role values are `1`, then `0`. Look for `A2dpSinkService` connected to the sender, an active stream-4 AudioFlinger track with frames, and a nonzero stream-4 mixer level. An inactive empty track with stream 4 already unmuted is a different failure from volume zero; output re-selection recovered the observed case. Transmit codec `None` and zero transmit counters are normal evidence for an unused transmit path, not proof the receiver has no audio.

Optional recent startup log:

```sh
./adb logcat -d -v threadtime | grep -iE 'a2dp|avrcp|audiofocus|audiotrack|btif_av' > ~/Desktop/joying-bt-start.txt
```

## 6. Headphone transmission: verified, deferred

The existing vendor role selector is:

| `persist.lsec.enable_a2dp` | Profiles selected |
|---|---|
| `0` or unset | Receiving mode; A2DP source profile 2 disabled. |
| `1` | A2DP transmission; sink profile 11 and headset/HFP roles 1/16 disabled. |

For the tested headphone mode, the Mac's Bluetooth connection was disconnected while Wi-Fi/ADB stayed connected:

```sh
./adb shell 'setprop persist.lsec.enable_a2dp 1 && am force-stop com.android.bluetooth && am start -a android.settings.BLUETOOTH_SETTINGS'
```

Headphones connected with audio and working play/pause control. SBC/AAC/LDAC changes were reflected in actual codec activity; one capture used stereo LDAC at 96 kHz/24-bit with adaptive bitrate. The normal Joying buttons did not control headphone level: `com.syu.ms` reset Android music volume to **13/15**, and AVRCP reported **111/127**. `C32107.d()` returns the Android maximum minus 2, explaining the fixed 13. Ordinary Joying volume commands still reached that rule; an existing dedicated headphone-volume command was not established. No volume patch was implemented.

Return to receiving with the command in section 2. Codec/volume quality and simultaneous receive/transmit operation remain unproven; headphone work is currently lower priority.

## 7. Stock toggle fault and confirmed enable recovery

Observed 18–19 October 2026: toggling Android Bluetooth off/on could leave `enabled: false`, `state: BLE_ON`, with only GATT active and no music receiver profile. One shutdown took about 6.2 seconds; switching on after about 1.8 seconds overlapped it. Waiting ten seconds did not resolve the later test, so a short toggle interval alone is not an established explanation.

The shell command `svc bluetooth enable` was denied `BLUETOOTH_ADMIN` permission on this firmware. Its unchanged state was not evidence that a permitted enable request failed.

While stuck, the following public Android enable action showed a prompt and recovered the adapter without restarting the Bluetooth process:

```sh
./adb shell am start -a android.bluetooth.adapter.action.REQUEST_ENABLE
```

Accept the prompt on the Joying, then inspect:

```sh
./adb shell dumpsys bluetooth_manager | head -n 4
```

The owner confirmed `enabled: true`, `state: ON`. This establishes a recovery method; the exact cause of the stock toggle/continuation fault remains unresolved. Earlier code/logs also showed a Settings enable request promoting BLE_ON into full ON and starting the native audio profiles.

The [floating controls](../app/readme.md) now use direct adapter enablement with the declared Android 8.1 permission, state confirmation and one bounded retry. The enable confirmation remains a fallback if direct access is denied. Settings is a separate button; launching the bar does not force Bluetooth on. Current app status and validation are recorded separately in [validation.md](validation.md).
