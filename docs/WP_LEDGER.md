# TS18 launcher work-package ledger

This ledger is the source of truth for the integration candidate `integration/ts18-candidate`
(PR #44). Keep it in step with code. Status values:

| Status | Meaning |
|---|---|
| IMPLEMENTED | code and tests exist |
| PARTIAL | the exact remainder is stated |
| BLOCKED | a genuine external gate is stated |
| NOT_RUN | the physical check has not been run |
| DEFERRED | physical work held back |

**No TS18 physical qualification has been run for any item.**

| WP | Requirement | Status | Code / tests | Remaining gate |
|---|---|---|---|---|
| WP0 | Live reconciliation, stack ancestry | IMPLEMENTED | PR #31→#39→#40→#44 by ancestry; feature PRs #41–#43, #45–#50 merge into #44 with `--no-ff` | — |
| WP1 | Reference adoption | PARTIAL | `docs/REFERENCE_ADOPTION.md` (four pinned references) | open-car-project, device-reveng, openlauncher, dashline and cabin not studied |
| WP2 | Leaflet/theme retirement | IMPLEMENTED | #32, #37; `tools/ts18_theme.py validate` | historical tags and installed DoFun left untouched by design |
| WP3 | Startup measurement and orchestration | PARTIAL | `StartupTimeline` (#43); campaign comparison (#48) | refactor waits for real TS18 phase timings; no speed claim |
| WP4 | M1/M2 seam, M1 default and rollback | IMPLEMENTED | `MediaFeatureFacade`; `LegacyMonitorDemandTest` | — |
| WP5 | M2 warm control | IMPLEMENTED | `DirectSourceEngine`; matrix, handover and consent tests (#34, #36) | physical warm campaign NOT_RUN |
| WP6 | M2 cold start and passive readiness | IMPLEMENTED (software) | `BrowserSessionStarter`; `preparePassive`; `DirectPassivePrepareTest`; API 29 IPC proof (#41, #46) | the Auxio-TS boot/ACC queue restore on the TS18 is NOT_RUN; NavRadio+ has no browser service in the inspected scope |
| WP7 | OEM capabilities, read only | IMPLEMENTED | `oem/*`; observation gated (#42, #49) | callback-storage semantics unknown; illumination UNKNOWN; native tuner BLOCKED (needs authority, owner approval and a reversible test) |
| WP8 | N3 backend and presenter | IMPLEMENTED (software) | `navigation/n3/*`; `NavigationFeatureFacade`; API 29 private-display proof (#47) | third-party Maps needs embedding authority (host or cooperative contract); TS18 pixels, touch, Back and IME NOT_RUN |
| WP9 | Optional display host | IMPLEMENTED (inert skeleton) | `n3host/*`; `DisplayHostCoreTest`; `N3HostPolicyTest` | privileged deployment needs owner approval; no default enablement |
| WP10 | Driver UX: setup, app library, accessibility | PARTIAL | `SetupActivity` (#50); existing drawer and picker | component-aware shortcut fallback, deferred icon cache and favourites/hide/sort not reworked in this pass |
| WP11 | Diagnostics campaign | IMPLEMENTED | #40 kit; `TS18Startup` capture and compare (#48) | physical captures NOT_RUN |
| WP12 | Final integration and TESTING artefact | IMPLEMENTED | Validate and TESTING build on the #44 head | owner install and physical matrices below |

## Physical matrices (prepared, NOT_RUN)

| Area | Checks |
|---|---|
| Media | M1/M1, M2/M1, M1/M2, M2/M2 × warm commands (20 per action), cold Play (10 per source), handover (10), passive readiness after a real cold boot (5) and a real ACC wake (5), late USB, revoked or re-granted notification access, with audible confirmation |
| Navigation | N0 open; N1/N2 regressions; N3 on the private display (pixels, multi-touch, Back, IME on the main screen, surface reattach, Retry, Open normally, Use legacy); optional host only after an approved privileged install |
| Lifecycle | HOME, reboot, true cold power, true ACC, calls and projection interruptions |
| OEM | descriptor check; one acknowledged source observation, with a check that Auxio-TS and stock apps still receive Topway status afterwards |

## Rollback

- In Settings, set each media source back to Legacy and turn N3 off. Setting N3 off ends its
  session.
- Use Settings → Backup & reset.
- Choose another HOME in Android Settings, or disable the launcher, to return to the stock DoFun
  HOME.
- The optional host is never installed by any script here. Uninstalling it removes it entirely.
