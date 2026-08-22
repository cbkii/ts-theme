# Architecture decisions

## ADR-001: vendor binaries stay out of source

Record hashes and use ignored local compatibility inputs. Do not redistribute vendor APKs, extracted
assets or decrypted implementation code.

## ADR-002: prove template compatibility before a clean build

First use a unique-package FYD-based probe to isolate discovery, schema and geometry from old
RePlugin build-tool uncertainty.

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
