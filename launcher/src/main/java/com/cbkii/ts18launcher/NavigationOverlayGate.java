package com.cbkii.ts18launcher;

/** Delivers a drawer only after the bounded task handoff and HOME visibility agree. */
final class NavigationOverlayGate {
    private Runnable pending;
    private boolean ready;
    private boolean visible;

    boolean request(Runnable action) {
        if (pending != null) return false;
        pending = action;
        ready = false;
        return true;
    }

    boolean isPending() { return pending != null; }

    void onVisible() { visible = true; deliver(); }
    void onStopped() { visible = false; }
    void onSettled() { ready = true; deliver(); }
    void cancel() { pending = null; ready = false; }

    private void deliver() {
        if (!visible || !ready || pending == null) return;
        Runnable action = pending;
        cancel();
        action.run();
    }
}
