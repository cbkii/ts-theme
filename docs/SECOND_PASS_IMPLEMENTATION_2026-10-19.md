# TS18 second-pass implementation — 19 October 2026

Target confirmed by owner: Android 10/API 29, Topway UIS8581A, Cortex-A55,
PowerVR GE8322, 4 GB RAM, eMMC, 1280x720 landscape, Magisk and OEM services.
Root is not platform-signature or privileged-app authority.

## Launcher / PR26

Based on `136c62ead089c3a0158161e0956b05913b82d582` (main base
`2cd04022231064deec627bd1593fc4cfde5d94ce`). Startup readiness consumes immutable,
event-updated content/playback/session observations. Background reads are bounded
to two non-queued workers, time out to unknown and reject superseded completions.
Readiness ticks do not enumerate sessions or read remote queues/metadata.
Meaningful source content is separate from Play capability; known Auxio/NavRadio
placeholder-only metadata cannot establish content readiness.

Automatic media dispatch first commits a per-boot/user/source/package journal
off-main. A recreated HOME retains accepted/uncertain admissions. A previously
usable session's authoritative disappearance permits recovery; unknown evidence,
metadata churn or late storage alone does not authorise duplicate foreground
admission. Journal failure or unknown boot refuses automatic dispatch. Explicit
app icons remain separate user actions. Existing normal/root refusal policy,
exact-HOME restoration, silence checks and protected UI/playing-source checks stay.

Prospective media reservation requires an eligible resumed/focused HOME. An
accepted media stage still owns its bounded lane. Navigation's wait epoch is
reset on deferral and receives a fresh budget on release. HOME/focus/wake events
refresh authenticated renderer evidence; renderer loss revokes readiness and
exposes the existing fallback without inventing a second Maps launcher. This is
event reconciliation, not continuous idle process-death monitoring.

## Radio / stacked PR27

Based on `27d91379752c759c2bc5291107f7d76fe6c5abc1`; parent PR26 fixes are merged
with ancestry preserved. Radio metadata uses an already-observed in-process fix
only. Native Maps does not populate that optional snapshot, so no-location
ACT/NSW/VIC ranking often applies. No extra provider query or listener is added.
Notification station epochs invalidate old enrichment through placeholder states,
including delayed old-station posts. Frequency-only text is not promoted into
both ticker rows. Fades consume cached shaped TextView layout widths.

Observed unit-bearing frequencies still outrank inferred directory matches;
conflicts remain blank. Actual installed NavRadio tuner authority remains unverified.
The six appended A17–A22 cases extend the existing P/F/N guide and its A0 gate.
They do not mutate the device automatically. The old signed paired kit is unchanged.

## Maps / PR69

Based on `78b123785ead6041fae030356902ecfde8f732a0` (master base
`f13716f7b1dad1d3efc8cf1191e533bf75da439b`). Cached locations are requested
asynchronously after subscriptions start. Provider membership is copied on its
owner thread; late results cannot publish after stop/restart, provider replacement
or a newer accepted fix. Cache evidence is never replayed as a live navigation fix.

## Validation and remaining boundaries

Launcher source validation and full source/helper suite pass (361 tests,
three existing host-namespace skips). The actual API29 resource compiler/linker
and Java8-target production compilation pass; D8 conversion is separately recorded
in the external handoff. Narrow coordinator/media/radio regressions cover journal
failure, process recreation, focus release, placeholder content and station epochs.
Maps' 92 tool tests pass, including all three native-owner harnesses and the
complete production LocationHelper/provider boundary test. Scope verifier passes.

Normal launcher Gradle lint/JVM/APK validation was attempted but cannot resolve
pinned AGP 9.3.1 here. Do not change the pinned dependency to hide that boundary.
Full engine-linked BookmarkManager tests remain NOT_RUN: the retained `map_tests`
requires Qt/server and generator test support, while root CMake deliberately
rejects non-Android configuration and forces host tests off. CMake is also absent
in this environment. No host/GPU gate was weakened or substituted by a headless
simulation. The inherited whole-tree formatter failure is not repaired by unrelated
bulk formatting. New-source CI, minified signed TESTING publication and exact APK
hashes are pending remote publication.

All TS18 physical cases are NOT_RUN: A0, historical T11/T13/T14, genuine boot/ACC,
audio, pixels/touch, OEM protection, source loss/late USB, native bookmarks/graphics
and sustained CPU/RAM/frame/thermal comparison. No measured performance gains,
merge readiness, installation or production publication are claimed.
