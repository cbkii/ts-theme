package com.cbkii.ts18launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bounded, background-only source readiness and transport bootstrap.
 *
 * It never launches a source Activity, owns playback/audio focus, creates a MediaSession, or
 * synthesises private Topway commands. Exact source adapters may ask Magisk root to start a proven
 * exported background service first; normal Android service/bind paths remain the fallback.
 */
final class MediaSourceBootstrapper {
    interface ResultCallback { void onResult(boolean success, String message); }

    private static final long COMMAND_TIMEOUT_MS = 4500L;
    private static final long COMMAND_RETRY_MS = 250L;
    private static final long SERVICE_RETRY_GUARD_MS = 2500L;
    private static final long ROOT_START_TIMEOUT_MS = 900L;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Connection> connections = new HashMap<>();
    private final Map<String, ServiceStart> serviceStarts = new HashMap<>();
    private final ExecutorService rootExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ts18-media-root");
        thread.setDaemon(true);
        return thread;
    });
    private final MediaSessionManager sessionManager;
    private final ComponentName listenerComponent;
    private boolean destroyed;

    MediaSourceBootstrapper(Context context) {
        this.context = context.getApplicationContext();
        sessionManager =
                (MediaSessionManager) this.context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        listenerComponent = new ComponentName(this.context, MediaListenerService.class);
    }

    void warmConfiguredSources() {
        warm(RadioProvider.resolvePackage(context));
        warm(configuredMusicPackage());
    }

    void warm(String packageName) {
        if (destroyed || packageName == null || packageName.isEmpty()
                || exactController(packageName) != null) return;
        MediaSourceAdapter adapter = MediaSourceAdapter.resolve(context, packageName);
        if (adapter.kind == MediaSourceAdapter.Kind.MEDIA_BROWSER) {
            queueBrowser(adapter, null);
        } else if (adapter.kind == MediaSourceAdapter.Kind.EXPLICIT_SERVICE) {
            prepareExplicitService(adapter, null);
        }
    }

    void pausePackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        MediaController exact = exactController(packageName);
        if (exact != null && pauseIfPlaying(exact)) return;
        Connection connection = connections.get(packageName);
        if (connection != null && connection.controller != null) {
            pauseIfPlaying(connection.controller);
        }
    }

    boolean isPlaying(String packageName) {
        MediaController exact = exactController(packageName);
        if (exact != null) return isPlaying(exact);
        Connection connection = connections.get(packageName);
        return connection != null && connection.controller != null
                && isPlaying(connection.controller);
    }

    void command(String sourceLabel, String packageName, MediaListenerService.Command command,
                 ResultCallback callback) {
        if (destroyed) {
            finish(callback, false, "Launcher unavailable");
            return;
        }
        if (packageName == null || packageName.isEmpty()) {
            finish(callback, false,
                    "No " + sourceLabel.toLowerCase(java.util.Locale.ROOT) + " app configured");
            return;
        }

        MediaController exact = exactController(packageName);
        if (exact != null && send(exact, command)) {
            finish(callback, true, "");
            return;
        }

        Pending pending = new Pending(sourceLabel, packageName, command, callback,
                command == MediaListenerService.Command.PLAY_PAUSE,
                SystemClock.uptimeMillis() + COMMAND_TIMEOUT_MS);
        MediaSourceAdapter adapter = MediaSourceAdapter.resolve(context, packageName);
        if (adapter.kind == MediaSourceAdapter.Kind.MEDIA_BROWSER) {
            queueBrowser(adapter, pending);
        } else if (adapter.kind == MediaSourceAdapter.Kind.EXPLICIT_SERVICE) {
            prepareExplicitService(adapter, pending);
        } else {
            finish(callback, false, adapter.notReadyMessage(sourceLabel));
        }
    }

    void destroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        for (Connection connection : connections.values()) {
            if (connection.browser != null) {
                try {
                    connection.browser.disconnect();
                } catch (RuntimeException ignored) {
                    // Best-effort lifecycle cleanup.
                }
            }
        }
        connections.clear();
        serviceStarts.clear();
        rootExecutor.shutdownNow();
    }

    private String configuredMusicPackage() {
        String packageName = LauncherPrefs.packageFor(context, LauncherPrefs.KEY_MUSIC);
        return packageName.isEmpty() ? TopwayAdapter.defaultMusicPackage(context) : packageName;
    }

    private MediaController exactController(String packageName) {
        if (sessionManager == null || packageName == null || packageName.isEmpty()
                || !MediaListenerService.hasNotificationAccess(context)) return null;
        try {
            MediaController fallback = null;
            for (MediaController controller : sessionManager.getActiveSessions(listenerComponent)) {
                if (controller == null || !packageName.equals(controller.getPackageName())) continue;
                if (isPlaying(controller)) return controller;
                if (fallback == null) fallback = controller;
            }
            return fallback;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void queueBrowser(MediaSourceAdapter adapter, Pending pending) {
        Connection connection = connections.get(adapter.packageName);
        if (connection == null) {
            connection = new Connection(adapter);
            connections.put(adapter.packageName, connection);
        }
        if (pending != null) connection.pending.add(pending);
        if (connection.preparing || connection.controller != null) {
            if (connection.controller != null && pending != null) {
                issueConnected(connection.controller, pending);
                connection.pending.remove(pending);
            }
            return;
        }

        connection.preparing = true;
        final Connection target = connection;
        if (adapter.rootPrime) {
            rootExecutor.execute(() -> {
                RootShell.runMillis(adapter.rootStartCommand(), ROOT_START_TIMEOUT_MS);
                handler.post(() -> connectBrowser(target));
            });
        } else {
            connectBrowser(target);
        }
    }

    private void connectBrowser(Connection connection) {
        if (destroyed || connections.get(connection.adapter.packageName) != connection) return;
        if (connection.browser != null) return;

        final String packageName = connection.adapter.packageName;
        MediaBrowser.ConnectionCallback connectionCallback = new MediaBrowser.ConnectionCallback() {
            @Override public void onConnected() {
                Connection current = connections.get(packageName);
                if (destroyed || current == null || current.browser == null
                        || !current.browser.isConnected()) return;
                try {
                    current.controller =
                            new MediaController(context, current.browser.getSessionToken());
                    current.preparing = false;
                    MediaListenerService.refreshActiveSessions();
                    drain(current);
                } catch (RuntimeException ignored) {
                    connectionFailed(packageName);
                }
            }

            @Override public void onConnectionSuspended() {
                connectionFailed(packageName);
            }

            @Override public void onConnectionFailed() {
                connectionFailed(packageName);
            }
        };

        connection.browser =
                new MediaBrowser(context, connection.adapter.service, connectionCallback, (Bundle) null);
        try {
            connection.browser.connect();
        } catch (RuntimeException ignored) {
            connectionFailed(packageName);
        }
    }

    private void connectionFailed(String packageName) {
        Connection failed = connections.remove(packageName);
        if (failed == null) return;
        if (failed.browser != null) {
            try {
                failed.browser.disconnect();
            } catch (RuntimeException ignored) {
                // Best-effort lifecycle cleanup.
            }
        }
        List<Pending> pending = new ArrayList<>(failed.pending);
        failed.pending.clear();
        for (Pending command : pending) retryExact(command);
    }

    private void drain(Connection connection) {
        List<Pending> pending = new ArrayList<>(connection.pending);
        connection.pending.clear();
        for (Pending command : pending) issueConnected(connection.controller, command);
    }

    private void issueConnected(MediaController controller, Pending pending) {
        if (pending.playRequest && isPlaying(controller)) {
            finish(pending.callback, true, "");
            return;
        }
        if (send(controller, pending.command)) {
            MediaListenerService.refreshActiveSessions();
            finish(pending.callback, true, "");
            return;
        }
        retryExact(pending);
    }

    private void prepareExplicitService(MediaSourceAdapter adapter, Pending pending) {
        ServiceStart start = serviceStarts.get(adapter.packageName);
        if (start == null) {
            start = new ServiceStart(adapter);
            serviceStarts.put(adapter.packageName, start);
        }
        if (pending != null) start.pending.add(pending);

        long now = SystemClock.uptimeMillis();
        if (start.inFlight) return;
        if (start.lastAttemptMs > 0L && now - start.lastAttemptMs < SERVICE_RETRY_GUARD_MS) {
            if (pending != null) {
                start.pending.remove(pending);
                retryExact(pending);
            }
            return;
        }

        start.inFlight = true;
        start.lastAttemptMs = now;
        final ServiceStart target = start;
        rootExecutor.execute(() -> {
            RootShell.Result root =
                    RootShell.runMillis(adapter.rootStartCommand(), ROOT_START_TIMEOUT_MS);
            handler.post(() -> {
                if (destroyed || serviceStarts.get(adapter.packageName) != target) return;
                boolean started = root.success();
                if (!started) started = startExplicitServiceNormally(adapter);
                target.inFlight = false;
                MediaListenerService.refreshActiveSessions();
                handler.postDelayed(MediaListenerService::refreshActiveSessions, COMMAND_RETRY_MS);

                List<Pending> commands = new ArrayList<>(target.pending);
                target.pending.clear();
                for (Pending command : commands) {
                    if (started) retryExact(command);
                    else finish(command.callback, false,
                            adapter.notReadyMessage(command.sourceLabel));
                }
            });
        });
    }

    private boolean startExplicitServiceNormally(MediaSourceAdapter adapter) {
        Intent intent = adapter.explicitServiceIntent();
        if (intent == null) return false;
        try {
            if (adapter.foregroundService) context.startForegroundService(intent);
            else context.startService(intent);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void retryExact(Pending pending) {
        if (destroyed) return;
        MediaListenerService.refreshActiveSessions();
        MediaController exact = exactController(pending.packageName);
        if (exact != null) {
            // A background service may auto-resume its source while becoming ready. An initial
            // Play request is then already satisfied and must not immediately toggle it back off.
            if (pending.playRequest && isPlaying(exact)) {
                finish(pending.callback, true, "");
                return;
            }
            if (send(exact, pending.command)) {
                finish(pending.callback, true, "");
                return;
            }
        }

        if (SystemClock.uptimeMillis() >= pending.deadlineMs) {
            MediaSourceAdapter adapter = MediaSourceAdapter.resolve(context, pending.packageName);
            finish(pending.callback, false, adapter.notReadyMessage(pending.sourceLabel));
            return;
        }
        handler.postDelayed(() -> retryExact(pending), COMMAND_RETRY_MS);
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
                    controller.getTransportControls().skipToPrevious();
                    return true;
                case NEXT:
                    if ((actions & PlaybackState.ACTION_SKIP_TO_NEXT) == 0L) return false;
                    controller.getTransportControls().skipToNext();
                    return true;
                case PLAY_PAUSE:
                    boolean playing = usesPauseAction(stateValue);
                    long direct =
                            playing ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY;
                    if ((actions & direct) == 0L
                            && (actions & PlaybackState.ACTION_PLAY_PAUSE) == 0L) return false;
                    if (playing) controller.getTransportControls().pause();
                    else controller.getTransportControls().play();
                    return true;
                default:
                    return false;
            }
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean pauseIfPlaying(MediaController controller) {
        if (!isPlaying(controller)) return false;
        try {
            controller.getTransportControls().pause();
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
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

    private void finish(ResultCallback callback, boolean success, String message) {
        if (callback == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) callback.onResult(success, message);
        else handler.post(() -> callback.onResult(success, message));
    }

    private static final class Connection {
        final MediaSourceAdapter adapter;
        final List<Pending> pending = new ArrayList<>();
        MediaBrowser browser;
        MediaController controller;
        boolean preparing;

        Connection(MediaSourceAdapter adapter) {
            this.adapter = adapter;
        }
    }

    private static final class ServiceStart {
        final MediaSourceAdapter adapter;
        final List<Pending> pending = new ArrayList<>();
        boolean inFlight;
        long lastAttemptMs;

        ServiceStart(MediaSourceAdapter adapter) {
            this.adapter = adapter;
        }
    }

    private static final class Pending {
        final String sourceLabel;
        final String packageName;
        final MediaListenerService.Command command;
        final ResultCallback callback;
        final boolean playRequest;
        final long deadlineMs;

        Pending(String sourceLabel, String packageName, MediaListenerService.Command command,
                ResultCallback callback, boolean playRequest, long deadlineMs) {
            this.sourceLabel = sourceLabel;
            this.packageName = packageName;
            this.command = command;
            this.callback = callback;
            this.playRequest = playRequest;
            this.deadlineMs = deadlineMs;
        }
    }
}
