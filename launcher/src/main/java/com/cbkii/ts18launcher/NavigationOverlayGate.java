package com.cbkii.ts18launcher;

/**
 * Holds one explicit external-app launch until navigation parking settles. The user's request is
 * never dropped: if parking fails, times out, or HOME stops first, the launch is released instead.
 */
final class NavigationOverlayGate {
    private Runnable pending;
    private boolean ready;

    boolean request(Runnable action) {
        if (action == null) return false;
        pending = action; ready = false;
        return true;
    }

    boolean isPending() { return pending != null; }

    void onVisible() { }
    /** HOME stopping often means the requested app is already taking over; honour the request. */
    void onStopped() { release(); }
    void onSettled() { ready = true; release(); }
    /** Deliver the pending launch exactly once regardless of parking outcome. */
    void release() {
        Runnable action = pending;
        pending = null; ready = false;
        if (action != null) action.run();
    }
    /** Supersede without delivering (a newer explicit action replaces this one). */
    void cancel() { pending = null; ready = false; }
}
