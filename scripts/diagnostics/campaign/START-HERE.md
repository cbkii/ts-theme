# TS18 capture campaign

Capture one trial after a cold boot, reboot, ACC wake, process restart or HOME return.
The toolkit observes; it never reboots, launches an app, presses Play, changes HOME,
changes permissions or installs anything. Perform the intended actions yourself.
Use one collector at a time, with the car parked. Physical qualification remains
**NOT_RUN** until somebody reviews actual observations on the candidate.

## What you need

Keep `campaign.py` in private Termux storage, or on your ADB computer. It needs an
existing Python 3.9+ installation (standard library only). Capture and marker commands
require a POSIX host: Termux, Linux, macOS, or a Linux environment in WSL with working
ADB access. Native Windows Python is not supported: the collector uses `fcntl` file
locks and POSIX process groups. ADB mode also needs an existing authorised ADB
connection and the exact device serial. Nothing is downloaded
or installed automatically. No Magisk module or boot entry is added.

Local mode uses the **current caller**. Ordinary Termux normally cannot read system
logs or record the screen: those capabilities become BLOCKED. For the TS18's existing
root setup, run the collector once from a root shell using the original
`$PREFIX/bin/su`; do not replace that binary or add a su wrapper. Keep the same caller
and `--state-root` for marker commands. UID, SELinux, namespace and Android user are
captured, with no claim that root has app/platform authority. State directories must
be caller-owned mode 0700 under trusted ancestors. Symlinks, hard links, foreign owners
and unsafe permissions are rejected before truncating any private file. Root uses
`/data/ts18-campaign`; do not use shared `/data/local/tmp` for root control state.

## First capture

From a root shell on the TS18, with the working directory containing `campaign.py`:

```sh
python campaign.py capture --transport local --campaign baseline --trial 1 \
  --lifecycle home_resume --duration 180 \
  --state-root /data/ts18-campaign
```

If the root shell's PATH does not contain Termux Python, use its existing absolute
path `/data/data/com.termux/files/usr/bin/python`, and the absolute private path to
`campaign.py`. Do not move the Python environment into shared storage.

Alternatively, from an ADB computer:

```sh
python3 campaign.py capture --transport adb --serial EXACT_ADB_SERIAL \
  --campaign baseline --trial 1 --lifecycle home_resume --duration 180 \
  --out /absolute/path/to/results
```

The tool first records identities and bounded snapshots, then prints **Capturing**.
That is the beginning of the timed phase. Keep this terminal running and exercise
HOME, Music, Radio and navigation normally. ADB capture does not require root.
For a quieter comparison, choose `--workload idle` and `--no-video`. The snapshot
phases are outside the timed phase; each snapshot has its own 30-second deadline.
Each before/after identity read has a separate 30-second producer budget. Recording,
logging and in-phase clock reads share the 30–300 second capture deadline. Bounded
flush/termination checks can add a few seconds after a producer budget ends.

Use trial numbers 1–5. Repeat the command after **each actual lifecycle event**, with
the matching `--lifecycle cold_boot`, `reboot`, `acc_wake`, `process_restart` or
`home_resume`. A trial label is an operator report, not proof an ACC or cold boot
occurred. Five invocations during one boot are not five cold boots. No collector
can observe first HOME pixels retrospectively; bounded historical app logs are
retained separately from the live stream. Start the collector before the actions
being timed, or leave their measurements missing.

## Mark an observation

In a second terminal on the **same computer/caller**, while capture is active:

```sh
python campaign.py mark --campaign baseline \
  --state-root /data/ts18-campaign MUSIC_TAP
python campaign.py mark --campaign baseline \
  --state-root /data/ts18-campaign MUSIC_AUDIBLE
```

For ADB computer captures, use `python3` and omit `--state-root` in both capture and
marker commands to use that computer's private default. Markers use locked local
files, with no networking. A closed/dead collector rejects new markers. Starting a
new trial does not replay old markers or overwrite an exported trial.

| Observation | Marker |
| --- | --- |
| Deliberate HOME action / useful visible HOME / accepted touch | `HOME_START`, `HOME_VISIBLE`, `HOME_TOUCH` |
| Music tap / observed source playback acknowledgement / actually heard audio | `MUSIC_TAP`, `MUSIC_ACK`, `MUSIC_AUDIBLE` |
| Radio equivalents | `RADIO_TAP`, `RADIO_ACK`, `RADIO_AUDIBLE` |
| Navigation action / actual map pixels / working map touch | `NAV_TAP`, `NAV_PIXELS`, `NAV_TOUCH` |
| First usable fix / Driving View actually ready | `GNSS_FIX`, `DRIVING_READY` |

