package com.cbkii.ts18launcher;

/** Delivers a drawer only after the bounded task handoff and HOME visibility agree. */
final class NavigationOverlayGate {
    private final java.util.ArrayDeque<Runnable> pending = new java.util.ArrayDeque<>();
    private boolean ready;
    private boolean visible;
    private boolean mustDeliver;

    boolean request(Runnable action) { return request(action, false); }

    /** An explicit user launch (Music/Radio) must never be lost when HOME stops or navigation fails. */
    boolean request(Runnable action, boolean explicitLaunch) {
        if (action == null) return false;
        pending.clear(); ready = false; mustDeliver = explicitLaunch;
        pending.add(action);
        return true;
    }

    boolean isPending() { return !pending.isEmpty(); }

    void onVisible() { visible = true; deliver(); }
    void onStopped() { visible = false; if (mustDeliver) flush(); else cancel(); }

    /** Delivers a pending explicit launch now, regardless of HOME visibility or settle state. */
    void flush() {
        if (pending.isEmpty()) return;
        ready = true; boolean v = visible; visible = true;
        deliver();
        visible = v;
    }
    void onSettled() { ready = true; deliver(); }
    void cancel() { pending.clear(); ready = false; mustDeliver = false; }

    private void deliver() {
        if (!visible || !ready || pending.isEmpty()) return;
        java.util.ArrayDeque<Runnable> batch = new java.util.ArrayDeque<>(pending);
        cancel();
        while (!batch.isEmpty()) batch.removeFirst().run();
    }
}
