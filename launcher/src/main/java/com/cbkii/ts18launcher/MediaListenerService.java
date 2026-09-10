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

import com.cbkii.ts18launcher.platform.TopwayAdapter;

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
        public final int state;
        public final long actions;

        Snapshot(String packageName, String title, String artist, boolean playing) {
            this(packageName, title, artist,
                    playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_NONE,
                    0L);
        }

        Snapshot(String packageName, String title, String artist, int state, long actions) {
            this.packageName = packageName == null ? "" : packageName;
            this.title = title == null ? "" : title;
            this.artist = artist == null ? "" : artist;
            this.state = state;
            this.actions = actions;
            this.playing = usesPauseAction(state);
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

        public boolean supports(Command command) {
            if (command == null || packageName.isEmpty()) return false;
            switch (command) {
                case PREVIOUS:
                    return (actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L;
                case NEXT:
                    return (actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0L;
                case PLAY_PAUSE:
                    long direct = playing ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY;
                    return (actions & direct) != 0L
                            || (actions & PlaybackState.ACTION_PLAY_PAUSE) != 0L;
                default:
                    return false;
            }
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
        if (listenerComponent != null) requestRebind(listenerComponent);
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

        MediaController genericController = selectGenericController(controllers);
        MediaController radioController = pickExactPackage(
                controllers, RadioProvider.resolvePackage(this));

        lastGeneric = snapshotOf(genericController);
        lastRadio = snapshotOf(radioController);
        notifyObservers();
    }

    private MediaController selectGenericController(List<MediaController> controllers) {
        String radioPackage = RadioProvider.resolvePackage(this);
        String preferredPackage = preferredMusicPackage();
        boolean preferConfigured = LauncherPrefs.MEDIA_MODE_PREFER_MUSIC.equals(
                LauncherPrefs.mediaMode(this));
        return pickPrimary(controllers, radioPackage, preferredPackage, preferConfigured);
    }

    private String preferredMusicPackage() {
        String preferred = LauncherPrefs.packageFor(this, LauncherPrefs.KEY_MUSIC);
        return preferred.isEmpty() ? TopwayAdapter.defaultMusicPackage(this) : preferred;
    }

    private static MediaController pickPrimary(
            List<MediaController> controllers,
            String excludedPackage,
            String preferredPackage,
            boolean preferConfigured) {
        if (preferConfigured && preferredPackage != null && !preferredPackage.isEmpty()
                && !preferredPackage.equals(excludedPackage)) {
            MediaController preferred = pickExactPackage(controllers, preferredPackage);
            if (preferred != null) return preferred;
        }

        MediaController fallback = null;
        for (MediaController controller : controllers) {
            if (controller == null || excludedFromGenericMedia(controller, excludedPackage)) continue;
            PlaybackState state = controller.getPlaybackState();
            int value = state == null ? PlaybackState.STATE_NONE : state.getState();
            if (usesPauseAction(value)) return controller;
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
        // Bluetooth media remains eligible; only radio and call/telecom sessions are excluded.
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
        int state = playbackState == null ? PlaybackState.STATE_NONE : playbackState.getState();
        long actions = playbackState == null ? 0L : playbackState.getActions();
        return new Snapshot(
                controller.getPackageName(),
                title == null ? "" : title.toString(),
                artist == null ? "" : artist.toString(),
                state,
                actions);
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
            if (candidate == null || candidate == observer) OBSERVERS.remove(reference);
        }
    }

    public static void refreshActiveSessions() {
        MediaListenerService service = instance;
        if (service != null) service.refresh();
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
        if (sessionManager == null) return null;
        try {
            return selectGenericController(sessionManager.getActiveSessions(listenerComponent));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private MediaController findRadioController() {
        if (sessionManager == null) return null;
        try {
            return pickExactPackage(
                    sessionManager.getActiveSessions(listenerComponent),
                    RadioProvider.resolvePackage(this));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean sendToController(MediaController controller, Command command) {
        if (controller == null || command == null) return false;
        try {
            PlaybackState state = controller.getPlaybackState();
            long actions = state == null ? 0L : state.getActions();
            boolean pauseSide = state != null && usesPauseAction(state.getState());
            switch (command) {
                case PREVIOUS:
                    if ((actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) == 0L) return false;
                    controller.getTransportControls().skipToPrevious();
                    break;
                case NEXT:
                    if ((actions & PlaybackState.ACTION_SKIP_TO_NEXT) == 0L) return false;
                    controller.getTransportControls().skipToNext();
                    break;
                case PLAY_PAUSE:
                    long directAction = pauseSide
                            ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY;
                    if ((actions & directAction) == 0L
                            && (actions & PlaybackState.ACTION_PLAY_PAUSE) == 0L) {
                        return false;
                    }
                    if (pauseSide) controller.getTransportControls().pause();
                    else controller.getTransportControls().play();
                    break;
                default:
                    return false;
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static String sessionDiagnostics(Context context) {
        if (!hasNotificationAccess(context)) return "Notification access is not granted.";
        MediaListenerService service = instance;
        if (service == null) return "Notification listener is not connected.";
        return service.buildSessionDiagnostics();
    }

    private String buildSessionDiagnostics() {
        if (sessionManager == null) return "MediaSessionManager unavailable.";
        final List<MediaController> controllers;
        try {
            controllers = sessionManager.getActiveSessions(listenerComponent);
        } catch (RuntimeException e) {
            return "Active sessions unavailable: " + e.getClass().getSimpleName();
        }

        String radioPackage = RadioProvider.resolvePackage(this);
        String preferredPackage = preferredMusicPackage();
        String mode = LauncherPrefs.mediaMode(this);
        MediaController generic = selectGenericController(controllers);
        MediaController radio = pickExactPackage(controllers, radioPackage);

        StringBuilder out = new StringBuilder();
        out.append("Mode: ")
                .append(LauncherPrefs.MEDIA_MODE_PREFER_MUSIC.equals(mode)
                        ? "prefer music app" : "auto")
                .append('\n');
        out.append("Preferred music: ").append(emptyAsNone(preferredPackage)).append('\n');
        out.append("Radio: ").append(emptyAsNone(radioPackage));
        if (RadioProvider.isAutoDetectedNavRadio(this)) out.append(" (NavRadio+ auto-detected)");
        out.append("\n\n");

        if (controllers == null || controllers.isEmpty()) {
            out.append("No active media sessions.");
            return out.toString();
        }

        int index = 0;
        for (MediaController controller : controllers) {
            if (controller == null) continue;
            Snapshot snapshot = snapshotOf(controller);
            boolean genericSelected = sameSession(controller, generic);
            boolean radioSelected = sameSession(controller, radio);
            out.append(++index).append(". ").append(controller.getPackageName());
            if (genericSelected) out.append(" [music]");
            if (radioSelected) out.append(" [radio]");
            out.append('\n');
            out.append("   state=").append(stateName(snapshot.state));
            out.append(" actions=").append(actionSummary(snapshot.actions, snapshot.playing));
            String display = snapshot.displayText();
            if (!display.isEmpty()) out.append("\n   ").append(display);
            out.append('\n');
        }
        return out.toString().trim();
    }

    private static boolean sameSession(MediaController first, MediaController second) {
        if (first == null || second == null) return false;
        MediaSession.Token firstToken = first.getSessionToken();
        MediaSession.Token secondToken = second.getSessionToken();
        return firstToken != null && firstToken.equals(secondToken);
    }

    private static String actionSummary(long actions, boolean playing) {
        boolean previous = (actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L;
        boolean next = (actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0L;
        long direct = playing ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY;
        boolean playPause = (actions & direct) != 0L
                || (actions & PlaybackState.ACTION_PLAY_PAUSE) != 0L;
        return "prev=" + yesNo(previous)
                + " play/pause=" + yesNo(playPause)
                + " next=" + yesNo(next);
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static String emptyAsNone(String value) {
        return value == null || value.isEmpty() ? "none" : value;
    }

    private static String stateName(int state) {
        switch (state) {
            case PlaybackState.STATE_PLAYING: return "playing";
            case PlaybackState.STATE_PAUSED: return "paused";
            case PlaybackState.STATE_BUFFERING: return "buffering";
            case PlaybackState.STATE_CONNECTING: return "connecting";
            case PlaybackState.STATE_STOPPED: return "stopped";
            case PlaybackState.STATE_ERROR: return "error";
            case PlaybackState.STATE_SKIPPING_TO_NEXT: return "skipping-next";
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS: return "skipping-previous";
            default: return "state-" + state;
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
