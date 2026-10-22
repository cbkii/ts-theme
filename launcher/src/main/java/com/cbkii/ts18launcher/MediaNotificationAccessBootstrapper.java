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
            ensureConnected(app);
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
                ensureConnected(app);
                ProcessMediaSessionMonitor.refresh(app);
                MediaListenerService.refreshActiveSessions();
            });
        });
    }

    private static int connectionEpoch;
    private static boolean connectionPending;
    private static final long[] REBIND_DELAYS = {0L, 1000L, 2500L, 5000L, 10000L};

    static void connected() {
        connectionEpoch++; connectionPending = false;
    }

    static void ensureConnected(Context context) {
        Context app = context.getApplicationContext();
        MAIN.post(() -> {
            if (MediaListenerService.isConnected() || connectionPending
                    || !MediaListenerService.hasNotificationAccess(app)) return;
            connectionPending = true;
            recoverConnection(app, ++connectionEpoch, 0);
        });
    }

    private static void recoverConnection(Context app, int epoch, int attempt) {
        MAIN.postDelayed(() -> {
            if (epoch != connectionEpoch) return;
            if (MediaListenerService.isConnected() || !MediaListenerService.hasNotificationAccess(app)) {
                connectionPending = false; return;
            }
            android.os.PowerManager power = (android.os.PowerManager) app.getSystemService(Context.POWER_SERVICE);
            android.app.KeyguardManager keyguard = (android.app.KeyguardManager) app.getSystemService(Context.KEYGUARD_SERVICE);
            if (power == null || !power.isInteractive() || keyguard == null || keyguard.isKeyguardLocked()) {
                connectionPending = false; return; // Unlock/wake/HOME will re-arm, without any root mutation.
            }
            requestRebind(new ComponentName(app, MediaListenerService.class));
            ProcessMediaSessionMonitor.refresh(app);
            if (attempt + 1 < REBIND_DELAYS.length) recoverConnection(app, epoch, attempt + 1);
            else connectionPending = false;
        }, REBIND_DELAYS[attempt]);
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
