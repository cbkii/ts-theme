# Authorised execution revision — 12 November 2026

This section and the preserved comparison are historical programme context. Current implementation authority is [the documentation index](README.md), [architecture](ARCHITECTURE.md) and [continuous readiness](CONTINUOUS_READINESS.md); the original defect/status tables below are not an outstanding-work list. The following decisions retain their historical scope except where explicitly updated here. Selective subsystem consolidation is approved; this is an implementation programme, not a rewrite.

## Outcomes and decisions

Priorities: automatic Maps → Music → Radio readiness; first-tap playback; dependable N2/warm-task reuse and HOME/fullscreen return; responsive UI; correct source state/handover; efficient owners and preserved settings/recovery.

D1: M2 is the intended TESTING default per source when source/automated evidence supports it. Explicit Legacy settings remain a reversible compatibility path until their responsibilities are replaced. D2: exact-package MediaBrowser-owned tokens do not need notification access; unrelated listener sessions still do. D3: remove inert n3host and unused dependencies. D4: finish a bounded API29 N3 assessment; N2 remains primary.

Physical TS18 qualification is deferred until the consolidated candidate. NOT_RUN is not an implementation blocker. Unknown private contracts stay capability-gated; independently executable work continues. Do not merge PR44, publish production, or modify protected OEM software.

## Execution and acceptance

| Phase | Work and automated acceptance |
|---|---|
| A | Reconcile PR44/current Auxio dev. Bound HOME_PLAY_V1 throughout deferred restoration, including autoplay override. Plain PLAY only for confirmed loaded sessions; unsupported cold Play is rejected. Validate browser provenance, revocation and token lifecycle. Investigate Radio/XTService from matching evidence. |
| B | Default M2, independent readiness/handover and idle unused legacy observers. Prefer silent preparation; permit the authorised bounded drawn-mask transaction for explicit NavRadio demand before any transport dispatch. Passive tuner activation stays gated. Remove architecture only with replacement coverage. |
| C | Preserve warm tasks, permission/settings flows and unrelated foreground; inconclusive task/focus results never authorise relaunch. Consolidate proven broker verbs, retain narrowly justified compatibility fallback. Remove n3host; bound N3 assessment. |
| D | Background component catalogue/icons; aliases and component shortcuts; v1/v2 import compatibility and deterministic fallback. Bounded local diagnostics and behavioural regression coverage. |
| E | Combined review, relevant host/JVM/lint/build/API29 CI; final-head signed TESTING provenance; one beginner-friendly lifecycle qualification package. |

HOME_PLAY_V1 uses elapsedRealtime deadlines. Accepted dispatch without observed playback is SENT_UNCONFIRMED, never a rejection or retry instruction. Do not pause the opposite source before replacement playback is observed. A bind alone does not establish Media3 interoperability or safe tuner readiness. Unsupported background Radio activation may use the implemented, source-gated masked Activity fallback on explicit Play. It must not require manual priming, replay uncertain transport, use a guessed plain bind or launch unmasked. Exact installed-build/tuner acceptance remains separate.

Qualification states are IMPLEMENTED, STATIC VERIFIED, UNIT TESTED, EMULATOR VERIFIED, REFERENCE SUPPORTED, TS18 NOT_RUN and prerequisite-specific BLOCKED. Claim only executed checks. MediaSession PLAYING does not prove audible output; screen recordings provide visual evidence unless audio capture is separately verified. Android reboot, cold power and ACC are separate.

Reuse the pinned comparison/research below; inspect narrowly and test proportionately. Final source/CI/artifact/runtime states must be reported separately. The following original review is retained as historical research, not current execution authority.

---

# ts-theme implementation plan: architecture review and source comparison (12 November 2026)

Scope: `cbkii/ts-theme` (standalone launcher `com.cbkii.ts18launcher`) for CB's Topway TS18, Android 10 / API 29, 1280×720.
This is analysis and planning only. Nothing was implemented, committed, pushed, merged, released or installed.

Evidence labels used throughout:

- **[SRC]** read in source at the pinned commit.
- **[CI]** observed in a GitHub check on the stated head.
- **[EMU]** proven on the API 29 emulator by the repo's own CI probe.
- **[DOC]** stated by a repo or project document (historical unless re-matched).
- **[INF]** my inference from source.
- **[HYP]** hypothesis that needs a named test.
- **NOT_RUN** no physical TS18 evidence exists for the current candidate.

---

## 0. Recommended direction

**Selective subsystem replacement. Not a rewrite, and not more parallel engines.**

The launcher's UI layer, the M2 Direct media engine, the root task broker and the Organic Maps compact-startup contract are sound and should stay. The reliability and performance cost sits in three places where a newer mechanism was added beside an older one and both still run:

| Area | Older path still running | Newer path already in the tree | Decision |
|---|---|---|---|
| Media | M1 Legacy: three observers, a 1 s visible poll, Activity foreground staging at boot (≈3,600 + 1,600 lines) | M2 Direct: one registry, event-driven, PREPARE-based readiness (≈2,150 lines) | Finish three M2 gaps, qualify, make M2 the default, then delete M1 and the staging coordinator |
| Navigation task control | 1,260-line shell helper, one `su` spawn plus `dumpsys` parsing per action | Persistent root broker using `IActivityTaskManager` directly, wired for only four actions | Move every action to the broker, then delete the shell helper |
| Navigation hosting | N2 freeform task on display 0 (the shape stock DoFun itself uses) | N3 private virtual display (view-only on API 29) plus an inert privileged host APK | Keep N2 as the product path. Freeze N3 behind one decisive spike. Remove the `n3host` module |

Highest-value improvements, in order:

1. **Bound cold Play in M2.** M2 sends a plain `PLAY` to a cold Auxio-TS, which restores with no deadline. Audio can start after the launcher has already told the driver it failed. Auxio-TS already has a deadline-bound action; M2 does not use it.
2. **Give M2 a Radio cold-start path** that needs no root, no foreground-service start and no Activity: a plain bind to NavRadio+'s exported session service.
3. **Make M2 the default and delete M1 plus foreground staging.** This removes Activity launches at boot, the 1 s poll, duplicate session observers and most startup root spawns.
4. **Run all navigation task operations through the existing broker.**
5. **Fix the app library:** component-aware targets, one asynchronous catalogue, no main-thread icon decode.
6. **Resolve the Topway callback-slot question by static analysis** of the exact service APK before any on-device observation.

What I would not do: add another media engine, grow N3, add a toolkit (Compose, AndroidX, Kotlin), or tune startup before the first TS18 timeline exists.

Styling impact of the core plan: **none**. Every work package below keeps the current layout, colours, typography, icons, dimensions and animation. Optional visible changes are listed separately in section 9.

---

## 1. Current baseline

### 1.1 Candidate and heads (resolved live, 12 Nov 2026)

| Item | Value | Source |
|---|---|---|
| Default branch | `main` @ `2cd0402` | GitHub API |
| Only open PR | **#44** `integration/ts18-candidate` → `main`, head `30f4a8d868dfeff4b9a8840facfefa681466709e`, 107 commits, 350 files, mergeable, 0 unresolved threads | GitHub API |
| Canonical check | "Source, workflow and Android builds" **success** on `30f4a8d`; owner-requested TESTING snapshot built and published for the same head | **[CI]** |
| PR #46 (M2 passive PREPARE) | **Included.** Its head `f7f776b` is an ancestor of `30f4a8d` | `git merge-base --is-ancestor` |
| PRs #32–#43, #45, #47–#51 | All heads are ancestors of `30f4a8d`. #32–#45 closed by the owner as consolidated; #46–#51 merged into the integration branch | same |
| Review of #44 itself | Its only review entry is Sourcery declining on size (350 files > 300 limit); cubic neutral; CodeRabbit skipped. **The combined diff has had no line review on this PR** | PR #44 reviews and check runs |

