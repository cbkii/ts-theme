# Organic Maps compact startup v1

Outcome: `app.organicmaps.incar` owns the explicit startup choice and renderer evidence;
`com.cbkii.ts18launcher` owns admission and compact task presentation on API29.
This child builds on #24; the matching Maps child builds on #67. Neither parent
stack is merged, rewritten or replaced.

## Active contract and implementation

- First In-Car Settings toggle defaults ON when the key is unset in the InCar product. A stored OFF remains OFF. Other products and unrelated driving/camera/route preferences are unchanged.
- InCar-only read-only provider `app.organicmaps.incar.compact.startup/status`.
  Query parameters `v=1&boot=<Settings.Global.BOOT_COUNT>` are mandatory. The
  response binds protocol, package, boot, Android user, generation `boot:user:1`,
  PID, elapsed query time, enabled choice and process-local renderer evidence.
- Both providers verify the actual Binder caller UID, same Android user, sole
  package owning that UID and the independently pinned release certificate.
  Package targeting alone is not authentication. No debug trust bypass, shared
  UID, arbitrary command, rectangle or preference-write RPC exists. The authenticated observer supports one action-specific root-Back request carrying the Maps task and boot; the launcher independently verifies ownership before focusing its existing HOME task.
- Pins: launcher certificate from signing run 38877410241; Maps certificate
  `40ad32351bf55902c617df2351a5761bb5e5462cc2f2ed268503082331139780`
  from publish job 116853941672, run 38863163942. A signer rotation requires a
  verified Android signing lineage back to the pinned identity, or a reviewed pin update. Ordinary debug builds are intentionally not an
  authenticated install pair.
- Launcher capability reports API29 + actual default HOME + native-window mode
  + compatible selected navigation package. This is not root or physical proof.
  The setting explains the requirement for authorised task control; root is
  verified by the existing helper at execution, with truthful refusal on failure.
- Queries and peer callback authentication run off HOME's main thread. Deadlines
  invalidate late evidence; a non-queued two-worker pool bounds blocked Binder calls. Authenticated Messenger invalidations drive updates;
  500ms observation is limited to a 45s startup window. Missing, old, locked,
  stale or wrong-signer peers deny automatic cold launch. Warm task adoption and
  explicit user opening remain available.
- Boot receivers persist only a credential-unlocked boot signal using goAsync;
  they do not launch Activities or rely on a surviving singleton. The Maps boot signal is
  exposed as authenticated diagnostic evidence, not a prerequisite that delays early HOME. HOME queries
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
no current external-navigation yield, and confirmed HOME window focus.
Unknown/null focus yields; Android call state cannot prove the vendor video path.

The original `launch-user-package-boot` atomic claim still protects every
accepted/uncertain admission. A second `auto-v1-user-package-boot` directory is
reserved atomically for automatic admission and **retained after task success**.
Its identity includes schema, kernel boot ID, user, package and generation. Only
a proven pre-admission refusal releases it. Launcher recreation, screen-on and package update cannot replay an accepted or uncertain admission. A recorded previously-ready process lifetime may recover once only after corroborated process death and fresh task absence. An unresolved initial claim or intentionally closed task in a live process remains protected. Runtime state is launcher-private, outside `/data/adb`. Ordinary HOME touches do not suppress startup for the boot; actual external navigation temporarily yields and a valid HOME return reconciles again.

The selected launcher profile is N2 + bridge + fullscreen departure. Existing-task adoption precedes new admission. N2 admits through ordinary Splash/entry and converts the same verified task. Its journalled handoff survives HOME stop. No profile comparison or manual priming is required. Launcher and Maps candidates must be paired.

Integrated root Back preserves the Maps task after transient and route handlers have had their normal opportunity. The provider sends Messenger `what=2`, task in `arg1`, boot in `arg2`, to the previously authenticated observer. Launcher authenticates sender UID and current boot off-main, then verifies exact task and foreground through its sole root owner. No callback creates HOME or bypasses protected foreground. If cooperation is unavailable, normal standalone Back remains available.

