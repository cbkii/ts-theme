# TS18 candidate — physical observations

Run date/time + timezone:
Vehicle parked / safe test condition:
Operator:
Android 10 / Topway build (actual):
Installed Launcher package SHA-256:
Installed Auxio package SHA-256:
Installed Organic Maps package SHA-256:
Signer verified? (method and expected):
Existing rollback APK paths copied/saved? (no app data wiped):
HOME resolved component:

| Stage | UTC/local time and boot ID | PASS / FAIL / PARTIAL / BLOCKED / NOT_RUN | Exact user action and physical observation | Collector archive / video / task / session ID |
|---|---|---|---|---|
| Installed APK identity and signers | | | | |
| First HOME from reboot #1 | | | | |
| First HOME from reboot #2 (different boot ID) | | | | |
| True cold power/start (state and method) | | | | |
| ACC off/sleep/wake (conditions) | | | | |
| Cold Music one Play, without priming | | | | |
| Cold NavRadio one Play, without priming | | | | |
| 20+ committed Next transitions, metadata gaps | | | | |
| Previous >3 s into track, rewind confirmation | | | | |
| Rapid Next/Next/Pause and seek interruption | | | | |
| Shuffle OFF/ALL after process restart | | | | |
| Cached playback while library UI unavailable | | | | |
| 10+ Maps compact/fullscreen/HOME cycles | | | | |
| Maps Back empties task; one explicit Retry; same task ID | | | | |
| Maps permissions/download/settings flow | | | | |
| Navigation rail and fullscreen button | | | | |
| Music→Radio audible handover | | | | |
| Radio→Music audible handover | | | | |
| Launcher/process restart recovery | | | | |
| DoFun, system nav, steering/reverse where appropriate | | | | |

## First causal failure (fill out once before retry)

Timestamp and boot ID:
Action attempted:
Earlier successful observation:
Expected state:
Actual on-screen state (pixels/touch/focus separate):
Android task ID/display/user:
App process still alive? Evidence:
Session/transport action and its acknowledgement:
Audio audible? Who checked it?:
Related logs/events-buffer excerpt:
Did another app/permission flow own focus?:
Only one Retry tried? Its result:
Fallback N2/N1/N0 or M2/Legacy (state exactly when switched):
Outstanding dependencies marked BLOCKED rather than additional failures:

## Outputs to upload for engineering assessment

- All `/storage/emulated/0/Download/ts-theme/qualification/*.tar.gz` for tested stages.
- Screen recording/video with clocks matched to the logs; phone video with sound for cold/audio.
- Any `TS18-startup-logs` or separate bounded campaign ZIPs captured by one active owner at a time.
- This completed notes file, with personal information and location redacted.
