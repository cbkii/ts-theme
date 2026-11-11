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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cold start through the source's own exported media browser service, the standard Android
 * contract a player publishes for exactly this purpose.
 *
 * Connecting makes the source create its process and hand over its real session token. This class
 * then only reports that session; it sends no transport command, opens no Activity, uses no root
 * and invents no session. A package without an enabled, exported browser service cannot be
 * started here and is reported as such.
 */
final class BrowserSessionStarter implements DirectSourceEngine.Starter {
    /** Receives the outcome of a start attempt on the engine thread. */
    interface Events {
        void onSessionOffered(String packageName, DirectSessionRegistry.Session session);
        void onSessionWithdrawn(String packageName, Object token);
        void onStartFailed(String packageName, String reason);
    }

    private static final String BROWSER_SERVICE = "android.media.browse.MediaBrowserService";

    private static final class Link {
        MediaBrowser browser;
        Object token;
    }

    private final Context app;
    private final Handler handler;
    private final Events events;
    private final Map<String, Link> links = new HashMap<>();

    BrowserSessionStarter(Context context, Handler handler, Events events) {
        this.app = context.getApplicationContext();
        this.handler = handler;
        this.events = events;
    }

    @Override public boolean canStart(String packageName) { return service(packageName) != null; }

    @Override public void start(String packageName) {
        Link existing = links.get(packageName);
        if (existing != null) {
            // Still connecting: this request joins it. Already connected but asked again means the
            // offered session has gone, so start over with a fresh connection.
            if (existing.token == null) return;
            release(packageName);
        }
        ComponentName service = service(packageName);
        if (service == null) { events.onStartFailed(packageName, "has no background media service"); return; }
        Link link = new Link();
        links.put(packageName, link);
        try {
            link.browser = new MediaBrowser(app, service, new MediaBrowser.ConnectionCallback() {
                @Override public void onConnected() { connected(packageName, link); }
                @Override public void onConnectionSuspended() { ended(packageName, link, "stopped"); }
                @Override public void onConnectionFailed() { ended(packageName, link, "refused the connection"); }
            }, null);
            link.browser.connect();
        } catch (RuntimeException refused) {
            ended(packageName, link, "could not be reached");
        }
    }

    @Override public void release(String packageName) {
        Link link = links.remove(packageName);
        if (link == null) return;
        disconnect(link);
        if (link.token != null) events.onSessionWithdrawn(packageName, link.token);
    }

    private void connected(String packageName, Link link) {
        if (links.get(packageName) != link) return;
        try {
            MediaSession.Token token = link.browser.getSessionToken();
            MediaController controller = new MediaController(app, token);
            if (!packageName.equals(controller.getPackageName())) {
                // The token must belong to the package that was asked; never adopt another owner.
                ended(packageName, link, "offered a session it does not own");
                return;
            }
            link.token = token;
            events.onSessionOffered(packageName, new FrameworkSessions.ControllerSession(controller, handler));
        } catch (RuntimeException gone) {
            ended(packageName, link, "stopped while starting");
        }
    }

    private void ended(String packageName, Link link, String reason) {
        if (links.get(packageName) != link) return;
        links.remove(packageName);
        disconnect(link);
        if (link.token != null) events.onSessionWithdrawn(packageName, link.token);
        else events.onStartFailed(packageName, reason);
    }

    private static void disconnect(Link link) {
        try {
            if (link.browser != null) link.browser.disconnect();
        } catch (RuntimeException gone) {
            // Already disconnected by the platform.
        }
    }

    private ComponentName service(String packageName) {
        if (packageName == null || packageName.isEmpty()) return null;
        try {
            List<ResolveInfo> services = app.getPackageManager()
                    .queryIntentServices(new Intent(BROWSER_SERVICE).setPackage(packageName), 0);
            if (services != null) for (ResolveInfo info : services) {
                ServiceInfo service = info == null ? null : info.serviceInfo;
                if (service != null && service.enabled && service.exported
                        && packageName.equals(service.packageName)) {
                    return new ComponentName(service.packageName, service.name);
                }
            }
        } catch (RuntimeException unavailable) {
            // Package manager refused; treat as not startable.
        }
        return null;
    }
}
