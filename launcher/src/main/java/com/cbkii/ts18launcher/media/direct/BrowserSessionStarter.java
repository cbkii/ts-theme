package com.cbkii.ts18launcher.media.direct;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.os.Handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cold start through the source's own exported media browser service, the standard Android
 * contract a player publishes for exactly this purpose.
 *
 * Connecting makes the source create its process and hand over its real session token. This class
 * then only reports that session; it sends no transport command, opens no Activity, uses no root
 * and invents no session.
 *
 * Every connection attempt has its own identity. A callback from an attempt that was abandoned,
 * released or replaced is ignored. One attempt per package may be shared by both sources (Music
 * and Radio configured to the same app); it is torn down when its last owner lets go.
 */
final class BrowserSessionStarter implements DirectSourceEngine.Starter {
    /** Receives the outcome of a start attempt on the engine thread. */
    interface Events {
        void onSessionOffered(String packageName, DirectSessionRegistry.Session session);
        void onSessionWithdrawn(String packageName, Object token);
        void onStartFailed(String packageName, String reason);
    }

    /** The Android-facing edge, replaceable in tests. */
    interface Connector {
        /** One exported, enabled browser service of the package, or null with no guess made. */
        ComponentName resolve(String packageName);
        default boolean passiveSafe(String packageName) { return true; }
        Connection connect(ComponentName service, ConnectionCallbacks callbacks);
    }

    interface Connection { void disconnect(); }

    interface ConnectionCallbacks {
        /** sessionPackage is the package that owns the offered session, as the platform reports it. */
        void onConnected(String sessionPackage, DirectSessionRegistry.Session session);
        void onSuspended();
        void onFailed();
    }

    private static final class Attempt {
        final long id;
        final Set<Object> owners = new LinkedHashSet<>();
        Connection connection;
        Object token;
        Attempt(long id) { this.id = id; }
    }

    private final Connector connector;
    private final Events events;
    private final Map<String, Attempt> attempts = new HashMap<>();
    private long nextAttemptId;

    BrowserSessionStarter(Context context, Handler handler, Events events) {
        this(new FrameworkConnector(context, handler), events);
    }

    BrowserSessionStarter(Connector connector, Events events) {
        this.connector = connector;
        this.events = events;
    }

    @Override public boolean canStart(String packageName) {
        return packageName != null && !packageName.isEmpty() && connector.resolve(packageName) != null;
    }

    @Override public boolean canPreparePassive(String packageName) { return connector.passiveSafe(packageName); }

    @Override public void start(Object owner, String packageName) {
        Attempt existing = attempts.get(packageName);
        if (existing != null) {
            if (existing.token == null) {
                // Still connecting: join it rather than open a second connection.
                existing.owners.add(owner);
                return;
            }
            // A connection that already produced a session is being asked again, so that session
            // has gone. Replace it with a fresh attempt for every owner that still holds it.
            Set<Object> owners = new LinkedHashSet<>(existing.owners);
            end(packageName, existing, null);
            owners.add(owner);
            begin(packageName, owners);
            return;
        }
        begin(packageName, Collections.singleton(owner));
    }

    @Override public void abandon(Object owner, String packageName) {
        Attempt attempt = attempts.get(packageName);
        if (attempt == null || attempt.token != null) return;
        attempt.owners.remove(owner);
        if (attempt.owners.isEmpty()) end(packageName, attempt, null);
    }

    @Override public void release(Object owner, String packageName) {
        Attempt attempt = attempts.get(packageName);
        if (attempt == null) return;
        attempt.owners.remove(owner);
        if (attempt.owners.isEmpty()) end(packageName, attempt, null);
    }

    /** Live attempts, for tests and diagnostics. */
    int attemptCount() { return attempts.size(); }

    private void begin(String packageName, Set<Object> owners) {
        ComponentName service = connector.resolve(packageName);
        if (service == null) { events.onStartFailed(packageName, "has no single background media service"); return; }
        Attempt attempt = new Attempt(++nextAttemptId);
        attempt.owners.addAll(owners);
        attempts.put(packageName, attempt);
        try {
            Connection connected = connector.connect(service, new ConnectionCallbacks() {
                @Override public void onConnected(String sessionPackage, DirectSessionRegistry.Session session) {
                    connected(packageName, attempt, sessionPackage, session);
                }
                @Override public void onSuspended() {
                    if (current(packageName, attempt)) end(packageName, attempt, "stopped");
                }
                @Override public void onFailed() {
                    if (current(packageName, attempt)) end(packageName, attempt, "media service connection failed");
                }
            });
            // A connector can complete/fail synchronously. Never leak a returned connection
            // after its callback has already removed/replaced this attempt.
            if (current(packageName, attempt)) attempt.connection = connected;
            else if (connected != null) connected.disconnect();
        } catch (RuntimeException refused) {
            if (current(packageName, attempt)) end(packageName, attempt, "could not be reached");
        }
    }

