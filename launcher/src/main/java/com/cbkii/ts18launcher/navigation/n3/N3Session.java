package com.cbkii.ts18launcher.navigation.n3;

/**
 * One N3 navigation session as a framework-free, generation-tagged state machine.
 *
 * Every asynchronous report carries the generation it was issued under; a report from an older
 * generation is ignored. Each waiting state has one monotonic deadline. A failure stops in FAILED
 * and never retries by itself: the driver chooses Retry N3, open navigation normally (N0) or the
 * legacy surface. This class never touches N1/N2 tasks; it has no reference to them.
 */
public final class N3Session {
    /** Side effects the session asks its host to perform. All are idempotent. */
    public interface Host {
        void createDisplay(long generation);
        void launchApp(long generation, String component);
        void releaseDisplay(long generation);
        void schedule(long generation, long deadlineMs);
        void onState(N3State state, String detail);
    }

    public static final long DISPLAY_DEADLINE_MS = 3000L;
    public static final long LAUNCH_DEADLINE_MS = 6000L;
    public static final long RENDER_DEADLINE_MS = 8000L;
    public static final long REATTACH_DEADLINE_MS = 3000L;

    private final Host host;
    private N3State state = N3State.DISABLED;
    private long generation;
    private String component = "";
    private String detail = "";
    private boolean hostAvailable;

    public N3Session(Host host) { this.host = host; }

    public N3State state() { return state; }
    public long generation() { return generation; }
    public String detail() { return detail; }
    public String component() { return component; }

    /** Opt-in and capability gate. N3 starts only from enable(); never implicitly. */
    public void enable(boolean available) {
        hostAvailable = available;
        if (state == N3State.DISABLED || state == N3State.HOST_UNAVAILABLE || state == N3State.SHUTDOWN) {
            move(available ? N3State.HOST_CONNECTING : N3State.HOST_UNAVAILABLE,
                    available ? "" : "No display host with authority for external apps");
        }
    }

    /** Begin presenting component (an exact package/Activity) on a new launcher-owned display. */
    public void start(String targetComponent) {
        if (state.holdsDisplay()) return; // Even an invalid new target cannot abandon a live owner.
        if (!hostAvailable || targetComponent == null || targetComponent.isEmpty()) {
            move(N3State.HOST_UNAVAILABLE, targetComponent == null || targetComponent.isEmpty()
                    ? "No navigation app selected" : "No display host with authority for external apps");
            return;
        }
        component = targetComponent;
        generation++;
        move(N3State.DISPLAY_CREATING, "");
        host.schedule(generation, DISPLAY_DEADLINE_MS);
        host.createDisplay(generation);
    }

    public void onDisplayCreated(long gen) {
        if (gen != generation || state != N3State.DISPLAY_CREATING) return;
        move(N3State.APP_LAUNCHING, "");
        host.schedule(generation, LAUNCH_DEADLINE_MS);
        host.launchApp(generation, component);
    }

    /** The platform accepted the start on this display. That is not pixels. */
    public void onLaunchAccepted(long gen) {
        if (gen != generation || state != N3State.APP_LAUNCHING) return;
        move(N3State.RENDER_AWAITING, "");
        host.schedule(generation, RENDER_DEADLINE_MS);
    }

    /** Real content was observed on the display surface and input reached it. */
    public void onInteractive(long gen) {
        if (gen != generation) return;
        if (state == N3State.RENDER_AWAITING || state == N3State.REATTACHING || state == N3State.RECOVERING) {
            move(N3State.INTERACTIVE, "");
        }
    }

    /** The host SurfaceView went away (HOME stopped, drawer, recreation). The display is kept. */
    public void onSurfaceDetached(long gen) {
        if (gen != generation || !state.holdsDisplay()) return;
        move(N3State.SURFACE_DETACHED, "");
    }

    public void onSurfaceAttached(long gen) {
        if (gen != generation || state != N3State.SURFACE_DETACHED) return;
        move(N3State.REATTACHING, "");
        host.schedule(generation, REATTACH_DEADLINE_MS);
    }

    /** The external task died or the display was removed underneath us. */
    public void onContentLost(long gen, String reason) {
        if (gen != generation || !state.holdsDisplay()) return;
        fail(reason == null || reason.isEmpty() ? "Navigation stopped on the N3 display" : reason);
    }

    public void onFailure(long gen, String reason) {
        if (gen != generation || state == N3State.FAILED || state == N3State.SHUTDOWN) return;
        fail(reason);
    }

    public void onDeadline(long gen) {
        if (gen != generation) return;
        switch (state) {
            case DISPLAY_CREATING: fail("Display was not created in time"); break;
            case APP_LAUNCHING: fail("Navigation did not start on the N3 display"); break;
            case RENDER_AWAITING: fail("No map appeared on the N3 display"); break;
            case REATTACHING: fail("Navigation did not return after the screen changed"); break;
            default: break;
        }
    }

    /** Explicit driver retry from FAILED; the only way N3 tries again. */
    public void retry() {
        if (state != N3State.FAILED) return;
        state = N3State.HOST_CONNECTING;
        start(component);
    }

    /** End the session and release the display. Safe to call in any state. */
    public void shutdown() {
        if (state.holdsDisplay() || state == N3State.FAILED) host.releaseDisplay(generation);
        generation++; // Every outstanding report is now stale.
        move(N3State.SHUTDOWN, "");
    }

    private void fail(String reason) {
        long failed = generation;
        generation++; // Late reports from the failed attempt can no longer change anything.
        host.releaseDisplay(failed);
        move(N3State.FAILED, reason == null ? "" : reason);
    }

    private void move(N3State next, String why) {
        state = next;
        detail = why == null ? "" : why;
        host.onState(next, detail);
    }
}
