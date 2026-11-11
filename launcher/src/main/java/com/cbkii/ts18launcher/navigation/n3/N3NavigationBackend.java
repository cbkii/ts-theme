package com.cbkii.ts18launcher.navigation.n3;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.Surface;

import java.util.ArrayList;
import java.util.List;

/**
 * The N3 navigation backend: one session, one display, one owner. Drives N3Session through a
 * display path and publishes immutable snapshots. Never touches N0-N2 tasks, never falls back to
 * them under an N3 label and never repeats an accepted launch; every retry is the driver's.
 * Main thread only.
 */
public final class N3NavigationBackend implements N3Session.Host {
    public interface Listener { void onN3(N3Snapshot snapshot); }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final N3Session session = new N3Session(this);
    private final List<Listener> listeners = new ArrayList<>();
    private N3DisplayPath path;
    private boolean useHost;
    private Surface surface;
    private int width, height, dpi;
    private int displayId;
    private boolean pixels;
    private boolean expanded;
    private N3MotionConverter converter;
    private N3Snapshot last;

    /** Creates the display path for the chosen authority; replaceable in tests. */
    interface PathFactory { N3DisplayPath create(boolean optionalHost); }

    private final PathFactory factory;

    public N3NavigationBackend(Context context) {
        this(context, null);
    }

    N3NavigationBackend(Context context, PathFactory factory) {
        this.app = context.getApplicationContext();
        this.factory = factory != null ? factory
                : host -> host ? new HostDisplayPath(app, main) : new PublicDisplayPath(app, main);
    }

    public void addListener(Listener listener) { listeners.add(listener); if (last != null) listener.onN3(last); }
    public void removeListener(Listener listener) { listeners.remove(listener); }
    public N3Snapshot snapshot() { return last != null ? last : snapshot(session.state(), session.detail()); }

    /** Which display authority the next session uses. Changing it never affects a live session. */
    public void selectHost(boolean optionalHost) { if (!session.state().holdsDisplay()) useHost = optionalHost; }

    public static boolean hostInstalled(Context context) { return HostDisplayPath.installed(context); }

    /** Fixed display geometry for the session; the presenter scales it, aspect preserved. */
    public void setGeometry(int displayWidth, int displayHeight, int densityDpi) {
        if (session.state().holdsDisplay()) return;
        width = displayWidth; height = displayHeight; dpi = densityDpi;
    }

    /** User-initiated start of an exact component. */
    public void start(String component) {
        if (path == null || path.authority() != (useHost ? N3DisplayPath.Authority.OPTIONAL_HOST
                : N3DisplayPath.Authority.LAUNCHER_PRIVATE_DISPLAY)) {
            if (path != null) path.release();
            path = factory.create(useHost);
        }
        session.enable(true);
        session.start(component);
    }

    public void retry() { session.retry(); }

    public void shutdown() { session.shutdown(); }

    public void setExpanded(boolean value) { expanded = value; publish(); }

    /** Presenter surface lifecycle. */
    public void surfaceAvailable(Surface next, int viewWidth, int viewHeight) {
        surface = next;
        converter = new N3MotionConverter(new N3InputMapper(viewWidth, viewHeight, 0, 0, 0, 0,
                Math.max(1, width), Math.max(1, height)));
        if (path != null && session.state() == N3State.SURFACE_DETACHED) {
            path.setSurface(next);
            long gen = session.generation();
            session.onSurfaceAttached(gen);
            // The display kept its content; presenting it again is the reattachment.
            if (pixels) session.onInteractive(gen);
        }
    }

    public void surfaceGone() {
        surface = null;
        converter = null;
        if (path != null && session.state().holdsDisplay()) {
            path.setSurface(null);
            session.onSurfaceDetached(session.generation());
        }
    }

    /** View-space touch from the presenter; true when it was delivered to the N3 display. */
    public boolean touch(MotionEvent viewEvent) {
        if (converter == null || path == null || session.state() != N3State.INTERACTIVE) return false;
        MotionEvent display = converter.convert(viewEvent);
        if (display == null) return false;
        try {
            return path.input().motion(display);
        } finally {
            display.recycle();
        }
    }

    /** Display-scoped Back; never a system Back on the launcher's own display. */
    public boolean back() {
        return path != null && session.state() == N3State.INTERACTIVE && path.input().back();
    }

    // --- N3Session.Host

    private final N3DisplayPath.Callback callback = new N3DisplayPath.Callback() {
        @Override public void created(long gen, int id) {
            if (gen != session.generation()) return;
            displayId = id;
            session.onDisplayCreated(gen);
        }
        @Override public void launched(long gen) { session.onLaunchAccepted(gen); }
        @Override public void firstFrame(long gen) {
            if (gen != session.generation()) return;
            pixels = true;
            session.onInteractive(gen);
        }
        @Override public void lost(long gen, String reason) { session.onContentLost(gen, reason); }
        @Override public void failed(long gen, String reason) { session.onFailure(gen, reason); }
    };

    @Override public void createDisplay(long generation) {
        pixels = false;
        displayId = 0;
        if (surface == null || width <= 0 || height <= 0 || dpi <= 0) {
            main.post(() -> session.onFailure(generation, "N3 presenter is not on screen"));
            return;
        }
        path.create(generation, surface, width, height, dpi, callback);
    }

    @Override public void launchApp(long generation, String component) {
        path.launch(generation, component, callback);
    }

    @Override public void releaseDisplay(long generation) {
        if (path != null) path.release();
        displayId = 0;
        pixels = false;
    }

    @Override public void schedule(long generation, long deadlineMs) {
        main.postDelayed(() -> session.onDeadline(generation), deadlineMs);
    }

    @Override public void onState(N3State state, String detail) {
        last = snapshot(state, detail);
        for (Listener listener : new ArrayList<>(listeners)) listener.onN3(last);
    }

    private void publish() { onState(session.state(), session.detail()); }

    private N3Snapshot snapshot(N3State state, String detail) {
        String inputReason = path == null ? "No session" : path.input().unavailableReason();
        return new N3Snapshot(state, detail, session.generation(), "",
                path == null ? "" : path.authority().name(), displayId, width, height, pixels,
                inputReason, expanded);
    }
}
