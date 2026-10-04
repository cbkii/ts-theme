package com.cbkii.ts18launcher;

import android.content.ComponentName;
import android.content.Context;
import android.service.notification.NotificationListenerService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bounded one-time setup for the launcher's own notification-listener authority.
 *
 * Android 10 does not expose notification-listener access as a normal runtime permission. On this
 * rooted TS18 the launcher may grant only its exact declared listener through NotificationManager's
 * shell command, verify the resulting secure state, and then ask the framework to rebind it. It
 * never rewrites enabled_notification_listeners or changes another package's listener access.
 */
final class MediaNotificationAccessBootstrapper {
    private static final long ROOT_TIMEOUT_MS = 2200L;
    private static final long VERIFY_WINDOW_MS = 900L;
    private static final long VERIFY_RETRY_MS = 120L;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ts18-media-listener-access");
        thread.setDaemon(true);
        return thread;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private MediaNotificationAccessBootstrapper() {}

    static void ensureEarly(Context context) {
        Context app = context.getApplicationContext();
        ComponentName listener = new ComponentName(app, MediaListenerService.class);
        if (MediaListenerService.hasNotificationAccess(app)) {
            MediaEventTrace.record("listener-access", "already-granted");
            requestRebind(listener);
            ProcessMediaSessionMonitor.refresh(app);
            return;
        }

        EXECUTOR.execute(() -> {
            int userId = AndroidUserId.current();
            String command = rootGrantCommand(listener, userId);
            MediaEventTrace.record("listener-access", "root-grant-request",
                    "user=" + userId + " component=" + listener.flattenToShortString());
            RootShell.Result root = RootShell.runMillis(command, ROOT_TIMEOUT_MS);
            boolean granted = waitForGrant(app);
            if (!granted) {
                MediaEventTrace.record("listener-access",
                        root.completed ? "root-grant-rejected" : "root-grant-unavailable",
                        "user=" + userId + " exit=" + root.exitCode);
                return;
            }
            MediaEventTrace.record("listener-access", "root-grant-verified",
                    "user=" + userId + " component=" + listener.flattenToShortString());
            MAIN.post(() -> {
                requestRebind(listener);
                ProcessMediaSessionMonitor.refresh(app);
                MediaListenerService.refreshActiveSessions();
            });
        });
    }

    static String rootGrantCommand(ComponentName listener, int userId) {
        if (listener == null || userId < 0) return "exit 1";
        String component = listener.flattenToString();
        if (component == null || component.isEmpty()) return "exit 1";
        return "/system/bin/cmd notification allow_listener "
                + shellQuote(component) + " " + userId;
    }

    private static boolean waitForGrant(Context context) {
        long deadline = SystemClock.uptimeMillis() + VERIFY_WINDOW_MS;
        do {
            if (MediaListenerService.hasNotificationAccess(context)) return true;
            try {
                Thread.sleep(VERIFY_RETRY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        } while (SystemClock.uptimeMillis() < deadline);
        return MediaListenerService.hasNotificationAccess(context);
    }

    private static void requestRebind(ComponentName listener) {
        try {
            NotificationListenerService.requestRebind(listener);
            MediaEventTrace.record("listener-access", "rebind-requested");
        } catch (RuntimeException e) {
            MediaEventTrace.record("listener-access", "rebind-failed",
                    e.getClass().getSimpleName());
        }
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