**Most complete coherent candidate: PR #44 @ `30f4a8d`.** No local composition was needed. All findings below are against that head unless stated.

### 1.2 What exists, by status

| Capability | Status at `30f4a8d` |
|---|---|
| HOME UI, rail, media strip, drawer, settings, appearance | Implemented and runtime-wired |
| M1 Legacy media (default) | Implemented and wired; last physical evidence predates this stack |
| M2 Direct media (per-source opt-in) | Implemented and wired; warm control, browser cold start, passive PREPARE. **[EMU]** cross-process proof for PREPARE, Play, source death and retry. Physical NOT_RUN |
| N0 fullscreen, N2 native freeform window (TESTING default) | Implemented and wired; physical qualification of this stack NOT_RUN |
| Organic Maps compact-startup contract (authenticated provider pair) | Implemented both sides; Maps side is in the draft release `in-car-v2.2.0` |
| N3 private virtual display | Implemented, default off. **[EMU]** embeddable fixture renders; non-embeddable target refused; **input view-only** |
| `n3host` privileged display host | Skeleton only: inert by default, never installed, and its input path does not exist on API 29 |
| Topway read-only capability check | Implemented; "observe" mode gated behind a default-off acknowledgement. Callback storage semantics unknown |
| Native Topway radio | Blocked by design (`TopwayNativeRadio`) |
| Startup timeline and campaign comparison | Implemented; no TS18 capture exists |
| Setup checklist | Implemented |
| Component-aware shortcuts, icon cache, hide/sort | Not implemented (ledger WP10 PARTIAL) |

### 1.3 Size of each subsystem (main sources, lines)

| Subsystem | Lines |
|---|---|
| M1 Legacy media | 3,591 |
| Startup foreground staging (coordinator, policies, loader, root fallback) | 1,608 |
| M2 Direct engine and `media/` contract | 2,147 |
| N1/N2 controller, root helper, broker | 2,393 Java + 1,260 shell |
| Compact startup client/consent | 585 |
| N3 (launcher + shared) / `n3host` | 1,771 / 391 |
| OEM and platform adapters | 965 |
| HOME, drawer, settings, appearance UI | 4,236 |

Retiring M1 and foreground staging removes about 5,200 lines and the shell helper another 1,260, against roughly 400–600 added. That is a consequence, not the goal; the goal is one owner per behaviour.

---

## 2. Source comparison matrix

All reference commits were fetched on 12 Nov 2026. "Design only" means the mechanism is to be implemented independently in this repo's Java/Views envelope; no code is taken. Licence notes are for a routine third-party review before any verbatim reuse.

| # | ts-theme mechanism (file @ `30f4a8d`) | Reference mechanism (pinned) | Decision | Expected benefit | Compatibility / authority cost | Confidence |
|---|---|---|---|---|---|---|
| A | M2 cold Play: `DirectSourceEngine.continueStart()` sends plain `PLAY` (`media/direct/DirectSourceEngine.java` L384) | **Auxio-TS** `f36fee4`: `onCustomAction("…HOME_PLAY_V1")` validates `protocol=1` and a bounded `deadline_elapsed_ms`, then restores and plays only inside the window (`MediaSessionInterface.kt` L233–249). Plain `onPlay()` with no current media calls `startColdRestore(play = true)` with no deadline | **ADOPT** (own companion contract) | No late audio after a reported failure | Needs the session to advertise the capability so M2 stays source-neutral; small Auxio-TS change | High [SRC both sides] |
| B | M2 session source: notification-listener only; without access M2 reports `PERMISSION_NEEDED` and drops the browser link (`DirectSourceEngine.java` L175–185) | **AOSP car-media-common** `1a6b9b2` (android10-release): `MediaBrowserConnector` connects to the selected source's browse service, ignores obsolete callbacks with a sequence counter (L60–71), and the controller is built from `browser.getSessionToken()` (`MediaSourceViewModel.java` L156). No listener access involved | **ADAPT** (owner decision D2) | Music control no longer depends on listener access; source death arrives as `onConnectionSuspended` | API 29 public API only. Changes a stated repo rule ("listener access is mandatory") | High for mechanism [SRC]; policy is the owner's |
| C | M2 Radio cold start: none. NavRadio+ has no browse service, so M2 can only control a running session. M1 starts it with root `am start-foreground-service` (`MediaSourceAdapter.java` L89; `MediaSourceBootstrapper.java` L692) | Plain `bindService` to the exported session service (Android public API). A foreground-service start obliges the target to call `startForeground` in time; a bind does not | **ADAPT** | Radio one-tap Play under M2 with no root and no FGS contract on the target | Must read the installed NavRadio+ manifest and prove creating the service does not grab the tuner [HYP] | Medium |
| D | Session choice: M2 exact package then one token, else AMBIGUOUS (`DirectSessionSelector.java`) | **car-launcher** `0f14c7b` `chooseSession()`: "a playing app beats a paused preferred one" (`feature/media/MediaSessionSelection.kt`); **GTS-Launcher** `7af683f` `MediaMonitor` and **CleanDash** `5c0e66f` `NowPlaying` follow whichever session is playing | **REJECT** | — | Violates exact configured source identity | High [SRC] |
| E | Play/Pause: directional, resolved at tap, confirmed by the owning session | **car-launcher** `MediaSessionController.playPause()` toggles on last-read state | **REJECT** (ts-theme is stricter) | — | — | High |
| F | Boot classification: `Settings.Global.BOOT_COUNT` journal in `StartupReadinessCoordinator.onTrigger` | **device-reveng** `b5c07f6` `ColdBootResume`: once per boot via `BOOT_COUNT`, journal written before launch; but it starts the player Activity, waits a fixed 3 s and sends a global media Play key | **LEARN** (journal-before-act already matched); **REJECT** the foreground launch, fixed dwell and global key | Confirms the journal pattern | — | High |
| G | N2: real nav task, freeform mode 5 on display 0, placed by root | **Dashwheel** `a1f6f26` `SplitLauncher.kt`: freeform fallback, and a recorded OEM failure where split left *HOME itself* a floating window until reinstall | **LEARN** (negative example) | Add a HOME-windowing self-check to N2 | — | Medium (other OEM) |
| H | N3 public path: launcher-owned private display, launch needs target `allowEmbedded`; input unavailable | **AOSP frameworks/base** android10 `37a24f5`: `ActivityView` reparents display content into the host window (`ActivityView.java` L497–524); the server checks only display ownership (`WindowManagerService.java` L6739–6751). `IWindowSession.reparentDisplayContent` (aidl L273) is **not** in any API 29 greylist file | **LEARN** | Explains the CI `NoSuchMethodException`; the true transport is blocked to ordinary apps by the hidden-API list, not by a permission | Needs a hidden-API exemption on a fixed API 29 device, or a different transport | High [SRC] |
| I | N3 generic targets: needs `n3host` as a privileged app | **open-car-project** `8551c34` `EmbeddedAppSession.kt`: launcher owns the display; a root shell starts the app on the main screen then runs `am display move-stack <stack> <display>`; touches go through a root `app_process` helper that calls `InputManager.injectInputEvent` with the event's display id (`TouchInjector.kt`, `TouchLine.kt`, tests `TouchLineTest`, `StackListTest`). **FYT-Launcher-Mod** `65fbd59` `VdHostCore.java`: privileged process owns the display; documents that a new display takes top focus and schedules focus hand-back at 400 ms and 1.5 s (L633–672) | **LEARN / spike only** | A host with authority the unit already has (Magisk root + existing broker), no privileged APK | Root; display-scoped injection; focus hand-back; no IME on the private display. Donor ROMs differ from TS18 | Medium for mechanism; Low for TS18 |
| J | N3 as a product surface | **AOSP Car Launcher** android10 `c1ac502` `CarLauncher.java` L46–50: because the hosted Maps Activity lives on a virtual display, "launching the maps Activity with a direct Intent will not work"; every other entry point must be routed through the launcher | **REJECT as default** | Avoids breaking the Topway NAVI key, voice, `geo:` links and notifications, which target the task | — | High (first-party statement) |
| K | `n3host` module | FYT's host runs as uid 1000 daemon, Shizuku (uid 2000) or a system launcher. None is available on the TS18 without a privileged install | **REJECT / remove** | Less dead code and CI time | — | High |
| L | App catalogue: one entry per *package*; shortcuts store a package (`AppDrawerPanel.java` L369; `ShortcutSlot.java`) | **dashline** `64ef23f` `AppRepository.kt`: a saved "target" is either a bare package (old form) or a flattened component; every function accepts both; one entry per icon. Its comment records the exact bug of collapsing by package | **ADAPT** (design only, GPL-3.0) | Multi-icon vendor APKs appear correctly; old settings stay valid with no migration | None | High [SRC] |
| M | Hide / rename / sort | **cabin** `8263178` `LauncherAppLibrary.kt` (Unlicense): per-component hidden set, names, usage | **LEARN**; hide only, in the optional section | — | Needs a visible affordance | Medium |
| N | Performance evidence: `StartupTimeline` only | **car-launcher** `core/performance/PerformanceMonitor.kt` + `PerformanceBudgets.kt`: opt-in Choreographer frame-interval sampling, PSS snapshot, cold-launch and warm-return classification into Healthy / Advisory / Investigate | **ADAPT** (design only, AGPL-3.0) | Comparable frame, memory and warm-return numbers from the production path | ~150 lines of Java; sampling only on request | High |
| O | Topway command service: descriptor check; optional temporary status callback (`oem/TopwayStatusProbe.java`) | **GTS-Launcher** `TwCommand.java`: same service and version name (2022.02.17), same codes 1/2/67/8; registers a status callback continuously from an ordinary app and unregisters by passing the same binder. **t-music** `2723537`: the obfuscated stock client stub has paired one-argument methods per callback family, which reads as register/unregister [INF] | **LEARN** | Corroborates codes; hints at list-like storage | GTS is a TS10; t-music targets an Android 13 TS18 variant. Neither is the exact TS18 service bytes | Medium |
| P | Native radio: blocked | **litedash** `33a35d4` `TopWayRadio.kt`: tuner and audio focus through `TWUtil.write`. It cites **ivvlev/CarRadio** `d7a10fb` for "radio = 1 on TS18", but that project states MST768 / Android 7.1 | **REJECT** | — | A second tuner writer beside NavRadio+; provenance is not TS18 | High |
| Q | Metadata for sources with no session | **litedash** `BroadcastMeta.kt`: passive broadcast list (AVRCP, vendor BT apps) | **REJECT for now** | — | No Topway action observed on this unit | Medium |
| R | First run | **device-reveng** `FirstRunGate.kt`: never raise a permission dialog on first run | Already matched by `SetupActivity` (offered after first draw) | — | — | High |

