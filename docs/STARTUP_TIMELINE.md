# Startup phase timeline

Use the timeline to measure startup before claiming it is faster. `StartupTimeline` records each
phase once per launcher process. Every phase is timed on the elapsed-since-boot clock and as an
offset from process start (`Process.getStartElapsedRealtime()`). Timings are written to the
`TS18Startup` log tag and to the local media event trace. Nothing is persisted or sent anywhere.

## Phases

| Phase | Recorded when |
|---|---|
| `process.start` | with the first phase; carries `boot_id` when it is readable |
| `app.create` / `app.created` | `Application.onCreate` entry and exit |
| `startup.kind` | first readiness trigger and its classification (`FULL_BOOT`, `SAME_BOOT_WAKE`, `HOME_RETURN`) |
| `home.create` / `home.content` | HOME `onCreate` entry; the dashboard views are built |
| `home.first_draw` | first HOME frame drawn; `reportFullyDrawn()` is called here |
| `home.focus` | HOME window first gains focus (touchable) |
| `music.ready` / `radio.ready` | the M1 coordinator first observes a usable queue for that source |
| `navigation.ready` | the startup navigation controller first reports ready, with its boundary |

Spans time the synchronous main-thread steps in `Application.onCreate`:

- `app.migrate-surfaces`
- `app.root-task-warm`
- `app.nav-permission-early`
- `app.media-access-early`
- `app.media-monitor-start`
- `app.startup-coordinator`

A span of 50 ms or more is also added to the trace as `slow-span`. This attributes delay before
first HOME to the step that owns it.

## Capture

```sh
logcat -d -v monotonic -s TS18Startup ActivityTaskManager | grep -E 'TS18Startup|Displayed|Fully drawn'
```

`ActivityTaskManager` contributes the framework's own `Displayed` and `Fully drawn` lines for the
same launch. Keep these points in mind when reading a capture:

- Match captures by `boot_id`.
- A warm HOME return in an existing process records no new phases.
- `music.ready` and `radio.ready` measure queue readiness, not audible output.
- `navigation.ready` is the launcher's own judgement, not Organic Maps' GNSS time to first fix.

## Status

Host tests cover recording once per phase, offsets, the phase limit and span propagation. **No
TS18 timing has been captured (NOT_RUN)**, so no speed claim is made from this change.
