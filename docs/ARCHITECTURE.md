# Architecture

## Controlling layers

| Concern | Authority |
| --- | --- |
| Theme discovery | Android PackageManager and DoFun's RePlugin scanner |
| Layout/resources | DoFun theme parser in `com.dofun.variety` |
| Map | DoFun `desktop_window` and selected navigation app |
| Radio | DoFun/Topway radio source and the installed radio app |
| Generic media | DoFun media services, Android MediaSession and observed Topway compatibility paths |
| Status/navigation bars | SystemUI, outside this theme |

## Lane A — clean declarative RePlugin theme

The `theme` Android application module compiles the project-authored JSON, compatibility resource
names and visual assets into a unique package. It reproduces only the cross-sample host contract:

- `launcher.variety.theme.plugin` metadata and matching plug-in/package ID;
- Android 10-compatible min/target/compile SDK values;
- the declarative theme entry point and resource aliases DoFun resolves at runtime;
- the small Apache-2.0 Qihoo360 RePlugin 2.3.4 loader payload used by all audited samples.

The RePlugin AAR is fetched from its official repository and accepted only at its pinned SHA-256.
No vendor APK, resource, DEX or signing identity is a build input. The output is signed with the
project's own key and must pass package/version/signer/resource/loader checks before publication.

Lane A can prove clean packaging and source geometry in CI. The TS18 must still prove third-party
signer acceptance, discovery, rendering, persistence and host-owned touch behaviour.

A declarative theme cannot add executable ticker/controller logic.

## Lane B — broad media integration adapter

This separately developed lane is used only if the installed host's native behaviour does not
satisfy the fixed strip. It must not be folded into the theme merely to reach v1.0.0.

### Inputs

The adapter should recognise, rank and correlate:

1. DoFun `RemoteMediaService` state and its notification listener;
2. Android active MediaSessions in system priority order;
3. public `com.android.music.metachanged` and `playstatechanged` state;
4. Topway `com.tw.music.info`, progress and command broadcasts;
5. verified `com.tw.service.xt.CommandService` callbacks;
6. current selected music target from DoFun's exported provider;
7. an optional genuine-stock relay only when stock selection is positively proven.

This mirrors the integration breadth already developed in
[Auxio-TS](https://github.com/cbkii/Auxio-TS): Android MediaSession/notification support,
Topway metadata and commands, CommandService callbacks, selected-target diagnostics and an optional
stock-scoped relay.

### Normalised state

One internal snapshot should contain:

- target package/component and launch intent;
- playback state and allowed actions;
- artist, title and the rendered `Artist - Track Name` string;
- session/source generation and update time;
- evidence source used for the snapshot.

State from several lanes may confirm the same target, but must not create several control owners.

### Selection policy

1. Retain the current playing target while it remains valid.
2. Prefer a playing/buffering Android session over paused/stopped sessions.
3. Respect an explicit DoFun selected target where the exported provider proves it.
4. Exclude call/telecom sessions.
5. Treat ambiguous or stale evidence as unknown rather than guessing.
6. Expose diagnostics explaining which lane won.

### View behaviour

- Radio, media and date occupy one continuous top row.
- The map begins at y=119 and is never an underlay for another theme widget.
- Media text scrolls continuously only when it exceeds the available width.
- Tapping media text/unused media space opens the selected/default music app.
- Tapping radio text/unused radio space opens the selected/default radio app.
- Button hit regions consume their own tap and never also trigger app launch.

If the host does not already provide these behaviours, an exact-version DoFun view adapter may attach
the ticker and click listeners after the theme view is created.

### Command dispatch

Each press is correlated and dispatched exactly once through the highest-confidence available lane.
Do not broadcast blindly across every lane. The adapter may fail over after a bounded rejection or
timeout, but a late acknowledgement must not produce a second action.

### Safety boundary

The adapter may be broad in compatibility but must remain fail-open and reversible:

- no second player, queue, playback service, MediaSession, notification or audio-focus owner;
- exact package/version/method guards for any DoFun hook;
- log-only discovery stage before view or command changes;
- bounded worker threads, IPC timeouts and reconnects;
- independent kill switch and automatic disable after repeated host failures;
- no system partition, MCU, CAN or forced Topway source writes.

Radio remains a separate authority and must not be represented as a synthetic Android media session.
