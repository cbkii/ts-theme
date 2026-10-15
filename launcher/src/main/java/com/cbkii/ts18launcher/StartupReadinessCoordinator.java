package com.cbkii.ts18launcher;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;

import com.cbkii.ts18launcher.platform.TopwayAdapter;

/**
 * Application-scoped, main-looper-only startup readiness. Per source: one in-flight admission, a
 * bounded background warm, then (mode permitting) one sequential foreground Activity stage while
 * HOME owns the transaction, followed by HOME restoration. Never plays, never switches sources.
 * Maps compact presentation stays owned by NavigationWindowController on HOME visibility.
 */
final class StartupReadinessCoordinator implements ProcessMediaSessionMonitor.Observer {
    private static final String PREFS = "startup_readiness";
    private static final long DEBOUNCE_MS = 1200L;

    private static final class Source {
        final String name;
        StartupReadinessPolicy.State state = StartupReadinessPolicy.State.UNAVAILABLE;
        boolean inFlight;
        int generation;
        String method = "none";
        String firstFailure = "";
        long startedMs;
        Source(String name) { this.name = name; }
    }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Source music = new Source("music");
    private final Source radio = new Source("radio");
    private MediaSourceBootstrapper bootstrapper;
    private boolean launcherResumed;
    private boolean wakeSinceLastHome;
    private boolean userInteracting;
    private boolean reconcilePosted;
    private boolean foregroundActive;
    private int transaction;
    private String trigger = "";

    StartupReadinessCoordinator(Context context) { this.context = context.getApplicationContext(); }

    void onHomeResumed() {
        launcherResumed = true;
        userInteracting = false;
        if (foregroundActive) return;
        schedule("home-resume");
    }

    void onHomePaused() { launcherResumed = false; }

    void onScreenOff() { wakeSinceLastHome = true; MediaEventTrace.record("startup", "screen-off"); }

    void onScreenOn() {
        MediaEventTrace.record("startup", "screen-on", "acc=UNVERIFIED");
        if (launcherResumed) schedule("wake");
    }

    void onStorage() { if (!StartupReadinessPolicy.healthy(music.state)) schedule("storage"); }

    /** External or HOME touch during preparation always wins; nothing further is launched. */
    void noteUserInteraction() {
        userInteracting = true;
        if (foregroundActive) cancelForeground("user-interaction");
    }

    private void schedule(String why) {
        trigger = why;
        if (reconcilePosted) { MediaEventTrace.record("startup", "coalesced", why); return; }
        reconcilePosted = true;
        handler.postDelayed(this::reconcile, DEBOUNCE_MS);
    }

    private void reconcile() {
        reconcilePosted = false;
        if (!UiPersonalizationPrefs.mediaStartupWarmup(context)) return;
        StartupReadinessPolicy.Kind kind = kind();
        MediaEventTrace.record("startup", "reconcile", "kind=" + kind + " trigger=" + trigger
                + " music=" + music.state + " radio=" + radio.state);
        wakeSinceLastHome = false;
        ProcessMediaSessionMonitor.addObserver(context, this);
        ProcessMediaSessionMonitor.refresh(context);
        if (!StartupReadinessPolicy.shouldReconcile(kind, music.state, radio.state)) return;
        StartupReadinessPolicy.Mode mode = StartupReadinessPolicy.mode(TestingProfiles.startup(context));
        final int tx = ++transaction;
        String musicPkg = musicPackage(), radioPkg = RadioProvider.resolvePackage(context);
        boolean any = admit(music, musicPkg, "background-warm") | admit(radio, radioPkg, "background-warm");
        if (!any) return;
        if (bootstrapper == null) bootstrapper = new MediaSourceBootstrapper(context);
        if (music.inFlight) bootstrapper.warm(musicPkg);
        if (radio.inFlight) bootstrapper.warm(radioPkg);
        handler.postDelayed(() -> afterBackground(tx, mode, musicPkg, radioPkg),
                StartupReadinessPolicy.backgroundWindowMs(mode) + 250L);
    }

    private boolean admit(Source source, String pkg, String method) {
        if (pkg.isEmpty()) { source.state = StartupReadinessPolicy.State.BLOCKED; return false; }
        if (!StartupReadinessPolicy.admit(source.state, source.inFlight)) return false;
        source.inFlight = true; source.generation++; source.method = method;
        source.startedMs = SystemClock.uptimeMillis(); source.firstFailure = "";
        source.state = StartupReadinessPolicy.State.STARTING;
        MediaEventTrace.record("startup", "admit", source.name + " gen=" + source.generation + " method=" + method + " pkg=" + pkg);
        return true;
    }

    private void afterBackground(int tx, StartupReadinessPolicy.Mode mode, String musicPkg, String radioPkg) {
        if (tx != transaction) return;
        boolean musicNeeds = music.inFlight && !StartupReadinessPolicy.healthy(music.state);
        boolean radioNeeds = radio.inFlight && !StartupReadinessPolicy.healthy(radio.state);
        if (musicNeeds) music.firstFailure = "background-no-session";
        if (radioNeeds) radio.firstFailure = "background-no-session";
        if (!musicNeeds && !radioNeeds) { finish(); return; }
        if (!StartupReadinessPolicy.foregroundAllowed(mode, launcherResumed, interactive(),
                userInteracting, inCall(), HomeMode.isDefaultHome(context))) {
            MediaEventTrace.record("startup", "foreground-skipped", "mode=" + mode);
            if (musicNeeds) fail(music); if (radioNeeds) fail(radio);
            finish();
            return;
        }
        foregroundActive = true;
        java.util.ArrayDeque<Object[]> queue = new java.util.ArrayDeque<>();
        if (musicNeeds) queue.add(new Object[] {music, musicPkg});
        if (radioNeeds) queue.add(new Object[] {radio, radioPkg});
        stageNext(tx, queue);
    }

