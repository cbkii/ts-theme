# Capture-driven TS18 repairs — 22 October 2026

## Evidence and ownership

The six latest supplied runs match launcher `0c062fe`, Auxio `cb38298` and **older Maps `df289c5`**.
They do not physically qualify Maps `1aaec09`. Listener grant without connection, early browser
failure, Maps provider unavailability and uncertain foreground handoffs are separate boundaries.
FallbackHome and incomplete BootTrace durations are collector limitations, not launcher results.

The confirmed observer-wrapper lifetime, stale merge, drawer window and splash presentation defects
are launcher-owned. Auxio owns the original preparation expiry across its non-exported trampoline.
Maps' current candidate already contains renderer/task recovery; no new renderer mutation is
justified by captures from the older APK. Keep all three existing draft PRs and their ancestry.

## Changes and regressions

- One retained callback-owning wrapper per token, explicit main-handler delivery, retry after failed
  registration and exact teardown. A single-flight one-second visible-only sampler checks just the
  selected controllers off-main; membership remains event-driven. No root media polling.
- Current selected session metadata/state wins over stale listener enrichment. Direct Radio A→B
  invalidates notification generation; supplied notification tokens must match the selected source.
- Access and listener connection are separate: bounded framework rebind after safe unlock/wake/HOME,
  cancellation on connection/revocation/sleep, and an independent process observer while disconnected.
- Failed browser binds back off inside the first deadline. Accepted/uncertain Activity starts are
  observed, never redispatched. Exact known trampoline/HOME aliases and already-HOME return are
  handled without relaxing permission, task, user, package or opposite-playback guards.
- Full-page drawer Activity has its own task affinity and reuses the existing asynchronous catalogue,
  five-column grid, search/voice, quick slots and pickers. Maps' presentation epoch is cancelled
  immediately; app launch does not wait for parking. Safe-area sizing uses residual live insets.
- Opaque single-spinner splash, at most eight seconds; owning-window BACK; exact owned-window
  capability checks; cancellation of later foreground work. Both opt-in settings and configuration
  round-tripping are retained. No debug status text or global key interception.
- Paired Auxio honours the launcher's original preparation expiry. The launcher checks its installed
  deadline capability; old APKs cannot silently qualify it. No second playback/session authority.
- Radio uses one centred marquee row with distinct station/frequency/band/RDS/description/genre text.
  Unit-bearing observed frequency takes priority. Cached system location is read off-main with
  existing permission, without starting GPS. Offline directory values are marked `(reference)`.
  The two ACT Triple J transmitters remain ambiguous: no guessed actual tuner frequency.

## Qualification and rollback

Host tests exercise production owners at controlled Android/Binder boundaries, including original
expiry and late callbacks. Android tests/lint/build, minification and signed TESTING publication must
pass on final heads. Source SHA, package identity, signer continuity and APK SHA-256 must match.
No protected SystemUI/firmware/OEM/SELinux changes, merge or production release are included.

Physical validation is NOT_RUN for these replacements. Previous device failures remain acceptance
failures for their captured versions. Install the matching launcher and Auxio plus Maps `1aaec09`.
The smallest useful device matrix is in `STARTUP_READINESS_LOADER.md`. Existing permission/storage
and app settings remain authoritative. Disable the two loader settings to roll back optional
preparation; retain existing APK/configuration backups for a package-level rollback.
