package com.cbkii.ts18launcher;

import android.content.Context;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.cbkii.ts18launcher.platform.TopwayAdapter;

/**
 * Event-driven removable-media reconciliation for HOME.
 *
 * This does not scan storage, infer Auxio visibility, own a queue, or issue playback. A mounted
 * volume merely causes one ordinary exported MediaBrowser connection when the configured music
 * source already exposes a passive-safe browser contract. Removal events only cancel this bounded
 * launcher-owned connection and refresh observed sessions.
 */
final class MediaStorageReconciler {
    private static final long CONNECT_TIMEOUT_MS = 3000L;
    private static final long HOLD_CONNECTED_MS = 1800L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static int generation;
    private static MediaBrowser browser;
    private static MediaController controller;
    private static Runnable timeout;
    private static Runnable release;

    private MediaStorageReconciler() {}

    static void handle(Context context, String action, Uri data) {
        Context app = context.getApplicationContext();
        MAIN.post(() -> handleOnMain(app, action, data));
    }

    static void cancel(String reason) {
        MAIN.post(() -> cancelOnMain(reason));
    }

    private static void handleOnMain(Context context, String action, Uri data) {
        if (!MediaStorageEventPolicy.relevant(action)) return;
        String scheme = data == null || data.getScheme() == null ? "none" : data.getScheme();
        MediaEventTrace.record("storage.event", action + " scheme=" + scheme);
        MediaListenerService.refreshActiveSessions();
        if (MediaStorageEventPolicy.isRemoval(action)) {
            cancelOnMain("removable media unavailable");
            return;
        }
        warmConfiguredMusic(context);
    }

    private static void warmConfiguredMusic(Context context) {
        cancelOnMain("new storage reconciliation");
        String packageName = LauncherPrefs.packageFor(context, LauncherPrefs.KEY_MUSIC);
        if (packageName.isEmpty()) packageName = TopwayAdapter.defaultMusicPackage(context);
        if (packageName.isEmpty()) {
            MediaEventTrace.record("storage.warm", "blocked: no configured music package");
            return;
        }

        MediaSourceAdapter adapter = MediaSourceAdapter.resolve(context, packageName);
        if (adapter.kind != MediaSourceAdapter.Kind.MEDIA_BROWSER || !adapter.passiveWarmSafe
                || adapter.service == null) {
            MediaEventTrace.record("storage.warm",
                    packageName + " skipped: no passive-safe MediaBrowser contract");
            return;
        }

        final int expected = ++generation;
        MediaEventTrace.record("storage.warm",
                packageName + " ordinary MediaBrowser reconciliation begin");
        MediaBrowser.ConnectionCallback callback = new MediaBrowser.ConnectionCallback() {
            @Override public void onConnected() {
                if (expected != generation || browser == null || !browser.isConnected()) return;
                clearTimeout();
                try {
                    controller = new MediaController(context, browser.getSessionToken());
                    MediaListenerService.observeExternalController(controller);
                    MediaListenerService.refreshActiveSessions();
                    MediaEventTrace.record("storage.warm",
                            packageName + " MediaBrowser connected; no playback issued");
                    release = () -> finishConnected(expected, "bounded storage warm complete");
                    MAIN.postDelayed(release, HOLD_CONNECTED_MS);
                } catch (RuntimeException error) {
                    MediaEventTrace.record("storage.warm",
                            packageName + " controller creation failed");
                    cancelOnMain("controller creation failed");
                }
            }

            @Override public void onConnectionSuspended() {
                if (expected != generation) return;
                MediaEventTrace.record("storage.warm", packageName + " browser suspended");
                cancelOnMain("browser suspended");
            }

            @Override public void onConnectionFailed() {
                if (expected != generation) return;
                MediaEventTrace.record("storage.warm", packageName + " browser failed");
                cancelOnMain("browser failed");
            }
        };

        browser = new MediaBrowser(context, adapter.service, callback, (Bundle) null);
        timeout = () -> {
            if (expected != generation) return;
            MediaEventTrace.record("storage.warm", packageName + " browser connect timeout");
            cancelOnMain("browser connect timeout");
        };
        MAIN.postDelayed(timeout, CONNECT_TIMEOUT_MS);
        try {
            browser.connect();
        } catch (RuntimeException error) {
            MediaEventTrace.record("storage.warm", packageName + " browser connect threw");
            cancelOnMain("browser connect threw");
        }
    }

    private static void finishConnected(int expected, String reason) {
        if (expected != generation) return;
        cancelOnMain(reason);
        MediaListenerService.refreshActiveSessions();
    }

    private static void cancelOnMain(String reason) {
        generation++;
        clearTimeout();
        if (release != null) {
            MAIN.removeCallbacks(release);
            release = null;
        }
        if (controller != null) {
            MediaListenerService.forgetExternalController(controller);
            controller = null;
        }
        if (browser != null) {
            try {
                browser.disconnect();
            } catch (RuntimeException ignored) {
                // Best-effort bounded cleanup.
            }
            browser = null;
        }
        if (reason != null && !reason.isEmpty()) {
            MediaEventTrace.record("storage.reconcile", reason);
        }
    }

    private static void clearTimeout() {
        if (timeout != null) {
            MAIN.removeCallbacks(timeout);
            timeout = null;
        }
    }
}
