# Future app objectives

Recorded 19 October 2026. These are future goals, not authorization to implement them now.

- Keep the app small and provide easy access to native Android Bluetooth settings.
- Select Joying's Bluetooth audio routing/mixing as the original Bluetooth Music app does, and explicitly leave that route too.
- Provide playback controls: play, stop/pause, skip forward/back and other commands that the actual Joying/native interfaces support.
- Investigate the stock Android Bluetooth/settings problems. A system app/framework patch is an option to assess if evidence requires it; none is agreed or implemented.

Current work is limited to the v1.1 launch-time enable confirmation and settings shortcut. It does not change source/mixing, connect a sender, control media, patch the system Bluetooth package, change the DSP service or solve headphone volume.

Before implementing routing or media controls, trace the original app's commands, distinguish Joying's external Bluetooth module from native A2DP reception, and verify both activation and release on this unit. Do not treat pairing or a stale connected label as proof of audio flow.
