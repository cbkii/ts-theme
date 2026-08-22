# Architecture decisions

## ADR-001: vendor binaries stay out of source

Record hashes and use ignored local compatibility inputs. Do not redistribute vendor APKs, extracted
assets or decrypted implementation code.

## ADR-002: clean build, never vendor repackaging

Build the unique-package plug-in from project-authored sources. Reproduce only the cross-sample
contract and the checksum-pinned open-source RePlugin loader; never use a vendor APK as a build
template. Physical discovery remains a separate acceptance gate.

## ADR-003: the map is never an underlay

The information strip and map use disjoint rectangles. No card, control or transparent touch surface
may cover the navigation window.

## ADR-004: no stock package impersonation

Do not replace, alias or disable `com.tw.music`. Package naming cannot confer its signer/UID
authority and would create unsafe rollback and ownership conflicts.

## ADR-005: broad compatibility, single authority

The media adapter may observe Android, DoFun, Topway and proven stock-relay paths. For each user
action it selects one target/route and owns no playback service, queue, MediaSession or notification.

## ADR-006: ticker replaces artwork

Album art and visualiser surfaces are hidden. The media information region displays
`Artist - Track Name`, scrolling only when required, and opens the selected/default app when tapped.

## ADR-007: radio remains separate

Radio previous/next stays with the DoFun/Topway radio source. Stock radio, NavRadio+, MCU routing and
Android media are different authorities; saved-preset semantics require physical proof.

## ADR-008: version and release identity have one authority

`gradle.properties` owns `VERSION_CODE` and `VERSION_NAME`; `release-config.json` declares their
property names plus the package, task and expected APK. The manual release workflow accepts only a
strict `vX.Y.Z` tag matching `VERSION_NAME`, builds once, and never moves an existing tag.

## ADR-009: publication is transactional

Qualification is read-only. Publication creates or repairs a draft, uploads only the closed bundle,
re-downloads and hashes every remote asset, and changes release state last. A failed run may leave an
immutable tag or draft as recoverable evidence; repair mode must reconcile that exact state.
