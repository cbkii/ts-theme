package com.cbkii.ts18launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.cbkii.ts18launcher.platform.TopwayAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded source warm-up/bootstrap using public Android media surfaces only.
 * It never owns playback, audio focus, a MediaSession, or a persistent foreground service.
 */
final class MediaSourceBootstrapper {
    interface ResultCallback { void onResult(boolean success, String message); }

    private static final String MEDIA_BROWSER_ACTION = "android.media.browse.MediaBrowserService";
    private static final long COMMAND_TIMEOUT_MS = 4500L;
    private static final long COMMAND_RETRY_MS = 250L;
    private static final long RETURN_TO_HOME_MS = 700L;

    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Connection> connections = new HashMap<>();
    private final MediaSessionManager sessionManager;
    private final ComponentName listenerComponent;
    private boolean destroyed;

    MediaSourceBootstrapper(Activity activity) {
        this.activity = activity;
        sessionManager = (MediaSessionManager) activity.getSystemService(Activity.MEDIA_SESSION_SERVICE);
        listenerComponent = new ComponentName(activity, MediaListenerService.class);
    }

    void warmConfiguredSources() {
        warm(RadioProvider.resolvePackage(activity));
        warm(configuredMusicPackage());
    }

    void warm(String packageName) {
        if (destroyed || packageName == null || packageName.isEmpty() || exactController(packageName) != null) return;
        ensureConnection(packageName);
    }

