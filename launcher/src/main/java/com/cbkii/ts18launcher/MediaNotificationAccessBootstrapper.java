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
 * Connection upkeep for the launcher's own notification listener, plus an opt-in root grant.
 *
 * Android 10 has no runtime permission for notification-listener access; the owner enables it on
 * the system "Notification access" screen. Root is used only after the explicit, persisted opt-in
 * described by MediaListenerAccessPolicy: then the launcher may enable exactly its own
 * declared listener through NotificationManager's shell command, verify the secure state, and ask
 * the framework to rebind. It never rewrites enabled_notification_listeners, never touches another
 * package's access, and never restores access the owner has revoked.
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

    interface GrantCallback { void onResult(boolean granted, String detail); }

    /** Process start: keep an existing grant connected; use root only under stored consent. */
    static void ensureEarly(Context context) {
        Context app = context.getApplicationContext();
        MediaListenerAccessPolicy.Action action = MediaListenerAccessPolicy.decide(
                MediaListenerService.hasNotificationAccess(app),
                UiPersonalizationPrefs.mediaListenerRootGrant(app),
                UiPersonalizationPrefs.mediaListenerRootGrantApplied(app), false);
        switch (action) {
            case ALREADY_GRANTED:
                MediaEventTrace.record("listener-access", "already-granted");
                ensureConnected(app);
                ProcessMediaSessionMonitor.refresh(app);
                return;
            case WITHDRAW_CONSENT:
                // Access was removed after our own grant: that is the owner's decision.
                UiPersonalizationPrefs.setMediaListenerRootGrant(app, false);
                MediaEventTrace.record("listener-access", "revocation-respected", "root consent withdrawn");
                return;
            case ROOT_GRANT:
                rootGrant(app, null);
                return;
            case USE_SETTINGS:
            default:
                MediaEventTrace.record("listener-access", "not-granted", "awaiting Android Settings");
        }
    }

    /** Settings: the owner turned the root option on. This is fresh consent for one grant. */
    static void requestRootGrant(Context context, GrantCallback callback) {
        Context app = context.getApplicationContext();
        UiPersonalizationPrefs.setMediaListenerRootGrant(app, true);
        if (MediaListenerService.hasNotificationAccess(app)) {
            ensureConnected(app);
            deliver(callback, true, "Notification access is already granted");
            return;
        }
        rootGrant(app, callback);
    }

    private static void rootGrant(Context app, GrantCallback callback) {
        ComponentName listener = new ComponentName(app, MediaListenerService.class);
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
                deliver(callback, false, root.completed
                        ? "Root did not enable notification access; use Notification access instead"
                        : "Root is unavailable; use Notification access instead");
                return;
            }
            UiPersonalizationPrefs.markMediaListenerRootGrantApplied(app);
            MediaEventTrace.record("listener-access", "root-grant-verified",
                    "user=" + userId + " component=" + listener.flattenToShortString());
            MAIN.post(() -> {
                ensureConnected(app);
                ProcessMediaSessionMonitor.refresh(app);
                MediaListenerService.refreshActiveSessions();
            });
            deliver(callback, true, "Notification access granted with root");
        });
    }

    private static void deliver(GrantCallback callback, boolean granted, String detail) {
        if (callback != null) MAIN.post(() -> callback.onResult(granted, detail));
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
