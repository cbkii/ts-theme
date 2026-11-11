# Media control and readiness

Music and Radio each have one selected engine through `MediaFeatureFacade`. M2 Direct is the TESTING default when no preference exists. Explicit `legacy` remains a compatibility rollback; malformed/future values refuse to Legacy. An unselected engine cannot command, prepare or publish for that source.

The playback application owns its service, queue, MediaSession, notification and audio focus. The launcher never creates another player/session or writes the tuner.

## Authority and lifecycle

M2 controls exactly the configured package and one pinned session token. Its registry deduplicates tokens across notification-listener discovery and source-owned MediaBrowser connections. Notification access is required for listener discovery, but an authenticated browser-owned token carries its own authority. Revoking listener access removes unrelated listener-only sessions while retaining a still-connected source-owned session. Disconnect, source death, reconfiguration and stale attempt callbacks cannot transfer ownership to a different source.

The browser connector resolves one enabled, exported source service, verifies the returned controller package and shares a binding only between owners of that same package. It never opens an Activity or dispatches Play itself. Unsupported or ambiguous service discovery is a truthful limitation.

## Commands and bounded cold Play

Resolve Play/Pause direction once at tap time. One operation dispatches to one token; pending duplicate taps join it. Confirm directional playback from the owning session and skips from changed item identity. No acknowledgement before the deadline means SENT_UNCONFIRMED, not rejection or an automatic retry. Handover pauses the former source only after replacement playback is observed.

A confirmed loaded session may receive ordinary Play. An uncertain cold source must advertise `org.oxycblt.auxio.action.HOME_PLAY_V1`. M2 then sends `protocol=1` and a monotonic `deadline_elapsed_ms` no later than the original tap cutoff or five seconds after browser acquisition, whichever is earlier; service binding consumes that same operation budget. Without the capability, cold Play is unsupported rather than silently becoming unbounded ordinary PLAY.

Auxio's companion implementation enforces the deadline through queue/raw restoration and decoder readiness, excludes launch-autoplay upgrades of passive/expired restoration and prevents detached deadline-free fallbacks. It requests focus/Play only when decoder readiness can commit inside that window. Timely software commit is separate from the first audible hardware sample.

## Startup and compatibility

`StartupReadinessCoordinator` classifies boot/wake/HOME events and initiates independent background readiness epochs. M2 may bind its source's browser and send advertised PREPARE once when no item is loaded. Preparation never intentionally Plays, launches an Activity or reserves navigation focus. Music/Radio failure does not hold up Maps. There is no automatic source-Activity staging lane.

With both sources on M2, legacy process/listener observers and the visible legacy readiness tick are idle. Explicit Legacy retains its background compatibility paths. The installed notification-listener component stays present for discovery; idle legacy machinery does not acquire extra observation just because that component is enabled.

The available NavRadio reference is minSdk32 and does not establish the installed API29 protocol. A plain bind to Media3 is not a controller connection. Existing source-owned sessions remain supported; when no substantiated cold path exists the source icon is the explicit app-opening fallback. No guessed tuner operation, root service launch or hidden foreground start is added.

## Evidence and acceptance

Behavioural JVM/Robolectric tests cover token ownership, listener revocation, unsupported cold sources, original-budget accounting, cancellation, handover and no duplicate transport. The API29 IPC fixture checks a real framework browser/controller connection, silent PREPARE, deadline-action advertisement/delivery, expired refusal, reconnect and process death. It is a fixture, not Auxio or NavRadio.

Canonical CI, exact signed artefact identity, installation, audible output, USB, cold boot and ACC remain separate qualification levels. The owner deferred physical TS18 acceptance to the consolidated TESTING campaign; it does not block implementation. See [architecture](ARCHITECTURE.md), [private-contract review](PRIVATE_CONTRACT_REVIEW_2026-11-12.md) and [physical playbook](CONSOLIDATED_TESTING_PLAYBOOK.md).
