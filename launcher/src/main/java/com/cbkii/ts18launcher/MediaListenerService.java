package com.cbkii.ts18launcher;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

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

    private static final long DEFENSIVE_REFRESH_INTERVAL_MS = 15000L;

    public static final class Snapshot {
        public final String packageName;
        public final String title;
        public final String artist;
        final RadioFrequency radioFrequency;
        public final boolean playing;
        public final int state;
        public final long actions;
        final Object sessionIdentity;
        final String mediaId;
        final long queueItemId;
        final long observationGeneration;
        final long observedAtMs;
        final boolean itemVerified;
        final String itemEvidence;
        final int observedQueueSize;

        Snapshot(String packageName, String title, String artist, boolean playing) {
            this(packageName, title, artist,
                    playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_NONE,
                    0L, null);
        }

        Snapshot(String packageName, String title, String artist, int state, long actions) {
            this(packageName, title, artist, state, actions, null);
        }

        Snapshot(String packageName, String title, String artist, int state, long actions,
                 Object sessionIdentity) {
            this(packageName, title, artist, state, actions, sessionIdentity, "", -1L, 0L, 0L, false);
        }

        Snapshot(String packageName, String title, String artist, int state, long actions,
                 Object sessionIdentity, String mediaId, long queueItemId,
                 long generation, long observedAtMs, boolean itemVerified) {
            this(packageName, title, artist, state, actions, sessionIdentity, mediaId, queueItemId,
                    generation, observedAtMs, itemVerified, "NO_ITEM_ID", -1);
        }

        Snapshot(String packageName, String title, String artist, int state, long actions,
                 Object sessionIdentity, String mediaId, long queueItemId,
                 long generation, long observedAtMs, boolean itemVerified, String evidence, int queueSize) {
            this.packageName = packageName == null ? "" : packageName;
            this.title = normalise(title);
            this.artist = normalise(artist);
            this.radioFrequency = RadioFrequency.observed(this.packageName, this.title, this.artist);
            this.state = state;
            this.actions = actions;
            this.sessionIdentity = sessionIdentity;
            this.playing = state == PlaybackState.STATE_PLAYING;
            this.mediaId = normalise(mediaId);
            this.queueItemId = queueItemId;
            this.observationGeneration = generation;
            this.observedAtMs = observedAtMs;
            this.itemVerified = itemVerified;
            this.itemEvidence = evidence;
            this.observedQueueSize = queueSize;
        }

        Snapshot withMetadata(String title, String artist) {
            return new Snapshot(packageName, title, artist, state, actions, sessionIdentity,
                    mediaId, queueItemId, observationGeneration, observedAtMs, itemVerified,
                    itemEvidence, observedQueueSize);
        }

        String trackIdentity() {
            return String.valueOf(sessionIdentity) + ":" + observationGeneration + ":" + mediaId + ":" + queueItemId;
        }

        String observationEvidence() {
            String idHash = "none";
            if (!mediaId.isEmpty()) {
                try {
                    byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                            .digest(mediaId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    StringBuilder hash = new StringBuilder();
                    for (int i = 0; i < 6; i++) hash.append(String.format(java.util.Locale.ROOT, "%02x", digest[i] & 255));
                    idHash = hash.toString();
                } catch (java.security.NoSuchAlgorithmException unavailable) { idHash = "unavailable"; }
            }
            return "owner=" + observationGeneration + " item=" + idHash + " active=" + queueItemId
                    + " queue=" + observedQueueSize + " evidence=" + itemEvidence
                    + " state=" + state + " actions=" + actions;
        }

        public boolean isEmpty() {
            return packageName.isEmpty() && title.isEmpty() && artist.isEmpty();
        }

        public boolean hasMetadata() {
            return !title.isEmpty() || !artist.isEmpty();
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
                    return (actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L
                            || MediaSourceAdapter.allowsUnadvertisedSkip(packageName);
                case NEXT:
                    return (actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0L
                            || MediaSourceAdapter.allowsUnadvertisedSkip(packageName);
                case PLAY_PAUSE:
                    long direct = usesPauseAction(state) ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY;
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
    private static final Map<MediaSession.Token, MediaController> REGISTERED_EXTERNAL =
            new HashMap<>();
    private static volatile MediaListenerService instance;
    private static volatile Snapshot lastGeneric = new Snapshot("", "", "", false);
    private static volatile Snapshot lastRadio = new Snapshot("", "", "", false);
    private static volatile long lastDefensiveRefreshUptimeMs;

    private final Map<MediaSession.Token, MediaController> watched = new HashMap<>();
    private final Map<MediaSession.Token, MediaController> external = new HashMap<>();
    private final Map<MediaSession.Token, MediaController.Callback> externalCallbacks = new HashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean connected;
    private int listenerGeneration;
    private MediaSessionManager sessionManager;
    private ComponentName listenerComponent;
    private String radioNotificationKey = "";
    private String radioNotificationTitle = "";
    private String radioNotificationText = "";
    private String radioNotificationStation = "";
    private final RadioStationAssociation radioAssociation = new RadioStationAssociation();

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) { refresh(); }
        @Override public void onPlaybackStateChanged(PlaybackState state) { refresh(); }
        @Override public void onSessionDestroyed() { refresh(); }
        @Override public void onQueueChanged(List<MediaSession.QueueItem> queue) { refresh(); }
    };

    private MediaSessionManager.OnActiveSessionsChangedListener sessionsChangedListener;

    @Override public void onCreate() {
        super.onCreate();
        sessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        listenerComponent = new ComponentName(this, MediaListenerService.class);
        MediaEventTrace.record("listener", "created");
    }

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        connected = true;
        final int generation = ++listenerGeneration;
        sessionsChangedListener = ignored -> main.post(() -> {
            if (connected && instance == this && generation == listenerGeneration) refresh();
        });
        instance = this;
        publishEmpty();
        ProcessMediaSessionMonitor.refresh(this);
        MediaNotificationAccessBootstrapper.connected();
        MediaEventTrace.record("listener", "connected");
        if (sessionManager == null) {
            MediaEventTrace.record("listener", "manager-unavailable");
            publishEmpty();
            return;
        }
        try {
            sessionManager.addOnActiveSessionsChangedListener(
                    sessionsChangedListener, listenerComponent, main);
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
        connected = false;
        listenerGeneration++;
        releaseAll();
        detachSessionListener();
        if (instance == this) { instance = null; publishEmpty(); }
        super.onListenerDisconnected();
        MediaNotificationAccessBootstrapper.ensureConnected(this);
    }

    @Override public void onDestroy() {
        MediaEventTrace.record("listener", "destroyed");
        connected = false;
        listenerGeneration++;
        releaseAll();
        detachSessionListener();
        if (instance == this) { instance = null; publishEmpty(); }
        super.onDestroy();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !RadioProvider.resolvePackage(this).equals(sbn.getPackageName())) return;
        Notification notification = sbn.getNotification();
        if (notification == null || (notification.flags & Notification.FLAG_ONGOING_EVENT) == 0) return;
        Bundle extras = notification.extras;
        if (extras == null) return;
        String title = firstNotificationText(extras, Notification.EXTRA_TITLE,
                Notification.EXTRA_TITLE_BIG);
        String text = firstNotificationText(extras, Notification.EXTRA_TEXT,
                Notification.EXTRA_BIG_TEXT, Notification.EXTRA_SUB_TEXT);
        text = MediaTickerPolicy.join(text,
                firstNotificationText(extras, Notification.EXTRA_SUB_TEXT),
                firstNotificationText(extras, "frequency", "freq"));
        if (title.isEmpty() && text.isEmpty()) return;
        Object notificationToken = extras.getParcelable(Notification.EXTRA_MEDIA_SESSION);
        Snapshot currentRadio = snapshotOf(findRadioController());
        if (notificationToken != null && !notificationToken.equals(currentRadio.sessionIdentity)) return;
        radioNotificationKey = sbn.getKey() == null ? "" : sbn.getKey();
        radioNotificationTitle = title;
        radioNotificationText = text;
        radioAssociation.observe(currentRadio.sessionIdentity, meaningfulRadioTitle(currentRadio));
        radioNotificationStation = notificationStation(title, currentRadio);
        radioAssociation.notification(radioNotificationStation);
        MediaEventTrace.record("metadata", "radio-notification", sbn.getPackageName());
        refresh();
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null || radioNotificationKey.isEmpty() || sbn.getKey() == null
                || !radioNotificationKey.equals(sbn.getKey())) return;
        radioNotificationKey = "";
        radioNotificationTitle = "";
        radioNotificationText = "";
        radioNotificationStation = "";
        MediaEventTrace.record("metadata", "radio-notification-removed", sbn.getPackageName());
        refresh();
    }

    private void detachSessionListener() {
        if (sessionManager == null || sessionsChangedListener == null) return;
        try {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener);
        } catch (RuntimeException ignored) {
            // Listener may already be detached by the framework.
        }
    }

    private void refresh() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(this::refresh); return; }
        if (!connected || instance != this) return;
        if (sessionManager == null) {
            publishEmpty();
            return;
        }
        try {
            reconcile(sessionManager.getActiveSessions(listenerComponent));
        } catch (SecurityException ignored) {
            MediaEventTrace.record("listener", "refresh-access-denied");
            releaseAll(); publishEmpty();
        } catch (RuntimeException unavailable) {
            MediaEventTrace.record("listener", "refresh-unavailable");
            releaseAll(); publishEmpty();
        }
    }

    private void reconcile(List<MediaController> activeControllers) {
        if (!connected || instance != this) return;
        List<MediaController> active = activeControllers == null
                ? new ArrayList<>() : activeControllers;
        active = SessionControllerRegistry.reconcile(watched, active, callback, main);
        List<MediaController> controllers = mergedControllers(active);
        MediaController genericController = selectGenericController(controllers);
        MediaController radioController = pickExactPackage(
                controllers, RadioProvider.resolvePackage(this));

        Snapshot previousGeneric = lastGeneric;
        Snapshot previousRadio = lastRadio;
        Snapshot rawRadio = snapshotOf(radioController);
        boolean navRadio = "com.navimods.radio".equals(rawRadio.packageName);
        radioAssociation.observe(rawRadio.sessionIdentity, meaningfulRadioTitle(rawRadio));
        boolean notificationMatches = (!navRadio || radioAssociation.current())
                && notificationMatchesRadioSnapshot(radioNotificationStation, rawRadio);
        if (!notificationMatches
                && (!radioNotificationTitle.isEmpty() || !radioNotificationText.isEmpty())) {
            MediaEventTrace.record("metadata", "radio-notification-mismatch",
                    "notificationStation=" + radioNotificationStation
                            + " sessionStation=" + meaningfulRadioTitle(rawRadio));
        }
        Snapshot nextGeneric = stabiliseSnapshot(previousGeneric, snapshotOf(genericController));
        Snapshot fallbackRadio = applyRadioFallback(rawRadio,
                notificationMatches ? radioNotificationTitle : "",
                notificationMatches ? radioNotificationText : "");
        // An identity gap is meaningful. Do not resurrect the previous station through stability.
        Snapshot nextRadio = navRadio && meaningfulRadioTitle(rawRadio).isEmpty() && !notificationMatches
                ? fallbackRadio : stabiliseSnapshot(previousRadio, fallbackRadio);
        boolean genericChanged = !sameSnapshot(previousGeneric, nextGeneric);
        boolean radioChanged = !sameSnapshot(previousRadio, nextRadio);
        lastGeneric = nextGeneric;
        lastRadio = nextRadio;
        if (genericChanged) MediaEventTrace.record("session", "music-state", snapshotTrace(nextGeneric));
        if (radioChanged) MediaEventTrace.record("session", "radio-state", snapshotTrace(nextRadio));
        if (genericChanged || radioChanged) notifyObservers();
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
            MediaEventTrace.record("listener", "external-reattach", "count=" + registered.size());
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
            controller.registerCallback(observer, main);
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
            return configured != null && !excludedFromGenericMedia(configured, radioPackage) ? configured : null;
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
                    value == PlaybackState.STATE_PLAYING, value == PlaybackState.STATE_PAUSED || value == PlaybackState.STATE_STOPPED));
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
            if (value == PlaybackState.STATE_PLAYING) return controller;
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
        MediaSession.Token token = tokenOf(controller);
        try {
            metadata = controller.getMetadata();
            playbackState = controller.getPlaybackState();
        } catch (RuntimeException ignored) {
            return new Snapshot(controller.getPackageName(), "", "",
                    PlaybackState.STATE_NONE, 0L, token);
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
        if (RadioFrequency.NAVRADIO.equals(controller.getPackageName())) artist = radioDetails(metadata, artist);
        return new Snapshot(controller.getPackageName(), title, artist, state, actions, token);
    }

    static String radioDetails(MediaMetadata metadata, String artist) {
        if (metadata == null) return artist;
        java.util.List<String> fields = new java.util.ArrayList<>();
        fields.add(artist);
        // Unit-bearing text only. Unqualified vendor integers never become tuner state.
        for (String key : new String[] {MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
                MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION, MediaMetadata.METADATA_KEY_ALBUM,
                MediaMetadata.METADATA_KEY_GENRE, "frequency", "freq", "band", "rds"}) {
            try {
                CharSequence value = metadata.getText(key);
                if (value != null && value.length() <= 256) fields.add(value.toString());
            } catch (RuntimeException wrongType) { /* Numeric/private contracts remain unknown. */ }
        }
        return MediaTickerPolicy.join(fields.toArray(new String[0]));
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

    private static String firstNotificationText(Bundle extras, String... keys) {
        if (extras == null) return "";
        for (String key : keys) {
            CharSequence value = extras.getCharSequence(key);
            String text = value == null ? "" : value.toString().trim();
            if (!text.isEmpty()) return text;
        }
        return "";
    }

    private static String normalise(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isRadioPlaceholder(String value) {
        String clean = normalise(value);
        return clean.equalsIgnoreCase("NavRadio+") || clean.equalsIgnoreCase("Radio");
    }

    static String meaningfulRadioTitle(Snapshot snapshot) {
        if (snapshot == null) return "";
        String title = normalise(snapshot.title);
        return title.isEmpty() || isRadioPlaceholder(title) ? "" : title;
    }

    static String notificationStation(String notificationTitle, Snapshot currentRadio) {
        String title = normalise(notificationTitle);
        // Notifications can arrive before the MediaSession advances to the new station.
        // Associate their details with the notification's station, not the old session.
        return title.isEmpty() || isRadioPlaceholder(title)
                ? meaningfulRadioTitle(currentRadio) : title;
    }

    static boolean notificationMatchesRadioSnapshot(String associatedStation, Snapshot snapshot) {
        String currentStation = meaningfulRadioTitle(snapshot);
        String associated = normalise(associatedStation);
        if (currentStation.isEmpty()) return true;
        return !associated.isEmpty() && associated.equalsIgnoreCase(currentStation);
    }

    static Snapshot applyRadioFallback(Snapshot snapshot, String title, String text) {
        if (snapshot == null || snapshot.packageName.isEmpty()) return snapshot;
        String cleanTitle = normalise(title);
        String cleanText = normalise(text);
        String primary = snapshot.title;
        String secondary = snapshot.artist;
        boolean placeholder = isRadioPlaceholder(primary);
        if (placeholder && RadioFrequency.isFrequencyOnly(snapshot.packageName, secondary)
                && cleanTitle.isEmpty() && cleanText.isEmpty()) return snapshot;
        if (placeholder && cleanTitle.isEmpty() && cleanText.isEmpty()
                && !secondary.isEmpty() && !isRadioPlaceholder(secondary)) {
            primary = secondary;
            secondary = "";
        } else {
            if (placeholder) primary = "";
            if (primary.isEmpty()) primary = cleanTitle;
            if (isRadioPlaceholder(primary)) {
                primary = cleanText;
                cleanText = "";
            }
            if (secondary.isEmpty() && !cleanText.isEmpty()
                    && !cleanText.equalsIgnoreCase(primary)) secondary = cleanText;
        }
        if (primary.equals(snapshot.title) && secondary.equals(snapshot.artist)) return snapshot;
        return snapshot.withMetadata(primary, secondary);
    }

    static Snapshot stabiliseSnapshot(Snapshot previous, Snapshot next) {
        // A token identifies a session, never an individual song. Retention window is zero:
        // absent current metadata means unknown, even if the token has not changed.
        return next == null ? new Snapshot("", "", "", false) : next;
    }

    private static boolean sameSnapshot(Snapshot first, Snapshot second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        boolean sameIdentity = first.sessionIdentity == second.sessionIdentity
                || (first.sessionIdentity != null && first.sessionIdentity.equals(second.sessionIdentity));
        return sameIdentity
                && first.packageName.equals(second.packageName)
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
        radioNotificationKey = radioNotificationTitle = radioNotificationText = radioNotificationStation = "";
        radioAssociation.observe(null, "");
    }

    private static void publishEmpty() {
        boolean changed = !lastGeneric.isEmpty() || !lastRadio.isEmpty();
        lastGeneric = new Snapshot("", "", "", false);
        lastRadio = new Snapshot("", "", "", false);
        if (changed) {
            MediaEventTrace.record("session", "published-empty");
            notifyObservers();
        }
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

    static boolean isConnected() { return instance != null; }

    public static void refreshActiveSessions() {
        MediaListenerService service = instance;
        if (service == null) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastDefensiveRefreshUptimeMs < DEFENSIVE_REFRESH_INTERVAL_MS) return;
        lastDefensiveRefreshUptimeMs = now;
        service.refresh();
    }

    static boolean hasObservableSession(String packageName) {
        MediaListenerService service = instance;
        if (service == null || packageName == null || packageName.isEmpty()) return false;
        for (MediaController controller : service.activeAndExternalControllers()) {
            if (controller != null && packageName.equals(controller.getPackageName())) return true;
        }
        return false;
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
        MediaController selected = ProcessMediaSessionMonitor.selectedController(lastGeneric.packageName);
        if (selected != null) LauncherPrefs.rememberMusic(service, selected.getPackageName());
        LauncherPrefs.selectSource(service, MediaSelection.MUSIC);
        return service.sendToController(selected, command);
    }

    public static boolean sendRadio(Command command) {
        MediaListenerService service = instance;
        if (service == null) return false;
        LauncherPrefs.selectSource(service, MediaSelection.RADIO);
        return service.sendToController(ProcessMediaSessionMonitor.selectedController(
                RadioProvider.resolvePackage(service)), command);
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
                    if ((actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) == 0L
                            && !MediaSourceAdapter.allowsUnadvertisedSkip(controller.getPackageName())) return false;
                    controller.getTransportControls().skipToPrevious();
                    break;
                case NEXT:
                    if ((actions & PlaybackState.ACTION_SKIP_TO_NEXT) == 0L
                            && !MediaSourceAdapter.allowsUnadvertisedSkip(controller.getPackageName())) return false;
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
            if ((command == Command.PREVIOUS || command == Command.NEXT)
                    && MediaSourceAdapter.allowsUnadvertisedSkip(controller.getPackageName())) {
                MediaEventTrace.record("listener-command", "exact-skip-compat",
                        controller.getPackageName() + " command=" + command);
            }
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
