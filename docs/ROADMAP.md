# Development roadmap

## Phase 0 — repository and clean-build baseline (complete)

- Audit FYD, GB2, five additional theme samples and the supplied installed DoFun APK.
- Separate declarative theme work from executable media integration.
- Add deterministic validation, a clean RePlugin-compatible Gradle APK and an exact visual prototype.
- Exclude vendor binaries, assets, credentials and signing keys.

## Phase 1 — compatibility-hardening before physical install

Implement before the next release:

- unique `sfp_cbk_black` / `launcher.variety.theme.plugin.sfp_cbk_black` window/PIP identity without impersonating a vendor package;
- audited SDK/RePlugin/minimal-manifest compatibility envelope;
- exact-device safe-area geometry with all theme content at or left of x=1225;
- central layout profile and regression tests;
- Termux preflight/direct-install/U-disk/donor/rollback toolkit;
- deterministic installation-tools release asset participating in the same remote-verification transaction as the APK.

Acceptance is static/CI only until the physical unit is used. Independent-signer discovery is not declared successful by this phase.

## Phase 2 — physical discovery and geometry

Attempt in order:

1. ordinary installed-package discovery;
2. DoFun/4PDA U-disk import;
3. guarded rooted donor substitution only for the signature-rejection case and only after current private-storage preflight.

Acceptance:

- project theme appears exactly once through at least one safe supported path;
- it can be applied without modifying/re-signing DoFun;
- right SystemUI remains unobstructed and usable;
- the strip is exactly one continuous row;
- the 1144 x 583 release map is fully visible below it and receives touch across its entire rectangle;
- `DD MMM`, frequency/FM and the music field fit;
- restart/reboot persistence is recorded;
- donor rollback is proven before retaining a donor-based install.

## Phase 3 — native DoFun media investigation

The exact installed APK is statically recorded. Runtime work is separate from the theme APK:

1. query `content://com.dofun.variety.ExportedProvider/hotseat_app_music`;
2. inspect/bind `cn.cardoor.libs.media.RemoteMediaService` without guessing transactions;
3. verify `NotifyService` is connected;
4. compare DoFun state with `dumpsys media_session`;
5. test Auxio-TS and one unrelated third-party player;
6. record metadata, ticker source, launch target and each control result separately.

## Phase 4 — broad adapter decision

Use native host behaviour when physical evidence proves it. Otherwise develop the broad compatibility adapter as a separate guarded component; do not turn the theme APK into another playback authority.

## Phase 5 — release qualification

- Fresh install, update and uninstall/direct rollback.
- Launcher restart, reboot, cold power cycle and ACC sleep/wake.
- Auxio-TS, unrelated Android media, Bluetooth, stock radio and NavRadio+ tested separately.
- Target switching and app-launch behaviour verified.
- Thirty-minute map plus playback stability run.
- Internet reconnect/persistence tested where initial import was offline.
- No vendor APK, decrypted code, private key, device log or proprietary asset committed.
