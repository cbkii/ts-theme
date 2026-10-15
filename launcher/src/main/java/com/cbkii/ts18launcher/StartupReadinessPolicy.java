package com.cbkii.ts18launcher;

/** Pure startup-readiness decisions (boot/wake classification, dedupe, staging gates). */
final class StartupReadinessPolicy {
    enum Trigger { PROCESS_START, BOOT_COMPLETED, SCREEN_ON, HOME_RESUME, SOURCE_LOST,
        STORAGE_MOUNT, ACC_ON }
    enum Session { FULL_BOOT, WAKE, HOME_RETURN }
    enum Mode { BACKGROUND_ONLY, AUTOMATIC_FALLBACK, AGGRESSIVE }
    enum State { UNAVAILABLE, STARTING, READY, ACTIVE, FAILED, BLOCKED, UNCERTAIN }

    static final long TRIGGER_DEBOUNCE_MS = 1500L;
    static final long BACKGROUND_WINDOW_MS = 5750L;
    static final long STAGE_DWELL_MS = 6000L;

    private StartupReadinessPolicy() {}

    static Mode mode(String value) {
        if ("background".equals(value)) return Mode.BACKGROUND_ONLY;
        if ("aggressive".equals(value)) return Mode.AGGRESSIVE;
        return Mode.AUTOMATIC_FALLBACK;
    }

    /** Screen-on is only a wake; only a verified ACC event is ACC_ON. */
    static Session classify(Trigger trigger, boolean newBootSinceLastHandled) {
        if (newBootSinceLastHandled) return Session.FULL_BOOT;
        if (trigger == Trigger.SCREEN_ON || trigger == Trigger.ACC_ON) return Session.WAKE;
        return Session.HOME_RETURN;
    }

    /** Drops duplicate triggers of the same kind arriving inside the debounce window. */
    static boolean debounced(Trigger trigger, Trigger last, long nowMs, long lastMs) {
        return last == trigger && lastMs > 0L && nowMs - lastMs < TRIGGER_DEBOUNCE_MS;
    }

    static boolean isReadyState(State state) {
        return state == State.READY || state == State.ACTIVE;
    }

    /** One in-flight admission per source; healthy sources are never re-admitted. */
    static boolean shouldAdmit(State state) {
        return state != State.STARTING && state != State.BLOCKED && state != State.UNAVAILABLE
                && !isReadyState(state);
    }

    /** Foreground Activity staging is a bounded fallback, never the first method. */
    static boolean mayStageForeground(Mode mode, Session session, boolean backgroundFailed,
            boolean homeResumed, boolean screenInteractive, boolean audioIdle,
            boolean alreadyStagedThisGeneration) {
        if (mode == Mode.BACKGROUND_ONLY || !backgroundFailed || alreadyStagedThisGeneration) return false;
        if (!homeResumed || !screenInteractive || !audioIdle) return false;
        if (mode == Mode.AUTOMATIC_FALLBACK) return session != Session.HOME_RETURN;
        return true;
    }

    /** HOME is restored only when this transaction started with HOME in front. */
    static boolean shouldRestoreHome(boolean homeWasForeground, boolean cancelled,
            boolean screenInteractive) {
        return homeWasForeground && !cancelled && screenInteractive;
    }
}
