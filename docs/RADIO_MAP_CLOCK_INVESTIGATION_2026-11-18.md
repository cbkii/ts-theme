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
