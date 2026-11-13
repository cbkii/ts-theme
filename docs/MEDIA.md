# Media control and readiness

Music and Radio each have one selected engine through `MediaFeatureFacade`. M2 Direct is the TESTING default when no preference exists. Explicit `legacy` remains a compatibility rollback; malformed or future stored values also resolve to Legacy, so a corrupted preference or a downgrade never strands a source on an unavailable engine. An unselected engine cannot command, prepare or publish for that source.

The playback application owns its service, queue, MediaSession, notification and audio focus. The launcher never creates another player/session or writes the tuner.

## Authority and lifecycle

M2 controls exactly the configured package and one pinned session token. Its registry deduplicates tokens across notification-listener discovery and source-owned MediaBrowser connections. Notification access is required for listener discovery, but an authenticated browser-owned token carries its own authority. Revoking listener access removes unrelated listener-only sessions while retaining a still-connected source-owned session. Disconnect, source death, reconfiguration and stale attempt callbacks cannot transfer ownership to a different source.

The browser connector resolves one enabled, exported source service, verifies the returned controller package and shares a binding only between owners of that same package. It never opens an Activity or dispatches Play itself. Unsupported or ambiguous service discovery is a truthful limitation.

## Commands and bounded cold Play

Resolve Play/Pause direction once at tap time. One operation dispatches to one token; pending duplicate taps join it. Confirm directional playback from the owning session and skips from changed item identity. No acknowledgement before the deadline means SENT_UNCONFIRMED, not rejection or an automatic retry. Handover pauses the former source only after replacement playback is observed. A newer Play demand on the other source cancels older undispatched commands through their selected backend without detaching healthy session observation; it does not pause existing playback before replacement is ready.

Every Auxio M2 Play, including cached paused items, requires the advertised `org.oxycblt.auxio.action.HOME_PLAY_V1` contract and carries its original absolute deadline. Metadata or PAUSED state does not establish decoder readiness. An unloaded third-party source may use its ordinary advertised Play contract. An uncertain cold Auxio source must advertise `org.oxycblt.auxio.action.HOME_PLAY_V1`. M2 then sends `protocol=1` and a monotonic `deadline_elapsed_ms` no later than the original tap cutoff or five seconds after browser acquisition, whichever is earlier; service binding consumes that same operation budget. An early capability snapshot is observed until the original bound expires; it never silently upgrades an unloaded Auxio request to unbounded ordinary PLAY.

Auxio's companion implementation enforces the deadline through queue/raw restoration and decoder readiness, excludes launch-autoplay upgrades of passive/expired restoration and prevents detached deadline-free fallbacks. It requests focus/Play only when decoder readiness can commit inside that window. Timely software commit is separate from the first audible hardware sample.

## Startup and compatibility

`StartupReadinessCoordinator` admits Maps first, then independent Music/Radio background readiness. M2 may bind an owning browser/Media3 service and send advertised PREPARE without playing. It tracks observed readiness, engine/package changes, source loss and screen wake with bounded recovery. On explicit NavRadio demand only, a non-dispatched failure may enter the operator-authorised masked Activity transaction; that transaction temporarily leases presentation, observes the exact source and verifies HOME plus the surviving session before releasing the mask. A dispatched/unconfirmed action is never replayed. See [continuous readiness](CONTINUOUS_READINESS.md).

With both sources on M2, legacy process/listener observers and the visible legacy readiness tick are idle. Explicit Legacy retains its background compatibility paths. The installed notification-listener component stays present for discovery; idle legacy machinery does not acquire extra observation just because that component is enabled.

The available NavRadio reference is minSdk32 and does not establish the installed API29 protocol. A plain bind to Media3 is not a controller connection. Existing source-owned sessions remain supported. Configured NavRadio can use the bounded masked Activity fallback on explicit demand; other unsupported sources report the unavailable contract and remain explicitly openable through their icons. No guessed tuner operation or root service launch is added. The source-specific NavRadio path still requires exact-device qualification.

## Evidence and acceptance

Behavioural JVM/Robolectric tests cover token ownership, listener revocation, unsupported cold sources, original-budget accounting, cancellation, handover and no duplicate transport. The API29 IPC fixture checks a real framework browser/controller connection, silent PREPARE, deadline-action advertisement/delivery, expired refusal, reconnect and process death. It is a fixture, not Auxio or NavRadio.

Canonical CI, exact signed artefact identity, installation, audible output, USB, cold boot and ACC remain separate qualification levels. The owner deferred physical TS18 acceptance to the consolidated TESTING campaign; it does not block implementation. See [architecture](ARCHITECTURE.md), [private-contract review](PRIVATE_CONTRACT_REVIEW_2026-11-12.md) and [physical playbook](CONSOLIDATED_TESTING_PLAYBOOK.md).

## Legacy (M1) freeze

From the 2026-11-13 integration remediation, M1 Legacy receives no new behaviour. It remains an explicit, install-time rollback until M2 passes physical qualification, then it is removed. Direct (M2) changes and their tests are listed in [the remediation note](INTEGRATION_REMEDIATION_2026-11-13.md).
