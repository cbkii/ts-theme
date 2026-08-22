# Development roadmap

## Phase 0 — repository and clean-build baseline (complete)

- Audit FYD, GB2, five additional theme samples and the supplied installed DoFun APK.
- Separate declarative theme work from executable media integration.
- Add deterministic validation, a clean RePlugin-compatible Gradle APK and an exact visual prototype.
- Exclude vendor binaries, assets, credentials and signing keys.

## Phase 1 — user-signed v1.0.0 discovery

Acceptance:

- `launcher.variety.theme.plugin.cbk_black` installs alongside FYD;
- it appears once in DoFun's theme catalogue and can be applied;
- restart and reboot retain it;
- uninstall restores the previous selectable set.

If missing, distinguish signer, OEM ID, metadata, theme ID and parser rejection. Do not bypass theme
licensing or patch the protected host merely to make the probe appear.

## Phase 2 — geometry and interaction

Acceptance:

- the strip is exactly one continuous row;
- the map is fully visible below it and receives touch across its entire rectangle;
- `DD MMM`, frequency/FM and the music field fit at 1280 x 720;
- no album art, visualiser, cool accent or icon tile remains;
- radio/music information regions open their selected/default apps;
- button taps do not also launch an app.

## Phase 3 — native DoFun media investigation

The exact installed APK is now statically recorded. The next work is runtime:

1. query `content://com.dofun.variety.ExportedProvider/hotseat_app_music`;
2. inspect/bind `cn.cardoor.libs.media.RemoteMediaService` without guessing transactions;
3. verify `NotifyService` is connected;
4. compare DoFun state with `dumpsys media_session`;
5. test Auxio-TS and one unrelated third-party player;
6. record metadata, ticker source, launch target and each control result separately.

The Jiagu-protected implementation may also be captured after normal host startup for authorised
local analysis, but protection must not be bypassed by redistributing decrypted vendor code.

## Phase 4 — broad adapter decision

### Route A: native host support

Use it when physical evidence proves generic state, controls, ticker and launch behaviour.

### Route B: broad in-process compatibility adapter

Use when the host exposes the views/services but does not combine all required sources. Implement:

- native DoFun source and selected-target observation;
- Android active-session control;
- public Android and observed Topway state/command paths;
- verified CommandService callbacks;
- ticker formatting/marquee and information-region launch;
- exact-version view hooks only where required.

Correlate duplicate evidence and dispatch each press exactly once.

### Route C: external companion

Fallback when protected-process integration is not supportable. It has greater lifecycle and overlay
risk, and still must use the same single-target/exactly-once policy.

## Phase 5 — release qualification

- Fresh install, update and uninstall.
- Launcher restart, reboot, cold power cycle and ACC sleep/wake.
- Auxio-TS, unrelated Android media, Bluetooth, stock radio and NavRadio+ tested separately.
- Target switching and app-launch behaviour verified.
- Thirty-minute map plus playback stability run.
- Rollback tested before release.
- No vendor APK, decrypted code, private key, device log or proprietary asset committed.