    private void connected(String packageName, Attempt attempt, String sessionPackage,
                           DirectSessionRegistry.Session session) {
        if (!current(packageName, attempt) || attempt.token != null) return;
        if (session == null || session.token() == null) { end(packageName, attempt, "stopped while starting"); return; }
        if (!packageName.equals(sessionPackage)) {
            // The token must belong to the package that was asked; never adopt another owner.
            end(packageName, attempt, "offered a session it does not own");
            return;
        }
        attempt.token = session.token();
        events.onSessionOffered(packageName, session);
    }

    private boolean current(String packageName, Attempt attempt) {
        return attempts.get(packageName) == attempt;
    }

    /** Tear down one attempt. reason non-null reports a failure when no session was offered. */
    private void end(String packageName, Attempt attempt, String reason) {
        if (attempts.get(packageName) == attempt) attempts.remove(packageName);
        Connection connection = attempt.connection;
        attempt.connection = null;
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (RuntimeException gone) {
                // Already disconnected by the platform.
            }
        }
        if (attempt.token != null) events.onSessionWithdrawn(packageName, attempt.token);
        else if (reason != null) events.onStartFailed(packageName, reason);
    }

    /** Real platform edge: package-manager discovery and android.media.browse.MediaBrowser. */
    static final class FrameworkConnector implements Connector {
        private static final String BROWSER_SERVICE = "android.media.browse.MediaBrowserService";
        private final Context app;
        private final Handler handler;

        FrameworkConnector(Context context, Handler handler) {
            this.app = context.getApplicationContext();
            this.handler = handler;
        }

        @Override public boolean passiveSafe(String packageName) {
            // NavRadio service creation may select the hardware source: user demand only.
            return !"com.navimods.radio".equals(packageName)
                    && resolve(packageName) != null;
        }

        @Override public ComponentName resolve(String packageName) {
            List<ComponentName> browser = resolveForActions(packageName, BROWSER_SERVICE);
            // Prefer the standard browser contract, preserving its ambiguity/failure boundary.
            if (browser == null || !browser.isEmpty()) return choose(browser);
            return choose(resolveForActions(packageName,
                    "androidx.media3.session.MediaSessionService",
                    "androidx.media3.session.MediaLibraryService"));
        }

        private List<ComponentName> resolveForActions(String packageName, String... actions) {
            Set<ComponentName> candidates = new LinkedHashSet<>();
            try {
                for (String action : actions) {
                List<ResolveInfo> services = app.getPackageManager()
                        .queryIntentServices(new Intent(action).setPackage(packageName), 0);
                if (services != null) for (ResolveInfo info : services) {
                    ServiceInfo service = info == null ? null : info.serviceInfo;
                    if (service != null && service.enabled && service.exported
                            && packageName.equals(service.packageName)) {
                        if (service.permission == null || service.permission.isEmpty()
                                || app.checkSelfPermission(service.permission) == android.content.pm.PackageManager.PERMISSION_GRANTED)
                            candidates.add(new ComponentName(service.packageName, service.name));
                    }
                }
                }
            } catch (RuntimeException unavailable) {
                return null;
            }
            return new ArrayList<>(candidates);
        }

        @Override public Connection connect(ComponentName service, ConnectionCallbacks callbacks) {
            MediaBrowser[] browser = new MediaBrowser[1];
            browser[0] = new MediaBrowser(app, service, new MediaBrowser.ConnectionCallback() {
                @Override public void onConnected() {
                    MediaController controller;
                    try {
                        MediaSession.Token token = browser[0].getSessionToken();
                        controller = new MediaController(app, token);
                    } catch (RuntimeException gone) {
                        callbacks.onConnected("", null);
                        return;
                    }
                    callbacks.onConnected(controller.getPackageName(),
                            new FrameworkSessions.ControllerSession(controller, handler));
                }
                @Override public void onConnectionSuspended() { callbacks.onSuspended(); }
                @Override public void onConnectionFailed() { callbacks.onFailed(); }
            }, null);
            browser[0].connect();
            return () -> browser[0].disconnect();
        }
    }

    /**
     * Exactly one exported browser service is unambiguous. Several would require knowing which one
     * the app treats as its player, so none is chosen and the source is reported not startable.
     */
    static ComponentName choose(List<ComponentName> candidates) {
        return candidates != null && candidates.size() == 1 ? candidates.get(0) : null;
    }
}