    void pausePackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        MediaController exact = exactController(packageName);
        if (exact != null && pauseIfPlaying(exact)) return;
        Connection connection = connections.get(packageName);
        if (connection != null && connection.controller != null) pauseIfPlaying(connection.controller);
    }

    boolean isPlaying(String packageName) {
        MediaController exact = exactController(packageName);
        if (exact != null) return isPlaying(exact);
        Connection connection = connections.get(packageName);
        return connection != null && connection.controller != null && isPlaying(connection.controller);
    }

    void command(String sourceLabel, String packageName, MediaListenerService.Command command,
                 ResultCallback callback) {
        if (destroyed) { finish(callback, false, "Launcher unavailable"); return; }
        if (packageName == null || packageName.isEmpty()) {
            finish(callback, false, "No " + sourceLabel.toLowerCase(java.util.Locale.ROOT) + " app configured");
            return;
        }

        MediaController exact = exactController(packageName);
        if (exact != null) {
            if (send(exact, command)) finish(callback, true, "");
            else finish(callback, false, actionName(command) + " unavailable");
            return;
        }

        Pending pending = new Pending(sourceLabel, packageName, command, callback,
                command == MediaListenerService.Command.PLAY_PAUSE);
        Connection connection = ensureConnection(packageName);
        if (connection == null) {
            fallbackLaunch(pending);
            return;
        }
        if (connection.controller != null) {
            issueConnected(connection.controller, pending);
            return;
        }
        connection.pending.add(pending);
    }

    void destroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        for (Connection connection : connections.values()) {
            if (connection.browser != null) {
                try { connection.browser.disconnect(); } catch (RuntimeException ignored) {}
            }
        }
        connections.clear();
    }

    private String configuredMusicPackage() {
        String packageName = LauncherPrefs.packageFor(activity, LauncherPrefs.KEY_MUSIC);
        return packageName.isEmpty() ? TopwayAdapter.defaultMusicPackage(activity) : packageName;
    }

    private MediaController exactController(String packageName) {
        if (sessionManager == null || packageName == null || packageName.isEmpty()
                || !MediaListenerService.hasNotificationAccess(activity)) return null;
        try {
            MediaController fallback = null;
            for (MediaController controller : sessionManager.getActiveSessions(listenerComponent)) {
                if (controller == null || !packageName.equals(controller.getPackageName())) continue;
                if (isPlaying(controller)) return controller;
                if (fallback == null) fallback = controller;
            }
            return fallback;
        } catch (RuntimeException ignored) { return null; }
    }

    private Connection ensureConnection(String packageName) {
        Connection existing = connections.get(packageName);
        if (existing != null) return existing;
        ComponentName service = mediaBrowserService(packageName);
        if (service == null) return null;

        Connection connection = new Connection(packageName);
        MediaBrowser.ConnectionCallback connectionCallback = new MediaBrowser.ConnectionCallback() {
            @Override public void onConnected() {
                Connection current = connections.get(packageName);
                if (destroyed || current == null || current.browser == null || !current.browser.isConnected()) return;
                try {
                    current.controller = new MediaController(activity, current.browser.getSessionToken());
                    MediaListenerService.refreshActiveSessions();
                    drain(current, true);
                } catch (RuntimeException ignored) { connectionFailed(packageName); }
            }
            @Override public void onConnectionSuspended() {
                Connection current = connections.get(packageName);
                if (current != null) current.controller = null;
            }
            @Override public void onConnectionFailed() { connectionFailed(packageName); }
        };
        connection.browser = new MediaBrowser(activity, service, connectionCallback, (Bundle) null);
        connections.put(packageName, connection);
        try { connection.browser.connect(); }
        catch (RuntimeException ignored) { connections.remove(packageName); return null; }
        return connection;
    }

    private ComponentName mediaBrowserService(String packageName) {
        Intent query = new Intent(MEDIA_BROWSER_ACTION).setPackage(packageName);
        final List<ResolveInfo> services;
        try { services = activity.getPackageManager().queryIntentServices(query, 0); }
        catch (RuntimeException ignored) { return null; }
        for (ResolveInfo info : services) {
            if (info == null || info.serviceInfo == null || !info.serviceInfo.exported) continue;
            return new ComponentName(info.serviceInfo.packageName, info.serviceInfo.name);
        }
        return null;
    }

    private void connectionFailed(String packageName) {
        Connection failed = connections.remove(packageName);
        if (failed == null) return;
        if (failed.browser != null) {
            try { failed.browser.disconnect(); } catch (RuntimeException ignored) {}
        }
        List<Pending> pending = new ArrayList<>(failed.pending);
        failed.pending.clear();
        for (Pending command : pending) fallbackLaunch(command);
    }

    private void drain(Connection connection, boolean connected) {
        List<Pending> pending = new ArrayList<>(connection.pending);
        connection.pending.clear();
        if (!connected || connection.controller == null) {
            for (Pending command : pending) fallbackLaunch(command);
            return;
        }
        for (Pending command : pending) issueConnected(connection.controller, command);
    }

    private void issueConnected(MediaController controller, Pending pending) {
        if (pending.startIntent && isPlaying(controller)) {
            finish(pending.callback, true, "");
            return;
        }
        if (send(controller, pending.command)) {
            MediaListenerService.refreshActiveSessions();
            finish(pending.callback, true, "");
        } else finish(pending.callback, false, actionName(pending.command) + " unavailable");
    }

    private void fallbackLaunch(Pending pending) {
        if (destroyed) { finish(pending.callback, false, "Launcher unavailable"); return; }
        if (!AppResolver.launchPackage(activity, pending.packageName)) {
            finish(pending.callback, false, pending.sourceLabel + " app unavailable");
            return;
        }
        long deadline = SystemClock.uptimeMillis() + COMMAND_TIMEOUT_MS;
        handler.postDelayed(this::returnToLauncher, RETURN_TO_HOME_MS);
        retryExact(pending, deadline);
    }

    private void retryExact(Pending pending, long deadline) {
        if (destroyed) return;
        MediaListenerService.refreshActiveSessions();
        MediaController exact = exactController(pending.packageName);
        if (exact != null) {
            if (pending.startIntent && isPlaying(exact)) { finish(pending.callback, true, ""); return; }
            if (send(exact, pending.command)) { finish(pending.callback, true, ""); return; }
            finish(pending.callback, false, actionName(pending.command) + " unavailable");
            return;
        }
        if (SystemClock.uptimeMillis() >= deadline) {
            finish(pending.callback, false, pending.sourceLabel + " did not become ready");
            return;
        }
        handler.postDelayed(() -> retryExact(pending, deadline), COMMAND_RETRY_MS);
    }

    private void returnToLauncher() {
        if (destroyed || activity.isFinishing() || activity.isDestroyed()) return;
        Intent intent = new Intent(activity, LauncherActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try { activity.startActivity(intent); } catch (RuntimeException ignored) {}
    }

    private static boolean send(MediaController controller, MediaListenerService.Command command) {
        if (controller == null || command == null) return false;
        try {
            PlaybackState state = controller.getPlaybackState();
            int stateValue = state == null ? PlaybackState.STATE_NONE : state.getState();
            long actions = state == null ? 0L : state.getActions();
            switch (command) {
                case PREVIOUS:
                    if ((actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) == 0L) return false;
                    controller.getTransportControls().skipToPrevious(); return true;
                case NEXT:
                    if ((actions & PlaybackState.ACTION_SKIP_TO_NEXT) == 0L) return false;
                    controller.getTransportControls().skipToNext(); return true;
                case PLAY_PAUSE:
                    boolean playing = usesPauseAction(stateValue);
                    long direct = playing ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY;
                    if ((actions & direct) == 0L && (actions & PlaybackState.ACTION_PLAY_PAUSE) == 0L) return false;
                    if (playing) controller.getTransportControls().pause();
                    else controller.getTransportControls().play();
                    return true;
                default:
                    return false;
            }
        } catch (RuntimeException ignored) { return false; }
    }

    private static boolean pauseIfPlaying(MediaController controller) {
        if (!isPlaying(controller)) return false;
        try { controller.getTransportControls().pause(); return true; }
        catch (RuntimeException ignored) { return false; }
    }

    private static boolean isPlaying(MediaController controller) {
        PlaybackState state = controller == null ? null : controller.getPlaybackState();
        return state != null && usesPauseAction(state.getState());
    }

    private static boolean usesPauseAction(int state) {
        return state == PlaybackState.STATE_PLAYING
                || state == PlaybackState.STATE_BUFFERING
                || state == PlaybackState.STATE_CONNECTING;
    }

    private static String actionName(MediaListenerService.Command command) {
        if (command == MediaListenerService.Command.PREVIOUS) return "Previous";
        if (command == MediaListenerService.Command.NEXT) return "Next";
        return "Play/pause";
    }

    private void finish(ResultCallback callback, boolean success, String message) {
        if (callback == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) callback.onResult(success, message);
        else handler.post(() -> callback.onResult(success, message));
    }

    private static final class Connection {
        final String packageName;
        final List<Pending> pending = new ArrayList<>();
        MediaBrowser browser;
        MediaController controller;
        Connection(String packageName) { this.packageName = packageName; }
    }

    private static final class Pending {
        final String sourceLabel;
        final String packageName;
        final MediaListenerService.Command command;
        final ResultCallback callback;
        final boolean startIntent;
        Pending(String sourceLabel, String packageName, MediaListenerService.Command command,
                ResultCallback callback, boolean startIntent) {
            this.sourceLabel = sourceLabel;
            this.packageName = packageName;
            this.command = command;
            this.callback = callback;
            this.startIntent = startIntent;
        }
    }
}
