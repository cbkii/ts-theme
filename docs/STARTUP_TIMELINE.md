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
| `home.focus` | HOME window first gains focus; physical touch is separately qualified |
| `home.first_input` | the first noted HOME user interaction; not completion latency |
| `nav.window.verified` | the coordinator receives successful navigation-boundary evidence; not physical pixels/touch |

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
logcat -d -v monotonic -s TS18Startup TS18Media ActivityTaskManager | grep -E 'TS18Startup|TS18Media|Displayed|Fully drawn'
```

`ActivityTaskManager` contributes the framework's own `Displayed` and `Fully drawn` lines for the
same launch. `TS18Media` carries the local media event trace, including the startup-phase spans and
their `slow-span` events, so it must be in the filter for those to appear. Keep these points in mind when reading a capture:

- Match captures by `boot_id`.
- Each phase is recorded at most once per process. Repeated HOME returns do not repeat an existing phase; a previously unobserved boundary can still appear later.
- Current media readiness/dispatch/results are in the bounded `TS18Media` startup/M2 events; current callsites do not emit the older `music.ready` / `radio.ready` phases.
- `nav.window.verified` is launcher boundary evidence, not map pixels/touch or Organic Maps GNSS time to first fix. Old `navigation.ready` reports belong to earlier revisions.

## Campaign integration

The WP11 campaign kit (`scripts/diagnostics/campaign/`) captures `TS18Startup` live and in history,
together with narrowed framework `Displayed` / `Fully drawn` lines. Its `compare` command reports
the phases for each process. See that kit's START-HERE.

## Status

Host tests cover recording once per phase, offsets, the phase limit and span propagation. **No
TS18 timing has been captured (NOT_RUN)**, so no speed claim is made from this change.
