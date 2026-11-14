# TS18 launcher implementation and qualification ledger

This is a source-status ledger, not CI or installed-APK provenance. Reviewed against the integrated source containing PR #53 on 13 November 2026; refresh live heads/checks/artefacts before finalisation. [The index](README.md) identifies the branch snapshot and current guides.

IMPLEMENTED means code/tests are present, not that all checks were run during this documentation audit. PARTIAL states the remaining boundary. RETIRED is deliberate removal. Current integrated TS18 acceptance remains NOT_RUN; historical physical successes/failures are preserved in their named reports and are not erased by that status.

| Area | Source status | Current owner/evidence | Remaining boundary |
|---|---|---|---|
| Integration | IMPLEMENTED | Candidate retains subsidiary ancestry, including continuous-readiness #53 | Refresh live PR/base/head/reviews and final-head CI; do not rely on old handoff text |
| Reference adoption | IMPLEMENTED for targeted mechanisms | REFERENCE_ADOPTION, preserved plan comparison, AOSP/Taskbar/Media3 in CONTINUOUS_READINESS | Reference privileges/runtime do not qualify this unit; no requirement to repeat the whole comparison |
| Theme/Leaflet retirement | RETIRED | No product/build/runtime lane; preference migration and source/envelope guards | Historical tags and installed DoFun are preserved |
| Startup readiness | IMPLEMENTED (software) | StartupReadinessCoordinator; Maps-first admission, independent source recovery, retained first request, screen/storage/configuration triggers | Actual readiness latency, eviction/boot/ACC behaviour and resource cost need measurement |
| Media selection | IMPLEMENTED | MediaFeatureFacade; M2 default, explicit Legacy rollback, demand-gated old observers | Mixed-engine and installed-source physical behaviour |
| Warm/cold media | IMPLEMENTED (software) | DirectSourceEngine, BrowserSessionStarter, real API29 browser/Media3 fixture and deadline contracts | Real Auxio saved selection/USB/decoder and NavRadio installed contract/sound remain source/device checks; fixtures are not those apps |
| Masked Radio | IMPLEMENTED (software) | MaskedSourcePreparation/StartupReadinessLoader; pre-dispatch exact-package transaction and bounded cleanup | NavRadio splash/setup/tuner/audio, task background survival and HOME/N2 return on TS18 |
| N2 Maps | IMPLEMENTED (software) | NavigationWindowController, RootTaskSession, NavTaskBridge and narrow nav-window.sh compatibility path | Latest integrated pixels/touch, route continuity, permission flows and lifecycle |
| N3 | PARTIAL, default OFF | Isolated private-display experiment and API29 probe | Recorded emulator input forwarder unavailable; generic target embedding and TS18 feasibility not established; no priority over N2 |
| Privileged N3 host | RETIRED | n3host module/client/protocol removed | No host installation is a current campaign step |
| Catalogue/settings/UI | IMPLEMENTED for current scope | AppCatalog component/alias discovery and lazy cache; component shortcuts/import compatibility; skippable setup; scheduled Day/Night | Physical responsiveness/readability/migration. New hide/favourites/custom-sort features are not required by this audit |
| OEM read-only capabilities | IMPLEMENTED, runtime gated | oem probes and exact XTService callback-list static review | Runtime Binder/SELinux/coexistence NOT_RUN; illumination unverified; native tuner has no qualified writer |
| Diagnostics/provenance | IMPLEMENTED | Bounded timeline, collectors and existing TESTING workflows | Refresh final-head build/signer/hash and exact companion dependency; docs edits create no new APK |

## Final campaign

Use [the consolidated playbook](CONSOLIDATED_TESTING_PLAYBOOK.md) first. Qualify Maps → Music → Radio startup, one-tap audible playback, metadata/handover, masked fallback/cancel/cleanup, responsive drawer/settings, fullscreen/HOME/BACK and preserved permissions. Repeat separately after ordinary source/launcher process recreation, Android reboot, cold power and genuine ACC. Include late USB, relevant calls/projection/camera takeovers and user override. Do not manufacture a cold process with a protected-app force-stop.

Expanded matrices and repetition counts are targeted follow-ups, not a reason to repeat obsolete PR14/PR24 setup or install a retired host. A missing collector prerequisite is BLOCKED/UNKNOWN; the corresponding feature is not automatically FAIL.

## Rollback

Retain the prior same-signer APK pair/data and explicit Legacy per-source setting; turn N3 off. Resolve/preserve current HOME and keep DoFun enabled for explicit recovery. Configuration reset is a separate user action, not routine rollback; it discards settings. No protected APK/data, partition or vehicle-service mutation is part of this candidate.

## Preserved-patch completion and physical regression disposition (13 November 2026)

Copilot commit `feaf2a4` is retained by ancestry. Follow-up changes correct the detach test, exercise old-package cold-Play cancellation through the real M2 backend, use public AIDL interface methods rather than inaccessible proxy classes, withdraw a failed N3 input path, and restore explicit N0/N1 fallback choices with N2 default. No retired privileged host is restored. The removed automatic-loader policy/preferences had no runtime consumer; the explicit drawn-mask Radio transaction and its overlay setting remain. Existing UI styling is preserved.

| Owner observation | Confirmed source defects repaired | Qualification boundary |
|---|---|---|
| Startup “Audio refused…” | Literal producer not established in the inspected current launcher/Auxio sources. A source-labelled “refused the connection” browser error exists; this is a hypothesis, not proof of the reported message. Unlock now reconnects the listener; browser/audio evidence is included in the collector | Keep unresolved until the exact message and failing operation are matched. Do not call this an audio-focus fix |
| Metadata disappears after launcher transport | Legacy callbacks withdrew the display before the fresh read; an old M2 command error could replace live metadata or survive a newer successful request. Retain the display through a non-sliding 2-second read deadline while command authority is gated, clear obsolete errors, and prefer live source state | Behavioural callback/expiry regressions pass; final CI and physical comparison with external keys remain separate |
| Maps first appears, then becomes unavailable | Broker resume reply lacked the component required by the controller; later verification ignored valid peer-renderer evidence; renderer gaps authorised unnecessary task repair; HOME input revoked an already presented map | Behavioural broker/controller regressions reproduce the failures and pass after correction. Final TS18 pixels/touch/routes remain PHYSICAL RE-TEST REQUIRED |

The standalone compact-client and N3 host checks pass in this environment; the reported compact-client error did not reproduce. No test is relaxed to imply installed-app or TS18 success. The existing consolidated playbook and collector now cover the three-APK combination, exact identities, the three P0 symptoms and existing optional bounded video capture. Firmware, OEM services, production branches and device installation remain untouched.
