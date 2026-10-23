# First-HOME acquisition repair after BootTrace v3.1

## Scope and evidence

Initial launcher head d768592c4f87098ac8c04d44f3f7417090c95424; Auxio head
c796392c46e80f719a7569eb08ce918e82827bee; initial Maps head
3c10b59fcd2661fbb8f5dd2a459e8e223632037b. PRs #31/#281/#78 remain draft.
Target: confirmed Topway TS18, API29, UIS8581A/SP9863A family, 4GB class,
1280x720, concurrent media/navigation, normal app MediaSession access plus the
already authorised narrow root task broker. Actual OEM cold acquisition, ACC,
audio and first-HOME latency require physical measurement.

The four completed v3.1 captures failed automatic startup. Two waited past
three minutes with no Auxio session or Maps task. Warm metadata/NEXT/PAUSE
work after manual opening; retain those fixes. This change does not declare the
apps physically finalised, and does not replace the single source/task owners.

## Confirmed root causes and changes

- The root broker classified every non-zero native probe exit as a timeout.
  `NavWindowProbe` executes dumpsys directly with Java-enforced real deadlines,
  separate bounded stdout/stderr drains, output caps, exit/spawn/drain/deadline
  categories and short path-redacted stderr. It clears loader environment variables
  like the existing shell helper. The exact historical OEM command failure is still
  unproven; do not claim the absence of a Toybox option caused it.
- A non-null failed fast observation suppressed the independent read-only fallback.
  The coordinator now tries that fallback on acquisition failure. Positive foreign
  ownership still refuses it; unknown cannot launch. Mutations retain their own
  current task/user/component/window/mask/generation checks.
- A local stage failure globally yielded, drained the other source queue and could
  suppress Maps. Source-local completion now releases only its own stage. Proven
  pre-dispatch failures release the durable claim with a three-attempt bound;
  accepted/uncertain starts retain their claims and are never replayed. Late definite
  refusal after cancellation can clean its own still-current claim.
- Registered Maps provider acquisition may fail before provider code runs. The
  signed InCar APK explicitly advertises a v1 fallback for its existing private
  OrganicMapsPrefs/InCarCompactStartup contract, unset=true, saved OFF authoritative.
  `CompactStartupConsent` verifies the peer signer/unique UID/user/capability and
  package-update identity around the read. `NavCompactConsent` uses the actual PM
  data directory, owner/canonical/backup/size/XML checks and requires the Maps UID
  absent before and after reading. A live process, corruption or any uncertainty
  produces unknown. No settings, permissions or files are modified. This is consent
  only: task=-1, renderer=false. The live authenticated provider must establish all
  fresh renderer/task/presentation evidence. Older APKs without opt-in do not qualify.
- Finite evidence watching now publishes an explicit ended state. HOME shows
  a localised retry affordance when acquisition is exhausted rather than claiming
  work is still pending. Late authenticated evidence can recover on the same HOME.
  Boot/unlock/HOME/screen/ACC edges coalesce and re-arm a finite watch; they do not
  create a background daemon or unbounded poll.
- MediaBrowser keeps its single framework connection. A ContextWrapper observes
  the actual bind return/exception; connection-failed/suspended/token/deadline
  phases include the source generation and original lease. Maps calls record the
  caller/peer UID and null acquisition results. No second media observer/player,
  root media polling or synthetic keys are introduced.

## Architecture choice and limits

Retain the existing process media publisher, source readiness coordinator and
navigation controller. Repair their command/acquisition seams rather than add a
second coordinator. A preference mirror was rejected because a stale ON could
ignore a later saved OFF. The cold fallback reads only the owner's current persisted
preference while that UID is absent; no cached renderer state can cross a boot.
The changed privileged consent boundary requires independent human review before
merge. Root availability/SELinux and exact OEM Binder/launch behaviour remain
physical qualification gates.

## Regression and validation contract

Host tests execute actual bounded subprocess I/O (success, exit 7, missing executable,
hang, long-line/stdout/stderr caps), structural XML parsing, default/OFF/ON, live UID,
owner/user/protocol/backup/symlink rejection. The production coordinator regression
fails the original source with `failed fast observation suppressed independent
fallback` and passes the repair. Existing authority, delayed readiness, source
selection, stale callbacks, mask expiry, uncertain dispatch, HOME return and user/
permission/call/screen protections remain regression gates.

CI now executes the actual broker/dumpsys/reflection/IPC in an isolated API29 root
emulator, with real HOME and Settings ownership and an absent signed Maps peer;
it also executes the MediaSession instrumentation APK. A generated CI-only peer
then exercises actual PM/process/Os/private XML default/OFF/backup/live-process
boundaries; the launcher-UID instrumentation rejects that peer's untrusted signer.
The fixture has no app code, no Gradle module/product, and no distributed artefact;
its temporary key/APKs are excluded from uploaded evidence. It must pass, along with
host tests, Android unit/API29 Robolectric tests, lint, debug and signed/minified
builds. It does not validate Magisk, the signed peer cold-consent file under TS18
SELinux, radio tuning, USB, map pixels/touch or audible playback.

## Physical handoff

Use the exact replacement launcher and Maps APKs plus unchanged Auxio c796392.
The paired capture must label each boundary separately and seal before export.
Perform three untouched first-HOME boots before any manual app opening. Require
silent Music preparation, one native selected session, current queue/metadata,
one admitted Maps task, current renderer at compact bounds, usable pixels/touch,
and an honest finite result for all sources. Then one Music PLAY, natural tracks,
NEXT/PAUSE/PLAY, Radio handover, app-away/HOME, saved Maps OFF/ON and separate ACC/
full-power/audio/guidance qualification. No physical result is inferred from CI.