Markers record manual observation time, including collector/transport delay and clock
uncertainty. They are not instrumented tap, native first-frame, actual GNSS or audio
timestamps. Do not mark an acknowledgement just because a command was dispatched.
Use launcher/companion events and video to corroborate the manual observations.

## Video and output

Video requests 1280×720 at 2 Mbit/s, in segments no longer than **150 seconds**, within
Android 10's per-recording limit. Requested and observed FPS remain unknown: Android
screenrecord does not offer an FPS setting here. Actual segment start/end clock
samples, host gaps, exit/cleanup status, sizes and SHA-256 are recorded. Codec or
permission failure is one BLOCKED prerequisite; it does not trigger repeated starts.
Speaker/radio audio is **not captured**. Use audible markers. Enable Developer
**Show touches** yourself if wanted; the toolkit does not change it.

Finished trials export to `/storage/emulated/0/Download/ts-theme/campaigns/`, or the
explicit ADB computer output directory, with a ZIP and outer SHA-256 sidecar. Each
trial is committed as one complete `baseline-trial-01/` directory containing both
files; an interrupted copy does not reserve the final trial name. Text
exports filter common paths, metadata, addresses and coordinates. **Video shows
whatever was on screen; review it and the text before sharing.** No automatic upload
occurs. Optional video compression should be done on a copy after capture; changing
a sealed ZIP invalidates its checksums. Keep originals for comparison.

`trial.json` records caller/boot/workload and installed APK hashes when readable;
`segments.json` records video boundaries; `markers.json` records observations;
`before/` and `after/` contain package, session/audio, task/window, display, location,
memory and frame snapshots and per-probe statuses. `events.log` contains only the
selected TS18 app tags (including the launcher's `TS18Startup` phases) plus Organic Maps'
`InCarCompactStartup` events, and the framework's own `Displayed`/`Fully drawn` lines for the
launcher and Organic Maps only (every other `ActivityTaskManager` line is dropped). Worker
protocol records are stored as metadata. No paired-device list or full getprop dump is requested.
Redaction/filtering is supplementary privacy protection, not a guarantee that all
vendor output formats are covered.

Ctrl+C requests bounded producer cleanup. Partial evidence may still be sealed with
`interrupted=true` **only after writer termination is established**. If transport loss
prevents cleanup proof, evidence remains private/unsealed and the command reports
BLOCKED. Target-side producers have native time limits even if the ADB client dies.
Never start another collector until an interrupted run's producer has stopped.
If export fails, the printed private archive remains available. A stale collector
lock is released automatically on process death; a late marker cannot create one.
A shared host/target lock prevents overlapping campaigns with different names or
state roots on that host. Coordinate explicitly with collectors on other computers
or with different transport aliases; this is not a global vehicle lock.

## Verify and compare

```sh
python3 campaign.py verify /path/to/baseline-trial-01/baseline-trial-01.zip
python3 campaign.py compare-runs /path/to/baseline-trial-01/baseline-trial-01.zip \
  /path/to/baseline-trial-02/baseline-trial-02.zip > comparison.json
```

Verification checks complete manifest coverage and hashes without extracting files.
It rejects traversal, duplicate names, symlinks, oversized archives and unclosed-writer
claims. Integrity PASS means the retained bytes are consistent, not that a device
feature passed. A missing optional probe stays BLOCKED/PARTIAL rather than establishing
absence or failure in the target.

Comparison retains each repeated observation, boot-aligned uptime, latency samples,
manual submission delay, missing phases and sample counts. A boot change inside a
trial blocks cross-epoch latency claims. Grouping requires identical readable APK
hashes, stable Android user/build, local pseudonymous target identity, lifecycle label
and workload. Identity is re-read after capture; changes prevent grouping. Copies of
one trial count once; each group lists its candidate and member trials. Unknown identities stay separate;
different candidate builds must be compared explicitly by the reviewer. No p95,
automatic performance improvement, audible success or physical PASS is invented.
Do not conflate Android boot, launcher response, player readiness, navigation pixels,
GNSS acquisition or Driving View acquisition.


## Startup phases in comparisons

`compare` parses `TS18Startup` lines from each archive's live `events.log` and history
snapshots. For each launcher process it reports:

- `boot_id` when it was readable;
- every phase, with its offset from process start;
- the synchronous startup spans;
- any expected phase that is missing (`app.create`, `home.create`, `home.first_draw`,
  `home.focus`);
- framework `Displayed` and `Fully drawn` timings.

Repeated phases are kept as separate entries. No percentiles or improvements are computed.
Instrumented phases are what the launcher reported about itself. They are kept separate from
manual markers and from audible, visible-pixel and GNSS evidence.
