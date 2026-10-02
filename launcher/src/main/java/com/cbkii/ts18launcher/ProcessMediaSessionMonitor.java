package com.cbkii.ts18launcher;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;

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
 * This monitor therefore closes the cold-boot ordering gap between access being granted and the
 * listener service receiving onListenerConnected(). Notification notifications remain owned by
 * MediaListenerService and may enrich radio metadata separately.
 */
final class ProcessMediaSessionMonitor {
    interface Observer {
        void onProcessMediaStateChanged(
                MediaListenerService.Snapshot genericMedia,
                MediaListenerService.Snapshot radio);
    }

    private static final CopyOnWriteArrayList<WeakReference<Observer>> OBSERVERS =
            new CopyOnWriteArrayList<>();
    private static final Object LOCK = new Object();
    private static ProcessMediaSessionMonitor instance;

    private final Context context;
    private final MediaSessionManager manager;
    private final ComponentName listenerComponent;
    private final Map<MediaSession.Token, MediaController> watched = new HashMap<>();
    private boolean sessionListenerRegistered;
    private boolean destroyed;
    private MediaListenerService.Snapshot lastGeneric = empty();
    private MediaListenerService.Snapshot lastRadio = empty();

    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) { refreshInternal(); }
        @Override public void onPlaybackStateChanged(PlaybackState state) { refreshInternal(); }
        @Override public void onSessionDestroyed() { refreshInternal(); }
        @Override public void onQueueChanged(List<MediaSession.QueueItem> queue) { refreshInternal(); }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener activeSessionsChanged =
            controllers -> reconcile(controllers == null ? new ArrayList<>() : controllers);

    private ProcessMediaSessionMonitor(Context context) {
        this.context = context.getApplicationContext();
        this.manager = (MediaSessionManager) this.context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        this.listenerComponent = new ComponentName(this.context, MediaListenerService.class);
    }

    static void start(Context context) {
        monitor(context).ensureStarted();
    }

    static void refresh(Context context) {
        ProcessMediaSessionMonitor monitor = monitor(context);
        monitor.ensureStarted();
        monitor.refreshInternal();
    }

    static void addObserver(Context context, Observer observer) {
        if (observer == null) return;
        for (WeakReference<Observer> reference : OBSERVERS) {
            if (reference.get() == observer) return;
        }
        OBSERVERS.add(new WeakReference<>(observer));
        ProcessMediaSessionMonitor monitor = monitor(context);
        monitor.ensureStarted();
        observer.onProcessMediaStateChanged(monitor.lastGeneric, monitor.lastRadio);
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
        if (destroyed || manager == null || sessionListenerRegistered
                || !MediaListenerService.hasNotificationAccess(context)) return;
        try {
            manager.addOnActiveSessionsChangedListener(activeSessionsChanged, listenerComponent);
            sessionListenerRegistered = true;
            MediaEventTrace.record("session-monitor", "listener-registered");
        } catch (SecurityException e) {
            MediaEventTrace.record("session-monitor", "access-denied");
        } catch (RuntimeException e) {
            MediaEventTrace.record("session-monitor", "listener-register-failed",
                    e.getClass().getSimpleName());
        }
    }

    private void refreshInternal() {
        if (destroyed || manager == null) return;
        if (!MediaListenerService.hasNotificationAccess(context)) {
            MediaEventTrace.record("session-monitor", "refresh-skipped", "access-not-granted");
            return;
        }
        ensureStarted();
        try {
            reconcile(manager.getActiveSessions(listenerComponent));
        } catch (SecurityException e) {
            MediaEventTrace.record("session-monitor", "refresh-access-denied");
        } catch (RuntimeException e) {
            MediaEventTrace.record("session-monitor", "refresh-failed",
                    e.getClass().getSimpleName());
        }
    }

    private void reconcile(List<MediaController> controllers) {
        if (destroyed) return;
        List<MediaController> active = controllers == null ? new ArrayList<>() : controllers;
        Map<MediaSession.Token, MediaController> next = new HashMap<>();
        for (MediaController controller : active) {
            MediaSession.Token token = tokenOf(controller);
            if (controller == null || token == null) continue;
            next.put(token, controller);
            if (!watched.containsKey(token)) {
                try {
                    controller.registerCallback(controllerCallback);
                } catch (RuntimeException ignored) {
                    // One broken external session must not break HOME observation.
                }
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
        MediaListenerService.Snapshot nextGeneric = snapshotOf(generic);
        MediaListenerService.Snapshot nextRadio = snapshotOf(radio);
        boolean changed = !same(lastGeneric, nextGeneric) || !same(lastRadio, nextRadio);
        lastGeneric = nextGeneric;
        lastRadio = nextRadio;
        if (changed) {
            MediaEventTrace.record("session-monitor", "state",
                    "music=" + nextGeneric.packageName + " radio=" + nextRadio.packageName);
            notifyObservers();
        }
    }

    private MediaController pickGeneric(List<MediaController> controllers, String radioPackage) {
        String preferred = LauncherPrefs.packageFor(context, LauncherPrefs.KEY_MUSIC);
        if (preferred.isEmpty()) preferred = TopwayAdapter.defaultMusicPackage(context);
        MediaController preferredController = pickExact(controllers, preferred);
        MediaController firstPlayable = null;
        MediaController firstNonRadio = null;
        for (MediaController controller : controllers) {
            if (controller == null || samePackage(controller, radioPackage)) continue;
            if (firstNonRadio == null) firstNonRadio = controller;
            PlaybackState state = safeState(controller);
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) return controller;
            if (firstPlayable == null && state != null
                    && state.getState() != PlaybackState.STATE_NONE
                    && state.getState() != PlaybackState.STATE_ERROR) {
                firstPlayable = controller;
            }
        }
        if (preferredController != null && !samePackage(preferredController, radioPackage)) {
            return preferredController;
        }
        return firstPlayable != null ? firstPlayable : firstNonRadio;
    }

    private static MediaController pickExact(List<MediaController> controllers, String packageName) {
        if (packageName == null || packageName.isEmpty()) return null;
        MediaController fallback = null;
        for (MediaController controller : controllers) {
            if (!samePackage(controller, packageName)) continue;
            PlaybackState state = safeState(controller);
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) return controller;
            if (fallback == null) fallback = controller;
        }
        return fallback;
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
                    MediaMetadata.METADATA_KEY_DISPLAY_TITLE,
                    MediaMetadata.METADATA_KEY_TITLE);
            artist = first(metadata,
                    MediaMetadata.METADATA_KEY_ARTIST,
                    MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                    MediaMetadata.METADATA_KEY_AUTHOR);
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
            if (text != null && text.length() > 0) return text.toString().trim();
            String value = metadata.getString(key);
            if (value != null && !value.trim().isEmpty()) return value.trim();
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
        return left.packageName.equals(right.packageName)
                && left.title.equals(right.title)
                && left.artist.equals(right.artist)
                && left.state == right.state
                && left.actions == right.actions;
    }

    private void notifyObservers() {
        for (WeakReference<Observer> reference : OBSERVERS) {
            Observer observer = reference.get();
            if (observer == null) OBSERVERS.remove(reference);
            else observer.onProcessMediaStateChanged(lastGeneric, lastRadio);
        }
    }

    private void destroy() {
        destroyed = true;
        if (manager != null && sessionListenerRegistered) {
            try {
                manager.removeOnActiveSessionsChangedListener(activeSessionsChanged);
            } catch (RuntimeException ignored) {
                // Framework may already have detached it.
            }
        }
        sessionListenerRegistered = false;
        for (MediaController controller : watched.values()) {
            try {
                controller.unregisterCallback(controllerCallback);
            } catch (RuntimeException ignored) {
                // Session may already be destroyed.
            }
        }
        watched.clear();
    }
}
