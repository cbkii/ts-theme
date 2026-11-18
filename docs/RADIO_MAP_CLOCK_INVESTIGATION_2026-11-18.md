# Separate Radio, Maps and clock investigation

This work starts from main `fc4c990985a97d166a350c457268fa5157e5e306`, after PR #44 and
Auxio #287/#289 were merged. It is separate from those remediations. The owner reports that
black map content during resizing has existed since the first Magisk-based validation campaigns;
it is not a new regression and the collector's involvement is not established as a cause.

## Confirmed launcher gaps

- The new Auxio service-only preparation entry returns HOME without MainActivity. The masked
  transaction still required a foreground source sample and could expire despite usable Music.
  Accept the exact ready Music session at verified HOME, without an unnecessary focus command.
- HOME was sampled before a 300 ms survival delay. A foreign task could become foreground during
  that delay yet the stale sample still admitted completion. Re-read HOME after the delay.
- The prior-refused-browser method switch applied to Music only. Explicit Direct Radio demand
  now uses the same existing package-gated mask fallback after observed refusal, preserving the
  original tap deadline. Healthy sessions, Legacy and skip commands retain their existing paths.
- The opaque mask covered the full display and merely inset its controls. Its window now stays
  inside the measured app safe area. This removes an app-owned SystemUI occlusion risk; it does
  not establish the cause of the reported clock disappearance or alter protected SystemUI.

The initial installed Radio service refusal remains an unproved bind/root boundary. This change
uses the already-authorised, observed on-demand fallback; no guessed root service/tuner protocol,
passive Radio activation, transport replay or second playback authority is introduced.
The bounded read-only collector now hashes the installed SystemUI APK/splits as well as the
existing packages and samples NavRadio service state during early admission. Existing status-bar
policy, window/overlay and continuous video capture remain the clock evidence. No protected
SystemUI write or private notification/view-hierarchy dump is added.

## Maps boundary

Task/window geometry, surface attachment, native viewport dimensions, frame presentation and
visible pixels are separate. The existing Organic Maps geometry coordinator stops recovery once
dimensions match. The longstanding black-content case needs presentation/lifecycle investigation
in that owner; another launcher task launch or route rebuild is not an acceptable recovery.

## Verification and acceptance

Host tests exercise the actual transaction, fresh HOME check, service-only completion, cancellation,
permission yield, original Radio tap budget and exact opaque-window bounds. API29 WindowManager
coverage and physical TS18 remain separate. No new APK has been installed or physical acceptance
claimed. Retain the final signed rollback pair and qualify first Radio/Music demand, foreign-focus
changes, Maps resizing with actual pixels, clock visibility and wake in one final campaign.

## Plan versus consolidated fixes

The original plan's 19 issue IDs remain the acceptance register. Source completion and automated
coverage are distinct from physical acceptance. Auxio dev `6a02e24` includes #287 and #289;
launcher main `fc4c990` includes #44. New launcher work is [#56](https://github.com/cbkii/ts-theme/pull/56)
and Maps owner work is [#85](https://github.com/cbkii/organicmaps/pull/85). No new Auxio change is
needed for this pass. Full source refs are in the consolidation and PR metadata.

| ID | Consolidated implementation / separate follow-up | Automated boundary and remaining acceptance |
| --- | --- | --- |
| M1 | Auxio prepare-only entry starts its existing service; browser restore admits saved legacy membership. #56 accepts its service-only HOME outcome. | Real API29 pair and actual holder tests cover distinct boundaries; exact initial bind refusal remains unknown. First device HOME Play is unqualified. |
| M2 | Existing HOME_PLAY deadline/restore owner retained; no MainActivity prerequisite or unbounded replay. | Deadline/cancellation/late capability and real decoder/browser tests; actual TS18 first audio remains unrun. |
| M3 | Auxio retains the immutable canonical decoder-item Song lease through Next/Previous. | Actual holder: 1,069 items, 30 Next and 30 Previous after cached handoff. Player/persistence are doubles; real-device publication still unqualified. |
| M4 | Source identity fix plus token-owned launcher observation and strict command acknowledgement. | Real pair injects four publication gaps, independent of large canonical test. Notification/launcher/audible convergence needs device evidence. |
| M5 | Existing configured rewind semantics retained. Source identity repairs enable truthful confirmation. | Policy/transport checks are not the complete audible Previous matrix. No unsupported claim that every reported Previous case was a defect. |
| M6 | Legacy queue restore no longer depends on a primitive descriptor or rich browse readiness; prepare entry avoids Compose. | These are confirmed path corrections, not a measured startup speed improvement or established first regression commit. |
| R1 | #56 extends observed-refusal method switching to explicit Direct Radio Play, retaining one original deadline and mask gates. | Prior-refused, healthy, Legacy, skip and denied-staging cases covered. Cold bind/root cause and first audible Play remain unresolved. |
| R2 | Replacement playback still gates opposite-source pause. | Existing handover/deadline contracts; one-second reported arrival is not a benchmark and no new latency claim is made. |
| N1 | Consolidated N2 warm task/presentation ownership and effective bounds checks; #85 separates dimensions from frame evidence. | Host/framework contracts; actual compact pixels, touch and retained-Recents recovery remain unqualified. |
| N2 | Fullscreen avoids refused task resize when already correct; stale exclusive-stack bounds use guarded readback. | Physical-shape shell fixtures/framework checks. Zero recurrence on TS18 remains unproved. |
| N3 | #85 invalidates pre-resize swap evidence, fences in-flight swaps and adds bounded same-Surface recovery. | Actual EGL owners and coordinator tested. Longstanding black pixels, occlusion and driver cause remain physical questions. |
| N4 | Collector guide names the existing bottom fullscreen icon and separates BACK from HOME. | Guide/source review and packaging checks; the two physical lifecycle sequences remain distinct. |
| U1 | Loader shows real progress and Continue to Home; #56 verifies current HOME/session after settling. | Actual loader/transaction tests and API29 boundary coverage; no new physical UX acceptance. |
| U2 | #56 confines the opaque window to measured safe area; collector hashes SystemUI and captures policy/window/overlay/video evidence. | Exact bounds and cancellation tested. This removes an app occlusion risk; clock cause and visibility after shade/wake remain UNKNOWN. |
| P1 | Existing shuffle persistence implementation retained; issue #288 remains open. | Queue/persistence tests do not replace deliberate committed OFF/ALL/order/source/position checks after restart and reboot. Lowest priority. |
| Q1 | Collector records consecutive bounded segments with encoder status/gaps, replacing sparse samples. | Module/packager host checks. Actual encoder start, timestamp continuity and phase coverage require a new captured run. |
| Q2 | Per-run preflight, explicit stop reasons, bounded caps and sealing are implemented. | Host collector/packager checks; collector success cannot mark app acceptance passed. |
| Q3 | Separate read-only same-boot wake observer and documented power procedure. | Not an ACC actuator or electrical-cold detector. New kernel boot, cold power and ACC remain separately unqualified. |
| Q4 | External force-stop and expected collector termination are classified separately from spontaneous failures. | Original logs retain those events. Dependent session loss is not counted as an independent crash. |

The signed artefact, installation, focused TS18 and lifecycle columns are **NOT_RUN for this new
scope across all rows**. No old APK or historical physical success is relabelled as qualification
of these new heads. Repository CI is tracked on each final PR head; publication of an installable
TESTING set must preserve prior signed rollback snapshots and match source/build/package/signer/
SHA-256. The collector must be packaged from the final pinned selection, never the unpinned template.