Triage only, not investigated further:

- **KswPLauncher, ZxwCustomerUI, ZxwLauncher3QuickStepKsw**: decompiled OEM dumps for the ZXW/KSW platform, no licence, different vendor services. No concrete unresolved ts-theme problem maps to them.
- **byd-split-launcher** `401be12`: reflection split-screen with a fixed 8 s boot delay and force-stop as the only exit. Negative example.
- **openlauncher** `8ee7d3a`: Compose launcher; follows the active session. Nothing better than the items above.

References added by this review, and why:

- **AOSP frameworks/base android10-release** (mirror `37a24f5`): the first-party answer to "how does API 29 actually embed and route input".
- **AOSP Car libs `car-media-common` android10-release** (`1a6b9b2`): the first-party pattern for "one selected media source, controlled through its browse connection".
- **ivvlev/CarRadio** (`d7a10fb`): checked only to test litedash's provenance claim; it does not support it.

---

## 3. Prioritised findings

### 3.1 Confirmed static defects (source-proven at `30f4a8d`)

| ID | Finding | Evidence | Effect |
|---|---|---|---|
| S1 | **M2 cold Play is unbounded on the source side.** M2 sends `PLAY`; Auxio-TS then restores with no deadline, while M2 gives up after `PLAY_ACK_MS` = 5 s | `DirectSourceEngine.java` L384; Auxio-TS `MediaSessionInterface.kt` `onPlay()` cold path; M1 alone uses `HOME_PLAY_V1` (`MediaSourceBootstrapper.java` L1090) | Possible audio start after a reported failure. **Earliest causal gap for M2 becoming default** |
| S2 | **M2 cannot cold-start Radio.** The only starter resolves `android.media.browse.MediaBrowserService`; the repo records NavRadio+ as exposing a Media3 session service instead **[DOC]** (installed build to be confirmed by IP-02) | `BrowserSessionStarter.java` `resolve()`; `MediaSourceAdapter.java` NavRadio branch | Under M2, Radio needs the app opened first. Blocks M2/M2 |
| S3 | **Legacy listener observes even when no source uses M1.** `MediaListenerService` registers its membership listener and controller callbacks on connect with no engine check; only `ProcessMediaSessionMonitor` is demand-gated | `MediaListenerService.java` L216; `Ts18LauncherApplication.java` `setDemand` | Duplicate observation and Binder traffic in M2/M2 |
| S4 | **Four independent session observers** exist in one process: `MediaListenerService`, `ProcessMediaSessionMonitor`, `MediaSourceBootstrapper`, `FrameworkSessions` | each calls `getActiveSessions` / `registerCallback` | Competing views of the same sessions |
| S5 | **A 1 s poll runs while HOME is visible** in M1, although `docs/ARCHITECTURE.md` says "no periodic timer is scheduled" | `ProcessMediaSessionMonitor.java` L82–88 | Constant wake-ups and Binder reads; doc contradicts code |
| S6 | **M1 startup foregrounds source Activities** (Auxio-TS and NavRadio+) under a mask, then restores HOME, and defers Maps while it does | `StartupReadinessCoordinator.java` L423–507 | Conflicts with "do not foreground media merely to make it ready"; couples Maps start to media |
| S7 | **Foreground observation can spawn `su` + two `dumpsys` per read**, retried at 200 ms | `StartupReadinessCoordinator.java` L739–759 | Process spawns on the startup path |
| S8 | **Two root navigation implementations.** The shell helper handles `present-native`, `verify-native`, `status`, `fullscreen`, `park-windowed`, `startup-source`; the broker handles only `resume-windowed`, `return-home`, `background-fullscreen`, `startup-home` | `NavigationRootHelper.java` L86–98; `assets/nav/nav-window.sh` (1,260 lines, 7 `dumpsys` call sites) | Each shell action is a new `su` process tree plus text parsing |
| S9 | **App picker loads on the main thread** and decodes icons inside `getView` | `AppDrawerActivity.java` L142, L186–203, L280 | UI stall when choosing an app; scroll jank |
| S10 | **Catalogue collapses to one entry per package**; shortcuts and roles store a package only | `AppDrawerPanel.java` L369; `ShortcutSlot.java`; `AppResolver.java` | Multi-entry vendor APKs lose icons; a shortcut cannot name a specific Activity |
| S11 | **Rail quick-slot icons decode on the main thread during first HOME build** | `LauncherActivity.java` L407 → `ShortcutSlot.java` L55 | Cross-APK resource decode before first frame (size unmeasured) |
| S12 | **Dead and frozen code**: `LauncherActivity.appDrawerPanel` is never assigned but still branched on; `TestingProfiles` returns constants for five of its six "profiles" | `LauncherActivity.java` L67; `TestingProfiles.java` | Misleading branches |
| S13 | **Config import is all-or-nothing on unknown keys** and only accepts `version == 1` | `ConfigurationCodec.java` L16, L142 | A file exported by a newer build cannot be imported by an older one at all |
| S14 | **Docs are stale against code**: `ARCHITECTURE.md` still describes the retired `theme/` lane and Leaflet map; `AGENTS.md` layers six dated addenda over a 16 Oct baseline | `docs/ARCHITECTURE.md`; `AGENTS.md` | New work starts from wrong premises |
| S15 | **Test suite is dominated by source-text assertions**: 62 Python modules read repository source, with about 1,300 `assertIn`/`assertNotIn`/`assertRegex` calls, against 48 JVM test files and one emulator suite | `tests/*.py` | Refactoring is expensive; wording is protected more than behaviour |

