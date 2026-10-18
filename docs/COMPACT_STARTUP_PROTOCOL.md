# Organic Maps compact startup v1

Outcome: `app.organicmaps.incar` owns the opt-in choice and renderer evidence;
`com.cbkii.ts18launcher` owns admission and compact task presentation on API29.
This child builds on #24; the matching Maps child builds on #67. Neither parent
stack is merged, rewritten or replaced.

## Active contract and implementation

- First In-Car Settings toggle, default OFF. Existing installs have no key and
  remain OFF; unrelated driving/camera/route preferences are untouched.
- InCar-only read-only provider `app.organicmaps.incar.compact.startup/status`.
  Query parameters `v=1&boot=<Settings.Global.BOOT_COUNT>` are mandatory. The
  response binds protocol, package, boot, Android user, generation `boot:user:1`,
  PID, elapsed query time, enabled choice and process-local renderer evidence.
- Both providers verify the actual Binder caller UID, same Android user, sole
  package owning that UID and the independently pinned release certificate.
  Package targeting alone is not authentication. No debug trust bypass, shared
  UID, arbitrary command, task ID, rectangle or preference-write RPC exists.
- Pins: launcher certificate from signing run 38877410241; Maps certificate
  `40ad32351bf55902c617df2351a5761bb5e5462cc2f2ed268503082331139780`
  from publish job 116853941672, run 38863163942. A signer rotation requires a
  reviewed pin/lineage update. Ordinary debug builds are intentionally not an
  authenticated install pair.
- Launcher capability reports API29 + actual default HOME + native-window mode
  + compatible selected navigation package. This is not root or physical proof.
  The setting explains the requirement for authorised task control; root is
  verified by the existing helper at execution, with truthful refusal on failure.
- Queries run off HOME's main thread, have cancellation/deadlines and never
  accumulate parallel blocked Binder requests. ContentObserver drives updates;
  500ms observation is limited to a 45s startup window. Missing, old, locked,
  stale or wrong-signer peers deny automatic cold launch. Warm task adoption and
  explicit user opening remain available.
- Boot receivers persist only a credential-unlocked boot signal using goAsync;
  they do not launch Activities or rely on a surviving singleton. HOME queries
  the true preference and reconciles independently, before or after BOOT_COMPLETED.
  There is no direct-boot access to credential-protected preferences.
- Navigation readiness no longer depends on media warm-up. Background media
  observation does not hold Maps behind player deadlines. Only actual media
  foreground staging holds the shared foreground lane; user source actions
  remain immediate. This does not claim automatic media audibility.

## Admission and recovery

The original root helper remains the only cold/task mutator. It resolves the
current task through existing corroborated ATM/activity/stack observations before
admitting anything. An existing valid task is reused, even with opt-in OFF.

Automatic cold admission additionally requires ON, compatible authenticated
peer, current unlocked/interactive API29 HOME, normal Android audio mode,
no recorded user override for the boot, and confirmed HOME window focus.
Unknown/null focus yields; Android call state cannot prove the vendor video path.

The original `launch-user-package-boot` atomic claim still protects every
accepted/uncertain admission. A second `auto-v1-user-package-boot` directory is
reserved atomically for automatic admission and **retained after task success**.
Its identity includes schema, kernel boot ID, user, package and generation. Only
a proven pre-admission refusal releases it. Launcher recreation, screen-on,
package update and task loss cannot replay a previously accepted automatic cold
start. Root paths remain private; no daemon or persistent OEM writer is added.
If a truly lost task needs another admission in that boot, the user can choose
explicit Retry/Open; an unresolved accepted claim still blocks a duplicate.
User interaction suppresses further automatic cold admission for the boot,
including after launcher process recreation. It does not close a valid task.

N1 requests direct mode 5. N2 normally admits once then converts the same task.
Missing/explicitly refused direct launch options may select N2 + guarded bridge;
an accepted/uncertain launch never selects a second admission. N2's journalled
method is restored on delayed observation so the same-task handoff survives
HOME stop. Current Testing methods still permit controlled N1/N2 comparisons.
Neither method is physically preferred by these source changes.

## Application evidence and performance

Organic Maps emits monotonic `InCarCompactStartup` events for Splash, core,
map resume/pause, renderer attachment/detachment, native initialisation and
surface/native-size convergence. Renderer evidence is process-local and reset
by detachment/new Activity dimensions; a stale Activity pause cannot invalidate
its replacement. It does not call routing, camera, GNSS, task or focus APIs.

Launcher logs `compact-startup` observations with boot/task/stage/native/surface
size/resume/renderer state and elapsed time. Its ready state requires both the
existing mode/bounds/WMS visibility verification and the peer's renderer-ready
status for that exact task. This means software rendering eligibility; actual
pixels, touch, offline data usefulness and GNSS fixes are separate physical gates.
No startup timing target is invented. Compare boot videos with these monotonic
stages; report median and slowest from separate comparable boot trials.

## Research and bounded decisions

- [Android 10 ActivityStarter](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-release/services/core/java/com/android/server/wm/ActivityStarter.java):
  visible caller and UID0 exemptions; aborted background admission can be mapped
  to apparent success. Dispatch exit is never renderer/task proof.
- [Background Activity starts](https://developer.android.com/guide/components/activities/background-starts):
  boot reception alone does not authorise a visible Activity. SAW declaration
  is not an actual grant and is not used as an unproven bypass here.
- [Direct boot](https://developer.android.com/privacy-and-security/direct-boot):
  defer credential-protected preference access until user unlock.
- [ActivityOptions launch bounds](https://developer.android.com/reference/android/app/ActivityOptions#setLaunchBounds(android.graphics.Rect)):
  public bounds are advisory/capability-dependent; retain the physically working
  API29 root/task bridge rather than introduce a competing app-side controller.
- [Magisk boot scripts](https://topjohnwu.github.io/Magisk/guides.html#boot-scripts):
  late_start is the optional recovery stage. No companion is added: there is no
  exact-device evidence yet that corrected HOME admission needs another boot
  actor. A companion must share the existing claim if later justified.

Protected TW/DoFun/SystemUI/MCU/CAN, settings, partitions and SELinux are untouched.
Current supplied device documents are September snapshots; live HOME, package,
root domain/namespace, signature and runtime capability require physical recheck.
There is no qualified vendor ACC signal. SCREEN_ON stays an unverified wake hint;
no invented vendor receiver or separate cold launch is added for it.
Force-stopped/not-yet-opened packages must be manually opened once; no bypass is
claimed. Disabled components, incompatible hosts and missing root fail open.

## Validation and remaining gates

Use `scripts/qualification/ts18-validate.sh guide`, menu **a**, Compact A0-A16.
Run A0-A2 smoke before long trials. P/F/N with optional comment is retained; the
collector never installs, launches, reboots or fabricates a physical result.
Historical embedded candidate rows remain comparison evidence; match the new
pair using installed build/source metadata and final signed snapshot reports.

The signed pair must match final source heads, workflow IDs, package/version,
certificate and APK SHA-256. Retain rollback before requesting a rotating draft.
CI/JVM/shell checks cannot qualify TS18 cold boot, real ACC, pixels/touch,
call/reverse, startup flash, route preservation or timings. All physical levels
remain NOT_RUN until exact-device results arrive. A failed A0 should lead to a
focused fix, not another full validation campaign.
