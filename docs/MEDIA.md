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
| App not running | Source-specific background/foreground preparation | Reported as "Not running"; nothing is sent or started |
| Command | Bounded preparation, fallbacks and retries | One directional dispatch per tap, then observe |
| Result | success / failure | `ACKNOWLEDGED`, `SENT_UNCONFIRMED`, `UNSUPPORTED`, `SOURCE_LOST`, `FAILED` |
| Startup preparation | Yes (when enabled) | None |

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

### Not in M2 yet

- Starting a source that is not running (source-owned MediaBrowser/prepare). Use the source icon
  to open the app, or keep that source on Legacy.
- Native Topway tuner control and illumination input.

## Qualification status

Both engines are verified by host tests only (JVM/Robolectric API 29, source contracts, R8 release
envelope). **No TS18 physical trial of M2 has been run.** Before Direct can become a default it
needs, per source: warm command trials, handover trials, cold boots and ACC wakes with audible
confirmation, and a same-build comparison against Legacy.
