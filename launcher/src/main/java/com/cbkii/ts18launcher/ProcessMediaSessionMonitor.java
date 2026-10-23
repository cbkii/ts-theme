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
import android.os.SystemClock;
import com.cbkii.ts18launcher.platform.TopwayAdapter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Process-owned selected authority. Main owns registrations/publication; one worker reads Binder.
 * Listener connection is not a prerequisite for Android-authorised active-session access.
 */
final class ProcessMediaSessionMonitor {
    interface Observer {
        void onProcessMediaStateChanged(MediaListenerService.Snapshot music,
                MediaListenerService.Snapshot radio, boolean authoritative);
    }
    private static final CopyOnWriteArrayList<WeakReference<Observer>> OBSERVERS = new CopyOnWriteArrayList<>();
    private static ProcessMediaSessionMonitor instance;
    private static final long READ_TIMEOUT_MS = 2000L;
    private final Context context;
    private final MediaSessionManager manager;
    private final ComponentName listenerComponent;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService sampler;
    private final Map<MediaSession.Token, Owner> watched = new LinkedHashMap<>();
    private final Map<MediaSession.Token, MediaController> external = new LinkedHashMap<>();
    private final Map<String, Owner> selected = new LinkedHashMap<>();
    private boolean registered, destroyed, visible, busy, dirty, refreshPosted, authoritative;
    private boolean discoveryNeeded = true;
    private final java.util.Set<MediaSession.Token> destroyedTokens = new java.util.HashSet<>();
    private long revision, ownerGeneration;
    private MediaListenerService.Snapshot lastGeneric = empty(), lastRadio = empty();
    private MediaSession.Token musicToken, radioToken;

    private final class Owner {
        final MediaController controller;
        final MediaSession.Token token;
        final long generation = ++ownerGeneration;
        MediaListenerService.Snapshot snapshot = empty();
        final MediaController.Callback callback = new MediaController.Callback() {
            @Override public void onMetadataChanged(MediaMetadata ignored) { changed(); }
            @Override public void onPlaybackStateChanged(PlaybackState ignored) { changed(); }
            @Override public void onQueueChanged(List<MediaSession.QueueItem> ignored) { changed(); }
            @Override public void onSessionDestroyed() {
                if (!current()) return;
                destroyedTokens.add(token);
                external.remove(token); watched.remove(token); detach();
                invalidate(); requestRefresh();
            }
        };
        Owner(MediaController controller) { this.controller = controller; token = controller.getSessionToken(); }
        boolean current() { return !destroyed && watched.get(token) == this; }
        void changed() {
            if (!current()) return;
            // Old callback payloads, including late artwork, never become state.
            invalidate(); requestRefresh();
        }
        void detach() { try { controller.unregisterCallback(callback); } catch (RuntimeException gone) { } }
    }

    private final MediaSessionManager.OnActiveSessionsChangedListener membership = ignored -> main.post(() -> {
        if (!destroyed && registered) { invalidate(); requestRefresh(); }
    });
    private final Runnable refreshRunnable = () -> { refreshPosted = false; refreshInternal(); };
    private final Runnable visibleTick = new Runnable() {
        @Override public void run() {
            if (!visible || destroyed) return;
            requestVerification();
            main.postDelayed(this, 1000L);
        }
    };