### 3.2 Observed runtime behaviour

| ID | Observation | Level |
|---|---|---|
| R1 | M2 browser starter: PREPARE loads without Play, Play confirmed over IPC, reconnect, source death reported, retry works | **[EMU]** on the integration head |
| R2 | N3: embeddable fixture pixels observed on the private display; non-embeddable target refused; input forwarder absent | **[EMU]** |
| R3 | Stock DoFun presented Organic Maps as a mode-5 freeform task on display 0; PR11 build rendered bounded | **[DOC]** historical TS18 captures (Sept 2026) |
| R4 | Owner-reported repeated cold-readiness failures for Music, Radio and Maps on the PR #17 candidate | **[DOC]** historical; not re-tested on this stack |

There is **no physical observation of any kind for `30f4a8d`**.

### 3.3 Hypotheses (each has a named test in section 6)

| ID | Hypothesis | Test |
|---|---|---|
| H1 | Binding NavRadio+'s session service creates its session without taking the tuner or audio | T-RADIO-BIND |
| H2 | M1's Activity staging is no longer needed once Auxio-TS v26.10.0 handles PREPARE on a real cold boot | T-PREPARE-BOOT |
| H3 | Shell-helper actions cost hundreds of milliseconds each on this SoC; broker actions cost tens | T-NAV-COST |
| H4 | XTService stores status callbacks in a list, so a second registrant does not displace Auxio-TS or stock apps | T-XT-STATIC |
| H5 | A root-moved Organic Maps stack renders and takes input on a launcher-owned private display on this ROM | T-N3-SPIKE |

### 3.4 Qualification gaps

Everything in the ledger's physical matrices is NOT_RUN: warm and cold media on all four engine pairings, passive readiness after real cold boot and real ACC wake, N2 regressions, lifecycle boundaries and OEM observation.

### 3.5 Causal order

1. **Earliest gap overall:** no TS18 timeline or media/nav capture for the current candidate. Startup tuning and any speed claim are BLOCKED on it.
2. **Earliest gap for M2 default:** S1, then S2. M1 retirement (S3–S7) is BLOCKED on M2 default.
3. **Earliest gap for N3:** input transport and embedding authority. `n3host` is BLOCKED on both and on a privileged install.
4. **Earliest gap for any Topway observation:** callback storage semantics (H4).

---

## 4. Target architecture

### 4.1 Ownership

| Concern | Single owner | Notes |
|---|---|---|
| Playback, queue, session, audio focus | The playback app | Unchanged |
| Session observation and transport for a source | `media/direct` engine, one per source, one shared registry | M1 removed after gate G2 |
| Getting a session to exist | A `Starter` chosen per source: `BrowserStarter` (Auxio-TS), `BoundServiceStarter` (NavRadio+), none (session-only apps) | No root, no Activity, no FGS start |
| Boot / wake / HOME classification, passive-prepare epochs, navigation admission | `StartupOrchestrator` (what remains of the coordinator, about 250 lines) | No Activity launches, no mask, no media/Maps coupling |
| Navigation task placement | `NavigationWindowController` → root broker only | Shell helper removed after gate G3 |
| Navigation cold-launch admission | Compact-startup contract with Organic Maps | Unchanged |
| App catalogue, labels, icons | `AppCatalog` (process-level, asynchronous, cached) | Drawer, picker, rail and settings read it |
| Persistent settings | `LauncherPrefs` + `ConfigurationCodec` v2 | Targets accept package or component |
| OEM observation | `oem/` read-only, behind the existing gate | No new writers |
| Timing and performance evidence | `StartupTimeline` + opt-in `PerfSampler` | Emits under existing log tags; nothing persisted or uploaded |

### 4.2 Event flow

```
            BOOT_COMPLETED / process start / USER_UNLOCKED / SCREEN_ON / HOME resume / storage mount
                                              │
                                   ┌──────────▼──────────┐
                                   │  StartupOrchestrator │  classifies kind (BOOT_COUNT journal),
                                   │  (main thread only)  │  issues epoch N, never launches an Activity
                                   └───┬─────────────┬────┘
                 preparePassive(N)     │             │   navigation.reconcile()
                        ┌──────────────▼───┐     ┌───▼────────────────────────┐
                        │ MediaFeatureFacade│     │ NavigationWindowController │
                        │  Music    Radio   │     │  (one in-flight operation) │
                        └───┬─────────┬────┘     └───┬────────────────────────┘
                            │         │              │ typed request + epoch
                  ┌─────────▼──┐  ┌───▼────────┐  ┌──▼───────────────┐
                  │DirectSource│  │DirectSource│  │ RootTaskSession   │── pipes ──► NavTaskBridge
                  │Engine(Music│  │Engine(Radio│  │ (persistent su)   │            (IActivityTaskManager)
                  └──┬──────┬──┘  └──┬──────┬──┘  └───────────────────┘
        Starter      │      │        │      │      Starter
   BrowserStarter ◄──┘      │        │      └──► BoundServiceStarter
   (Auxio browse service)   ▼        ▼          (NavRadio+ session service)
                     DirectSessionRegistry  ◄── tokens offered by starters
                     (one membership source,     + notification-listener membership
                      one callback per token)      (fallback / session-only sources)
                            │
                            ▼  MediaSourceState (generation-tagged, immutable)
                     LauncherActivity renders the existing strip unchanged
```

### 4.3 State and lifecycle rules (already true in M2; to hold everywhere)

- Every asynchronous result carries the generation or epoch it was issued under; a stale one is dropped.
- One in-flight operation per owner; requests coalesce.
- Every wait has a deadline; a deadline ends the wait and never replays an accepted or uncertain action.
- Readiness is event-driven. No fixed dwell and no polling loop outside a bounded startup window.
- HOME renders before any optional dependency (root, listener access, storage, Maps) answers.

### 4.4 Boundaries kept separate

