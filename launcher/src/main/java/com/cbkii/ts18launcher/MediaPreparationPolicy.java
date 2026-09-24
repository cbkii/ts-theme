package com.cbkii.ts18launcher;

/** Pure service-start, dispatch and coexistence decisions exercised by JVM tests. */
final class MediaPreparationPolicy {
    enum StartRoute { ROOT, NORMAL_ANDROID, FAILED }

    private MediaPreparationPolicy() {}

    static StartRoute resolveStartRoute(boolean rootAccepted, boolean normalAndroidAccepted) {
        if (rootAccepted) return StartRoute.ROOT;
        if (normalAndroidAccepted) return StartRoute.NORMAL_ANDROID;
        return StartRoute.FAILED;
    }

    static boolean callbackIsCurrent(
            boolean destroyed, int expectedGeneration, int currentGeneration,
            long nowMs, long deadlineMs) {
        return !destroyed
                && expectedGeneration == currentGeneration
                && nowMs < deadlineMs;
    }

    static boolean shouldDispatch(boolean settled, boolean alreadyDispatched,
                                  boolean callbackCurrent, boolean controllerCapable) {
        return !settled && !alreadyDispatched && callbackCurrent && controllerCapable;
    }

    static boolean shouldCoalesce(MediaCommandPolicy.Desired existing,
                                  MediaCommandPolicy.Desired incoming,
                                  boolean settled, boolean dispatched) {
        return !settled && !dispatched && existing != null && existing == incoming
                && (incoming == MediaCommandPolicy.Desired.PLAY
                || incoming == MediaCommandPolicy.Desired.PAUSE);
    }

    static boolean shouldCommitOppositePause(
            String requestedPackage, String oppositePackage,
            boolean oppositePlaying, boolean pauseSupported) {
        return requestedPackage != null
                && oppositePackage != null
                && !requestedPackage.isEmpty()
                && !oppositePackage.isEmpty()
                && !requestedPackage.equals(oppositePackage)
                && oppositePlaying
                && pauseSupported;
    }
}
