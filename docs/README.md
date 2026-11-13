# Documentation index and current authority

Reviewed on 13 November 2026 against integration source `801abd2b8f779faced243a6e6efabffb082574e7` (including readiness PR #53). This is an audit snapshot, not a permanent latest-head pin. At inspection, default `main` was `2cd04022231064deec627bd1593fc4cfde5d94ce`, and open [PR #44](https://github.com/cbkii/ts-theme/pull/44), branch `integration/ts18-candidate`, carried the current candidate. Refresh these refs before work; do not review `main` as though it contains the integration. Preserve ancestry and do not merge merely to refresh docs.

## Start here

1. Read [AGENTS](../AGENTS.md), [architecture](ARCHITECTURE.md) and only the current guide relevant to the task. Current user instructions and original/live code/evidence outrank old plans.
2. For release review use [priorities](ROADMAP.md), [the implementation/qualification ledger](WP_LEDGER.md), then relevant source, review feedback and final-head CI. Docs describe contracts; they do not prove those contracts work.
3. For device acceptance use [one consolidated campaign](CONSOLIDATED_TESTING_PLAYBOOK.md). Existing setup, physical reports and preserved signed rollback artefacts are inputs; do not restart obsolete PR-specific campaigns or require intermediate installs before independent engineering is finished.

## Current product facts

- `launcher/` is the only product: `com.cbkii.ts18launcher`, Android10/API29, Java/Views, one-DEX/no-native/no launcher runtime dependencies. `media-fixture/` is CI-only. Theme/RePlugin, Leaflet/WebView and privileged n3host product paths are retired; installed DoFun remains protected recovery HOME.
- M2 is the per-source TESTING default when unset; explicit Legacy remains rollback. Source-owned browser tokens do not require notification access; listener-only discovery does. Playback/queue/focus belong to the source app.
- Prepare Maps → Music → Radio with independent progress, bounded recovery and explicit opt-outs. One-tap requests survive initialisation; process/bind/title is not usability. NavRadio passive tuner activation remains unqualified; explicit pre-dispatch Play may use the implemented drawn-mask fallback.
- N2 real-task presentation is primary; fullscreen remains the fallback. N3 is separate/default-off and input/embedding-limited, not an alternative production requirement.
- Appearance has two effective modes, Day/Night, with scheduled Auto. Old High contrast/Dim/Sensor values migrate; no launcher map or lux-sensor integration remains.
- Current HOME and measured display/decor/insets are live authorities. Historical 55 px/x=1225/y=702 geometry is a conservative profile, not a licence to overwrite actual bounds or HOME.
- Latest integrated TS18 pixels, touch, audible playback, cold power and genuine ACC acceptance remain NOT_RUN here. Preserve historical physical evidence under its revision. Source/tests/CI/APK/install/runtime are separate.

## Current guides and source-contract references

| File | Use |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Runtime owners and application/repository boundaries |
| [CONTINUOUS_READINESS.md](CONTINUOUS_READINESS.md) | Maps-first admission, independent media recovery and masked Radio transaction |
| [MEDIA.md](MEDIA.md) | M2/Legacy, package/token authority, capability waits, deadline and handover |
| [MEDIA_BACKGROUND_READINESS.md](MEDIA_BACKGROUND_READINESS.md) | Per-source adapters, binary provenance and diagnostic limits |
| [MEDIA_CONTROLLER_SIMPLIFICATION.md](MEDIA_CONTROLLER_SIMPLIFICATION.md) | Ordinary transport and truthful metadata rules |
| [NAVIGATION.md](NAVIGATION.md) | N0/N2 and isolated N3; retired host boundary |
| [NATIVE_NAVIGATION_WINDOW.md](NATIVE_NAVIGATION_WINDOW.md) | Task/broker/shell ownership, current surfaces and recovery |
| [COMPACT_STARTUP_PROTOCOL.md](COMPACT_STARTUP_PROTOCOL.md) | Versioned launcher/Maps protocol; verify current companion/signers |
| [STANDALONE_LAUNCHER.md](STANDALONE_LAUNCHER.md) | Build envelope, TESTING workflow, UI, configuration and HOME rollout |
| [RELEASING.md](RELEASING.md) | Manual launcher release transaction; no implicit publish authority |
| [MONO_DRIVE_UX_ACCEPTANCE.md](MONO_DRIVE_UX_ACCEPTANCE.md) | Current source/UX targets and measurement boundaries |
| [ICON_SOURCES.md](ICON_SOURCES.md) | Pinned icon mappings and attribution |
| [OEM_CAPABILITIES.md](OEM_CAPABILITIES.md) | Operator-requested, hash-gated read-only OEM probes |
| [PRIVATE_CONTRACT_REVIEW_2026-11-12.md](PRIVATE_CONTRACT_REVIEW_2026-11-12.md) | Exact XTService static evidence, NavRadio reference limits and N3 |
| [RADIO_METADATA.md](RADIO_METADATA.md) | Implemented metadata/frequency policy; original PR IDs are provenance, directory values are inference |
| [STARTUP_TIMELINE.md](STARTUP_TIMELINE.md) | Current emitted phases/spans and their limits |
| [TOPWAY_DESKTOP_WINDOW_CONTRACT.md](TOPWAY_DESKTOP_WINDOW_CONTRACT.md) | Matched binary interface research, not automatic runtime authority |
| [REFERENCE_ADOPTION.md](REFERENCE_ADOPTION.md) | Pinned donor mechanisms and rejected assumptions; follow later superseding decisions |
| [ROADMAP.md](ROADMAP.md) | Prioritised product acceptance and remaining qualification |
| [DECISIONS.md](DECISIONS.md) | Current durable architecture decisions |
| [WP_LEDGER.md](WP_LEDGER.md) | Implementation versus remaining qualification; no live CI/provenance guarantee |
| [CONSOLIDATED_TESTING_PLAYBOOK.md](CONSOLIDATED_TESTING_PLAYBOOK.md) | Default final owner-operated campaign and read-only collector |
| [INTEGRATION_REMEDIATION_2026-11-13.md](INTEGRATION_REMEDIATION_2026-11-13.md) | Review remediation: refused-bind rule, cancel/bounded Play, tap budget, Maps own-screen hold, visibility, staging re-present; deferred evidence |

## Historical investigations and superseded procedures

These records preserve named findings, attempted approaches and old acceptance gates. Their “current”, NOT_IMPLEMENTED, PR/head, settings, SDK/build, counts and artefact statements apply to the recorded revision. Do not turn old proposals into requirements, resurrect retired paths, or infer outstanding defects without checking current code. The original comparison in the November implementation plan is preserved research, not an unchecked implementation queue.

| File | Status |
|---|---|
| [AUTOMATIC_CAR_START_READINESS_PLAN.md](AUTOMATIC_CAR_START_READINESS_PLAN.md) | Historical investigation/procedure; current guides above supersede conflicting instructions |
| [AUTOMATIC_STARTUP_2026-10-20.md](AUTOMATIC_STARTUP_2026-10-20.md) | Historical investigation/procedure; current guides above supersede conflicting instructions |
| [BOOTTRACE_V3_REPAIRS.md](BOOTTRACE_V3_REPAIRS.md) | Historical investigation/procedure; current guides above supersede conflicting instructions |
| [CAPTURE_FIXES_2026-10-22.md](CAPTURE_FIXES_2026-10-22.md) | Historical investigation/procedure; current guides above supersede conflicting instructions |
| [DAY_NIGHT_IMPLEMENTATION_2026-10-20.md](DAY_NIGHT_IMPLEMENTATION_2026-10-20.md) | Historical investigation/procedure; current guides above supersede conflicting instructions |
| [FEASIBILITY.md](FEASIBILITY.md) | Historical investigation/procedure; current guides above supersede conflicting instructions |
| [INCAR_FINALISATION_2026-10-19.md](INCAR_FINALISATION_2026-10-19.md) | Historical investigation/procedure; current guides above supersede conflicting instructions |
| [INTEGRATION_FINALISATION_2026-10-21.md](INTEGRATION_FINALISATION_2026-10-21.md) | Historical investigation/procedure; current guides above supersede conflicting instructions |
| [LAYOUT_SPEC.md](LAYOUT_SPEC.md) | Historical investigation/procedure; current guides above supersede conflicting instructions |
| [MEDIA_RELIABILITY_2026-10-23.md](MEDIA_RELIABILITY_2026-10-23.md) | Historical investigation/procedure; current guides above supersede conflicting instructions |
| [NAVIGATION_RELIABILITY_R1.md](NAVIGATION_RELIABILITY_R1.md) | Historical investigation/procedure; current guides above supersede conflicting instructions |
| [NAVIGATION_SURFACE_ROADMAP.md](NAVIGATION_SURFACE_ROADMAP.md) | Historical investigation/procedure; current guides above supersede conflicting instructions |
| [NAVIGATION_TASK_LIFECYCLE_QUALIFICATION.md](NAVIGATION_TASK_LIFECYCLE_QUALIFICATION.md) | Historical investigation/procedure; current guides above supersede conflicting instructions |
| [NAVIGATION_WINDOW_PHYSICAL_PLAYBOOK.md](NAVIGATION_WINDOW_PHYSICAL_PLAYBOOK.md) | Historical investigation/procedure; current guides above supersede conflicting instructions |
| [ORGANIC_MAPS_MODES.md](ORGANIC_MAPS_MODES.md) | Historical investigation/procedure; current guides above supersede conflicting instructions |
| [PHYSICAL_VALIDATION_REPAIRS_2026-10-16.md](PHYSICAL_VALIDATION_REPAIRS_2026-10-16.md) | Historical investigation/procedure; current guides above supersede conflicting instructions |
| [PR14_PHYSICAL_QUALIFICATION.md](PR14_PHYSICAL_QUALIFICATION.md) | Historical investigation/procedure; current guides above supersede conflicting instructions |
| [ROOT_READINESS_FALLBACKS.md](ROOT_READINESS_FALLBACKS.md) | Historical investigation/procedure; current guides above supersede conflicting instructions |
| [SECOND_PASS_IMPLEMENTATION_2026-10-19.md](SECOND_PASS_IMPLEMENTATION_2026-10-19.md) | Historical investigation/procedure; current guides above supersede conflicting instructions |
| [STARTUP_ACQUISITION_REPAIR_2026-10-23.md](STARTUP_ACQUISITION_REPAIR_2026-10-23.md) | Historical investigation/procedure; current guides above supersede conflicting instructions |
| [STARTUP_FINALISATION_2026-10-16.md](STARTUP_FINALISATION_2026-10-16.md) | Historical investigation/procedure; current guides above supersede conflicting instructions |
| [STARTUP_READINESS_LOADER.md](STARTUP_READINESS_LOADER.md) | Historical investigation/procedure; current guides above supersede conflicting instructions |
| [STARTUP_VALIDATION_REPAIRS_2026-10-16.md](STARTUP_VALIDATION_REPAIRS_2026-10-16.md) | Historical investigation/procedure; current guides above supersede conflicting instructions |
| [TESTING_REPAIR_PLAYBOOK.md](TESTING_REPAIR_PLAYBOOK.md) | Historical investigation/procedure; current guides above supersede conflicting instructions |
| [TS-THEME_IMPLEMENTATION_PLAN_2026-11-12.md](TS-THEME_IMPLEMENTATION_PLAN_2026-11-12.md) | Historical investigation/procedure; current guides above supersede conflicting instructions |
| [TS18_LSPOSED_ROADMAP_PLAYBOOK.md](TS18_LSPOSED_ROADMAP_PLAYBOOK.md) | Historical investigation/procedure; current guides above supersede conflicting instructions |
| [TS18_RUNTIME_DIAGNOSTIC.md](TS18_RUNTIME_DIAGNOSTIC.md) | Historical investigation/procedure; current guides above supersede conflicting instructions |
| [TS18_VALIDATION.md](TS18_VALIDATION.md) | Historical investigation/procedure; current guides above supersede conflicting instructions |
| [RESEARCH_EVIDENCE.md](RESEARCH_EVIDENCE.md) | Historical immutable binary/source observations; installed state requires a fresh match |

## Other Markdown in the repository

| File | Role |
|---|---|
| [README](../README.md) / [AGENTS](../AGENTS.md) | Current product entry and engineering rules |
| [CHANGELOG](../CHANGELOG.md) | Change history, including superseded behaviour; not a live specification |
| [NOTICE](../NOTICE.md) | Current attribution and retained historical licences |
| [Termux tools](../scripts/termux/README.md) | Optional installers/collectors; legacy theme section is not a launcher prerequisite |
| [Diagnostics](../scripts/diagnostics/README.md) / [qualification ledger](../scripts/diagnostics/QUALIFICATION.md) | Optional toolkit and historical run results |
| [Extended campaign](../scripts/diagnostics/campaign/START-HERE.md) | Targeted follow-up evidence; not the default final campaign |
| [Coldstart kit](../scripts/diagnostics/coldstart/START-HERE.md) / [scope](../scripts/diagnostics/coldstart/SCOPE-AND-EVIDENCE.md) | Optional persistent boot observer, explicitly separate from read-only capture |
| [Snow provenance](../third_party/snow/SOURCE.md) | Retained GPL SVG source; not packaged in current launcher APKs |

## Maintenance and verification

Update an active contract with its owning code change; mark superseded reports at the top instead of rewriting past evidence. Keep immutable hashes with their source and scope, and mutable branch/CI/APK facts in the current handoff. Confirm source/built SHA, package/version, signer and SHA-256 before an install. No documentation task silently authorises a merge, publication, protected write or device installation.

For documentation-only edits run `python3 tools/ts18_theme.py validate`, check Markdown links/anchors and code-path references, and run `git diff --check`. Do not rebuild unchanged APKs or claim old green checks cover a new head. Behavioural work follows the relevant repository test/CI workflow.


## Audit evidence (13 November 2026 snapshot)

Source validation, 68 existing documentation-consuming/source-contract tests, all 63 Markdown files' internal link/anchor and fence checks, index coverage and diff whitespace checks passed for this documentation update. No application code, workflow, APK, installation or physical TS18 state was changed by the audit.

The application baseline was not CI-green: [Validate on 801abd2](https://github.com/cbkii/ts-theme/actions/runs/43314413677) failed the coldstart collector's `test_accelerated_full_collector_deadline_and_early_probe_suppression` (`COMPLETE.txt` absent, cleanup unproven). The preceding [81741e6 run](https://github.com/cbkii/ts-theme/actions/runs/43312625613) reached the API29 probe and failed because `MaskFixtureActivity` was not resumed. These are immutable run observations for the app review, not findings caused or repaired by documentation edits. Recheck final-head CI; do not call the candidate release-ready from this audit.