Process restart, HOME resume, screen-on, reboot and cold boot are classified from `BOOT_COUNT` and process start time. **A real ACC transition has no passive signal available to an ordinary app in the inspected scope**; screen-on stays "ACC unverified". The only candidate signal is XTService's sleep-status callback, which stays behind H4.

---

## 5. Work packages (dependency order)

Each package is one PR unless stated. "Styling impact" is **none** for all of them. Effort: S ≤ 1 day, M 2–4 days, L about a week of focused work. Roadmap mapping uses the WP0–WP12 ledger.

### Batch 0 — evidence and decisions (no launcher code)

**IP-00 · First TS18 baseline on the PR #44 TESTING build** (owner, physical) — maps WP3, WP11, WP12

- Problem: no timing or behaviour evidence exists for this candidate.
- Action: install the verified TESTING APK set already recorded on PR #44, then run `scripts/diagnostics/campaign` (startup phases), `scripts/termux/trace-home-performance.sh` and `collect-navigation-window-evidence.sh`. Five cold boots, five reboots, ten warm HOME returns, M1/M1 engines.
- Output: per-phase medians and worst values for `TS18Startup`; which of Music, Radio and Maps reached readiness unaided.
- Acceptance: archives sealed with hashes; collector failures reported as BLOCKED, not as target failures.
- Not blocking Batch 1.

**IP-01 · Static analysis of the exact XTService callback storage** (T-XT-STATIC) — maps WP7

- Problem: registering a status callback might displace another registrant.
- Action: pull the APK from the unit read-only (`pm path com.tw.service.xt`), confirm SHA-256 `341af03c…7267`, decompile `CommandService` and read the register/unregister implementation (`RemoteCallbackList` or a single field).
- Output: one paragraph in `docs/OEM_CAPABILITIES.md` with the class and field names.
- Acceptance: the observe gate text can state the semantics. No device state changes.
- Effort S. Risk none.

**IP-02 · Read the installed NavRadio+ service declaration** (read-only) — maps WP6

- Action: `dumpsys package com.navimods.radio` filtered to services and filters; record the package version and APK hash.
- Output: the exact component and actions IP-11 may bind to, or proof that there is none.

**Owner decisions needed before Batch 2** (none block Batch 1):

- **D1** M2 becomes the default engine once gate G2 passes.
- **D2** A source's own browse connection may pin its session without notification-listener access (matrix row B).
- **D3** Remove the `n3host` module now (recommended), or keep it parked.
- **D4** Whether the N3 spike (IP-23) is worth running at all, given matrix row J.

### Batch 1 — executable now

**IP-10 · Bounded cold Play for M2** — fixes S1 — maps WP6

- Target repos: `cbkii/Auxio-TS` (small), `cbkii/ts-theme`.
- Intended behaviour: a cold Play either starts audio inside the launcher's window or does nothing. The launcher never reports failure and then hears music.
- Contract `auxio.session.capabilities v1` (versioned, discoverable from the session, no package checks in M2):
  - Auxio-TS advertises the existing action through the session: add a `PlaybackStateCompat.CustomAction` named `org.oxycblt.auxio.action.HOME_PLAY_V1` (or a session extra `deadline_play_protocol=1`). The handler already exists and is unchanged.
- ts-theme changes:
  - `SessionObservation`: add an immutable set of advertised custom-action names (read in `FrameworkSessions.read`).
  - `DirectOperation`: add `PLAY_BY_DEADLINE`.
  - `DirectSourceEngine.continueStart()`: when the pinned session advertises the action, dispatch it with `protocol=1` and `deadline_elapsed_ms = now + remaining PLAY_ACK_MS`; otherwise send plain `PLAY` as today.
  - `FrameworkSessions.ControllerSession.dispatch`: `sendCustomAction` for the new operation.
- Reference: Auxio-TS `MediaSessionInterface.kt` L233–249 (handler), ts-theme `MediaSourceBootstrapper.java` L1080–1090 (the M1 caller, to be deleted later).
- Migration / rollback: capability absent → current behaviour. Revert is one commit per repo.
- Tests:
  - JVM: `DirectColdPlayDeadlineTest` (advertised → deadline action with correct remaining time; not advertised → `PLAY`; expired window → nothing dispatched).
  - Emulator: extend `FixtureBrowserService` to advertise the action and assert an expired deadline produces no playback.
  - Auxio-TS: unit test that the action appears in the published state.
- Acceptance: emulator probe prints a new `PASS … deadline Play honoured, expired Play ignored` line, added to the smoke script's required lines.
- Effort M. Risk low.

**IP-11 · Bound-service starter for session-service sources** — fixes S2 — maps WP6

- Depends on IP-02.
- Intended behaviour: with Radio on M2, one tap on Radio Play starts NavRadio+ without opening it.
- Changes (all in `media/direct`):
  - Generalise `DirectSourceEngine.Starter` resolution into a small `StarterSet`: try `BrowserSessionStarter`; else `BoundServiceStarter`.
  - `BoundServiceStarter`: resolve exactly one enabled, exported service of the configured package that declares `androidx.media3.session.MediaSessionService`; `bindService(BIND_AUTO_CREATE)`; hold the binding while a source owns it; do not speak the binder. The session then arrives through registry membership. Same attempt identity, `abandon` and `release` rules as the browser starter.
  - A bound start is **tap-only** until T-RADIO-BIND passes; passive prepare skips it (`canPreparePassively() == false`).
- What must change relative to references: nothing is reused; AOSP's connector pattern (row B) supplies the stale-callback rule already present.
- Dependency note: this path needs listener access to see the session, because a plain binder client gets no session token from a Media3 session service **[INF]**.
- Rollback: delete the class from the set.
- Tests: JVM with a fake connector (single service, two services → refuse, unbind on release, late connect after abandon ignored); emulator fixture service with that action that creates a session in `onCreate`.
- Physical gate **T-RADIO-BIND** (extends `scripts/termux/qualify-navradio-service-start.sh`, read-only observation): while Music plays, bind; assert Music keeps playing, Topway source unchanged, no tuner audio, session appears; then Play and confirm audible radio and the Music handover.
- Effort M. Risk medium (target behaviour unknown until the gate).

**IP-12 · Idle the legacy listener when unused; cache default-package lookups** — fixes S3, part of S4

- `MediaListenerService`: register membership and callbacks only while some source is on M1 (reuse the `setDemand` signal); keep the component itself, because M2 uses its identity for listener access.
- `RadioProvider.resolvePackage` / `TopwayAdapter.defaultMusicPackage`: memoise, invalidated by the existing package-change broadcast.
- Tests: Robolectric: with both engines Direct, no `addOnActiveSessionsChangedListener` call from the legacy service.
- Effort S. Risk low.

**IP-13 · One asynchronous app catalogue with component targets** — fixes S9–S12 — maps WP10

- New `AppCatalog` (application-scoped):
  - One background load: `queryIntentActivities(MAIN/LAUNCHER)`, **one entry per Activity**, label via `ResolveInfo.loadLabel`, sorted with a `Collator`.
  - Icons loaded lazily per visible entry on the loader thread, scaled once to the grid cell size, held in an `LruCache` sized by bytes; cleared in `onTrimMemory`.
  - Package add/remove/change updates only the affected package's entries.
