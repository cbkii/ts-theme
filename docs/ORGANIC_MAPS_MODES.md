# Organic Maps future driving-mode integration

This is a roadmap note only. The standalone launcher does not currently invoke Organic Maps private/JNI location-mode APIs.

The current `cbkii/organicmaps` SDK source defines `app.organicmaps.sdk.location.LocationState` with the exact `EMyPositionMode`-aligned values:

| Mode | Value | Meaning for future launcher work |
| --- | ---: | --- |
| `PENDING_POSITION` | 0 | Waiting for an initial position fix. |
| `NOT_FOLLOW_NO_POSITION` | 1 | Position unavailable and map is not following. |
| `NOT_FOLLOW` | 2 | Position available, user/map is not following. |
| `FOLLOW` | 3 | Follow current position without heading rotation. |
| `FOLLOW_AND_ROTATE` | 4 | Follow current position and rotate with heading. |

The same class exposes `nativeSwitchToNextMode()`, `getMode()` and `setDrivingViewEnabled(enabled, autoReturn, recenter)`. These are implementation details of the Organic Maps fork, not a public cross-app intent contract. The launcher must therefore not call or emulate them from a separate package unless a supported integration surface is deliberately added to both repositories.

Future implementation should preserve the authority boundary:

1. Organic Maps remains the owner of route calculation, map-engine location state and driving-view behaviour.
2. Launcher follow/free UI remains local to the lightweight HOME map unless Organic Maps is foregrounded.
3. If cross-app mode control is wanted later, add an explicit, versioned interface in `cbkii/organicmaps` rather than using reflection, root input injection, JNI symbol access or guessed broadcasts.
4. Map launcher states should map only after that interface exists: launcher follow -> Organic Maps `FOLLOW`; heading-follow -> `FOLLOW_AND_ROTATE`; manual pan/free -> `NOT_FOLLOW`. `PENDING_POSITION` and `NOT_FOLLOW_NO_POSITION` are observed states, not user commands.
5. Validate the exact Organic Maps build and TS18 runtime before enabling any automatic mode synchronisation.

Source reviewed: `cbkii/organicmaps/android/sdk/src/main/java/app/organicmaps/sdk/location/LocationState.java` on the current repository `master` during the PR #10 audit.
