# Media controller simplification

This change deliberately returns the HOME media strip to Android's ordinary controller model.

## Reference model

Android SystemUI and Google's media-controller sample use the same core pattern:

1. discover the active `MediaController`/session;
2. observe `MediaController.Callback` metadata and playback-state changes;
3. render `MediaMetadata` as now-playing information;
4. call `TransportControls.play()`, `pause()`, `skipToPrevious()` and `skipToNext()` directly;
5. use service/browser preparation only when no usable controller exists.

The launcher keeps TS18-specific source selection and bounded cold preparation, but those concerns no
longer replace normal transport control or the now-playing surface.

## Corrected regressions

- Previous/Next are edge-triggered commands. They must never be considered acknowledged before the
  transport call is sent. The old policy did exactly that on reconnect paths, so a command could
  report success without invoking `skipToPrevious()`/`skipToNext()`.
- Warm interactive Play/Pause uses the existing controller and does not foreground-launch Auxio or
  NavRadio+ as a readiness fallback. A cold explicit Play with no usable controller first uses the
  MediaController/MediaBrowser/service preparation path; if that cannot produce a controller,
  it reports a bounded readiness failure. It never launches a source Activity or masks one.
- The ticker renders media snapshots only. Launcher readiness messages, command failures and source
  app labels are not now-playing metadata and cannot overwrite it.
- If a radio notification/session supplies the app label as primary text and useful station/channel
  text as secondary metadata, the useful text is promoted and the app label is discarded.
- Radio notification fallback is associated with the station observed when the notification is
  received. Once the session reports a different station, the old notification metadata is not
  merged into that session.

## Physical qualification

Repository tests cannot prove TS18 runtime publication. Validate on the exact unit with Auxio and
NavRadio+ separately:

- warm active session: Play/Pause must not foreground the app;
- Previous and Next must each cause exactly one transition;
- Auxio: ticker must show track and artist from the active session and survive HOME returns;
- NavRadio+: ticker must show station/channel/frequency when published by its session or ongoing
  notification and must never show only `Radio`/`NavRadio+`;
- pause/resume, player process restart, launcher restart, cold boot and ACC sleep/wake;
- if metadata is absent, capture the media-session and ongoing-notification fields rather than
  inventing a private radio metadata contract.
