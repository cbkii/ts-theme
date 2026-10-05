# Execution ledger — 2026-10-05

No TS18 is connected. Q0–Q8 and Auxio #264 remain **NOT_RUN**. No merge,
production publication or protected device operation was performed.

| Work | Current source/evidence | Disposition |
|---|---|---|
| A — combined OM | PR55 `db57091f5daa0cb53b481b4205fad6db0ccb1c6f`, includes56/57; Android Check37190046993 and publisher37191741502 | Implemented; canonical build/lint/JVM/API29+30 smoke passed. Full native-suite execution and physical qualification NOT_RUN. Neutral reviewdog is not a native pass. |
| B — HOME geometry/docs, media/task authority | PR14 `15775b5d6369ebe92ab4e475e6419aeebd4a2634`; Validate37184291142, publisher37184291630 | Implemented, canonical checks passed. Named root task capability and warm continuity on TS18 NOT_RUN. |
| C — Auxio | dev `3146849aeb6ff8831f67f3925ae5ceea7f51dffc`; signed v26.6.9 built from `101370071ffdf37dea9cda7cb0c2263879f75121`, successful Manual Release36284830963 | APK locally hashed against metadata and draft digest. Only dev delta is upstream-monitor JSON. Do not label this a build of3146849. No speculative app change; #264 physical NOT_RUN. |
| D1 — diagnostics | startup1.3, capture library, analyser, installer, README and regression tests in this directory/PR | Implemented for review. Host PID namespace prevents local process-group tests; canonical Linux CI must execute them. Android/Magisk qualification NOT_RUN. |
| D2 — remount | successful-ready UID map, AppOps, exact framework hashes, starts-only counts, redacted script/module evidence | Tools/proposal ready; fresh10148/10186 mapping and caller attribution UNKNOWN. No hook installed. |
| D3 — GMS | GMS/GSF colon processes, APK/splits, dexopt/oat/vdex/maps, memory and modules | Tooling and one-variable A/B protocol ready; causal result UNKNOWN. No module changes or cache wipe. |
| D4 — audio/CPU | manual phases, forensic audio snapshots, lower-bound rates, quiet-baseline profiling protocol | Tools ready; audible/latency/symbolised profile results NOT_RUN. |

## Candidate identities

Compare actual installed bytes and signers before qualification. Preserve DoFun
recovery HOME. This toolkit does not install APKs. DEBUG is a different artifact.

| Candidate | SHA256 | Signer/source evidence |
|---|---|---|
| `OrganicMaps-InCar-TESTING-PR55-db57091.apk`,43,778,785B,`app.organicmaps.incar` | `debc44fd7ef096e8796be2911cb9580b732800a77c27afe92de6d6241532994a` | Publisher signature verification passed; exact SIGNER/local signed bytes still need independent comparison before install. Draft391836691, tag`untagged-31fecdcb8d777c1a4184`. |
| `TS18-Standalone-Launcher-TESTING-PR14-15775b5.apk`,298,311B,`com.cbkii.ts18launcher` | `d0de2e700bd8a52b731efc18c926a39a0f629f26dfc2198bb4ad0f779dbc0762` | Signer`6b12337f70ec56313180a04e602aa0aa5cea3509dfa34d58c4eefb9f6202cda4`. Draft386228852, tag`untagged-9a05fb3eaaeea99a0877`. |
| `Auxio-TS-v26.6.9-app-release.apk`,7,310,211B,`com.tw.media`,code26060900 | `82de08d548b7f742c65259227f2e921a1953be3d54a77c80bfef2ed3b73ecb8c` | Publisher v2 signer`c1076f4624bab2ddafa2e3c4b298f6296e4a2ca75f57785cd35321998c1204a0`. Recovery artifact10919997694 bytes match metadata; draft397460141. |

[OM PR55](https://github.com/cbkii/organicmaps/pull/55),
[HOME PR14](https://github.com/cbkii/ts-theme/pull/14),
[Auxio #264](https://github.com/cbkii/Auxio-TS/issues/264).
Draft releases require the authorised signed-in repository connection.

## Physical requirements

Every row is **NOT_RUN**. Record per attempt: PASS/FAIL/WARN/UNKNOWN/BLOCKED/NOT_RUN,
boot ID, exact APK hash/source/signer, context, timestamps, markers and task/session
IDs. Reconstruct actual user action order. Functional testing may proceed with
noise disclosed; causal CPU/latency comparison requires quiet/quantified noise.
Never drive through remote interaction; arrange safe physical observation/replay.

| Stage | Next executable physical test |
|---|---|
| Q0 | Device/build/boot/root contexts, HOME/access, display/density/insets; installed base/splits/source/signer match candidates; remove duplicate observers |
| Q1 | Three cold boots: useful HOME, no readiness Activity, layout warnings absent, delayed access/metadata/icon recovery; single NavRadio/Auxio Play/Pause/transport |
| Q2 | Three drawer and fullscreen→HOME cycles; unrelated-app returns; map/launcher touch; App Info and quick-slot edit; no warm Retry |
| Q3 | Preserve existing route/download/permission during fullscreen/HOME/drawer/departure; named root mode call and independent package/task/stack evidence |
| Q4 | OM START→active→END, reroute/stops/alternatives/search/Places/back; permission OFF/ON/real Settings return; Driving View before/after fix |
| Q5 | Day/night/warnings, immediate group, AFTER→LANES→AFTER, compact/full bounds, four-digit/unit/duration extremes; posted limits at START/low speed/turns/non-route follow |
| Q6 | All Auxio subcases below, one authority and exactly-once transport |
| Q7 | Process recreation, reboot/full cold power, short/expired ACC; default/custom same-/cross-boot route lease; noninteractive fixes cannot revive expiry |
| Q8 | Reverse takeover/return, calls/audio, SystemUI interruption/restart, late USB arrival/removal/provider visibility |

Auxio Q6 subcases, each NOT_RUN:

- Fast Resume first audio then canonical handoff without reset/seek/pause glitches
  or duplicate service/player/queue/session/notification/focus authority.
- Previous/Next/Shuffle before/after restoration; visualiser at first audio, manual
  next, automatic next and pause/resume.
- Full UI cold autoplay OFF/ON; floating-only cold OFF/ON without unwanted
  MainActivity. Namespace`org.oxycblt.auxio`, installed`com.tw.media`, wrappers
  `com.tw.music.MusicActivity`/`MusicService`; stock`com.tw.music` remains protected.
- DoFun art/title/progress and stale item replacement; exactly-once taps; source
  icon intentionally opens Auxio; queue expand/collapse/scroll and RecyclerView pin.
- Process death, launcher/DoFun restart, reboot/full cold power and ACC wake.
- Disconnected-controller browser/ACTION_PREPARE: paused/restorable metadata with
  no autoplay; one Play; Pause wins pending restore; late USB/source converges
  once; bounded scan/preparation does not block first audio.

Only a fresh attributable current-build failure justifies new Auxio code. Older
v26.6.8 logs, HAL EPERM or remount target UID are insufficient. Leave #264 boxes
unchecked until actual physical evidence exists. D2–D4 attribution/approval
proposals and falsifiers are in README; no protected experiment is pre-approved.
