# Project conventions

- Keep READMEs concise and generic. The project README covers objectives/goals, repository folders, Joying Bluetooth structure, native-mode ADB commands, reboot persistence, capabilities/limitations and app status.
- Keep test results and validation procedures in `docs/validation.md`, not READMEs. Keep code comments short and limited to behavior or compatibility that needs explanation.
- Deliver Android source for local building. Do not upload APKs, signing material, personal logs or vendor decompilations. Do not create delivery ZIPs or expose binary/base64 contents in chat.
- Preserve the Java 25 build setup and Android 8.1 compatibility. Change SDK targets only when required by the task.
- Keep native Android playback separate from Joying's vendor Bluetooth commands. Confirm source changes through readback and preserve source ownership on restore/close.
- Distinguish observed device behavior, source checks and unverified changes. Preserve unrelated files when publishing.
- App versions advance by .01 with two digits after the dot: 1.10, 1.11, 1.12. Increment versionCode for each release. Android Studio APK outputs must use `Bluetooth-Controls-v${variant.versionName}.apk` for every build type.
- Keep the notification simple: Show controls and Turn both off & exit. Persist only with the bar hidden and BT ON; reopening removes the notification. Closing with BT OFF releases audio and exits.
