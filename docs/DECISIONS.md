# Architecture decisions

These decisions describe the current standalone launcher. Older theme-era decisions are retained in Git history and the [historical research](RESEARCH_EVIDENCE.md); they do not reactivate retired products. The current user task defines permitted changes.

## ADR-001: vendor binaries stay out of source

Record hashes and inspected contracts, not vendor APKs, extracted implementation code, private captures, credentials or signing material. Donor behaviour does not establish the installed TS18 contract.

## ADR-002: one independently built launcher product

`launcher/` (`com.cbkii.ts18launcher`) is framework Java/Views on API29 with one DEX, no native/runtime dependencies and no launcher-owned player/session/focus. The former DoFun/RePlugin theme and Leaflet/WebView product are retired. `media-fixture/` is a separate CI-only application, excluded from release/runtime. Installed DoFun remains protected recovery HOME.

## ADR-003: measured geometry and one navigation owner

Use live display/decor/insets; preserve protected SystemUI. Navigation and persistent HOME controls occupy disjoint rectangles. N2 uses the external app's real task and current panel bounds, with one controller/broker authority. Reuse healthy tasks/routes; failed observations cannot authorise relaunch. A temporary admitted loader mask is distinct from permanent map controls and always cleans up.

## ADR-004: package names do not confer stock authority

Preserve genuine stock `com.tw.music` and other protected OEM packages. On this unit `com.tw.media` is the Auxio-TS variant; resolve actual package/component/user/signer. Root, aliases and MediaSessions do not confer platform signing, UID1000, tuner/MCU or provider authority.

## ADR-005: source-owned playback, exact target and one engine

M2 Direct is the default when unset; explicit Legacy is a per-source compatibility rollback. Only the selected engine can observe/command/publish for that source. Source-owned browser tokens do not need notification access; listener-only discovery does. The playback app owns queue, restoration, decoder and audio focus. Resolve direction once, dispatch to one pinned package/token and observe. An uncertain sent command never authorises replay. Pause the former source only after replacement playback is observed.

## ADR-006: truthful shared metadata and explicit app entry

The shared metadata strip is display-only and follows real source state. Identical snapshots do not restart the slow marquee. Source icons open the assigned apps. Preserve the established UI and meaningful metadata over animation. Directory-derived radio frequency remains labelled inference, not tuned-hardware evidence.

## ADR-007: independent readiness, controlled recovery

Admit Maps → Music → Radio without blocking independent safe progress. Prefer observed service/PREPARE readiness. Keep the first Play request through capability publication; unloaded Auxio uses its advertised source-enforced HOME_PLAY_V1 deadline. Standard third-party contracts remain source-specific. The operator-authorised NavRadio mask is implemented for explicit-demand, pre-dispatch recovery; passive tuner activation stays gated. Mask windows before launch, preserve genuine prerequisites, verify HOME/N2/source survival and clean up boundedly. No global media keys, play-then-pause warm-up or unmasked automatic staging.

## ADR-008: source and release identity have explicit authorities

Current workflow definitions, `release-config.json` and the selected immutable source determine build identity. Manual Release dispatch owns its version; local defaults live in `gradle.properties`. TESTING uses the fixed test-version lane and source/head/built SHA plus signer/hash metadata. Old PR descriptions or upload dates are not current provenance.

## ADR-009: transactional publication and separate acceptance

Qualify exact bytes, publish draft-first and verify remote assets before changing final release state. Never infer merge/publication/install authority from a document. Code, host tests, API29 emulator, CI, signed APK, installation, sound/pixels/touch and boot/ACC are separate evidence. Physical acceptance may remain NOT_RUN for a reviewable TESTING candidate; it remains necessary before claiming production device readiness.
