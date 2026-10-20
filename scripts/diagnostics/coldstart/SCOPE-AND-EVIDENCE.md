# Scope and evidence

Prepared for TS18 Android 10 / API 29, UIS8581A Cortex-A55, PowerVR GE8322, 4 GB RAM, 64 GB eMMC and a 1280×720 display around 58 Hz with existing Magisk access. Runtime capability/permission failures are retained; root does not imply Android system permissions. No physical device or adb target was available in this authoring session.

| Integration/source baseline inspected | Commit |
|---|---|
| ts-theme PR #27 | `11ec69a0c53f723cca0c046a255ddf0f4322b006` |
| ts-theme PR #26 base | `9b4e67efec83f340f292ccdf328dd5b2c1016614` |
| Organic Maps PR #69 | `07340fca1e5e603e0f40aa576204b1ec39342c14` |
| Organic Maps master base | `f13716f7b1dad1d3efc8cf1191e533bf75da439b` |
| Auxio-TS dev | `21cd6f3fd84f3d163345fedfa1b5214fe68854d4` |

These source commits are context, not claims about the installed APKs. The collector records package/build information and hashes installed APKs/splits when permitted and within its deferred time budget. Missing hashes remain unknown.

Target packages: launcher `com.cbkii.ts18launcher`; Auxio-TS `com.tw.media`; Organic Maps `app.organicmaps.incar`. Inspected music contracts include exported `com.tw.music.MusicActivity` → Auxio MainActivity and browser service `com.tw.music.MusicService`; the collector does not launch or bind either. Relevant source trace tags include `TS18Media`, `TS18Nav` and `InCarCompactStartup`. The launcher media trace includes elapsedRealtime and a bounded memory ring, so external log capture is necessary; the ring is not assumed persistent.

Known launcher preference files read: `ts18_launcher.xml`, `testing_profiles.xml`, `startup_readiness.xml`, `compact_startup.xml`. The current Android user is resolved read-only; failure never defaults to user 0. No media database, bookmarks, downloaded maps, or arbitrary preference tree is copied. Existing navigation helper journal identity/state files are read with per-file/output limits. The new helper keeps runtime journals in launcher-private `no_backup/navigation-root`; legacy `/data/adb` journals are read only. The helper is never executed by this kit.

Snapshots collect display-level focus separately from per-window surface state; windows-only output does not establish Android 10 focus.

The diagnostic questions are: did a target process exist/start/restart; did the correct task/surface survive switching; did focus and embedding return; was music session discovery/binding/command dispatch delayed; did audio focus/route/output match the requested state; and was storage/CPU/memory/frame pressure involved? Raw observations and failure metadata are preserved. The scripts do not automatically assign a causal diagnosis or turn framework structural milestones into a pixel/audio PASS.

## Host validation

Run: `python3 -m unittest discover -s tests -p test_coldstart_kit.py -v` from the launcher repository. Python/tests are for maintainers on the authoring host; Android only needs the two shell scripts and Magisk's existing BusyBox.

Tests exercise shell syntax/help, actual capped producers and exit codes, timeouts and descendant cleanup, the independent worker watchdog after supervisor loss, accelerated full-run shutdown, unavailable storage, live-owner duplicate refusal, output paths, budgets, request data handling, P/S/F/N outcomes, note injection handling and the production `/proc` parser against fixtures.

Host execution uses Bash plus a BusyBox-API dispatcher to existing host tools. Its UID input models Magisk root independently of the host UID; a separate test verifies the production non-root refusal. This host exposes synthetic process-session fields in `/proc`, so positive process lifecycle tests replace only the identity I/O boundary in isolated **test copies**, using kernel `getpgid`/`getsid`; their generation token is synthetic. Signal delivery in the host dispatcher uses `os.kill` to avoid the host utility's negative-PID option parsing. Production scripts retain strict `/proc` generation/group/session checks. These tests do not establish real Android BusyBox compatibility, PID-reuse resistance on the device, OEM permissions, probe availability, display readiness or audio readiness. The production parser's field positions, spaces/parentheses handling and zombie rejection are separately fixture-tested.

Physical TS18 qualification: **NOT RUN**. The guide's real reboot film, package hashes, cold-process evidence, single-action music test and repeated HOME-return observations are required before accepting either integration.

## Primary documentation

- Magisk boot scripts / `service.d`, BusyBox shell environment: https://topjohnwu.github.io/Magisk/guides.html
- Android dumpsys: https://developer.android.com/tools/dumpsys
- Android logcat: https://developer.android.com/tools/logcat

The service directory is `service.d` (not `sercice.d`). Installation makes the bounded collector executable. Each invocation detaches and has its own absolute 480-second uptime deadline; repeated invocations do not extend an already-running collector.
