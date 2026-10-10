package com.cbkii.ts18launcher;

/** Bounded HOME-only recovery budget; resets on a real HOME return or explicit Retry. */
final class NavigationRecoveryPolicy {
    private static final long[] DELAYS_MS = {350L, 900L, 1800L, 3000L, 5000L, 8000L};
    private static final long BUDGET_MS = 45000L;
    private long startedAt = -1L;
    private int attempts;

    void reset() { startedAt = -1L; attempts = 0; }

    long nextDelay(String code, long now) {
        if (!recoverable(code) || attempts >= DELAYS_MS.length) return -1L;
        if (startedAt < 0L) startedAt = now;
        long delay = DELAYS_MS[attempts];
        if (now - startedAt + delay >= BUDGET_MS) return -1L;
        attempts++;
        return delay;
    }

    static boolean recoverable(String code) {
        if (code == null) return false;
        switch (code) {
            case "TASK_OBSERVATION_UNCERTAIN": case "TASK_NOT_FOUND": case "TASK_REPLACED":
            case "FOREGROUND_CHANGED": case "TASK_STATE_UNREADABLE": case "COMPONENT_UNKNOWN": case "BOOTSTRAP_PENDING":
            case "LAUNCH_PENDING": case "PHASE_TIMEOUT": case "PRESENTATION_UNCONFIRMED":
            case "FREEFORM_TRANSITION_REJECTED": case "NATIVE_NOT_FOREGROUND":
            case "BOUNDS_MISMATCH": case "WINDOWING_MODE_MISMATCH": case "FOCUS_FAILED":
            case "RESIZE_FAILED": case "FREEFORM_LAUNCH_FAILED": case "NO_RESPONSE":
                return true;
            default: return false;
        }
    }

    static boolean ordinaryFullscreenAllowed(String code) {
        return "ROOT_UNAVAILABLE".equals(code) || "ROOT_REQUIRED".equals(code)
                || "INSTALL_TIMEOUT".equals(code) || "FREEFORM_LAUNCH_UNSUPPORTED".equals(code)
                || "FULLSCREEN_LAUNCH_UNSUPPORTED".equals(code);
    }
}
