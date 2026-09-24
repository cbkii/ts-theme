package com.cbkii.ts18launcher;

/** Pure service-start/coexistence decisions exercised by JVM tests. */
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
