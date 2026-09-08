package com.cbkii.ts18launcher;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MediaListenerService extends NotificationListenerService {
    public enum Command { PREVIOUS, PLAY_PAUSE, NEXT }

    public static final class Snapshot {
        public final String packageName;
        public final String title;
        public final String artist;
        public final boolean playing;

        Snapshot(String packageName, String title, String artist, boolean playing) {
            this.packageName = packageName == null ? "" : packageName;
            this.title = title == null ? "" : title;
            this.artist = artist == null ? "" : artist;
            this.playing = playing;
        }

        public boolean isEmpty() {
            return packageName.isEmpty() && title.isEmpty() && artist.isEmpty();
        }

        public String displayText() {
            if (!artist.isEmpty() && !title.isEmpty()) return artist + " – " + title;
            if (!title.isEmpty()) return title;
            if (!artist.isEmpty()) return artist;
            return "";
        }
    }

    public interface Observer {
        void onMediaStateChanged(Snapshot genericMedia, Snapshot radio);
    }

    private static final CopyOnWriteArrayList<WeakReference<Observer>> OBSERVERS =
            new CopyOnWriteArrayList<>();
    private static volatile MediaListenerService instance;
    private static volatile Snapshot lastGeneric = new Snapshot("", "", "", false);
    private static volatile Snapshot lastRadio = new Snapshot("", "", "", false);

    private final Map<MediaSession.Token, MediaController> watched = new HashMap<>();
    private MediaSessionManager sessionManager;
    private ComponentName listenerComponent;

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) { refresh(); }
        @Override public void onPlaybackStateChanged(PlaybackState state) { refresh(); }
        @Override public void onSessionDestroyed() { refresh(); }
        @Override public void onQueueChanged(List<MediaSession.QueueItem> queue) { refresh(); }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChangedListener =
            controllers -> reconcile(controllers == null ? new ArrayList<>() : controllers);

    @Override
    public void onCreate() {
        super.onCreate();
        sessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        listenerComponent = new ComponentName(this, MediaListenerService.class);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
        if (sessionManager == null) {
            publishEmpty();
            return;
        }
        try {
            sessionManager.addOnActiveSessionsChangedListener(
                    sessionsChangedListener, listenerComponent);
        } catch (SecurityException ignored) {
            publishEmpty();
            return;
        }
        refresh();
    }

    @Override
    public void onListenerDisconnected() {
        releaseAll();
        detachSessionListener();
        instance = null;
        publishEmpty();
        super.onListenerDisconnected();
    }

    @Override
    public void onDestroy() {
        releaseAll();
        detachSessionListener();
        if (instance == this) instance = null;
        publishEmpty();
        super.onDestroy();
    }

    private void detachSessionListener() {
        if (sessionManager == null) return;
        try {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener);
        } catch (RuntimeException ignored) {
            // Listener may already be detached by the framework.
        }
    }

    private void refresh() {
        if (sessionManager == null) {
            publishEmpty();
            return;
        }
        try {
            reconcile(sessionManager.getActiveSessions(listenerComponent));
        } catch (SecurityException ignored) {
            publishEmpty();
        }
    }

    private void reconcile(List<MediaController> controllers) {
        Map<MediaSession.Token, MediaController> next = new HashMap<>();
        for (MediaController controller : controllers) {
            if (controller == null || controller.getSessionToken() == null) continue;
            next.put(controller.getSessionToken(), controller);
            if (!watched.containsKey(controller.getSessionToken())) {
                try {
                    controller.registerCallback(callback);
                } catch (RuntimeException ignored) {
                    // A single broken session must not break HOME.
                }
            }
        }

        for (Map.Entry<MediaSession.Token, MediaController> entry : watched.entrySet()) {
            if (!next.containsKey(entry.getKey())) {
                try {
                    entry.getValue().unregisterCallback(callback);
                } catch (RuntimeException ignored) {
                    // Framework may already have removed it.
                }
            }
        }
        watched.clear();
        watched.putAll(next);

        String radioPackage = LauncherPrefs.packageFor(this, LauncherPrefs.KEY_RADIO);
        MediaController genericController = pickPrimary(controllers, radioPackage);
        MediaController radioController = pickExactPackage(controllers, radioPackage);

        lastGeneric = snapshotOf(genericController);
        lastRadio = snapshotOf(radioController);
        notifyObservers();
    }

    private static MediaController pickPrimary(List<MediaController> controllers, String excludedPackage) {
        MediaController fallback = null;
        for (MediaController controller : controllers) {
            if (controller == null || excludedFromGenericMedia(controller, excludedPackage)) continue;
            PlaybackState state = controller.getPlaybackState();
            int value = state == null ? PlaybackState.STATE_NONE : state.getState();
            if (value == PlaybackState.STATE_PLAYING
                    || value == PlaybackState.STATE_BUFFERING
                    || value == PlaybackState.STATE_CONNECTING) {
                return controller;
            }
            if (fallback == null && (value == PlaybackState.STATE_PAUSED
                    || value == PlaybackState.STATE_STOPPED)) {
                fallback = controller;
            }
        }
        if (fallback != null) return fallback;
        for (MediaController controller : controllers) {
            if (controller != null && !excludedFromGenericMedia(controller, excludedPackage)) {
                return controller;
            }
        }
        return null;
    }

    private static boolean excludedFromGenericMedia(
            MediaController controller, String configuredRadioPackage) {
        String packageName = controller.getPackageName();
        if (configuredRadioPackage != null && !configuredRadioPackage.isEmpty()
                && configuredRadioPackage.equals(packageName)) {
            return true;
        }
        // Keep telecom/call sessions from becoming the dashboard music authority. Do not
        // blanket-exclude Bluetooth packages: on this TS18 Bluetooth media is a valid source.
        return "com.android.server.telecom".equals(packageName)
                || "com.android.dialer".equals(packageName)
                || "com.google.android.dialer".equals(packageName)
                || "com.android.phone".equals(packageName);
    }

    private static MediaController pickExactPackage(List<MediaController> controllers, String packageName) {
        if (packageName == null || packageName.isEmpty()) return null;
        MediaController fallback = null;
        for (MediaController controller : controllers) {
            if (controller == null || !packageName.equals(controller.getPackageName())) continue;
            PlaybackState state = controller.getPlaybackState();
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) return controller;
            if (fallback == null) fallback = controller;
        }
        return fallback;
    }

    private static Snapshot snapshotOf(MediaController controller) {
        if (controller == null) return new Snapshot("", "", "", false);
        MediaMetadata metadata = controller.getMetadata();
        CharSequence title = metadata == null ? null : metadata.getText(MediaMetadata.METADATA_KEY_TITLE);
        if (title == null && metadata != null) {
            title = metadata.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        }
        CharSequence artist = metadata == null ? null : metadata.getText(MediaMetadata.METADATA_KEY_ARTIST);
        if (artist == null && metadata != null) {
            artist = metadata.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        }
        if (artist == null && metadata != null) {
            artist = metadata.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        }
        PlaybackState playbackState = controller.getPlaybackState();
        boolean playing = playbackState != null
                && playbackState.getState() == PlaybackState.STATE_PLAYING;
        return new Snapshot(
                controller.getPackageName(),
                title == null ? "" : title.toString(),
                artist == null ? "" : artist.toString(),
                playing);
    }

    private void releaseAll() {
        for (MediaController controller : watched.values()) {
            try {
                controller.unregisterCallback(callback);
            } catch (RuntimeException ignored) {
                // Best-effort cleanup.
            }
        }
        watched.clear();
    }

    private static void publishEmpty() {
        lastGeneric = new Snapshot("", "", "", false);
        lastRadio = new Snapshot("", "", "", false);
        notifyObservers();
    }

    private static void notifyObservers() {
        for (WeakReference<Observer> reference : OBSERVERS) {
            Observer observer = reference.get();
            if (observer == null) {
                OBSERVERS.remove(reference);
            } else {
                observer.onMediaStateChanged(lastGeneric, lastRadio);
            }
        }
    }

    public static void addObserver(Observer observer) {
        if (observer == null) return;
        for (WeakReference<Observer> reference : OBSERVERS) {
            if (reference.get() == observer) return;
        }
        OBSERVERS.add(new WeakReference<>(observer));
        observer.onMediaStateChanged(lastGeneric, lastRadio);
    }

    public static void removeObserver(Observer observer) {
        Iterator<WeakReference<Observer>> iterator = OBSERVERS.iterator();
        while (iterator.hasNext()) {
            WeakReference<Observer> reference = iterator.next();
            Observer candidate = reference.get();
            if (candidate == null || candidate == observer) {
                OBSERVERS.remove(reference);
            }
        }
    }

    public static boolean sendGeneric(Command command) {
        MediaListenerService service = instance;
        if (service == null) return false;
        return service.sendToController(service.findGenericController(), command);
    }

    public static boolean sendRadio(Command command) {
        MediaListenerService service = instance;
        if (service == null) return false;
        return service.sendToController(service.findRadioController(), command);
    }

    private MediaController findGenericController() {
        try {
            String radioPackage = LauncherPrefs.packageFor(this, LauncherPrefs.KEY_RADIO);
            return pickPrimary(sessionManager.getActiveSessions(listenerComponent), radioPackage);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private MediaController findRadioController() {
        try {
            String radioPackage = LauncherPrefs.packageFor(this, LauncherPrefs.KEY_RADIO);
            return pickExactPackage(sessionManager.getActiveSessions(listenerComponent), radioPackage);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean sendToController(MediaController controller, Command command) {
        if (controller == null || command == null) return false;
        try {
            switch (command) {
                case PREVIOUS:
                    controller.getTransportControls().skipToPrevious();
                    break;
                case NEXT:
                    controller.getTransportControls().skipToNext();
                    break;
                case PLAY_PAUSE:
                    PlaybackState state = controller.getPlaybackState();
                    if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                        controller.getTransportControls().pause();
                    } else {
                        controller.getTransportControls().play();
                    }
                    break;
                default:
                    return false;
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean hasNotificationAccess(Context context) {
        String flat = Settings.Secure.getString(
                context.getContentResolver(), "enabled_notification_listeners");
        if (flat == null || flat.isEmpty()) return false;
        String packageName = context.getPackageName();
        for (String item : flat.split(":")) {
            ComponentName component = ComponentName.unflattenFromString(item);
            if (component != null && packageName.equals(component.getPackageName())) return true;
        }
        return false;
    }
}
