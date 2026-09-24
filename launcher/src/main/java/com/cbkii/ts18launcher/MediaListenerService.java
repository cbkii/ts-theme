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
import java.util.LinkedHashMap;
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
            this.title = normalise(title);
            this.artist = normalise(artist);
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
    private static final Object EXTERNAL_REGISTRY_LOCK = new Object();
    /**
     * Browser-owned controllers outlive NotificationListenerService reconnects. Keep only the
     * controllers explicitly owned by MediaSourceBootstrapper so a listener rebind can resume
     * metadata observation without starting the source again.
     */
    private static final Map<MediaSession.Token, MediaController> REGISTERED_EXTERNAL =
            new HashMap<>();
    private static volatile MediaListenerService instance;
    private static volatile Snapshot lastGeneric = new Snapshot("", "", "", false);
    private static volatile Snapshot lastRadio = new Snapshot("", "", "", false);

    /** Controllers independently discovered by the notification-listener active-session surface. */
    private final Map<MediaSession.Token, MediaController> watched = new HashMap<>();
    /** Controllers obtained through a launcher-owned MediaBrowser bind; same token is deduplicated. */
    private final Map<MediaSession.Token, MediaController> external = new HashMap<>();
    private final Map<MediaSession.Token, MediaController.Callback> externalCallbacks = new HashMap<>();
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

    @Override public void onCreate() {
        super.onCreate();
        sessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        listenerComponent = new ComponentName(this, MediaListenerService.class);
        MediaEventTrace.record("listener", "created");
    }

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
        MediaEventTrace.record("listener", "connected");
        if (sessionManager == null) {
            MediaEventTrace.record("listener", "manager-unavailable");
            publishEmpty();
            return;
        }
        try {
            sessionManager.addOnActiveSessionsChangedListener(
                    sessionsChangedListener, listenerComponent);
        } catch (SecurityException ignored) {
            MediaEventTrace.record("listener", "session-access-denied");
            publishEmpty();
            return;
        }
        attachRegisteredExternalControllers();
        refresh();
    }

    @Override public void onListenerDisconnected() {
        MediaEventTrace.record("listener", "disconnected", "requesting framework rebind");
        releaseAll();
        detachSessionListener();
        instance = null;
        publishEmpty();
        super.onListenerDisconnected();
        if (listenerComponent != null) requestRebind(listenerComponent);
    }

    @Override public void onDestroy() {
        MediaEventTrace.record("listener", "destroyed");
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
            MediaEventTrace.record("listener", "refresh-access-denied");
            publishEmpty();
        }
    }

    private void reconcile(List<MediaController> activeControllers) {
        List<MediaController> active = activeControllers == null
                ? new ArrayList<>() : activeControllers;
        Map<MediaSession.Token, MediaController> next = new HashMap<>();
        for (MediaController controller : active) {
            MediaSession.Token token = tokenOf(controller);
            if (controller == null || token == null) continue;
            next.put(token, controller);
            if (!watched.containsKey(token)) {
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

        List<MediaController> controllers = mergedControllers(active);
        MediaController genericController = selectGenericController(controllers);
        MediaController radioController = pickExactPackage(
                controllers, RadioProvider.resolvePackage(this));

        Snapshot previousGeneric = lastGeneric;
        Snapshot previousRadio = lastRadio;
        Snapshot nextGeneric = snapshotOf(genericController);
        Snapshot nextRadio = snapshotOf(radioController);
        lastGeneric = nextGeneric;
        lastRadio = nextRadio;
        if (!sameSnapshot(previousGeneric, nextGeneric)) {
            MediaEventTrace.record("session", "music-state", snapshotTrace(nextGeneric));
        }
        if (!sameSnapshot(previousRadio, nextRadio)) {
            MediaEventTrace.record("session", "radio-state", snapshotTrace(nextRadio));
        }
        notifyObservers();
    }

    private List<MediaController> mergedControllers(List<MediaController> active) {
        LinkedHashMap<MediaSession.Token, MediaController> merged = new LinkedHashMap<>();
        if (active != null) {
            for (MediaController controller : active) {
                MediaSession.Token token = tokenOf(controller);
                if (controller != null && token != null) merged.put(token, controller);
            }
        }
        for (Map.Entry<MediaSession.Token, MediaController> entry : external.entrySet()) {
            if (!merged.containsKey(entry.getKey())) merged.put(entry.getKey(), entry.getValue());
        }
        return new ArrayList<>(merged.values());
    }

    static void observeExternalController(MediaController controller) {
        MediaSession.Token token = tokenOf(controller);
        if (controller == null || token == null) return;
        synchronized (EXTERNAL_REGISTRY_LOCK) {
            REGISTERED_EXTERNAL.put(token, controller);
        }
        MediaEventTrace.record("listener", "external-registered", controller.getPackageName());
        MediaListenerService service = instance;
        if (service != null) service.addExternalController(controller);
    }

    static void forgetExternalController(MediaController controller) {
        MediaSession.Token token = tokenOf(controller);
        if (token == null) return;
        MediaEventTrace.record("listener", "external-forgotten",
                controller == null ? "unknown" : controller.getPackageName());
        forgetRegisteredExternalController(token, controller);
        MediaListenerService service = instance;
        if (service != null) service.removeExternalController(token);
    }

    private static void forgetRegisteredExternalController(
            MediaSession.Token token, MediaController expectedController) {
        if (token == null) return;
        synchronized (EXTERNAL_REGISTRY_LOCK) {
            MediaController registered = REGISTERED_EXTERNAL.get(token);
            if (expectedController == null || registered == expectedController) {
                REGISTERED_EXTERNAL.remove(token);
            }
        }
    }

    private void attachRegisteredExternalControllers() {
        List<MediaController> registered;
        synchronized (EXTERNAL_REGISTRY_LOCK) {
            registered = new ArrayList<>(REGISTERED_EXTERNAL.values());
        }
        if (!registered.isEmpty()) {
            MediaEventTrace.record("listener", "external-reattach",
                    "count=" + registered.size());
        }
        for (MediaController controller : registered) addExternalController(controller);
    }

    private void addExternalController(MediaController controller) {
        MediaSession.Token token = tokenOf(controller);
        if (controller == null || token == null || external.containsKey(token)) return;
        MediaController.Callback observer = new MediaController.Callback() {
            @Override public void onMetadataChanged(MediaMetadata metadata) { refresh(); }
            @Override public void onPlaybackStateChanged(PlaybackState state) { refresh(); }
            @Override public void onQueueChanged(List<MediaSession.QueueItem> queue) { refresh(); }
            @Override public void onSessionDestroyed() {
                MediaEventTrace.record("session", "external-destroyed", controller.getPackageName());
                forgetExternalController(controller);
            }
        };
        try {
            controller.registerCallback(observer);
        } catch (RuntimeException ignored) {
            forgetRegisteredExternalController(token, controller);
            return;
        }
        external.put(token, controller);
        externalCallbacks.put(token, observer);
        MediaEventTrace.record("listener", "external-attached", controller.getPackageName());
        refresh();
    }

    private void removeExternalController(MediaSession.Token token) {
        if (token == null) return;
        MediaController controller = external.remove(token);
        MediaController.Callback observer = externalCallbacks.remove(token);
        if (controller != null && observer != null) {
            try {
                controller.unregisterCallback(observer);
            } catch (RuntimeException ignored) {
                // Session may already be destroyed.
            }
            MediaEventTrace.record("listener", "external-detached", controller.getPackageName());
        }
        refresh();
    }

    private MediaController selectGenericController(List<MediaController> controllers) {
        String radioPackage = RadioProvider.resolvePackage(this);
        String preferredPackage = preferredMusicPackage();
        boolean preferConfigured = LauncherPrefs.MEDIA_MODE_PREFER_MUSIC.equals(
                LauncherPrefs.mediaMode(this));
        if (preferConfigured) {
            MediaController configured = pickExactPackage(controllers, preferredPackage);
            if (configured != null && !excludedFromGenericMedia(configured, radioPackage)) return configured;
        }
        return pickPrimary(controllers, radioPackage, preferredPackage, preferConfigured,
                LauncherPrefs.lastMusicPackage(this));
    }

    private String preferredMusicPackage() {
        String preferred = LauncherPrefs.packageFor(this, LauncherPrefs.KEY_MUSIC);
        return preferred.isEmpty() ? TopwayAdapter.defaultMusicPackage(this) : preferred;
    }

    private static MediaController pickPrimary(
            List<MediaController> controllers,
            String excludedPackage,
            String preferredPackage,
            boolean preferConfigured, String rememberedPackage) {
        List<MediaSelection.Candidate> candidates = new ArrayList<>();
        for (MediaController controller : controllers) {
            PlaybackState state = playbackState(controller);
            int value = state == null ? PlaybackState.STATE_NONE : state.getState();
            candidates.add(new MediaSelection.Candidate(controller == null ? "" : controller.getPackageName(),
                    controller != null && !excludedFromGenericMedia(controller, excludedPackage),
                    state != null && value != PlaybackState.STATE_NONE && value != PlaybackState.STATE_ERROR,
                    usesPauseAction(value), value == PlaybackState.STATE_PAUSED || value == PlaybackState.STATE_STOPPED));
        }
        int selected = MediaSelection.pick(candidates, preferredPackage, preferConfigured, rememberedPackage);
        return selected < 0 ? null : controllers.get(selected);
    }

    private static boolean excludedFromGenericMedia(
            MediaController controller, String configuredRadioPackage) {
        String packageName = controller.getPackageName();
        if (configuredRadioPackage != null && !configuredRadioPackage.isEmpty()
                && configuredRadioPackage.equals(packageName)) return true;
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
            PlaybackState state = playbackState(controller);
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
        MediaMetadata metadata;
        PlaybackState playbackState;
        try {
            metadata = controller.getMetadata();
            playbackState = controller.getPlaybackState();
        } catch (RuntimeException ignored) {
            return new Snapshot(controller.getPackageName(), "", "", false);
        }
        String title = firstNonBlank(metadata,
                MediaMetadata.METADATA_KEY_TITLE,
                MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        String artist = firstNonBlank(metadata,
                MediaMetadata.METADATA_KEY_ARTIST,
                MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        int state = playbackState == null ? PlaybackState.STATE_NONE : playbackState.getState();
        long actions = playbackState == null ? 0L : playbackState.getActions();
        return new Snapshot(controller.getPackageName(), title, artist, state, actions);
    }

    private static String firstNonBlank(MediaMetadata metadata, String... keys) {
        if (metadata == null) return "";
        for (String key : keys) {
            CharSequence value = metadata.getText(key);
            String text = value == null ? "" : value.toString().trim();
            if (!text.isEmpty()) return text;
        }
        return "";
    }

    private static String normalise(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean sameSnapshot(Snapshot first, Snapshot second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        return first.packageName.equals(second.packageName)
                && first.title.equals(second.title)
                && first.artist.equals(second.artist)
                && first.state == second.state
                && first.actions == second.actions;
    }

    private static String snapshotTrace(Snapshot snapshot) {
        if (snapshot == null || snapshot.packageName.isEmpty()) return "none";
        return snapshot.packageName + " state=" + stateName(snapshot.state)
                + " actions=" + snapshot.actions;
    }

    private static PlaybackState playbackState(MediaController controller) {
        if (controller == null) return null;
        try {
            return controller.getPlaybackState();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static MediaSession.Token tokenOf(MediaController controller) {
        if (controller == null) return null;
        try {
            return controller.getSessionToken();
        } catch (RuntimeException ignored) {
            return null;
        }
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
        for (Map.Entry<MediaSession.Token, MediaController> entry : external.entrySet()) {
            MediaController.Callback observer = externalCallbacks.get(entry.getKey());
            if (observer == null) continue;
            try {
                entry.getValue().unregisterCallback(observer);
            } catch (RuntimeException ignored) {
                // Best-effort cleanup.
            }
        }
        external.clear();
        externalCallbacks.clear();
    }

    private static void publishEmpty() {
        boolean changed = !lastGeneric.isEmpty() || !lastRadio.isEmpty();
        lastGeneric = new Snapshot("", "", "", false);
        lastRadio = new Snapshot("", "", "", false);
        if (changed) MediaEventTrace.record("session", "published-empty");
        notifyObservers();
    }

    private static void notifyObservers() {
        for (WeakReference<Observer> reference : OBSERVERS) {
            Observer observer = reference.get();
            if (observer == null) OBSERVERS.remove(reference);
            else observer.onMediaStateChanged(lastGeneric, lastRadio);
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

    static void noteExplicitLaunch(Context context, String pkg) {
        if (pkg == null || pkg.isEmpty()) return;
        if (pkg.equals(RadioProvider.resolvePackage(context))) {
            LauncherPrefs.selectSource(context, MediaSelection.RADIO);
            return;
        }
        MediaListenerService service = instance;
        boolean music = pkg.equals(LauncherPrefs.packageFor(context, LauncherPrefs.KEY_MUSIC))
                || pkg.equals(TopwayAdapter.defaultMusicPackage(context));
        if (service != null) {
            for (MediaController controller : service.mergedControllers(
                    new ArrayList<>(service.watched.values()))) {
                if (pkg.equals(controller.getPackageName())
                        && !excludedFromGenericMedia(controller, RadioProvider.resolvePackage(context))) {
                    music = true;
                }
            }
        }
        if (music) {
            LauncherPrefs.rememberMusic(context, pkg);
            LauncherPrefs.selectSource(context, MediaSelection.MUSIC);
        }
    }

    public static boolean sendGeneric(Command command) {
        MediaListenerService service = instance;
        if (service == null) return false;
        MediaController selected = service.findGenericController();
        if (selected != null) LauncherPrefs.rememberMusic(service, selected.getPackageName());
        LauncherPrefs.selectSource(service, MediaSelection.MUSIC);
        return service.sendToController(selected, command);
    }

    public static boolean sendRadio(Command command) {
        MediaListenerService service = instance;
        if (service == null) return false;
        LauncherPrefs.selectSource(service, MediaSelection.RADIO);
        return service.sendToController(service.findRadioController(), command);
    }

    private List<MediaController> activeAndExternalControllers() {
        List<MediaController> active = new ArrayList<>();
        if (sessionManager != null) {
            try {
                active = sessionManager.getActiveSessions(listenerComponent);
            } catch (RuntimeException ignored) {
                // External controllers can still remain observable.
            }
        }
        return mergedControllers(active);
    }

    private MediaController findGenericController() {
        return selectGenericController(activeAndExternalControllers());
    }

    private MediaController findRadioController() {
        return pickExactPackage(activeAndExternalControllers(), RadioProvider.resolvePackage(this));
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
                            && (actions & PlaybackState.ACTION_PLAY_PAUSE) == 0L) return false;
                    if (pauseSide) controller.getTransportControls().pause();
                    else controller.getTransportControls().play();
                    break;
                default:
                    return false;
            }
            MediaEventTrace.record("listener-command", "dispatched",
                    controller.getPackageName() + " command=" + command);
            return true;
        } catch (RuntimeException e) {
            MediaEventTrace.record("listener-command", "failed",
                    controller.getPackageName() + " command=" + command);
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
        List<MediaController> controllers = activeAndExternalControllers();
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
        out.append("\nBound MediaBrowser controllers: ").append(external.size()).append("\n\n");

        if (controllers.isEmpty()) {
            out.append("No observable media sessions.");
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
            if (external.containsKey(tokenOf(controller))) out.append(" [bound]");
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
        MediaSession.Token firstToken = tokenOf(first);
        MediaSession.Token secondToken = tokenOf(second);
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
