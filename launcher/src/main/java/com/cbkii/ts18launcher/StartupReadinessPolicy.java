package com.cbkii.ts18launcher;

/** Pure, deterministic startup-readiness rules. No Android dependencies. */
final class StartupReadinessPolicy {
    private StartupReadinessPolicy() { }

    enum State { UNAVAILABLE, STARTING, READY, ACTIVE, FAILED, BLOCKED, UNCERTAIN }

    /** Screen-on is never ACC-on; no verified TS18 ACC event exists, so none is modelled here. */
    enum Trigger { BOOT, WAKE, RESUME, USB_MOUNT, SESSION_LOST, RESTART }

    enum Mode {
        BACKGROUND, AUTOMATIC, AGGRESSIVE;
        static Mode parse(String value) {
            if ("background".equals(value)) return BACKGROUND;
            if ("aggressive".equals(value)) return AGGRESSIVE;
            return AUTOMATIC;
        }
    }

    static final long DEDUPE_WINDOW_MS = 1500L;
    static final long BACKGROUND_DEADLINE_MS = 9000L;
    static final long FOREGROUND_DEADLINE_MS = 9000L;
    static final long FAILED_BACKOFF_MS = 30000L;

    /** Full boot only when the persisted boot count differs; otherwise a same-boot trigger. */
    static Trigger classifyProcessStart(int storedBootCount, int currentBootCount) {
        if (currentBootCount < 0 || storedBootCount < 0 || storedBootCount != currentBootCount)
            return Trigger.BOOT;
        return Trigger.RESTART;
    }

    /** Overlapping triggers inside the dedupe window collapse, except a stronger boot/loss event. */
    static boolean shouldReconcile(Trigger trigger, long nowMs, long lastRunMs, Trigger lastTrigger) {
        if (lastRunMs <= 0L) return true;
        if (nowMs - lastRunMs >= DEDUPE_WINDOW_MS) return true;
        return rank(trigger) > rank(lastTrigger);
    }

    private static int rank(Trigger trigger) {
        if (trigger == null) return 0;
        switch (trigger) {
            case BOOT: return 4;
            case SESSION_LOST: case USB_MOUNT: return 3;
            case WAKE: case RESTART: return 2;
            default: return 1;
        }
    }

    /** Whether a source with this state should receive a new admission for this trigger. */
    static boolean needsAdmission(State state, Trigger trigger, long nowMs, long lastFailureMs) {
        switch (state) {
            case READY: case ACTIVE: case STARTING: case BLOCKED: case UNAVAILABLE: return false;
            case FAILED: case UNCERTAIN:
                if (trigger == Trigger.BOOT || trigger == Trigger.WAKE || trigger == Trigger.USB_MOUNT
                        || trigger == Trigger.SESSION_LOST || trigger == Trigger.RESTART) return true;
                return lastFailureMs <= 0L || nowMs - lastFailureMs >= FAILED_BACKOFF_MS;
            default: return true;
        }
    }

    /** Healthy sources are never touched; a lost session demotes READY back to needing admission. */
    static State onSessionLost(State state) {
        return state == State.READY || state == State.ACTIVE ? State.UNCERTAIN : state;
    }

    /**
     * Foreground Activity bootstrap is a fallback, only after background readiness failed, only once per
     * startup generation, only while HOME owns the transaction and never during a call/user command.
     */
    static boolean foregroundAllowed(Mode mode, boolean radio, State state, boolean attemptedThisGeneration,
                                     boolean homeResumed, boolean userBusy, boolean callActive,
                                     boolean otherForegroundTransaction) {
        if (mode == Mode.BACKGROUND || attemptedThisGeneration || !homeResumed || userBusy
                || callActive || otherForegroundTransaction) return false;
        if (radio && mode != Mode.AGGRESSIVE) return false;
        return state == State.FAILED || state == State.UNCERTAIN || state == State.UNAVAILABLE;
    }

    /** The transient loader is optional and needs an already-granted overlay app-op. */
    static boolean loaderAllowed(Mode mode, boolean overlayGranted) {
        return mode == Mode.AGGRESSIVE && overlayGranted;
    }

    /** Per-source state record: method, identity, generation, first failing boundary, elapsed time. */
    static final class Source {
        final String packageName;
        State state = State.UNAVAILABLE;
        String method = "";
        String firstFailingBoundary = "";
        int generation;
        long startedMs;
        long settledMs;
        long lastFailureMs;
        boolean foregroundAttempted;

        Source(String packageName) { this.packageName = packageName == null ? "" : packageName; }

        /** One in-flight admission per source: returns false when already STARTING. */
        boolean begin(String method, long nowMs, boolean newGeneration) {
            if (state == State.STARTING) return false;
            if (newGeneration) { generation++; foregroundAttempted = false; }
            this.method = method; state = State.STARTING; startedMs = nowMs; settledMs = 0L;
            firstFailingBoundary = "";
            return true;
        }

        void ready(long nowMs) { state = State.READY; settledMs = nowMs; firstFailingBoundary = ""; }

        void fail(State terminal, String boundary, long nowMs) {
            if (firstFailingBoundary.isEmpty()) firstFailingBoundary = boundary;
            state = terminal; settledMs = nowMs; lastFailureMs = nowMs;
        }

        long elapsedMs(long nowMs) { return (settledMs > 0L ? settledMs : nowMs) - startedMs; }

        String summary(long nowMs) {
            return packageName + " " + state + " method=" + method + " gen=" + generation
                    + " elapsed=" + elapsedMs(nowMs) + "ms"
                    + (firstFailingBoundary.isEmpty() ? "" : " firstFail=" + firstFailingBoundary);
        }
    }
}
