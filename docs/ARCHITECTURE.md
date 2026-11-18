# Launcher architecture

The launcher is an API29 framework-Java HOME application. It owns presentation and orchestration, not playback, routing, tuner or vehicle authority.

| Owner | Responsibility |
|---|---|
| `MediaFeatureFacade` | Exactly one selected engine per Music/Radio source; observed-playback handover |
| `DirectSourceEngine` | One exact package/token, directional commands, bounded cold Play and source-local readiness |
| `DirectSessionRegistry` / `FrameworkSessions` | Deduplicated membership and token callbacks; browser authority independent of notification discovery |
| `BrowserSessionStarter` | Source-owned exported MediaBrowser connection, stale-attempt and owner lifetime |
| Auxio-TS | Queue restoration, HOME_PLAY_V1 deadline through decoder readiness, one player/session/focus owner |
| `StartupReadinessCoordinator` | Maps-first admission, independent Music/Radio readiness, bounded recovery and one masked source lease |
| `MaskedSourcePreparation` / `StartupReadinessLoader` | Explicit cold Radio fallback: opaque frame, exact source launch, observed readiness, same-task HOME return and cleanup |
| `NavigationWindowController` / N2 backend | Task admission, lifecycle, warm adoption, recovery and presentation |
| `RootTaskSession` / `NavTaskBridge` | Bounded persistent broker for task observation/verification, warm resume, parking, HOME return and background fullscreen |
| `nav-window.sh` | Narrow remaining cold-launch claim/bootstrap and explicit fullscreen/OEM compatibility operations |
| `AppCatalog` | Background component/alias discovery, deterministic entries, shared lazy bounded icon cache and package invalidation |
| `ConfigurationCodec` | Version 2 export, v1/v2 import, strict known values, component shortcuts and explicit unknown-key preview |
| `PerfSampler` / `StartupTimeline` | Explicit local 10-second frame/CPU/PSS sample and bounded process timeline |

M2 is the default. Explicit Legacy remains a rollback for source-specific compatibility; its observers are demand-gated. The operator authorised masked Activity staging for closed apps when their supported service path cannot become ready. The current fallback is package-gated to NavRadio and explicit Play; passive tuner activation is unqualified and remains gated. See [readiness contract and acceptance](CONTINUOUS_READINESS.md).

Cold Play waits for the owning session's transport capabilities within the original elapsedRealtime budget. Auxio's unloaded HOME Play requires its advertised HOME_PLAY_V1 deadline; an ordinary third-party Play contract may itself load/resume a station. Confirmed loaded media requires an item identity and PAUSED/PLAYING state, not a stale title. Dispatch without playback acknowledgement is SENT_UNCONFIRMED; it never triggers a duplicate or premature opposite-source pause. A Radio fallback is allowed only before any transport dispatch. Auto-resume during staging fulfils Play without a toggle.

N2 remains primary. Broker task ownership and actual top Activity are distinct. Permission/settings and bootstrap Activities retain their task; failed observations do not authorise relaunch. The shell compatibility path remains because its cold-start claims and Android10 fullscreen delivery responsibilities are not interchangeable with a mode change. Geometry verification does not claim pixels or touch.

N3 remains isolated/default-off. Its inert privileged host, Binder protocol and client were removed. Existing API29 evidence is view-only without input forwarding; it does not justify replacing N2.

Private-contract evidence and isolated NavRadio limitations: [review](PRIVATE_CONTRACT_REVIEW_2026-11-12.md). Original research and authorised phases: [implementation plan](TS-THEME_IMPLEMENTATION_PLAN_2026-11-12.md). Physical acceptance: [consolidated playbook](CONSOLIDATED_TESTING_PLAYBOOK.md).

No protected Topway/DoFun/SystemUI/vehicle modification is part of this architecture. Automated qualification and physical TS18 acceptance are separate.
