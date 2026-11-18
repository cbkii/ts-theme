# Three-boot physical validation remediation

The v3.5.0 collector campaign tested launcher d8e9378, Auxio 6b00bc1 and Maps ab7ecd0.
It failed Music restore/publication and Maps presentation. Prior CI success does not supersede
those physical failures. External force stops and expected collector deadline exits are separate.

## Owning changes

- Auxio retains the immutable decoder item's Song mapping for as long as that item remains in
  the canonical timeline, including after Next and when Previous returns. Legacy persisted
  membership remains a background restore fallback when primitive refs/snapshot are absent.
- Fullscreen verification avoids resizeTask(null) on a fullscreen task. Already correct effective
  display geometry requires no write; stale bounds use the exclusive stack clear and readback.
  Foreground/background paths preserve task/user/display and focus guards.
- The loader displays the coordinator's live status in both HOME and the drawn mask. Continue
  to Home invokes the existing cancellation path; it does not indicate source readiness.

## Required qualification

Run a real saved-queue cached-to-canonical restore, then 30 Next transitions and Previous before
and after its rewind threshold. Test first HOME Play without opening Auxio, no saved state,
late/unavailable sources and expired requests. Keep Radio passive preparation silent; qualify
first Play and audible Music/Radio handover separately. Check 10 compact/fullscreen/HOME cycles
and BACK separately, with a retained active route/task and actual visible/touchable map content.
Renderer initialisation and task geometry alone cannot certify map pixels.

Clock disappearance has no established MediaSession cause. Capture status-bar disable/visibility,
window flags, exact SystemUI/overlay state and video around startup, Maps transitions and shade
expansion. Do not mutate protected SystemUI to suppress an unproven cause.

Shuffle issue #288 remains open pending committed-order/scope restart and physical qualification.
All new physical, electrical cold-power and ACC-wake acceptance remains NOT_RUN by the builder.
Use continuous bounded recordings and record segment gaps, preflight presence and true stop reasons.

## Collector source and limits

The previously external v3.5.0 module is now maintained under
`scripts/diagnostics/physical-validation/`. Its successor records consecutive 45-second segments,
retains the three-boot cap, admits Boot 3 preflight, records explicit log stop reasons and encoder
PIDs, and requires a final signed APK selection at packaging. The source template is deliberately
unpinned. Per-run raw cap is 32 MB; three-run archive cap is 100 MB in up to five 20 MB parts.
The optional read-only power observer arms a separate same-boot follow-up without claiming ACC.
The new guide separates BACK, HOME and the actual fullscreen icon and preserves silent-recording
limitations. Status-bar policy/overlays and existing Maps surface tags support the next narrow
clock/black-map investigation; no protected SystemUI or speculative native Maps mutation is made.

## Automated coverage boundary

The playback-holder regression uses the captured 1,069-item membership and invokes 30 actual
Next commands after cached-to-canonical handoff, followed by Previous back to the original
retained decoder item. Its player/persistence boundaries are doubles. The separate API29 pair
uses the actual decoder/browser/session across UIDs and four deliberate publication gaps;
it does not certify the large-queue canonical transition, audible TS18 behaviour or map pixels.
