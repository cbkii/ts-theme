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

    /** NavRadio's silence-avoiding cold-play guard (measured from service admission). */
    static final long NAVRADIO_COLD_PLAY_NOT_BEFORE_MS = 3200L; // keep equal to MediaSourceBootstrapper
    static final long NAVRADIO_ADMISSION_MS = 1500L;
    static final long ACK_WINDOW_MS = 1600L;
    static final long DEADLINE_SLACK_MS = 500L;

    /**
     * Separate startup, transport and acknowledgement budgets. The overall deadline must always
     * outlast the cold-play guard plus the acknowledgement window, otherwise the guard is silently
     * dropped and the single Play is dispatched too early (silence).
     */
    static long commandTimeoutMs(String packageName, long baseTimeoutMs) {
        if (!MediaSourceAdapter.NAVRADIO_PACKAGE.equals(packageName)) return baseTimeoutMs;
        long cold = NAVRADIO_ADMISSION_MS + NAVRADIO_COLD_PLAY_NOT_BEFORE_MS
                + ACK_WINDOW_MS + DEADLINE_SLACK_MS;
        return Math.max(baseTimeoutMs, cold);
    }
}