- **Target format** (design from dashline, row L): a stored value is either `pkg` (existing data, still valid) or `pkg/cls`. `AppResolver.launch(target)`, `ShortcutSlot`, `RoleIconCatalog` fallbacks and `ConfigurationCodec` accept both. Picking from the drawer stores `pkg/cls` only when the package has more than one launcher entry, so existing exports stay byte-compatible in the common case.
- Fallback rule: stored component missing → the package's default entry → the role default; never a silent different app.
- `AppDrawerActivity` pick mode uses the same panel and catalogue (delete its private `loadEntries` and adapter); remove the dead `LauncherActivity.appDrawerPanel` field and branches; rail slot icons come from the cache with the role glyph shown until ready.
- `ConfigurationCodec`: accept `PACKAGE` values matching package or component syntax (stays `version 1`; old builds reject only files that actually contain a component).
- Styling: grid, sizes and order rules unchanged. The only visible difference is that apps with several launcher icons show all of them.
- Tests: Robolectric with shadow PackageManager (two activities in one package → two entries; removal updates one package; target fallback chain); JVM codec round-trip for both forms; emulator: picker opens with zero `StrictMode` disk/Binder violations on the main thread.
- Effort M–L. Risk low.

**IP-14 · Broker parity for navigation task actions, in shadow mode** — prepares S8 — maps WP8 (N2)

- Add typed broker verbs in `NavTaskBridge` for `status`, `verify-native`, `present-native` (adopt-or-start-once with the existing claim rules), `fullscreen`, `park-windowed`, using `getAllStackInfos`, `setTaskWindowingMode`, `resizeTask`, `setFocusedTask` already resolved there (L189–195).
- Add **HOME windowing self-check** to `status`: HOME's own task must be fullscreen on display 0; otherwise report `HOME_NOT_FULLSCREEN` and do not present (lesson from row G).
- Shadow mode: for read-only verbs (`status`, `verify-native`) run both implementations and log disagreement under `TS18Nav`; mutations still go through the shell helper in this PR.
- Tests: extend `NavTaskBridge` parser tests with the existing dump fixtures; host test that every helper action has a broker verb.
- Effort M. Risk low (no behaviour change yet).

**IP-15 · Opt-in performance sampler and timeline additions** — maps WP3, WP11

- `PerfSampler` (design from row N): on request from Settings → Diagnostics or an explicit broadcast used by the campaign, sample Choreographer frame intervals for a bounded window (default 10 s), read one PSS snapshot, and emit one `TS18Perf` line: frames, >32 ms count, >100 ms count, PSS, process CPU time.
- `StartupTimeline`: add `home.first_input` (first `onUserInteraction` after first draw), `media.music.loaded`, `media.radio.live`, `nav.window.verified`.
- Campaign: parse `TS18Perf`; compare runs by median and worst.
- No always-on sampling; nothing persisted.
- Tests: JVM with injected clock and frame source.
- Effort S–M. Risk low.

### Batch 2 — after gates

**IP-20 · M2 default for Music, then Radio** — maps WP4–WP6 — needs D1

- Gate **G2-Music**: T-PREPARE-BOOT and the M2 warm/cold matrix pass on the TS18 with Auxio-TS v26.10.0 (currently a draft release) or later. Gate **G2-Radio**: T-RADIO-BIND passes.
- Change: default engine per source flips to Direct when the stored choice is unset; an explicit stored Legacy choice is respected. M1 stays selectable for one TESTING cycle as rollback.
- Tests: selector defaults; config import of both values.
- Effort S.

**IP-21 · Retire M1 and foreground staging; reduce the coordinator** — fixes S4–S7

- Precondition: one full TESTING cycle on M2 defaults with no rollback used.
- Delete: `MediaSourceBootstrapper`, `ProcessMediaSessionMonitor`, the legacy half of `MediaListenerService` (keep a minimal listener component), `LegacyMediaBackend`, `MediaSelection`, `MediaSourceAdapter`, `SessionControllerRegistry`, staging policies, `StartupReadinessLoader`, `StartupMaskFocus`, `StartupRootFallback`, `AuxioPreparationContract`, `TestingProfiles`, the `startup-source` / `startup-home` helper actions, the `SYSTEM_ALERT_WINDOW` permission if nothing else uses it, and the engine selector UI.
- Replace `StartupReadinessCoordinator` with `StartupOrchestrator`: trigger classification, boot journal, passive-prepare epoch, navigation reconcile. Navigation no longer has a "defer for media" input.
- Stored `legacy` engine values and the `media.selection.mode` key become upgrade input only (accepted on import, never stored).
- Rollback: the previous TESTING APK; settings remain readable both ways.
- Tests: the existing M2 suites become the only media suites; new JVM tests for the orchestrator (trigger → epoch, new boot resets once, late `BOOT_COMPLETED` does not double-reset).
- Effort L. Risk medium, contained by the gate.

**IP-22 · Remove the shell navigation helper** — fixes S8

- Gate **G3**: IP-14 shadow logs from at least one TS18 campaign show no disagreement, and T-NAV-COST shows the broker is not slower.
- Route all mutations through the broker; delete `assets/nav/nav-window.sh`, the install-by-copy step and `NavWindowProbe`'s dumpsys fallback.
- Fallback when the broker cannot start: N0 fullscreen with the existing truthful message. No second mechanism.
- Effort M. Risk medium.

**IP-23 · N3 decision spike (time-boxed), then keep or remove** — maps WP8, WP9 — needs D3, D4

- Step 1 (emulator, CI probe only, no product code):
  - a. `adb shell am display move-stack` of a **non-embeddable** fixture onto the launcher's private display → pixels observed?
  - b. display-scoped injection from shell (`input -d <id> tap`) reaches it?
  - c. focus returns to HOME after hand-back?
- Step 2 (TS18, only if step 1 passes): the same three checks with Organic Maps through a temporary broker verb, plus: does the Topway NAVI key or a `geo:` intent still reach Maps (row J)?
- Decision rule:
  - Any of 1a–1c fails, or the NAVI/intents check fails → **remove N3 entirely** (`navigation/n3`, `shared/n3`, `N3Activity`, trampoline, emulator probe).
  - All pass **and** N2 has a physically observed defect that N3 fixes → plan a root-brokered N3 as a separate project.
  - Otherwise N3 stays frozen and default-off.
- Independently of the spike: remove `n3host`, `HostDisplayPath`, the AIDL pair and `N3HostPolicy` (matrix row K), unless D3 says park.
- Effort S for removal, M for the spike.

**IP-24 · Behavioural tests for refactored areas; one rules test; docs consolidation** — fixes S14, S15

- For each subsystem touched by IP-13, IP-21, IP-22: replace its source-text Python guards with JVM/Robolectric behaviour tests in the same PR that changes the code.
- Keep one `test_architecture_rules.py` for envelope invariants only (no network permission, no AndroidX/Kotlin, `media/direct` imports, one-DEX release).
- Rewrite `docs/ARCHITECTURE.md` from the code; collapse `AGENTS.md` to current rules; move dated reports to `docs/history/`.
- Effort M, spread across the PRs above.

**IP-25 · Config v2 and tolerant import** — fixes S13

- `version: 2` adds nothing structural; import of v1 and v2 both accepted. Unknown keys are skipped and listed in the preview instead of failing the file. Retired keys stay accepted and unstored.
- Effort S.

### Roadmap mapping

| Ledger item | Carried by |
|---|---|
| WP1 reference adoption (remaining references) | This document, section 2 |
| WP3 startup measurement and orchestration | IP-00, IP-15, IP-21 |
| WP4 M1/M2 seam | IP-20 then removed by IP-21 |
| WP5 / WP6 M2 warm, cold, passive | IP-10, IP-11, IP-12, IP-20 |
| WP7 OEM read-only | IP-01 |
| WP8 / WP9 N3 and host | IP-23 |
| WP10 app library | IP-13 (+ optional section 9) |
| WP11 diagnostics | IP-00, IP-15 |
| WP12 integration | each PR's TESTING snapshot |
| N2 reliability (older roadmap R1) | IP-14, IP-22 |

