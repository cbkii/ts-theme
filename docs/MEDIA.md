# Media control engines

HOME has two independent audio sources, **Music** and **Radio**. Each is controlled by exactly one
engine, chosen per source in *Settings → Media → Control engine* and stored as `music.engine` /
`radio.engine` (`legacy` or `m2`). Missing, malformed or future values are `legacy`.

The launcher never owns playback: no player, queue, MediaSession, audio focus or tuner command of
its own. It observes sessions the source apps publish and sends them transport actions.

| | **M1 Legacy** (default, rollback) | **M2 Direct** (TESTING, opt-in) |
|---|---|---|
| Classes | `MediaSourceBootstrapper`, `ProcessMediaSessionMonitor`, `MediaListenerService`, `StartupReadinessCoordinator` behind `LegacyMediaBackend` | `media/direct/*` behind `DirectSourceMediaBackend` |
| Which app | Configured app; Music may follow the globally active player ("Follow active media") | The exact configured package only |
| Which session | Ranked selection with listener/process merge | One pinned token; several sessions resolve by activity, previous pin, then capability, otherwise `AMBIGUOUS_SOURCE` |
| App not running | Source-specific background/foreground preparation, optionally at startup | Started **only on a Play tap**, only through the app's own exported media browser service; otherwise reported as "Not running" |
| Command | Bounded preparation, fallbacks and retries | One directional dispatch per tap, then observe |
| Result | success / failure | `ACKNOWLEDGED`, `SENT_UNCONFIRMED`, `UNSUPPORTED`, `SOURCE_LOST`, `FAILED` |
| Startup preparation | Yes (when enabled) | None: nothing starts without a tap |

## Rules both engines obey

- `MediaFeatureFacade` is the only UI entry point. The engine that is not selected for a source is
  detached first and then cannot command, prepare or publish state for it.
- HOME renders each source only from its selected engine; the other engine's view of that source
  is discarded, never merged.
- Play and Pause are resolved to one direction at tap time. `BUFFERING` and `CONNECTING` are never
  shown or treated as playing.
- Switching source never silences the current one early. With both sources on Legacy, Legacy keeps
  its own nominate-then-commit handover. In every other combination the facade pauses the former
  source once, through its own engine, only after the replacement is **observed playing**.

## M2 Direct in detail

- **Authority:** `MediaSessionManager.getActiveSessions` through the launcher's one
  notification-listener component. Without notification access the source shows
  "Notification access needed". Access changes are picked up from the system setting itself.
- **Idle cost:** with no source on Direct it holds no subscription, callback or timer.
- **One tap:** resolve the pinned token and its advertised actions → one `play()`, `pause()`,
  `skipToNext()` or `skipToPrevious()` → wait for that same token to confirm (Play 5 s, Pause 2.5 s,
  skip 2 s). No confirmation in time is `SENT_UNCONFIRMED`; it is never sent again. A second
  Play/Pause tap while one is in flight joins it.
- **Skips** are confirmed only by a change of item on the same session.
- **Capabilities** are the session's advertised actions. Auxio-TS (`com.tw.media`) and NavRadio+
  (`com.navimods.radio`) additionally accept Next/Previous they do not always advertise.
- **Any player** the user selects as Music is supported as far as its own session allows.
- **Metadata** is exactly one reading of the pinned session; a new item never inherits old text.
  Radio text is the session's own title/subtitle (Legacy's notification-based station enrichment
  is not used).
- **Stock Topway radio** (`com.tw.radio`) is controlled only if it publishes a normal session. No
  private Topway command is sent by either engine.

### Starting a source that is not running

- Only a **Play** tap starts a source. Attaching, refreshing, HOME start, Next and Previous never do.
- The only mechanism is the standard `android.media.browse.MediaBrowserService` the app itself
  exports. Connecting makes the app create its process and hand over its own session token; the
  launcher checks the token belongs to the requested package, pins it, sends **one** `play()` and
  waits for that session to confirm. No Activity is opened, no root is used, no queue or session is
  invented, and Legacy's Auxio-specific preparation extras are not used.
- Bounded: 8 s for the session to appear, then the normal 5 s Play confirmation. Taps during the
  attempt join it. A source that resumes by itself is acknowledged without sending anything. A
  session arriving after the deadline becomes controllable but is not auto-played.
- A package with no enabled, exported browser service (stock `com.tw.radio`; NavRadio+ unless its
  installed build declares one) is **not startable** here: HOME shows it as unavailable and the
  source icon opens the app.
- HOME shows Play for a startable source, a connecting icon while starting, and the unavailable
  icon otherwise.

### Not in M2

- Preparing sources at boot/ACC wake (Legacy only).
- Native Topway tuner control and illumination input.
- Whether Auxio-TS actually begins playback from a bare `play()` after a cold bind is a property
  of the installed Auxio-TS build and is **not yet physically verified**.

## Idle cost per engine

- No source on Direct: the direct engine holds no subscription, callback or timer.
- No source on Legacy: the legacy `ProcessMediaSessionMonitor` is torn down (no session
  subscription, per-session callbacks or one-second visible verification tick) and HOME,
  listener and settings refreshes do not recreate it. Switching a source back to Legacy
  restores it. The shared notification-listener component itself stays enabled for both.

## Qualification status

Both engines are verified by host tests (JVM/Robolectric API 29, source contracts, R8 release
envelope). The M2 cold-start transport additionally has real cross-process evidence on the stock
API 29 emulator in CI: `MediaSessionApi29Instrumentation -e m2_browser 1` binds the production
`FrameworkConnector` to an exported fixture `MediaBrowserService` in the test APK's own process,
checks the token belongs to that package, sends one Play and requires the same session to report
Playing, then disconnects and reconnects. That fixture is not Auxio-TS or NavRadio+, and the
emulator is not the TS18. **No TS18 physical trial of M2 has been run.** Before Direct can become a default it
needs, per source: warm command trials, handover trials, cold boots and ACC wakes with audible
confirmation, and a same-build comparison against Legacy.
