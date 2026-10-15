package com.cbkii.ts18launcher;

/**
 * Delivers one explicit external-app launch after the bounded task handoff settles. HOME visibility
 * is intentionally not required: HOME onStop or a failed Maps operation must never drop the request.
 */
final class NavigationOverlayGate {
    private final java.util.ArrayDeque<Runnable> pending = new java.util.ArrayDeque<>();
    private boolean ready;
    private boolean visible;

    boolean request(Runnable action) {
        if (action == null) return false;
        pending.clear(); ready = false;
        pending.add(action);
        return true;
    }

    boolean isPending() { return !pending.isEmpty(); }

    void onVisible() { visible = true; deliver(); }
    void onStopped() { visible = false; }
    void onSettled() { ready = true; deliver(); }
    void cancel() { pending.clear(); ready = false; }

    private void deliver() {
        if (!ready || pending.isEmpty()) return;
        java.util.ArrayDeque<Runnable> batch = new java.util.ArrayDeque<>(pending);
        cancel();
        while (!batch.isEmpty()) batch.removeFirst().run();
    }
}