---

## 6. Validation and performance plan

All numeric goals are **proposed** until IP-00 gives a baseline. Compare like with like: same APK set, same USB stick, same engine settings, vendor logging state recorded.

| Test | Method | Samples | Report | Proposed goal |
|---|---|---|---|---|
| HOME visible | `TS18Startup` `home.first_draw` − `process.start` | 5 cold boots, 5 reboots, 10 process restarts | median, worst | no regression vs IP-00; later ≤ baseline − measured staging cost |
| HOME usable | `home.first_input` after a scripted-by-hand tap at first draw (video) | 5 cold boots | median, worst | first tap handled < 300 ms after first draw |
| Music ready (T-PREPARE-BOOT) | `media.music.loaded` after a real cold boot, no app opened; then one Play; record audible start on video | 5 cold boots, 5 real ACC wakes | per-run, median, failures | 5/5 loaded unaided; Play audible inside the launcher's window; **no audio without a tap** |
| Command acknowledgement vs sound | M2 trace `dispatch → state PLAYING` and video/audio timestamp | 20 per action, warm | median, worst, mismatches | 0 cases of "failed" followed by audio |
| Radio bind (T-RADIO-BIND) | section 5, IP-11 | 10 binds while Music plays | disruptions | 0 interruptions of Music; 0 source changes |
| Handover | Music↔Radio taps; old source paused only after new source confirmed | 10 each way | failures | old audio preserved on every failed switch |
| Nav window | `nav.window.verified`; screenshot shows map pixels in the panel; one drag and one pinch change the map | 5 cold boots, 10 HOME returns | median, worst, failures | same task id reused on every warm return |
| Nav action cost (T-NAV-COST) | broker vs helper elapsed from `root-broker` / `root` trace lines | 20 per verb | median | broker ≤ helper |
| Frames | `TS18Perf` during: idle HOME 10 s, marquee running, drawer open + scroll, picker open | 3 runs each | % frames > 32 ms, count > 100 ms | idle ≤ 5 % over 32 ms; no > 100 ms frames while scrolling |
| Memory / CPU | `TS18Perf` PSS and process CPU after 10 min idle HOME | 3 runs | median | no growth vs IP-00; M2/M2 CPU below M1/M1 |
| Lifecycle | process kill, HOME return, reboot, cold power, real ACC sleep/wake | as above | highest level completed | each reported separately; screen-on is never counted as ACC |

**Visual equivalence** (every PR that touches a UI class): on the API 29 emulator at 1280×720, density 147, capture HOME (Music selected, Radio selected, no source), drawer, picker and Settings before and after with `screencap`; compare pixel-for-pixel with clock and date regions masked. Any difference outside the masks fails the PR unless it is the intended extra app entries from IP-13.

Software checks per PR: `python3 tools/ts18_theme.py validate`, host unit tests, `:launcher:lintDebug`, `:launcher:testDebugUnitTest`, signed-release envelope check, emulator smoke with its required PASS lines.

---

## 7. Rejected and deferred alternatives

| Alternative | Why not | What would change the decision |
|---|---|---|
| Rewrite in Kotlin/Compose or add AndroidX | No defect found that a toolkit fixes. The release envelope is one DEX with no AndroidX or Kotlin; the last TESTING APK I verified (head `7398341`) was 638,556 bytes. Compose would add several megabytes and start-up class loading [INF] | A measured frame or maintenance problem in the Views UI that survives IP-13 |
| Keep M1 and M2 indefinitely | Two owners for one behaviour is the source of S3–S7 | M2 failing its physical gates for a reason M1 does not share |
| Follow "whichever session is playing" (CleanDash, GTS, car-launcher) | Breaks exact source identity | Never for dedicated Music/Radio |
| Media3 controller library to reach NavRadio+ | Adds AndroidX to obtain one token; a plain bind plus listener membership achieves it | Bind proves disruptive and a Media3 controller demonstrably is not |
| Launcher-side Topway radio via `TWUtil` (litedash) | Second tuner writer; donor provenance is not TS18 | Exact-device authority proof, single-writer arbitration, reversible test, owner approval |
| N3 as the HOME map surface | First-party caveat (row J); view-only on API 29; no IME; focus hand-back needed | The IP-23 decision rule |
| Hidden-API exemption to call `reparentDisplayContent` | Works only by defeating the API 29 hidden-API list; still needs embedding authority for Maps | Only inside the IP-23 spike, never in product code without owner approval |
| Cooperative `allowEmbedded` in Organic Maps | Lets any app embed the map Activity on its own display; still leaves input unsolved | Owner accepts the privacy trade-off **and** N3 passes its spike |
| `n3host` privileged APK | Needs a priv-app install and still has no API 29 input path | A decision to modify the system image, which is out of scope |
| Foreground-launch a player at boot (device-reveng, M1 staging) | Disruptive; fixed dwell; replaced by PREPARE | PREPARE failing on a real cold boot with no fix possible in Auxio-TS |
| Passive broadcast metadata (litedash) | No Topway action observed on this unit | A captured broadcast from the TS18 |
| Widgets, dashboards, AppWidgetHost | Outside "improve the existing product" | Owner request |
| Startup micro-tuning now | No baseline | IP-00 |

---

## 8. Evidence appendix

### 8.1 Pinned sources

