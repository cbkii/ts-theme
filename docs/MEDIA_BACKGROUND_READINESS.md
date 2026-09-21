# Background media readiness adapters

This note records the evidence used for the standalone launcher background-readiness implementation.
Vendor APK bytes are not committed.

## Supplied NavRadio+ reference

File supplied for analysis: `NavRadio_Plus_v4_00_PREMIUM (1).apk`

- SHA-256: `b68e96efbf98da957973604dfc5aafb3dcddd00dea4dd3a70c0476826fe5a937`
- package: `com.navimods.radio`
- version: `4.00` / versionCode `1032`
- manifest reports minSdk 32 / targetSdk 35, so these exact bytes are reference evidence rather than proof of the API-29 package currently installed on the TS18.
- declared service: `com.navimods.radio.RadioService`
- service is enabled, exported, persistent and declares media-playback foreground-service type.
- service intent action: `androidx.media3.session.MediaSessionService`
- boot receiver declares BOOT_COMPLETED, LOCKED_BOOT_COMPLETED and quick-boot actions.

Implication: a runtime NavRadio adapter may background-start the exact exported service and then wait for the real Android MediaSession. It does not need to foreground `RadioActivity`. The launcher re-checks that the service exists/exported in the installed package before using this adapter.

## Supplied exact stock TW Radio

File supplied for analysis: `Radio_TW_THEME.20240827.apk`

- SHA-256: `93e93796c56b5e0758c4a143a1c428fcf1c0fa24e1d636f15151af2aeb489504`
- package: `com.tw.radio`
- version: `TW_THEME.20240827` / versionCode `75`
- minSdk/targetSdk: API 29
- the hash matches the current exact-unit stock-radio pin.
- manifest declares `com.tw.radio.RadioActivity` and `PTYActivity`.
- the manifest declares no Android service and no boot receiver.

The APK contains MediaBrowser/MediaSession support-library classes, but library payload is not an exported component contract. Therefore the launcher must not treat stock TW Radio as a MediaBrowser service, secretly launch its Activity, or infer private Topway radio commands from static strings. If a genuine `com.tw.radio` MediaSession is already active it may be observed; otherwise background readiness is reported unsupported until an exact vendor service/control route is separately qualified.

## Auxio-TS

Current Auxio-TS repository code exposes `com.tw.media/com.tw.music.MusicService` as an exported `android.media.browse.MediaBrowserService` wrapper around the single Auxio playback authority. Binding/starting that service can initialise session/library state without opening `MainActivity`.

## Adapter policy

| Source | Background activation | Control/readiness |
| --- | --- | --- |
| Auxio-TS `com.tw.media` | bounded Magisk-root `am startservice` first; exported MediaBrowser bind remains normal-user control/fallback | exact MediaController |
| NavRadio+ `com.navimods.radio` | bounded Magisk-root `am start-foreground-service` first; explicit normal-user `startForegroundService` fallback | wait for exact MediaSession |
| Stock TW Radio `com.tw.radio` | none from the Radio APK; never launch Activity for readiness | existing exact MediaSession only |
| Other apps | exported `android.media.browse.MediaBrowserService` when present | MediaBrowser token / exact MediaController |

All root commands are bounded and run off the UI thread. Failure falls back to the proven normal Android path where one exists. No readiness or transport path calls `startActivity()`; only explicit source/app icons may foreground an app.

## Qualification boundary

Static APK inspection establishes component contracts only. Runtime service start, session publication, cold boot and ACC sleep/wake remain physical TS18 qualification items until exercised on the installed package versions.
