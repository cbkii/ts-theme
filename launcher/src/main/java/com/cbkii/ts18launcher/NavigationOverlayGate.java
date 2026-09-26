package com.cbkii.ts18launcher;

/** Delivers a drawer only after the bounded task handoff and HOME visibility agree. */
final class NavigationOverlayGate {
    private final java.util.ArrayDeque<Runnable> pending = new java.util.ArrayDeque<>();
    private boolean ready;
    private boolean visible;

    boolean request(Runnable action) {
        if (action == null) return false;
        if (pending.isEmpty()) ready = false;
        pending.add(action);
        return true;
    }

    boolean isPending() { return !pending.isEmpty(); }

    void onVisible() { visible = true; deliver(); }
    void onStopped() { visible = false; }
    void onSettled() { ready = true; deliver(); }
    void cancel() { pending.clear(); ready = false; }

    private void deliver() {
        if (!visible || !ready || pending.isEmpty()) return;
        java.util.ArrayDeque<Runnable> batch = new java.util.ArrayDeque<>(pending);
        cancel();
        while (!batch.isEmpty()) batch.removeFirst().run();
    }
}
