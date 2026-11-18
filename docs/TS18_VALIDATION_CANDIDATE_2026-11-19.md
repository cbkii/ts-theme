# v3.5.0 remediation candidate audit

Audited from launcher main `d1c589d2a0890a2e7080e831424ad04a1fa12c27`, after
merged #44 (`fc4c990`) and #56 (`d1c589d`). These are audit anchors; the selected
TESTING head and APK hashes belong in the release handoff, not mutable device assumptions.

## Current source and proven removal

The original three-boot v3.5.0 capture predates these remediations. Current source retains
source-owned browser/token/deadline admission, one playback owner in Auxio, effective N2
geometry/readback, independent readiness and useful loader progress. #56 adds service-only
Music completion, a fresh HOME observation after settling, refusal-aware Direct Radio
staging and a mask window confined to the measured app safe rectangle.

The package-private `AuxioPreparationContract.canonicalEntry` had no callers or reflective
references anywhere in the repository and is removed. Its removal does not change the
installed capability/UID/non-exported-entry checks. The older Music MainActivity observation
branch is retained deliberately: protocol-1 installed peers may still use that transition.
Legacy media, the shell cold-claim/fullscreen compatibility owner and N3's default-off
experiment are not proven obsolete by this capture and are not deleted speculatively.

The active integration guide now describes the service-only preparation contract instead
of the retired nonce/Compose path. No validation-module source is changed in this task.

## Plan disposition

| IDs | Current implementation and remaining acceptance |
| --- | --- |
| M1-M2 | Auxio owns saved-queue restore/decoder; launcher admits ready Music without UI and preserves the original bounded Play. Initial exact-device bind/root cause and first audible Play remain unqualified. |
| M3-M4 | Auxio #287 retains canonical decoder-item identity; launcher token/metadata updates and acknowledgements remain strict. Holder 1,069-item/30-Next coverage and real API29 pair publication-gap coverage are separate. Actual notification/session/launcher/audible convergence remains physical. |
| M5-M6 | Existing configured rewind and restore ownership are retained. Auxio's follow-up adds threshold/order/pause and incomplete-membership tests; transport position is source evidence, not an audio or speed benchmark. |
| R1-R2 | Previously refused Direct Radio changes method within the original tap deadline; healthy/Legacy/skip paths and replacement-observed handover are preserved. No passive tuner activation. Cold admission and audible overlap/delay require device evidence. |
| N1-N3 | #44 repairs effective fullscreen operation/readback; Maps #85 fences post-resize presentation evidence and bounds same-Surface repair. Black content is longstanding. Task, geometry, frame submission, pixels and touch remain distinct. |
| N4 | Existing guide separates the actual fullscreen icon, SystemUI HOME and BACK. Each needs its own lifecycle observations. |
| U1-U2 | Real loader status/cancellation and safe-area mask are implemented. The clock's cause is unknown; existing bounded policy/window/overlay/video/SystemUI-hash collection is retained. No protected SystemUI repair is guessed. |
| P1 | Existing committed shuffle durability is retained. Verify actual committed OFF/ALL/order/source/position after restart and reboot; #288 is not closed from CI. |
| Q1-Q2 | Existing collector records consecutive bounded segments, explicit gaps/status/stop reasons, per-run preflight and sealed manifests. Device encoder/integrity and product acceptance remain separate. |
| Q3-Q4 | Separate wake observation and power-procedure notes; boot ID is not electrical cold proof. External force stops and dependent losses are not spontaneous crashes. |

## Recently merged scope still needing physical validation

- Launcher #44, `fc4c990`: media handoff/publication integration, effective fullscreen
  geometry, useful loader text and consecutive-capture/procedure remediation.
- Launcher #56, `d1c589d`: service-only Music/HOME survival, initial Radio fallback
  admission and safe-area loader window; installed SystemUI/Radio read-only evidence.
- Auxio #287, `93d35a8`: saved legacy queue recovery, canonical retained-decoder mapping,
  browse-independent identity, service-only preparation, bounded HOME Play/shuffle ownership.
  #289, `6a02e24`, synchronises the MockK catalogue and adds no playback semantics.
- Maps #85, `b0a8d9c`: resize invalidation/in-flight swap fencing and bounded surface repair;
  frame submission still does not prove useful/unoccluded map pixels.

The final selected launcher/Auxio pair is pinned in `scripts/ci/paired-auxio-source.txt`.
Record source/build/package/version/signer/SHA-256 from each final signed TESTING group.
The prior signed rollback archives must remain available. No production publication or
device installation is implied by candidate construction.

Run first HOME Music Play, initial Radio/handover, 30 Next and Previous on both sides of
the rewind threshold, actual Maps pixels/touch across HOME and BACK, clock/shade, committed
shuffle, USB/SAF and source loss. Then qualify app/process restart, reboot, cold-power
procedure and genuine ACC separately. No new physical outcome is claimed by this audit.