    private ProcessMediaSessionMonitor(Context context) {
        this(context, Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ts18-visible-media"); t.setDaemon(true); return t;
        }));
    }
    ProcessMediaSessionMonitor(Context context, ExecutorService worker) {
        sampler = worker;
        this.context = context.getApplicationContext();
        manager = (MediaSessionManager) this.context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        listenerComponent = new ComponentName(this.context, MediaListenerService.class);
    }
    private static ProcessMediaSessionMonitor monitor(Context context) {
        if (instance == null) instance = new ProcessMediaSessionMonitor(context);
        return instance;
    }
    static void start(Context context) { refresh(context); }
    static void refresh(Context context) {
        ProcessMediaSessionMonitor owner = monitor(context);
        owner.main.post(owner::requestRefresh);
    }
    static void setVisible(Context context, boolean visible) {
        ProcessMediaSessionMonitor owner = monitor(context);
        owner.main.post(() -> {
            owner.visible = visible;
            owner.main.removeCallbacks(owner.visibleTick);
            // Fresh verification on return; a hidden Activity's cached observation is not replayed.
            if (visible && !owner.destroyed) {
                owner.invalidate(); owner.requestRefresh(); owner.main.postDelayed(owner.visibleTick, 1000L);
            }
        });
    }
    static void observeExternal(Context context, MediaController controller) {
        if (controller == null) return;
        ProcessMediaSessionMonitor owner = monitor(context);
        owner.main.post(() -> {
            if (owner.destroyed) return;
            owner.external.put(controller.getSessionToken(), controller);
            owner.requestRefresh();
        });
    }
    static void forgetExternal(MediaController controller) {
        ProcessMediaSessionMonitor owner = instance;
        if (owner == null || controller == null) return;
        owner.main.post(() -> {
            if (owner.external.get(controller.getSessionToken()) == controller) {
                owner.external.remove(controller.getSessionToken()); owner.invalidate(); owner.requestRefresh();
            }
        });
    }
    /** Main-looper callers only. No readiness/browser cache can override this callback owner. */
    static MediaController selectedController(String pkg) {
        ProcessMediaSessionMonitor owner = instance;
        Owner entry = owner == null ? null : owner.selected.get(pkg);
        return entry != null && entry.current() ? entry.controller : null;
    }
    static String selectedMusicPackage() {
        ProcessMediaSessionMonitor owner = instance;
        Owner entry = owner == null ? null : owner.watched.get(owner.musicToken);
        return entry != null && entry.current() && owner.selected.get(entry.controller.getPackageName()) == entry
                ? entry.controller.getPackageName() : "";
    }
    static void addObserver(Context context, Observer observer) {
        if (observer == null) return;
        for (WeakReference<Observer> r : OBSERVERS) if (r.get() == observer) return;
        OBSERVERS.add(new WeakReference<>(observer));
        ProcessMediaSessionMonitor owner = monitor(context);
        owner.main.post(() -> {
            if (!contains(observer)) return;
            long latest = Math.max(owner.lastGeneric.observedAtMs, owner.lastRadio.observedAtMs);
            boolean fresh = owner.authoritative && SystemClock.uptimeMillis() - latest <= READ_TIMEOUT_MS;
            observer.onProcessMediaStateChanged(fresh ? owner.lastGeneric : empty(),
                    fresh ? owner.lastRadio : empty(), fresh);
            owner.requestRefresh();
        });
    }
    private static boolean contains(Observer observer) {
        for (WeakReference<Observer> r : OBSERVERS) if (r.get() == observer) return true;
        return false;
    }
    static void removeObserver(Observer observer) {
        for (WeakReference<Observer> r : OBSERVERS) if (r.get() == null || r.get() == observer) OBSERVERS.remove(r);
    }
    static void stop() {
        ProcessMediaSessionMonitor owner = instance;
        instance = null; OBSERVERS.clear();
        if (owner != null) owner.main.post(owner::destroy);
    }
    private void requestRefresh() {
        discoveryNeeded = true;
        requestVerification();
    }
    private void requestVerification() {
        if (destroyed) return;
        if (busy) { dirty = true; return; }
        if (!refreshPosted) { refreshPosted = true; main.post(refreshRunnable); }
    }
    private void refreshInternal() {
        if (destroyed || busy) return;
        boolean access = MediaListenerService.hasNotificationAccess(context);
        boolean discover = discoveryNeeded || (!access && registered);
        discoveryNeeded = false;
        if (access && manager != null && !registered) {
            try { manager.addOnActiveSessionsChangedListener(membership, listenerComponent, main); registered = true; }
            catch (RuntimeException denied) { invalidate(); }
        } else if (!access && registered) detachMembership();
        busy = true; dirty = false;
        long ticket = revision;
        List<MediaController> bound = new ArrayList<>(external.values());
        String radioPackage = RadioProvider.resolvePackage(context);
        Runnable timeout = () -> {
            if (busy && ticket == revision) { selected.clear(); invalidate(); }
        };
        main.postDelayed(timeout, READ_TIMEOUT_MS);
        sampler.execute(() -> {
            List<MediaController> active = new ArrayList<>();
            boolean complete = !discover || !access;
            try { if (discover && access && manager != null) { active.addAll(manager.getActiveSessions(listenerComponent)); complete = true; } }
            catch (RuntimeException denied) { /* Unknown active membership: do not reuse old active data. */ }
            final boolean known = complete;
            main.post(() -> {
                if (destroyed) return;
                if (ticket != revision) { finishRead(timeout); return; }
                if (discover) reconcile(active, bound);
                List<Owner> owners = new ArrayList<>();
                if (discover) owners.addAll(watched.values());
                else {
                    Owner music = watched.get(musicToken), radio = watched.get(radioToken);
                    if (music != null) owners.add(music);
                    if (radio != null && radio != music) owners.add(radio);
                }
                sampler.execute(() -> {
                    Map<Owner, MediaListenerService.Snapshot> observations = new LinkedHashMap<>();
                    for (Owner owner : owners) observations.put(owner, MediaItemObservation.read(
                            owner.controller, owner.generation, SystemClock.uptimeMillis(),
                            radioPackage.equals(owner.controller.getPackageName())));
                    main.post(() -> {
                        if (destroyed) return;
                        if (ticket == revision) {
                            for (Map.Entry<Owner, MediaListenerService.Snapshot> entry : observations.entrySet())
                                if (entry.getKey().current()) entry.getKey().snapshot = entry.getValue();
                            if (access && !MediaListenerService.hasNotificationAccess(context)) {
                                selected.clear(); invalidate();
                            } else publish(known || !bound.isEmpty());
                        }
                        finishRead(timeout);
                    });
                });
            });
        });
    }
    private void finishRead(Runnable timeout) {
        main.removeCallbacks(timeout); busy = false;
        if (dirty) requestRefresh();
    }
    private void reconcile(List<MediaController> active, List<MediaController> bound) {
        Map<MediaSession.Token, MediaController> candidates = new LinkedHashMap<>();
        for (MediaController c : active) if (c != null && !destroyedTokens.contains(c.getSessionToken())) candidates.put(c.getSessionToken(), c);
        // Browser acquisition supplements absent packages, never overrides a live token from that package.
        for (MediaController c : bound) {
            boolean livePackage = false;
            for (MediaController a : active) if (a != null && a.getPackageName().equals(c.getPackageName())) livePackage = true;
            if (!livePackage && !destroyedTokens.contains(c.getSessionToken())) candidates.putIfAbsent(c.getSessionToken(), c);
        }
        for (Owner owner : new ArrayList<>(watched.values())) if (!candidates.containsKey(owner.token)) {
            watched.remove(owner.token); owner.detach();
        }
        for (Map.Entry<MediaSession.Token, MediaController> e : candidates.entrySet()) if (!watched.containsKey(e.getKey())) {
            Owner owner = new Owner(e.getValue());
            try { owner.controller.registerCallback(owner.callback, main); watched.put(owner.token, owner); }
            catch (RuntimeException gone) { /* Retry only at the next bounded discovery. */ }
        }
    }
    private void publish(boolean available) {
        selected.clear();
        for (Owner owner : watched.values()) {
            String pkg = owner.controller.getPackageName();
            Owner old = selected.get(pkg);
            if (old == null || rank(owner) > rank(old)
                    || (rank(owner) == rank(old) && (Objects.equals(owner.token, musicToken)
                            || Objects.equals(owner.token, radioToken)))) selected.put(pkg, owner);
        }
        String radioPkg = RadioProvider.resolvePackage(context);
        Owner radio = selected.get(radioPkg);
        String preferred = LauncherPrefs.packageFor(context, LauncherPrefs.KEY_MUSIC);
        if (preferred.isEmpty()) preferred = TopwayAdapter.defaultMusicPackage(context);
        boolean dedicated = LauncherPrefs.MEDIA_MODE_PREFER_MUSIC.equals(LauncherPrefs.mediaMode(context));
        List<Owner> candidates = new ArrayList<>();
        List<MediaSelection.Candidate> values = new ArrayList<>();
        for (Owner owner : selected.values()) {
            String pkg = owner.snapshot.packageName;
            int state = owner.snapshot.state;
            candidates.add(owner);
            values.add(new MediaSelection.Candidate(pkg, eligible(pkg, radioPkg),
                    state != PlaybackState.STATE_NONE && state != PlaybackState.STATE_ERROR,
                    state == PlaybackState.STATE_PLAYING,
                    state == PlaybackState.STATE_PAUSED || state == PlaybackState.STATE_STOPPED));
        }
        // In the absence of a playing competitor, keep the current connecting/buffering source.
        Owner previous = watched.get(musicToken);
        String remembered = previous != null && MediaCommandPolicy.usesPauseAction(previous.snapshot.state)
                ? previous.snapshot.packageName : LauncherPrefs.lastMusicPackage(context);
        int index = MediaSelection.pick(values, preferred, dedicated, remembered);
        Owner music = index < 0 ? null : candidates.get(index);
        lastGeneric = music == null ? empty() : music.snapshot;
        lastRadio = radio == null ? empty() : radio.snapshot;
        musicToken = music == null ? null : music.token; radioToken = radio == null ? null : radio.token;
        authoritative = available;
        if (!available) { selected.clear(); lastGeneric = empty(); lastRadio = empty(); }
        notifyObservers();
    }
    private static int rank(Owner owner) {
        int state = owner.snapshot.state;
        if (state == PlaybackState.STATE_PLAYING) return 3;
        if (MediaCommandPolicy.usesPauseAction(state)) return 2;
        return state == PlaybackState.STATE_PAUSED || state == PlaybackState.STATE_STOPPED ? 1 : 0;
    }
    private static boolean eligible(String pkg, String radio) {
        return !pkg.isEmpty() && !pkg.equals(radio) && !"com.android.server.telecom".equals(pkg)
                && !"com.android.dialer".equals(pkg) && !"com.google.android.dialer".equals(pkg)
                && !"com.android.phone".equals(pkg);
    }
    private void invalidate() {
        revision++; dirty = true; authoritative = false;
        lastGeneric = empty(); lastRadio = empty(); notifyObservers();
    }
    private static MediaListenerService.Snapshot empty() { return new MediaListenerService.Snapshot("", "", "", false); }
    private void notifyObservers() {
        for (WeakReference<Observer> r : OBSERVERS) {
            Observer observer = r.get();
            if (observer == null) OBSERVERS.remove(r);
            else observer.onProcessMediaStateChanged(lastGeneric, lastRadio, authoritative);
        }
    }
    private void detachMembership() {
        if (manager != null && registered) try { manager.removeOnActiveSessionsChangedListener(membership); }
        catch (RuntimeException gone) { }
        registered = false;
    }
    private void destroy() {
        destroyed = true; revision++;
        main.removeCallbacks(refreshRunnable); main.removeCallbacks(visibleTick);
        detachMembership(); for (Owner owner : watched.values()) owner.detach();
        watched.clear(); external.clear(); selected.clear(); sampler.shutdownNow();
        authoritative = false; lastGeneric = empty(); lastRadio = empty();
    }
}
