package com.cbkii.ts18launcher;

/** Pure bounded presentation session. Never owns an app launch, media command or Maps claim. */
final class StartupLoaderPolicy {
    static final long DEADLINE_MS = 45000L;
    private long deadline;
    private boolean active;

    boolean begin(long now) {
        if (active) return false;
        active = true;
        deadline = now + DEADLINE_MS;
        return true;
    }
    boolean active() { return active; }
    boolean expired(long now) { return active && now >= deadline; }
    void finish() { active = false; }

    static boolean shouldStart(boolean enabled, boolean afterSleep, long boot, long consumedBoot,
            boolean wakeObserved) {
        if (!enabled || boot < 0) return false;
        return boot != consumedBoot || afterSleep && wakeObserved;
    }

    static boolean terminal(StartupReadinessPolicy.State state) {
        return state != StartupReadinessPolicy.State.UNAVAILABLE
                && state != StartupReadinessPolicy.State.STARTING;
    }
}