The launcher reuses an inherited-pipe root bridge with bounded typed requests and idle exit; no listening socket, external service manager or persistent polling daemon is introduced. A fresh desired-presentation epoch supersedes old fullscreen/departure results. Fast task mutations await the current peer renderer and matching dimensions rather than treating dispatch as visible pixels.

## Application evidence and performance

Organic Maps emits monotonic `InCarCompactStartup` events for Splash, core,
map resume/pause, renderer attachment/detachment, native initialisation and
surface/native-size convergence. Core/native flags are process-local; core initialisation
and native state reused by a restored surface are retained. A newly created renderer resets
native readiness until its own callback. Detach, replacement and geometry epochs invalidate
attachment/dimensions; stale Activity callbacks cannot ready or invalidate a replacement. It does not call routing, camera, GNSS, task or focus APIs.

Launcher logs `compact-startup` observations with boot/task/stage/native/surface
size/resume/renderer state and elapsed time. Its ready state requires both the
existing mode/bounds/WMS visibility verification and the peer's renderer-ready
status for that exact task. This means software rendering eligibility; actual
pixels, touch, offline data usefulness and GNSS fixes are separate physical gates.
Around three seconds is a soft product target; reliability and the fastest measured approach take priority. Compare boot videos with these monotonic
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
The root owner may use ordinary Activity entry when current prerequisites and admission allow; no unconditional manual-launch prerequisite is imposed. Disabled components, incompatible hosts and missing root fail open. Device qualification must distinguish an Android reboot from package force-stop and first-run permission/resource gates.

## Validation and remaining gates

Use the matching ts-theme `scripts/qualification/ts18-validate.sh guide`, menu **a**, Compact A0-A16.
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

Observer registration is limited to the authenticated peer and boot, and accepts only a
Messenger Binder for invalidation. Invalidations carry no renderer state; the action-specific Back message carries only task/boot and its sending UID is checked;
there are no public renderer notifications. Provider authorities are resolved to the pinned
package before querying/registering. Capability probes use a bounded two-worker pool with no
queue. One stalled call leaves a second slot available; two stalled calls fail closed
until a worker returns. Registration and cleanup use the same bounded capacity; cleanup
is idempotent and never blocks HOME. Android Binder calls are not assumed interruptible.

The attempted optional PR17 archive could not read draft release assets with the PR
workflow token (HTTP403). It is removed from source CI; no archive is claimed. The live
managed draft also contains the immediate-parent PR24-96c128d signed candidate, APK SHA-256
`c7a53fa0ecc9d1876f10c64a38210b9d508d14c5538715c83399b10d6e37c590`.
The existing two-snapshot publisher retains that immediate-parent rollback when adding PR26;
the older PR17 snapshot rotates out under the existing authorised TESTING policy. No
publisher, signing permission or retention-policy change is required. PR24 remains a
rollback candidate, not a physically qualified cold-start success.

User yield is temporary and action-scoped. Accepted/uncertain Activity admission remains protected by the durable root claim.

## Resume hardening

Authenticated event invalidations remain live after the 45-second automatic polling window;
this is necessary to observe opt-out and renderer loss. The limit bounds periodic polling,
not genuine peer events. Changes during callback delivery are drained by a version counter.
New Activity creation publishes invalidation immediately.

User yield retains ownership of an in-flight bounded root transaction until completion or
its deadline; a late result cannot resume automatic presentation or admission. It does not
claim that destroying a Java executor proves all privileged descendants stopped. Existing
shell foreground guards and accepted/uncertain launch claims remain authoritative.
Media foreground deferral invalidates the navigation wait epoch and starts a fresh bounded
observation wait after release. Each Compact guide visit requires a new A0 smoke result;
a retained PASS from another installed candidate cannot unlock A1–A16.

Android Check on Maps f71a3aa compiled arm64 and passed lint, then exhausted the default
2 GiB Java heap while merging x86 debug resources. The x86 packaging invocation now uses
a 4 GiB heap and two Gradle workers; canonical final-head CI must verify the change.

## Current execution record

See [automatic preparation execution](AUTOMATIC_STARTUP_2026-10-20.md) for source pins, host checks, v2 kit and remaining physical gates.
