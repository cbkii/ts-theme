# Automatic cold preparation and HOME return — 20 October 2026

The selected profile is N2 + bridge + fullscreen departure + immediate root-assisted silent Music preparation. Retain and user profile comparisons are excluded. Around three seconds is a soft target: the mandatory objective is the fastest reliable measured outcome. Small timing overruns alone are not failures.

This child is based on PR27 `11ec69a0c53f723cca0c046a255ddf0f4322b006`. Pair it with the Maps child of `07340fca1e5e603e0f40aa576204b1ec39342c14` and Auxio child of dev `21cd6f3fd84f3d163345fedfa1b5214fe68854d4`. The captured old Auxio APK's source is unknown; no source-attribution claim is made for it.

## Resulting behaviour

- Music queues the short root Activity preparation slot immediately, while public browser observation proceeds concurrently. Reservation begins before the native panel's Activity-resume race. The paired non-exported source entry issues a one-use nonce into the existing MainActivity; the single playback owner restores paused. Source entry observation releases foreground staging without waiting for a full library scan or the former nine-second browser gate. The observation delay is 150 ms, not a claim that the service or audio is ready then.
- A same-source explicit Play remains authoritative. The paired public custom action carries the remainder of the existing 4.5-second command budget into source restore. Expired delayed commands cannot become later autoplay; no alternate command is dispatched after an uncertain result.
- HOME touches no longer write boot-long Maps suppression. External navigation temporarily yields. A proven cancellation before Activity dispatch releases only its matching journal and briefly reserves the lane until cleanup; accepted/uncertain starts stay protected.
- Maps unknown preference remains unresolved. Read-only discovery can still adopt a manually created task, including with startup explicitly OFF. Unset InCar preference defaults ON in the paired Maps candidate; saved OFF is preserved.
- One latest presentation epoch controls compact HOME, fullscreen Maps and background fullscreen. HOME supersedes old goals, including late fullscreen completion. Cooperative root Back preserves the map task after ordinary transient/routing handlers, authenticates UID/boot and focuses only the existing verified HOME task.
- The root bridge uses one reusable `app_process` over private inherited pipes. Typed API29 observation/transition operations preserve exact package/user/task/display and exclusive-stack checks, corroborate display-level focus and check epochs between mutations. Background fullscreen never focuses Maps. An unsupported, undispatched operation may use the shell; a timeout after dispatch never replays it. The process exits after 60 seconds idle or client EOF.
- Fast task success waits for authenticated renderer evidence for the same task and dimensions. It does not invent WMS drawn/visible flags. Peer readiness establishes software eligibility, not actual useful pixels or touch.
- Legacy current-boot launch claims remain read-only guards across an APK upgrade; a storage move cannot create a second admission. Runtime helper journals now live in launcher-private `no_backup/navigation-root`, invoked through `/system/bin/sh`. There are no new generated writes in `/data/adb`. Proven death of a previously ready Maps process may recover once; live-task close or uncertain initial admission does not reset a claim.

No second startup launcher, player/session, root management app, injected key sequence, UID1000 impersonation, SELinux change or protected-package writer is introduced. API29/default-HOME/unlocked/interactive/normal-audio and exact foreground guards remain required. Permission and first-run resource UI remain truthful blocked prerequisites.

## Diagnostic kit

Use `scripts/diagnostics/coldstart/START-HERE.md`. The collector runs from Magisk `service.d` for at most eight minutes and is read-only with respect to application behaviour. All generated logs, receipts, temporary files and archives go to Download. The collector is 22,950 bytes; two installed copies total 45,900 bytes, below 100 KB, with zero generated diagnostic output in Magisk. Existing v1 is recognised by its exact hash for upgrade; arbitrary service files are refused.

The guide observes automatic cold startup before manual comparison and includes separate Maps/Auxio HOME and Back cases. P means functional and acceptably fast (including near-target overruns); S means working but noticeably slow; F failed; N unknown/unrun. It never fabricates an audio/pixel pass from structural logs.

## Validation status

| Check | Result |
|---|---|
| `python3 tools/ts18_theme.py validate` | PASS |
| Complete launcher Python suite | 393 tests, PASS; three unavailable-tool skips |
| Production startup runtime | PASS, including cold Music lane, old source capability refusal, live Play and pre-dispatch cancellation/re-entry |
| Controller production model | PASS, including late fullscreen/HOME and OFF + existing task adoption |
| Root bridge server | Five PASS, including task preservation, protected foreground and mid-operation epoch cancellation |
| Compact client | PASS, including blocked Binder, off-main UID authentication, current-boot Back and double close |
| Diagnostic kit | 25 host tests PASS; shell syntax PASS |
| Real Android29 production Java/resources | PASS; D8 produces one DEX (debug compilation only) |
| Android Gradle lint/unit/build and signed/minified runtime envelope | Pending canonical CI; local full AGP toolchain unavailable |
| ShellCheck 0.10.0 | PASS for the root helper and both device scripts |
| Physical cold boot, ACC, pixel/touch, silence/audibility and fastest measured timings | NOT RUN |

The host doubles replace platform boundaries, not production reducers/owners. They cannot qualify OEM Binder/SELinux support, signer pairing or real window pixels. No APK is installed and no merge/public release is performed by this change. The signed TESTING snapshot must identify exact source head, package, signer and APK hash before physical qualification.

CI run 39361089950 (attempt 1, initial head 0da30a8) exposed a host-test portability defect: the collector shim returned GitHub runner UID1001 rather than modelled Magisk UID0. The shim now models UID0 at that boundary and separately exercises production UID1000 refusal. No device guard is removed. Canonical final-head validation is required; rerunning the unchanged failing head is not a fix.

## PR finalisation review — 20 October

The accepted Music Activity handoff is now observed across a bounded four-second window, rather than treating one 150 ms observation as final. It returns immediately when the exact MainActivity is observed, yields to known foreign/permission owners, and never replays the accepted launch. Regressions cover a 900 ms handoff, protected UI and an accepted entry that never appears. The paired Maps child now falls back to preserving its own task after an unanswered cooperative Back, with lifecycle/focus cancellation. The paired Auxio child carries a newer Pause into the no-saved-session fallback too.

Diagnostics v3 includes NavRadio+/stock-radio context, secondary processes, resource pressure/frequency/thermal streams and deferred input/kernel/idle/jobs/services/audio-route probes. The fixed profile, one source/task owner, first-minute lightweight capture, quiet windows, eight-minute bound and zero generated output under /data/adb remain. See scripts/diagnostics/coldstart/START-HERE.md. These are software fixes and evidence facilities, not a claim of physical startup qualification.