| Repo | Commit | Licence | Files read |
|---|---|---|---|
| cbkii/ts-theme | [`30f4a8d`](https://github.com/cbkii/ts-theme/tree/30f4a8d868dfeff4b9a8840facfefa681466709e) | repo | all files cited in section 3 under `launcher/src/main/java/com/cbkii/ts18launcher/`, `launcher/src/main/assets/nav/nav-window.sh`, `docs/`, `tests/`, `AGENTS.md` |
| cbkii/Auxio-TS | [`f36fee4`](https://github.com/cbkii/Auxio-TS/blob/f36fee4598aeaecae80ca64e88f451f905f0c3f7/app/src/main/java/org/oxycblt/auxio/playback/service/MediaSessionInterface.kt) | repo | `MediaSessionInterface.kt` (`onPrepare` L77–86, `HOME_PLAY_V1` L233–249, `onPlay` cold path), manifest L73 |
| cbkii/organicmaps | [`9709d35`](https://github.com/cbkii/organicmaps/tree/9709d351f91b41b59e4dc4fa6069576187c2d727/android/app/src/main/java/app/organicmaps/incar) | repo | `AndroidManifest.xml` (L82 resizeable, L436–438 `MwmActivity` singleTask, no `allowEmbedded`), `incar/InCarCompactStartupProvider.java` |
| cbkii/t-music | `2723537` | private | `AGENTS.md` (targets an Android 13 TS18 variant), `reference/jadx-aliased/…/InterfaceC0516d.java` (client stub of `ITWCommandAidl`) |
| AOSP frameworks/base, android10-release (mirror) | [`37a24f5`](https://github.com/aosp-mirror/platform_frameworks_base/blob/37a24f52e6be75e7f7db461e3bee465152318c99/core/java/android/app/ActivityView.java#L497-L524) | Apache-2.0 | `ActivityView.java`; [`WindowManagerService.java` L6739–6751](https://github.com/aosp-mirror/platform_frameworks_base/blob/37a24f52e6be75e7f7db461e3bee465152318c99/services/core/java/com/android/server/wm/WindowManagerService.java#L6739-L6751); [`IWindowSession.aidl` L273](https://github.com/aosp-mirror/platform_frameworks_base/blob/37a24f52e6be75e7f7db461e3bee465152318c99/core/java/android/view/IWindowSession.aidl#L273); `config/hiddenapi-greylist*.txt` (method absent) |
| AOSP Car Launcher, android10-release | [`c1ac502`](https://android.googlesource.com/platform/packages/apps/Car/Launcher/+/c1ac5029063cabe8e0f91cb6d779bdc6443254ce/src/com/android/car/carlauncher/CarLauncher.java) | Apache-2.0 | `CarLauncher.java` L39–55 |
| AOSP Car libs, android10-release | [`1a6b9b2`](https://android.googlesource.com/platform/packages/apps/Car/libs/+/1a6b9b2da551b47136d19ea6aa85a514f8ba3d99/car-media-common/src/com/android/car/media/common/source/MediaBrowserConnector.java) | Apache-2.0 | `MediaBrowserConnector.java`, `MediaSourceViewModel.java` |
| maldos23/open-car-project | [`8551c34`](https://github.com/maldos23/open-car-project/blob/8551c3406f597ed87b4b93517a564bfa59bb7aad/app/src/main/kotlin/org/opencarproject/launcher/data/embed/EmbeddedAppSession.kt) | Apache-2.0 | `data/embed/EmbeddedAppSession.kt`, `TouchInjector.kt`, `RootAdb.kt`, `core/pure/TouchLine.kt`, `docs/EMBEDDING.md` (stale against its own code) |
| vasyl91/FYT-Launcher-Mod | [`65fbd59`](https://github.com/vasyl91/FYT-Launcher-Mod/blob/65fbd59a6bb6ac24196d6630770ab6246014f8bc/vdhost/shared/src/main/java/com/syu/vdhost/VdHostCore.java) | none found | `vdhost/shared/…/VdHostCore.java` |
| wpfyorg/car-launcher | [`0f14c7b`](https://github.com/wpfyorg/car-launcher/tree/0f14c7b3c93380507c46951f3c207004b2308f6b/app/src/main/java/org/wpfy/carlauncher) | AGPL-3.0 | `feature/media/MediaSessionSelection.kt`, `MediaSessionController.kt`, `MediaRepository.kt` (outline), `core/performance/PerformanceMonitor.kt`, `PerformanceBudgets.kt` |
| armeehn/device-reveng | [`b5c07f6`](https://github.com/armeehn/device-reveng/tree/b5c07f620efbe7f615f0c36d87cae5fa9f4dfafa/launcher/app/src/main/java/com/ripostelabs/carlauncher) | Apache-2.0 | `data/ColdBootResume.kt`, `FirstRunGate.kt`, `IgnitionSession.kt`, `media/NowPlayingRepository.kt` (first 140 lines) |
| metehankaygsz/dashline | [`64ef23f`](https://github.com/metehankaygsz/dashline/blob/64ef23f731d120fdac1e524a41164f94d5d7bfd1/app/src/main/java/com/dashline/launcher/AppRepository.kt) | GPL-3.0-only | `AppRepository.kt` |
| cssbruno/cabin | `8263178` | Unlicense | `launcher/LauncherAppLibrary.kt` (outline) |
| deviloufr-ai/Dashwheel | [`a1f6f26`](https://github.com/deviloufr-ai/Dashwheel/blob/a1f6f26fe65be88e25da0ab951f5d0a03553a57d/app/src/main/java/com/openauto/dash/SplitLauncher.kt) | proprietary, all rights reserved | `SplitLauncher.kt`, `AppWindowTile.kt` (grep) |
| jorgejbz/GTS-Launcher | [`7af683f`](https://github.com/jorgejbz/GTS-Launcher/blob/7af683f0498820b2d815c10fc97b6346d862209c/launcher/app/src/main/java/com/jajua/gts/TwCommand.java) | MIT | `TwCommand.java`, `MediaMonitor.java`, `Audit.java` (headers) |
| IrvingGuerra/litedash | [`33a35d4`](https://github.com/IrvingGuerra/litedash/tree/33a35d41eb5fac6a4175ccc3e6c94d57494c0fb5/feature/music/src/main/java/com/irvingguerra/litedash/feature/music) | GPL-3.0 | `BroadcastMeta.kt`, `FactoryRadio.kt`, `TopWayRadio.kt` (outline) |
| donnyyu/CleanDash | `5c0e66f` | MIT | not re-read; the repo's earlier study at the same commit is relied on |
| ivvlev/CarRadio | `d7a10fb` | GPL-3.0 | `README.md` only |
| homeo26/byd-split-launcher | `401be12` | MIT | `README.md` |
| dw2lam/openlauncher | `8ee7d3a` | MIT (per README) | file list and README only |
| KswCarProject (three repos) | `e57a81d`, `8b5ba1d`, `ac514f5` | none | top-level listing only |

### 8.2 Checks run for this review

| Check | Result |
|---|---|
| Live GitHub state of ts-theme (branches, PRs, check runs, reviews) | Done |
| Ancestry of every subsidiary head in `30f4a8d` | Done, all ancestors |
| Source reading of ts-theme at `30f4a8d` | Done for the files cited; `SettingsActivity`, `AutomotiveUi`, geometry and appearance classes were **not** reviewed line by line |
| Line numbers in section 3 | Re-checked with `grep -n` at `30f4a8d` |
| Local build or unit tests | **NOT_RUN** (CI on the same head is green; nothing was changed) |
| Emulator | **NOT_RUN** locally; emulator statements are from the repo's CI probe |
| Physical TS18 | **NOT_RUN** |
| NavRadio+ installed manifest, XTService service bytes | **Not inspected**; covered by IP-01 and IP-02 |
| Android 10 hidden-API runtime behaviour for `reparentDisplayContent` | Inferred from the list files; **not executed** |

### 8.3 Statements that are inference, not observation

- That shell-helper actions are materially slower than broker actions (H3).
- That M1 staging is unnecessary with Auxio-TS v26.10.0 (H2).
- That a Media3 session service returns a usable binder to a plain bind and creates its session in `onCreate` (H1); this is standard Media3 behaviour but was not read from NavRadio+.
- That rail icon decode affects first-frame time (S11 is a code fact; its cost is unmeasured).

---

## 9. Optional items with visible effect (outside the core plan)

These change what the driver sees, so they are not part of the plan above.

| Item | Visible change | Value |
|---|---|---|
| Hide apps from the drawer | A per-app "Hide" action and a "Hidden apps" row in Settings | Removes vendor clutter (cabin, dashline) |
| Explicit "Open Radio" hint when no starter exists | One extra text state in the metadata line | Truthful capability for session-only radio apps |
| Diagnostics row showing the last `TS18Perf` result | One Settings row | Lets the owner read frame/memory status without a collector |

---

## 10. Recommended first implementation batch

Do these four, in this order, as separate PRs stacked on PR #44's head (or on `main` once #44 lands):

1. **IP-10** bounded cold Play (Auxio-TS capability advertisement + M2 use of it).
2. **IP-12** idle the legacy listener and cache package lookups.
3. **IP-13** asynchronous component-aware app catalogue.
4. **IP-14** broker parity in shadow mode, with the HOME windowing self-check.

In parallel, with no code: **IP-00** (first TS18 baseline on the existing TESTING build), **IP-01** (XTService static read) and **IP-02** (NavRadio+ manifest read). IP-11 follows as soon as IP-02 returns.

None of this batch changes a default, needs a new permission, or alters the UI's appearance.
