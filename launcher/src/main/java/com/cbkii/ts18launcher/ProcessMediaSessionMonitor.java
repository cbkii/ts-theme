package com.cbkii.ts18launcher;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;

import com.cbkii.ts18launcher.platform.TopwayAdapter;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Process-owned, event-driven MediaSession observer.
 *
 * Notification-listener access is still the Android authority required by API 29 to query active
 * sessions, but the NotificationListenerService process callback is not required to have connected.
 * All mutable monitor state is serialized on the main looper and defensive refreshes are coalesced.
 */
final class ProcessMediaSessionMonitor {
    interface Observer {
        void onProcessMediaStateChanged(
                MediaListenerService.Snapshot genericMedia,
                MediaListenerService.Snapshot radio,
                boolean authoritative);
    }

    private static final CopyOnWriteArrayList<WeakReference<Observer>> OBSERVERS =
            new CopyOnWriteArrayList<>();
    private static final Object LOCK = new Object();
    private static ProcessMediaSessionMonitor instance;

    private final Context context;
    private final MediaSessionManager manager;
    private final ComponentName listenerComponent;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<MediaSession.Token, MediaController> watched = new HashMap<>();
    private boolean sessionListenerRegistered;
    private boolean destroyed;
    private boolean refreshPosted;
    private volatile boolean authoritative;
    private volatile MediaListenerService.Snapshot lastGeneric = empty();
    private volatile MediaListenerService.Snapshot lastRadio = empty();

    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) { scheduleRefresh(); }
        @Override public void onPlaybackStateChanged(PlaybackState state) { scheduleRefresh(); }
        @Override public void onSessionDestroyed() { scheduleRefresh(); }
        @Override public void onQueueChanged(List<MediaSession.QueueItem> queue) { scheduleRefresh(); }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener activeSessionsChanged =
            controllers -> main.post(() -> handleActiveSessionsChanged(controllers));

    private ProcessMediaSessionMonitor(Context context) {
        this.context = context.getApplicationContext();
        this.manager = (MediaSessionManager) this.context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        this.listenerComponent = new ComponentName(this.context, MediaListenerService.class);
    }

    private void handleActiveSessionsChanged(List<MediaController> controllers) {
        if (destroyed || !sessionListenerRegistered) return;
        if (!MediaListenerService.hasNotificationAccess(context)) {
            markUnavailable("active-callback-access-lost", true);
            return;
        }
        reconcile(controllers == null ? new ArrayList<>() : new ArrayList<>(controllers));
    }

    static void start(Context context) {
        monitor(context).ensureStarted();
    }

    static void refresh(Context context) {
        ProcessMediaSessionMonitor monitor = monitor(context);
        monitor.ensureStarted();
        monitor.scheduleRefresh();
    }

    static void addObserver(Context context, Observer observer) {
        if (observer == null) return;
        for (WeakReference<Observer> reference : OBSERVERS) {
            if (reference.get() == observer) return;
        }
        OBSERVERS.add(new WeakReference<>(observer));
        ProcessMediaSessionMonitor monitor = monitor(context);
        monitor.ensureStarted();
        monitor.main.post(() -> observer.onProcessMediaStateChanged(
                monitor.lastGeneric, monitor.lastRadio, monitor.authoritative));
    }

    static void removeObserver(Observer observer) {
        if (observer == null) return;
        for (WeakReference<Observer> reference : OBSERVERS) {
            Observer candidate = reference.get();
            if (candidate == null || candidate == observer) OBSERVERS.remove(reference);
        }
    }

    static void stop() {
        synchronized (LOCK) {
            if (instance != null) instance.destroy();
            instance = null;
        }
        OBSERVERS.clear();
    }

    private static ProcessMediaSessionMonitor monitor(Context context) {
        synchronized (LOCK) {
            if (instance == null) instance = new ProcessMediaSessionMonitor(context);
            return instance;
        }
    }

    private void ensureStarted() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(this::ensureStarted);
            return;
        }
        if (destroyed || manager == null || sessionListenerRegistered
                || !MediaListenerService.hasNotificationAccess(context)) return;
        try {
            manager.addOnActiveSessionsChangedListener(activeSessionsChanged, listenerComponent);
            sessionListenerRegistered = true;
            MediaEventTrace.record("session-monitor", "listener-registered");
        } catch (SecurityException e) {
            markUnavailable("access-denied", true);
        } catch (RuntimeException e) {
            MediaEventTrace.record("session-monitor", "listener-register-failed",
                    e.getClass().getSimpleName());
        }
    }

    private void scheduleRefresh() {
        if (destroyed) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (refreshPosted) return;
            refreshPosted = true;
            main.post(refreshRunnable);
            return;
        }
        main.post(this::scheduleRefresh);
    }

    private final Runnable refreshRunnable = () -> {
        refreshPosted = false;
        refreshInternal();
    };

    private void refreshInternal() {
        if (destroyed || manager == null) return;
        if (!MediaListenerService.hasNotificationAccess(context)) {
            markUnavailable("access-not-granted", true);
            return;
        }
        ensureStarted();
        try {
            reconcile(manager.getActiveSessions(listenerComponent));
        } catch (SecurityException e) {
            markUnavailable("refresh-access-denied", true);
        } catch (RuntimeException e) {
            MediaEventTrace.record("session-monitor", "refresh-failed",
                    e.getClass().getSimpleName());
            markUnavailable("refresh-unavailable", false);
        }
    }

    private void reconcile(List<MediaController> controllers) {
        if (destroyed) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            List<MediaController> copy = controllers == null ? new ArrayList<>() : new ArrayList<>(controllers);
            main.post(() -> reconcile(copy));
            return;
        }
        List<MediaController> active = controllers == null ? new ArrayList<>() : controllers;
        Map<MediaSession.Token, MediaController> next = new HashMap<>();
        for (MediaController controller : active) {
            MediaSession.Token token = tokenOf(controller);
            if (controller == null || token == null) continue;
            if (watched.containsKey(token)) {
                next.put(token, controller);
                continue;
            }
            try {
                controller.registerCallback(controllerCallback, main);
                next.put(token, controller);
            } catch (RuntimeException ignored) {
                // Leave it unwatched so the next reconciliation retries registration.
            }
        }
        for (Map.Entry<MediaSession.Token, MediaController> entry : watched.entrySet()) {
            if (!next.containsKey(entry.getKey())) {
                try {
                    entry.getValue().unregisterCallback(controllerCallback);
                } catch (RuntimeException ignored) {
                    // Session may already have been destroyed.
                }
            }
        }
        watched.clear();
        watched.putAll(next);

        String radioPackage = RadioProvider.resolvePackage(context);
        MediaController radio = pickExact(active, radioPackage);
        MediaController generic = pickGeneric(active, radioPackage);
        MediaListenerService.Snapshot previousGeneric = lastGeneric;
        MediaListenerService.Snapshot previousRadio = lastRadio;
        MediaListenerService.Snapshot nextGeneric = MediaListenerService.stabiliseSnapshot(
                previousGeneric, snapshotOf(generic));
        MediaListenerService.Snapshot nextRadio = MediaListenerService.stabiliseSnapshot(
                previousRadio, snapshotOf(radio));
        boolean authorityChanged = !authoritative;
        authoritative = true;
        boolean changed = !same(previousGeneric, nextGeneric) || !same(previousRadio, nextRadio);
        lastGeneric = nextGeneric;
        lastRadio = nextRadio;
        if (changed || authorityChanged) {
            MediaEventTrace.record("session-monitor", "state",
                    "music=" + nextGeneric.packageName + " radio=" + nextRadio.packageName);
            notifyObservers();
        }
    }

    private MediaController pickGeneric(List<MediaController> controllers, String radioPackage) {
        String preferred = LauncherPrefs.packageFor(context, LauncherPrefs.KEY_MUSIC);
        if (preferred.isEmpty()) preferred = TopwayAdapter.defaultMusicPackage(context);
        boolean preferConfigured = LauncherPrefs.MEDIA_MODE_PREFER_MUSIC.equals(
                LauncherPrefs.mediaMode(context));
        String remembered = LauncherPrefs.lastMusicPackage(context);
        List<MediaSelection.Candidate> candidates = new ArrayList<>();
        for (MediaController controller : controllers) {
            PlaybackState state = safeState(controller);
            int value = state == null ? PlaybackState.STATE_NONE : state.getState();
            candidates.add(new MediaSelection.Candidate(
                    controller == null ? "" : controller.getPackageName(),
                    controller != null && !excludedFromGenericMedia(controller, radioPackage),
                    state != null && value != PlaybackState.STATE_NONE && value != PlaybackState.STATE_ERROR,
                    usesPauseAction(value),
                    value == PlaybackState.STATE_PAUSED || value == PlaybackState.STATE_STOPPED));
        }
        int selected = MediaSelection.pick(candidates, preferred, preferConfigured, remembered);
        return selected < 0 ? null : controllers.get(selected);
    }

    private static boolean excludedFromGenericMedia(MediaController controller, String radioPackage) {
        if (controller == null) return true;
        String packageName = controller.getPackageName();
        if (radioPackage != null && !radioPackage.isEmpty() && radioPackage.equals(packageName)) return true;
        return "com.android.server.telecom".equals(packageName)
                || "com.android.dialer".equals(packageName)
                || "com.google.android.dialer".equals(packageName)
                || "com.android.phone".equals(packageName);
    }

    private static MediaController pickExact(List<MediaController> controllers, String packageName) {
        if (packageName == null || packageName.isEmpty()) return null;
        MediaController fallback = null;
        for (MediaController controller : controllers) {
            if (!samePackage(controller, packageName)) continue;
            PlaybackState state = safeState(controller);
            int value = state == null ? PlaybackState.STATE_NONE : state.getState();
            if (usesPauseAction(value)) return controller;
            if (fallback == null) fallback = controller;
        }
        return fallback;
    }

    private static boolean usesPauseAction(int state) {
        return state == PlaybackState.STATE_PLAYING
                || state == PlaybackState.STATE_BUFFERING
                || state == PlaybackState.STATE_CONNECTING;
    }

    private static boolean samePackage(MediaController controller, String packageName) {
        return controller != null && packageName != null
                && packageName.equals(controller.getPackageName());
    }

    private static PlaybackState safeState(MediaController controller) {
        if (controller == null) return null;
        try {
            return controller.getPlaybackState();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static MediaListenerService.Snapshot snapshotOf(MediaController controller) {
        if (controller == null) return empty();
        String packageName;
        MediaMetadata metadata;
        PlaybackState state;
        try {
            packageName = controller.getPackageName();
            metadata = controller.getMetadata();
            state = controller.getPlaybackState();
        } catch (RuntimeException e) {
            return empty();
        }
        String title = "";
        String artist = "";
        if (metadata != null) {
            title = first(metadata,
                    MediaMetadata.METADATA_KEY_TITLE,
                    MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            artist = first(metadata,
                    MediaMetadata.METADATA_KEY_ARTIST,
                    MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                    MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        }
        int stateValue = state == null ? PlaybackState.STATE_NONE : state.getState();
        long actions = state == null ? 0L : state.getActions();
        return new MediaListenerService.Snapshot(packageName, title, artist, stateValue, actions,
                tokenOf(controller));
    }

    private static String first(MediaMetadata metadata, String... keys) {
        if (metadata == null || keys == null) return "";
        for (String key : keys) {
            CharSequence text = metadata.getText(key);
            if (text != null) {
                String clean = text.toString().trim();
                if (!clean.isEmpty()) return clean;
            }
        }
        return "";
    }

    private static MediaSession.Token tokenOf(MediaController controller) {
        if (controller == null) return null;
        try {
            return controller.getSessionToken();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static MediaListenerService.Snapshot empty() {
        return new MediaListenerService.Snapshot("", "", "", false);
    }

    private static boolean same(MediaListenerService.Snapshot left,
                                MediaListenerService.Snapshot right) {
        if (left == right) return true;
        if (left == null || right == null) return false;
        boolean sameIdentity = left.sessionIdentity == right.sessionIdentity
                || (left.sessionIdentity != null && left.sessionIdentity.equals(right.sessionIdentity));
        return sameIdentity
                && left.packageName.equals(right.packageName)
                && left.title.equals(right.title)
                && left.artist.equals(right.artist)
                && left.state == right.state
                && left.actions == right.actions;
    }

    private void markUnavailable(String reason, boolean clearSessions) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> markUnavailable(reason, clearSessions));
            return;
        }
        boolean wasAuthoritative = authoritative;
        authoritative = false;
        if (clearSessions) {
            detachSessionListener();
            releaseWatched();
            lastGeneric = empty();
            lastRadio = empty();
        }
        MediaEventTrace.record("session-monitor", "unavailable", reason);
        if (wasAuthoritative || clearSessions) notifyObservers();
    }

    private void detachSessionListener() {
        if (manager == null || !sessionListenerRegistered) return;
        try {
            manager.removeOnActiveSessionsChangedListener(activeSessionsChanged);
        } catch (RuntimeException ignored) {
            // Framework may already have detached it.
        }
        sessionListenerRegistered = false;
    }

    private void releaseWatched() {
        for (MediaController controller : watched.values()) {
            try {
                controller.unregisterCallback(controllerCallback);
            } catch (RuntimeException ignored) {
                // Session may already be destroyed.
            }
        }
        watched.clear();
    }

    private void notifyObservers() {
        MediaListenerService.Snapshot generic = lastGeneric;
        MediaListenerService.Snapshot radio = lastRadio;
        boolean available = authoritative;
        for (WeakReference<Observer> reference : OBSERVERS) {
            Observer observer = reference.get();
            if (observer == null) OBSERVERS.remove(reference);
            else observer.onProcessMediaStateChanged(generic, radio, available);
        }
    }

    private void destroy() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(this::destroy);
            return;
        }
        destroyed = true;
        refreshPosted = false;
        main.removeCallbacks(refreshRunnable);
        detachSessionListener();
        releaseWatched();
        authoritative = false;
    }
}