    private void stageNext(int tx, java.util.ArrayDeque<Object[]> queue) {
        if (tx != transaction || !foregroundActive) return;
        if (queue.isEmpty()) { restoreHome("done"); return; }
        Object[] item = queue.removeFirst();
        final Source source = (Source) item[0];
        final String pkg = (String) item[1];
        source.method = "foreground-activity";
        MediaEventTrace.record("startup", "foreground-stage", source.name + " pkg=" + pkg);
        if (!AppResolver.launchPackageQuietly(context, pkg)) {
            source.firstFailure = "no-launch-activity"; fail(source);
            stageNext(tx, queue); return;
        }
        final long deadline = SystemClock.uptimeMillis() + StartupReadinessPolicy.FOREGROUND_WINDOW_MS;
        poll(tx, source, deadline, queue);
    }

    private void poll(int tx, Source source, long deadline, java.util.ArrayDeque<Object[]> queue) {
        if (tx != transaction || !foregroundActive) return;
        ProcessMediaSessionMonitor.refresh(context);
        if (StartupReadinessPolicy.healthy(source.state)) {
            handler.postDelayed(() -> stageNext(tx, queue), StartupReadinessPolicy.ACTIVITY_SETTLE_MS);
            return;
        }
        if (SystemClock.uptimeMillis() >= deadline) {
            source.firstFailure = "foreground-no-session"; fail(source);
            stageNext(tx, queue); return;
        }
        handler.postDelayed(() -> poll(tx, source, deadline, queue), 500L);
    }

    private void cancelForeground(String why) {
        MediaEventTrace.record("startup", "foreground-cancelled", why);
        foregroundActive = false; transaction++;
        for (Source s : new Source[] {music, radio}) if (s.inFlight) { s.firstFailure = why; fail(s); }
        finish();
    }

    private void restoreHome(String why) {
        foregroundActive = false;
        MediaEventTrace.record("startup", "restore-home", why);
        HomeMode.bringLauncherToFront(context);
        finish();
    }

    private void fail(Source source) {
        source.state = StartupReadinessPolicy.State.FAILED; source.inFlight = false;
        MediaEventTrace.record("startup", "failed", source.name + " gen=" + source.generation
                + " method=" + source.method + " boundary=" + source.firstFailure
                + " elapsedMs=" + (SystemClock.uptimeMillis() - source.startedMs));
    }

    private void finish() {
        for (Source s : new Source[] {music, radio}) {
            if (!s.inFlight) continue;
            s.inFlight = false;
            if (!StartupReadinessPolicy.healthy(s.state)) s.state = StartupReadinessPolicy.State.FAILED;
        }
        if (bootstrapper != null) { bootstrapper.destroy(); bootstrapper = null; }
        MediaEventTrace.record("startup", "finished", "music=" + music.state + "/" + music.method
                + " radio=" + radio.state + "/" + radio.method);
    }

    @Override public void onProcessMediaStateChanged(MediaListenerService.Snapshot generic,
            MediaListenerService.Snapshot radioSnapshot, boolean authoritative) {
        observe(music, generic, musicPackage());
        observe(radio, radioSnapshot, RadioProvider.resolvePackage(context));
    }

    private void observe(Source source, MediaListenerService.Snapshot snapshot, String pkg) {
        boolean present = snapshot != null && !pkg.isEmpty() && pkg.equals(snapshot.packageName);
        StartupReadinessPolicy.State next = StartupReadinessPolicy.stateOf(
                present, present && snapshot.playing, present ? snapshot.actions : 0L);
        if (present || source.state == StartupReadinessPolicy.State.READY
                || source.state == StartupReadinessPolicy.State.ACTIVE) {
            if (next != source.state) {
                MediaEventTrace.record("startup", "state", source.name + " " + source.state + "->" + next);
            }
            if (StartupReadinessPolicy.healthy(next)) source.inFlight = false;
            if (!present && StartupReadinessPolicy.healthy(source.state)) {
                // Session loss: allow a later reconcile to admit this source again.
                source.state = StartupReadinessPolicy.State.UNAVAILABLE;
                if (launcherResumed) schedule("session-loss");
            } else if (present) source.state = next;
        }
    }

    private String musicPackage() {
        String pkg = LauncherPrefs.packageFor(context, LauncherPrefs.KEY_MUSIC);
        return pkg.isEmpty() ? TopwayAdapter.defaultMusicPackage(context) : pkg;
    }

    private StartupReadinessPolicy.Kind kind() {
        long boot = Settings.Global.getLong(context.getContentResolver(), "boot_count", -1L);
        android.content.SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long last = p.getLong("boot", -2L);
        p.edit().putLong("boot", boot).apply();
        return StartupReadinessPolicy.classify(boot, last, wakeSinceLastHome);
    }

    private boolean interactive() {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isInteractive();
    }

    private boolean inCall() {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        return am != null && (am.getMode() == AudioManager.MODE_IN_CALL
                || am.getMode() == AudioManager.MODE_IN_COMMUNICATION
                || am.getMode() == AudioManager.MODE_RINGTONE);
    }
}
