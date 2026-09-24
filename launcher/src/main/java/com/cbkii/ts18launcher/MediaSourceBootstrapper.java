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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bounded per-source readiness/command coordinator.
 *
 * Service existence, service-start acceptance, controller connection, playability and playback are
 * deliberately distinct. Root activation is bounded/off-main-thread and never expands the original
 * command deadline. No path in this class launches an Activity or owns audio focus/queue/session.
 */
final class MediaSourceBootstrapper {
    interface ResultCallback { void onResult(boolean success, String message); }

    static final long COMMAND_TIMEOUT_MS = 4500L;
    static final long PREPARE_TIMEOUT_MS = 5000L;
    static final long BROWSER_CONNECT_TIMEOUT_MS = 3000L;
    static final long ACK_TIMEOUT_MS = 1600L;
    private static final long COMMAND_RETRY_MS = 250L;
    private static final long ACK_RETRY_MS = 100L;
    private static final long SERVICE_RETRY_GUARD_MS = 2500L;
    private static final long ROOT_START_TIMEOUT_MS = 900L;

    static final class Status {
        final MediaCommandPolicy.Phase phase;
        final String detail;
        final long updatedUptimeMs;

        Status(MediaCommandPolicy.Phase phase, String detail, long updatedUptimeMs) {
            this.phase = phase;
            this.detail = detail == null ? "" : detail;
            this.updatedUptimeMs = updatedUptimeMs;
        }
    }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Connection> connections = new HashMap<>();
    private final Map<String, ServiceStart> serviceStarts = new HashMap<>();
    private final Map<String, Pending> inFlightToggle = new HashMap<>();
    private final Map<String, Status> statuses = new LinkedHashMap<>();
    private final ExecutorService rootExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ts18-media-root");
        thread.setDaemon(true);
        return thread;
    });
    private final MediaSessionManager sessionManager;
    private final ComponentName listenerComponent;
    private String deferredPauseCandidate = "";
    private boolean destroyed;

    MediaSourceBootstrapper(Context context) {
        this.context = context.getApplicationContext();
        sessionManager =
                (MediaSessionManager) this.context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        listenerComponent = new ComponentName(this.context, MediaListenerService.class);
        MediaEventTrace.record("coordinator.create", "media readiness coordinator created");
    }

    void warmConfiguredSources() {
        String radio = RadioProvider.resolvePackage(context);
        String music = configuredMusicPackage();
        MediaEventTrace.record("warm.request",
                "configured music=" + emptyAsNone(music) + " radio=" + emptyAsNone(radio));
        if (MediaSelection.RADIO.equals(LauncherPrefs.lastSource(context))) {
            warm(radio);
            warm(music);
        } else {
            warm(music);
            warm(radio);
        }
    }

    void warm(String packageName) {
        if (destroyed || packageName == null || packageName.isEmpty()) return;
        MediaEventTrace.record("warm.source", packageName);
        MediaController controller = controllerForPackage(packageName);
        if (controller != null) {
            Connection connection = connections.get(packageName);
            if (connection != null && connection.controller == controller) {
                MediaListenerService.observeExternalController(controller);
            }
            markController(packageName, controller);
            if (controllerReadyForPlay(controller)) return;
        }

        MediaSourceAdapter adapter = MediaSourceAdapter.resolve(context, packageName);
        if (adapter.kind == MediaSourceAdapter.Kind.SESSION_ONLY) {
            mark(packageName, MediaCommandPolicy.Phase.BLOCKED, adapter.notReadyMessage("Source"));
            return;
        }
        if (!adapter.passiveWarmSafe) {
            mark(packageName, MediaCommandPolicy.Phase.IDLE,
                    "Interactive Play preparation only until passive start is physically qualified");
            return;
        }
        if (adapter.kind == MediaSourceAdapter.Kind.MEDIA_BROWSER) queueBrowser(adapter, null);
        else prepareExplicitService(adapter, null);
    }

    /**
     * Nominate a currently-playing opposite source for a requested switch. It is not paused here;
     * the pause is committed only after the new source's Play acknowledgement succeeds.
     */
    void pausePackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            deferredPauseCandidate = "";
            return;
        }
        MediaController controller = controllerForPackage(packageName);
        deferredPauseCandidate = controller != null && isPlaying(controller) ? packageName : "";
        MediaEventTrace.record("switch.nominate",
                deferredPauseCandidate.isEmpty()
                        ? "no playing opposite source"
                        : "defer pause " + deferredPauseCandidate);
    }

    boolean isPlaying(String packageName) {
        return isPlaying(controllerForPackage(packageName));
    }

    Status status(String packageName) {
        Status status = statuses.get(packageName);
        return status == null
                ? new Status(MediaCommandPolicy.Phase.IDLE, "", SystemClock.uptimeMillis()) : status;
    }

    String readinessDiagnostics() {
        if (statuses.isEmpty()) return "No media readiness attempts in this launcher process.";
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Status> entry : statuses.entrySet()) {
            if (text.length() > 0) text.append('\n');
            text.append(entry.getKey()).append(": ").append(entry.getValue().phase);
            if (!entry.getValue().detail.isEmpty()) text.append(" · ").append(entry.getValue().detail);
        }
        return text.toString();
    }

    void command(String sourceLabel, String packageName, MediaListenerService.Command command,
                 ResultCallback callback) {
        if (destroyed) {
            finishCallback(callback, false, "Launcher unavailable");
            return;
        }
        if (packageName == null || packageName.isEmpty()) {
            deferredPauseCandidate = "";
            finishCallback(callback, false,
                    "No " + sourceLabel.toLowerCase(java.util.Locale.ROOT) + " app configured");
            return;
        }

        cancelPendingForOtherPackages(packageName);
        MediaController controller = controllerForPackage(packageName);
        MediaCommandPolicy.Desired desired = MediaCommandPolicy.resolve(command, isPlaying(controller));
        MediaEventTrace.record("command.intent",
                packageName + " desired=" + desired + " source=" + sourceLabel);
        Pending pending = new Pending(sourceLabel, packageName, desired, callback,
                SystemClock.uptimeMillis() + COMMAND_TIMEOUT_MS);
        if (desired == MediaCommandPolicy.Desired.PLAY) {
            pending.pauseOnPlayPackage = deferredPauseCandidate;
        }
        deferredPauseCandidate = "";

        if (desired == MediaCommandPolicy.Desired.PLAY
                || desired == MediaCommandPolicy.Desired.PAUSE) {
            Pending existing = inFlightToggle.get(packageName);
            if (existing != null && MediaCommandPolicy.shouldCoalesceToggle(
                    existing.desired, existing.settled, existing.dispatched, desired)) {
                existing.callbacks.addAll(pending.callbacks);
                if (existing.pauseOnPlayPackage.isEmpty()) {
                    existing.pauseOnPlayPackage = pending.pauseOnPlayPackage;
                }
                MediaEventTrace.record("command.coalesce", packageName + " desired=" + desired);
                return;
            }
            if (existing != null && !existing.settled) {
                finish(existing, false, "Superseded by a newer Play/Pause request");
            }
            inFlightToggle.put(packageName, pending);
        }

        if (controller != null && supports(controller, desired)) {
            dispatchToController(controller, pending);
            return;
        }

        MediaSourceAdapter adapter = MediaSourceAdapter.resolve(context, packageName);
        if (adapter.kind == MediaSourceAdapter.Kind.MEDIA_BROWSER) {
            queueBrowser(adapter, pending);
        } else if (adapter.kind == MediaSourceAdapter.Kind.EXPLICIT_SERVICE) {
            prepareExplicitService(adapter, pending);
        } else {
            mark(packageName, MediaCommandPolicy.Phase.BLOCKED, adapter.notReadyMessage(sourceLabel));
            finish(pending, false, adapter.notReadyMessage(sourceLabel));
        }
    }

    void destroy() {
        if (destroyed) return;
        destroyed = true;
        MediaEventTrace.record("coordinator.cancel", "destroyed; pending work invalidated");
        handler.removeCallbacksAndMessages(null);
        for (Connection connection : new ArrayList<>(connections.values())) {
            finishAll(connection.pending, false, "Launcher unavailable");
            disconnect(connection);
        }
        for (ServiceStart start : new ArrayList<>(serviceStarts.values())) {
            finishAll(start.pending, false, "Launcher unavailable");
        }
        finishAll(new ArrayList<>(inFlightToggle.values()), false, "Launcher unavailable");
        connections.clear();
        serviceStarts.clear();
        inFlightToggle.clear();
        deferredPauseCandidate = "";
        rootExecutor.shutdownNow();
    }

    private String configuredMusicPackage() {
        String packageName = LauncherPrefs.packageFor(context, LauncherPrefs.KEY_MUSIC);
        return packageName.isEmpty() ? TopwayAdapter.defaultMusicPackage(context) : packageName;
    }

    private MediaController controllerForPackage(String packageName) {
        MediaController exact = exactController(packageName);
        if (exact != null) return exact;
        Connection connection = connections.get(packageName);
        return connection == null ? null : connection.controller;
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
        Pending actual = pending == null ? null : addPending(connection.pending, pending);
        if (connection.controller != null) {
            MediaListenerService.observeExternalController(connection.controller);
            if (actual != null) issueConnected(connection.controller, actual);
            return;
        }
        if (connection.preparing) return;

        connection.preparing = true;
        int generation = ++connection.generation;
        long deadlineMs = actual == null
                ? SystemClock.uptimeMillis() + PREPARE_TIMEOUT_MS : actual.deadlineMs;
        connection.prepareDeadlineMs = deadlineMs;
        mark(adapter.packageName, MediaCommandPolicy.Phase.STARTING,
                adapter.rootPrime ? "Root service prime, then MediaBrowser connect"
                        : "MediaBrowser connect");

        final Connection target = connection;
        if (adapter.rootPrime) {
            MediaEventTrace.record("browser.prepare", adapter.packageName + " root-first prime");
            rootExecutor.execute(() -> {
                RootShell.Result root = RootShell.runMillis(
                        adapter.rootStartCommand(), ROOT_START_TIMEOUT_MS);
                MediaEventTrace.record("browser.root",
                        adapter.packageName + " route="
                                + MediaCommandPolicy.routeAfterRoot(root.success()));
                handler.post(() -> connectBrowser(target, generation, deadlineMs));
            });
        } else {
            connectBrowser(target, generation, deadlineMs);
        }
    }

    private void connectBrowser(Connection connection, int generation, long deadlineMs) {
        if (!valid(connection, generation)) return;
        long now = SystemClock.uptimeMillis();
        if (now >= deadlineMs) {
            browserFailed(connection, generation, "Background media service did not connect in time");
            return;
        }
        if (connection.browser != null) return;

        final String packageName = connection.adapter.packageName;
        MediaEventTrace.record("browser.connect", packageName + " begin generation=" + generation);
        MediaBrowser.ConnectionCallback callback = new MediaBrowser.ConnectionCallback() {
            @Override public void onConnected() {
                if (!valid(connection, generation) || connection.browser == null
                        || !connection.browser.isConnected()) return;
                removeConnectTimeout(connection);
                try {
                    connection.controller =
                            new MediaController(context, connection.browser.getSessionToken());
                    attachBrowserController(connection, generation);
                    connection.preparing = false;
                    MediaListenerService.observeExternalController(connection.controller);
                    MediaEventTrace.record("browser.connected", packageName + " session token observed");
                    markController(packageName, connection.controller);
                    MediaListenerService.refreshActiveSessions();
                    drain(connection);
                } catch (RuntimeException error) {
                    browserFailed(connection, generation,
                            "MediaBrowser connected but controller creation failed");
                }
            }

            @Override public void onConnectionSuspended() {
                browserFailed(connection, generation, "MediaBrowser connection suspended");
            }

            @Override public void onConnectionFailed() {
                browserFailed(connection, generation, "MediaBrowser connection failed");
            }
        };

        connection.browser = new MediaBrowser(
                context, connection.adapter.service, callback, (Bundle) null);
        connection.connectTimeout = () -> browserFailed(connection, generation,
                "MediaBrowser connect callback timed out");
        long delay = MediaCommandPolicy.boundedDelay(now, deadlineMs, BROWSER_CONNECT_TIMEOUT_MS);
        handler.postDelayed(connection.connectTimeout, Math.max(1L, delay));
        try {
            connection.browser.connect();
        } catch (RuntimeException ignored) {
            browserFailed(connection, generation, "MediaBrowser connect threw an exception");
        }
    }

    private void attachBrowserController(Connection connection, int generation) {
        MediaController controller = connection.controller;
        if (controller == null) throw new IllegalStateException("MediaBrowser controller missing");
        MediaController.Callback callback = new MediaController.Callback() {
            @Override public void onSessionDestroyed() {
                handler.post(() -> browserSessionDestroyed(connection, generation));
            }
        };
        controller.registerCallback(callback);
        connection.controllerCallback = callback;
    }

    private void browserSessionDestroyed(Connection connection, int generation) {
        if (!valid(connection, generation)) return;
        String packageName = connection.adapter.packageName;
        MediaSourceAdapter adapter = connection.adapter;
        List<Pending> queued = new ArrayList<>(connection.pending);
        connection.pending.clear();
        connections.remove(packageName);
        disconnect(connection);
        MediaEventTrace.record("session.destroyed", packageName + " bound session ended");
        mark(packageName, MediaCommandPolicy.Phase.FAILED,
                "Bound MediaSession ended; a future request will reconnect");

        long now = SystemClock.uptimeMillis();
        for (Pending command : queued) {
            if (command.settled) continue;
            if (now >= command.deadlineMs) {
                finish(command, false, command.sourceLabel + " session ended before dispatch");
            } else {
                queueBrowser(adapter, command);
            }
        }
    }

    private boolean valid(Connection connection, int generation) {
        return !destroyed && connection != null
                && connections.get(connection.adapter.packageName) == connection
                && connection.generation == generation;
    }

    private void browserFailed(Connection connection, int generation, String message) {
        if (!valid(connection, generation)) return;
        String packageName = connection.adapter.packageName;
        List<Pending> pending = new ArrayList<>(connection.pending);
        connection.pending.clear();
        connections.remove(packageName);
        disconnect(connection);
        MediaEventTrace.record("browser.failed", packageName + " " + message);
        mark(packageName, MediaCommandPolicy.Phase.FAILED, message);
        for (Pending command : pending) retryExact(command);
    }

    private void disconnect(Connection connection) {
        removeConnectTimeout(connection);
        if (connection.controller != null) {
            if (connection.controllerCallback != null) {
                try {
                    connection.controller.unregisterCallback(connection.controllerCallback);
                } catch (RuntimeException ignored) {
                    // Session may already be destroyed.
                }
                connection.controllerCallback = null;
            }
            MediaListenerService.forgetExternalController(connection.controller);
            connection.controller = null;
        } else {
            connection.controllerCallback = null;
        }
        if (connection.browser != null) {
            try {
                connection.browser.disconnect();
            } catch (RuntimeException ignored) {
                // Best-effort lifecycle cleanup.
            }
            connection.browser = null;
        }
        connection.preparing = false;
    }

    private void removeConnectTimeout(Connection connection) {
        if (connection.connectTimeout != null) {
            handler.removeCallbacks(connection.connectTimeout);
            connection.connectTimeout = null;
        }
    }

    private void drain(Connection connection) {
        List<Pending> pending = new ArrayList<>(connection.pending);
        connection.pending.clear();
        for (Pending command : pending) issueConnected(connection.controller, command);
    }

    private void issueConnected(MediaController controller, Pending pending) {
        if (pending.settled) return;
        if (MediaCommandPolicy.acknowledged(pending.desired, stateOf(controller))) {
            completeAcknowledged(controller, pending);
            return;
        }
        if (supports(controller, pending.desired)) {
            dispatchToController(controller, pending);
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
        Pending actual = pending == null ? null : addPending(start.pending, pending);
        long now = SystemClock.uptimeMillis();
        if (start.inFlight) return;
        if (start.lastAttemptMs > 0L && now - start.lastAttemptMs < SERVICE_RETRY_GUARD_MS) {
            if (actual != null) {
                start.pending.remove(actual);
                retryExact(actual);
            }
            return;
        }

        start.inFlight = true;
        start.lastAttemptMs = now;
        start.prepareDeadlineMs = actual == null ? now + PREPARE_TIMEOUT_MS : actual.deadlineMs;
        int generation = ++start.generation;
        mark(adapter.packageName, MediaCommandPolicy.Phase.STARTING,
                "Root service start, normal Android fallback, then exact-session discovery");
        final ServiceStart target = start;
        MediaEventTrace.record("service.prepare", adapter.packageName + " root-first start");
        rootExecutor.execute(() -> {
            RootShell.Result root =
                    RootShell.runMillis(adapter.rootStartCommand(), ROOT_START_TIMEOUT_MS);
            handler.post(() -> {
                if (destroyed || serviceStarts.get(adapter.packageName) != target
                        || target.generation != generation) {
                    MediaEventTrace.record("service.cancel",
                            adapter.packageName + " late root result ignored");
                    return;
                }
                boolean started = root.success();
                MediaCommandPolicy.RootRoute route =
                        MediaCommandPolicy.routeAfterRoot(root.success());
                MediaEventTrace.record("service.root", adapter.packageName + " route=" + route);
                if (!started) {
                    MediaEventTrace.record("service.fallback",
                            adapter.packageName + " ordinary Android start attempt");
                    started = startExplicitServiceNormally(adapter);
                    MediaEventTrace.record("service.fallback",
                            adapter.packageName + (started ? " accepted" : " rejected"));
                }
                target.inFlight = false;
                if (!started) {
                    mark(adapter.packageName, MediaCommandPolicy.Phase.FAILED,
                            "Background service start was rejected");
                    List<Pending> failed = new ArrayList<>(target.pending);
                    target.pending.clear();
                    finishAll(failed, false, adapter.notReadyMessage("Source"));
                    return;
                }

                mark(adapter.packageName, MediaCommandPolicy.Phase.CONNECTED,
                        "Service start accepted; session readiness not yet verified");
                MediaListenerService.refreshActiveSessions();
                List<Pending> commands = new ArrayList<>(target.pending);
                target.pending.clear();
                for (Pending command : commands) retryExact(command);
                if (commands.isEmpty()) probeExplicit(target, generation);
            });
        });
    }

    private void probeExplicit(ServiceStart start, int generation) {
        if (destroyed || serviceStarts.get(start.adapter.packageName) != start
                || start.generation != generation) return;
        MediaController controller = exactController(start.adapter.packageName);
        if (controller != null) {
            MediaEventTrace.record("session.observed", start.adapter.packageName + " exact session");
            markController(start.adapter.packageName, controller);
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now >= start.prepareDeadlineMs) {
            mark(start.adapter.packageName, MediaCommandPolicy.Phase.FAILED,
                    "Service started but no exact MediaSession became observable");
            return;
        }
        handler.postDelayed(() -> probeExplicit(start, generation),
                Math.max(1L, MediaCommandPolicy.boundedDelay(
                        now, start.prepareDeadlineMs, COMMAND_RETRY_MS)));
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
        if (destroyed || pending.settled) return;
        long now = SystemClock.uptimeMillis();
        if (now >= pending.deadlineMs) {
            mark(pending.packageName, MediaCommandPolicy.Phase.FAILED,
                    "Readiness deadline expired before command dispatch");
            finish(pending, false, pending.sourceLabel + " did not become ready in time");
            return;
        }

        MediaListenerService.refreshActiveSessions();
        MediaController controller = controllerForPackage(pending.packageName);
        if (controller != null) {
            markController(pending.packageName, controller);
            if (MediaCommandPolicy.acknowledged(pending.desired, stateOf(controller))) {
                completeAcknowledged(controller, pending);
                return;
            }
            if (supports(controller, pending.desired) && !pending.dispatched) {
                dispatchToController(controller, pending);
                return;
            }
        }
        handler.postDelayed(() -> retryExact(pending),
                Math.max(1L, MediaCommandPolicy.boundedDelay(
                        now, pending.deadlineMs, COMMAND_RETRY_MS)));
    }

    private void dispatchToController(MediaController controller, Pending pending) {
        if (destroyed || pending.settled) return;
        int state = stateOf(controller);
        if (MediaCommandPolicy.acknowledged(pending.desired, state)) {
            completeAcknowledged(controller, pending);
            return;
        }
        if (!supports(controller, pending.desired)) {
            retryExact(pending);
            return;
        }
        if (pending.dispatched) {
            awaitAcknowledgement(controller, pending);
            return;
        }
        MediaEventTrace.record("transport.dispatch",
                pending.packageName + " desired=" + pending.desired);
        if (!sendDesired(controller, pending.desired)) {
            mark(pending.packageName, MediaCommandPolicy.Phase.FAILED,
                    "Controller rejected command dispatch");
            finish(pending, false, pending.sourceLabel + " command could not be sent");
            return;
        }
        pending.dispatched = true;
        MediaListenerService.refreshActiveSessions();
        if (pending.desired == MediaCommandPolicy.Desired.PREVIOUS
                || pending.desired == MediaCommandPolicy.Desired.NEXT) {
            finish(pending, true, "");
            return;
        }
        pending.ackDeadlineMs = Math.min(pending.deadlineMs,
                SystemClock.uptimeMillis() + ACK_TIMEOUT_MS);
        mark(pending.packageName, MediaCommandPolicy.Phase.STARTING,
                pending.desired == MediaCommandPolicy.Desired.PLAY
                        ? "Play dispatched; awaiting playback acknowledgement"
                        : "Pause dispatched; awaiting playback acknowledgement");
        awaitAcknowledgement(controller, pending);
    }

    private void awaitAcknowledgement(MediaController controller, Pending pending) {
        if (destroyed || pending.settled) return;
        int state;
        try {
            PlaybackState playback = controller.getPlaybackState();
            state = playback == null ? PlaybackState.STATE_NONE : playback.getState();
        } catch (RuntimeException error) {
            MediaEventTrace.record("transport.error",
                    pending.packageName + " session ended before acknowledgement");
            finish(pending, false, pending.sourceLabel + " session ended before acknowledgement");
            return;
        }
        if (MediaCommandPolicy.acknowledged(pending.desired, state)) {
            completeAcknowledged(controller, pending);
            return;
        }
        if (state == PlaybackState.STATE_ERROR) {
            mark(pending.packageName, MediaCommandPolicy.Phase.FAILED,
                    "Playback state reported an error after dispatch");
            finish(pending, false, pending.sourceLabel + " reported a playback error");
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now >= pending.ackDeadlineMs) {
            MediaEventTrace.record("transport.timeout",
                    pending.packageName + " desired=" + pending.desired);
            mark(pending.packageName, MediaCommandPolicy.Phase.FAILED,
                    "Command was accepted but playback acknowledgement timed out");
            finish(pending, false, pending.sourceLabel
                    + " command sent, but playback was not confirmed");
            return;
        }
        handler.postDelayed(() -> awaitAcknowledgement(controller, pending),
                Math.max(1L, MediaCommandPolicy.boundedDelay(
                        now, pending.ackDeadlineMs, ACK_RETRY_MS)));
    }

    private void completeAcknowledged(MediaController controller, Pending pending) {
        MediaEventTrace.record("transport.ack",
                pending.packageName + " desired=" + pending.desired
                        + " state=" + stateOf(controller)
                        + " (audible output not inferred)");
        markController(pending.packageName, controller);
        if (pending.desired == MediaCommandPolicy.Desired.PLAY) commitDeferredPause(pending);
        finish(pending, true, "");
    }

    private void commitDeferredPause(Pending pending) {
        String oppositePackage = pending.pauseOnPlayPackage;
        pending.pauseOnPlayPackage = "";
        if (oppositePackage == null || oppositePackage.isEmpty()) return;
        MediaController opposite = controllerForPackage(oppositePackage);
        boolean oppositePlaying = opposite != null && isPlaying(opposite);
        if (!MediaCommandPolicy.shouldCommitOppositePause(
                true, oppositePlaying, oppositePackage.equals(pending.packageName))) return;
        if (supports(opposite, MediaCommandPolicy.Desired.PAUSE)
                && sendDesired(opposite, MediaCommandPolicy.Desired.PAUSE)) {
            MediaEventTrace.record("switch.pause", oppositePackage + " paused after Play acknowledgement");
            MediaListenerService.refreshActiveSessions();
        }
    }

    private static boolean sendDesired(MediaController controller, MediaCommandPolicy.Desired desired) {
        if (controller == null || desired == null) return false;
        try {
            switch (desired) {
                case PREVIOUS:
                    controller.getTransportControls().skipToPrevious();
                    return true;
                case NEXT:
                    controller.getTransportControls().skipToNext();
                    return true;
                case PLAY:
                    controller.getTransportControls().play();
                    return true;
                case PAUSE:
                    controller.getTransportControls().pause();
                    return true;
                default:
                    return false;
            }
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean supports(MediaController controller, MediaCommandPolicy.Desired desired) {
        PlaybackState state = playbackState(controller);
        return state != null && MediaCommandPolicy.supports(desired, state.getActions());
    }

    private static boolean controllerReadyForPlay(MediaController controller) {
        PlaybackState state = playbackState(controller);
        int value = state == null ? PlaybackState.STATE_NONE : state.getState();
        long actions = state == null ? 0L : state.getActions();
        return MediaCommandPolicy.controllerReadyForPlay(value, actions);
    }

    private static boolean isPlaying(MediaController controller) {
        return MediaCommandPolicy.usesPauseAction(stateOf(controller));
    }

    private static int stateOf(MediaController controller) {
        PlaybackState state = playbackState(controller);
        return state == null ? PlaybackState.STATE_NONE : state.getState();
    }

    private static PlaybackState playbackState(MediaController controller) {
        if (controller == null) return null;
        try {
            return controller.getPlaybackState();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void markController(String packageName, MediaController controller) {
        PlaybackState state = playbackState(controller);
        int value = state == null ? PlaybackState.STATE_NONE : state.getState();
        long actions = state == null ? 0L : state.getActions();
        MediaCommandPolicy.Phase phase = MediaCommandPolicy.phaseForController(value, actions);
        String detail = phase == MediaCommandPolicy.Phase.PLAYING
                ? "Playback acknowledged"
                : phase == MediaCommandPolicy.Phase.READY
                ? "Controller advertises Play"
                : "Controller connected; Play capability not yet verified";
        mark(packageName, phase, detail);
    }

    private void mark(String packageName, MediaCommandPolicy.Phase phase, String detail) {
        if (packageName == null || packageName.isEmpty()) return;
        statuses.put(packageName, new Status(phase, detail, SystemClock.uptimeMillis()));
        MediaEventTrace.record("readiness",
                packageName + " phase=" + phase + (detail == null || detail.isEmpty() ? "" : " " + detail));
    }

    private Pending addPending(List<Pending> queue, Pending incoming) {
        if (incoming.desired == MediaCommandPolicy.Desired.PLAY
                || incoming.desired == MediaCommandPolicy.Desired.PAUSE) {
            for (Pending existing : queue) {
                if (MediaCommandPolicy.shouldCoalesceToggle(
                        existing.desired, existing.settled, existing.dispatched, incoming.desired)) {
                    existing.callbacks.addAll(incoming.callbacks);
                    if (existing.pauseOnPlayPackage.isEmpty()) {
                        existing.pauseOnPlayPackage = incoming.pauseOnPlayPackage;
                    }
                    MediaEventTrace.record("command.coalesce",
                            incoming.packageName + " queued desired=" + incoming.desired);
                    return existing;
                }
            }
        }
        queue.add(incoming);
        return incoming;
    }

    private void cancelPendingForOtherPackages(String packageName) {
        for (Connection connection : connections.values()) {
            if (!packageName.equals(connection.adapter.packageName)) {
                finishAll(connection.pending, false, "Superseded by a newer source command");
                connection.pending.clear();
            }
        }
        for (ServiceStart start : serviceStarts.values()) {
            if (!packageName.equals(start.adapter.packageName)) {
                finishAll(start.pending, false, "Superseded by a newer source command");
                start.pending.clear();
            }
        }
        for (Pending pending : new ArrayList<>(inFlightToggle.values())) {
            if (!packageName.equals(pending.packageName)) {
                finish(pending, false, "Superseded by a newer source command");
            }
        }
    }

    private void finishAll(List<Pending> pending, boolean success, String message) {
        for (Pending command : new ArrayList<>(pending)) finish(command, success, message);
    }

    private void finish(Pending pending, boolean success, String message) {
        if (pending == null || pending.settled) return;
        pending.settled = true;
        if (inFlightToggle.get(pending.packageName) == pending) {
            inFlightToggle.remove(pending.packageName);
        }
        MediaEventTrace.record(success ? "command.complete" : "command.cancel",
                pending.packageName + " desired=" + pending.desired
                        + (message == null || message.isEmpty() ? "" : " " + message));
        for (ResultCallback callback : new ArrayList<>(pending.callbacks)) {
            finishCallback(callback, success, message);
        }
        pending.callbacks.clear();
    }

    private void finishCallback(ResultCallback callback, boolean success, String message) {
        if (callback == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) callback.onResult(success, message);
        else handler.post(() -> callback.onResult(success, message));
    }

    private static String emptyAsNone(String value) {
        return value == null || value.isEmpty() ? "none" : value;
    }

    private static final class Connection {
        final MediaSourceAdapter adapter;
        final List<Pending> pending = new ArrayList<>();
        MediaBrowser browser;
        MediaController controller;
        MediaController.Callback controllerCallback;
        Runnable connectTimeout;
        boolean preparing;
        int generation;
        long prepareDeadlineMs;

        Connection(MediaSourceAdapter adapter) {
            this.adapter = adapter;
        }
    }

    private static final class ServiceStart {
        final MediaSourceAdapter adapter;
        final List<Pending> pending = new ArrayList<>();
        boolean inFlight;
        int generation;
        long lastAttemptMs;
        long prepareDeadlineMs;

        ServiceStart(MediaSourceAdapter adapter) {
            this.adapter = adapter;
        }
    }

    private static final class Pending {
        final String sourceLabel;
        final String packageName;
        final MediaCommandPolicy.Desired desired;
        final List<ResultCallback> callbacks = new ArrayList<>();
        final long deadlineMs;
        boolean dispatched;
        boolean settled;
        long ackDeadlineMs;
        String pauseOnPlayPackage = "";

        Pending(String sourceLabel, String packageName, MediaCommandPolicy.Desired desired,
                ResultCallback callback, long deadlineMs) {
            this.sourceLabel = sourceLabel;
            this.packageName = packageName;
            this.desired = desired;
            if (callback != null) callbacks.add(callback);
            this.deadlineMs = deadlineMs;
        }
    }
}
