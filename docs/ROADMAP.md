# Launcher priorities and release roadmap

The only product is the standalone Android10/API29 launcher. DoFun theme, Leaflet/WebView and the inert privileged N3 host are retired. Their old investigations remain historical evidence in the [documentation index](README.md), not future release tasks.

## Product priorities

Investigate and perfect these in order, using the current code and actual installed-build evidence:

1. Automatic Maps → Music → Radio readiness and recovery, preserving explicit opt-outs. Observe usability; a preparation trigger is not success. One blocked dependency must not stall the others.
2. First-tap assigned-source playback, including absent processes, delayed capabilities and restore. Keep the original direction/deadline, correct item/queue/position or station, and exactly one command.
3. Visible, updating, touchable native Maps on HOME. Separate acquisition, renderer, geometry, pixels/touch, location and route state; avoid repeated ineffective launch/Retry methods.
4. Fullscreen, app switching and HOME/BACK return with the same valid Maps task/route, correct geometry and focus, and preserved permission/setup flows.
5. Responsive HOME, drawer, shortcuts and Settings during slow/failed integration. No swallowed launches or indefinite navigation-status barrier.
6. Correct controls, metadata and source handover. State belongs to the intended package/token; retain prior playback until replacement playback is observed.
7. Seamless preparation. Prefer service-owned readiness; use the implemented bounded opaque mask for explicit cold NavRadio recovery where its background contract is insufficient. No unmasked automatic launch, unsolicited tuner selection or stuck overlay.
8. Diagnose app-owned permission/location/route/renderer issues in the owning repository; launcher task fixes must not mask them.
9. Reduce duplicate owners, observers, polling, root launches and main-thread I/O. Keep N2 reliability ahead of N3 experimentation.
10. Preserve established automotive layout, settings/import compatibility, real app icons, measured SystemUI-safe geometry and binary Day/Night appearance.
11. Qualify one matched TESTING stack through one simple final owner-run physical campaign before production promotion.

These are acceptance targets, not claims that the current candidate has passed them. Start from [architecture](ARCHITECTURE.md), [readiness](CONTINUOUS_READINESS.md) and [the work ledger](WP_LEDGER.md). Preserve settings and useful integrated fixes rather than rebuilding parallel subsystems.

## Remaining qualification

Complete independent code review and automated validation before requesting device work. Final TS18 acceptance covers actual map pixels/touch, first-tap audible Music/Radio, metadata, masked recovery and cleanup, handover, HOME/BACK/fullscreen, ordinary process loss, late USB, permissions, calls and applicable vehicle takeovers. Report Android reboot, cold power and genuine ACC separately. Emulator and shell state do not qualify those physical boundaries.

Use [the consolidated campaign](CONSOLIDATED_TESTING_PLAYBOOK.md) with exact source/built SHA, package/version, signer and APK SHA-256. Retain a signed rollback pair and DoFun recovery. No automatic production release or merge is authorised by this roadmap.

## Capability-dependent work

- Installed API29 NavRadio protocol and passive non-disruption remain device-specific. Early safe portions may prepare; a closed app that inherently selects the tuner should finish automatically on explicit Play.
- Stock TW Radio native controls, vendor illumination, Bluetooth/projection and reverse-camera lifecycle need their own evidenced authority and qualification; do not invent OEM commands.
- N3 remains an isolated default-off developer experiment. Its API29 input/embedding limits do not justify a privileged host or displacement of N2.
- Other navigators need their own task/bootstrap/window evidence. Organic Maps behaviour cannot be imposed on a donor's different valid lifecycle.

Measure startup/return latency, CPU, memory and launch churn on the actual candidate and comparable state. Timeout constants and code removal are not performance measurements.
