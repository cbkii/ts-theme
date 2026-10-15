package com.cbkii.ts18launcher;

/** Pure startup-readiness decisions: trigger classification, per-source state and admission. */
final class StartupReadinessPolicy {
    enum State { UNAVAILABLE, STARTING, READY, ACTIVE, FAILED, BLOCKED, UNCERTAIN }
    enum Mode { BACKGROUND, AUTOMATIC, AGGRESSIVE }
    enum Kind { FULL_BOOT, SAME_BOOT_WAKE, HOME_RETURN }

    static final long BACKGROUND_WINDOW_MS = 8000L;
    static final long FOREGROUND_WINDOW_MS = 9000L;
    static final long ACTIVITY_SETTLE_MS = 1500L;

    private StartupReadinessPolicy() {}

    static Mode mode(String value) {
        if ("background".equals(value)) return Mode.BACKGROUND;
        if ("aggressive".equals(value)) return Mode.AGGRESSIVE;
        return Mode.AUTOMATIC;
    }

    /** Screen-on alone is a wake observation, never proof of vehicle ACC. */
    static Kind classify(long bootId, long lastBootId, boolean wakeSinceLastHome) {
        if (bootId != lastBootId) return Kind.FULL_BOOT;
        return wakeSinceLastHome ? Kind.SAME_BOOT_WAKE : Kind.HOME_RETURN;
    }

    /** Session advertising Play is service/session readiness only; physical audio is never inferred. */
    static State stateOf(boolean sessionPresent, boolean playing, long actions) {
        if (!sessionPresent) return State.UNAVAILABLE;
        if (playing) return State.ACTIVE;
        boolean canPlay = (actions & (android.media.session.PlaybackState.ACTION_PLAY
                | android.media.session.PlaybackState.ACTION_PLAY_PAUSE)) != 0L;
        return canPlay ? State.READY : State.UNCERTAIN;
    }

    static boolean healthy(State state) { return state == State.READY || state == State.ACTIVE; }

    static boolean shouldReconcile(Kind kind, State music, State radio) {
        if (kind != Kind.HOME_RETURN) return !(healthy(music) && healthy(radio)) || kind == Kind.FULL_BOOT;
        return !(healthy(music) && healthy(radio));
    }

    /** One in-flight admission per source; a healthy or running source is never re-admitted. */
    static boolean admit(State state, boolean inFlight) {
        return !inFlight && !healthy(state) && state != State.BLOCKED;
    }

    static boolean foregroundAllowed(Mode mode, boolean homeResumed, boolean interactive,
                                     boolean userInteracting, boolean inCall, boolean defaultHome) {
        return mode != Mode.BACKGROUND && homeResumed && interactive && defaultHome
                && !userInteracting && !inCall;
    }

    static long backgroundWindowMs(Mode mode) {
        return mode == Mode.AGGRESSIVE ? 0L : BACKGROUND_WINDOW_MS;
    }
}
