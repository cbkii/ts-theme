package com.cbkii.ts18launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;

import com.cbkii.ts18launcher.StartupReadinessPolicy.Method;
import com.cbkii.ts18launcher.StartupReadinessPolicy.Mode;
import com.cbkii.ts18launcher.StartupReadinessPolicy.Source;
import com.cbkii.ts18launcher.StartupReadinessPolicy.Trigger;
import com.cbkii.ts18launcher.platform.TopwayAdapter;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.regex.Pattern;

/**
 * Application-scoped startup readiness for Music, Radio and Navigation.
 *
 * Phase 1 (background): observe/prepare the source through the existing exported service/session
 * contracts, bounded and event-driven. Phase 2 (foreground, only when HOME is resumed and the
 * selected testing mode allows it): launch the source's normal Activity exactly once, wait for
 * readiness within a deadline, then restore the existing HOME task. Sources are sequential,
 * never autoplay, never touch audio focus, and a failure in one never blocks the others.
 * Readiness here means service/session or task observation only; audibility is a physical check.
 */
final class StartupReadinessCoordinator {
    private static final String PREFS = "startup_readiness";
    private static final String KEY_BOOT_COUNT = "boot_count";
    private static final long POLL_MS = 500L;
    private static final long MUSIC_BACKGROUND_MS = 9000L;
    private static final long RADIO_BACKGROUND_MS = 2500L;
    private static final long MUSIC_FOREGROUND_MS = 9000L;
    private static final long RADIO_FOREGROUND_MS = 9000L;
    // Organic Maps has no session to observe; allow legitimate Splash/permission/map initialisation.
    private static final long NAVIGATION_DWELL_MS = 9000L;
    private static final long HOME_RESTORE_MS = 3000L;
    private static final long INTER_STEP_SETTLE_MS = 1200L;
    private static final Pattern COMPONENT = Pattern.compile("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+");

    interface NavigationHook {
        /** While held the HOME navigation controller must not cold-launch or repair the task. */
        void holdNavigation(boolean hold);
        boolean hasNavigationTask();
    }

    private static StartupReadinessCoordinator instance;

    static synchronized StartupReadinessCoordinator get(Context context) {
        if (instance == null) instance = new StartupReadinessCoordinator(context.getApplicationContext());
        return instance;
    }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StartupReadinessPolicy policy = new StartupReadinessPolicy();
    private final ArrayDeque<Source> foregroundQueue = new ArrayDeque<>();
    private final MediaSourceBootstrapper bootstrapper;
    private final java.util.concurrent.ExecutorService rootExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "ts18-startup-root");
                t.setDaemon(true);
                return t;
            });
    private WeakReference<Activity> homeActivity = new WeakReference<>(null);
    private NavigationHook navigationHook;
    private boolean homeResumed;
    private boolean foregroundBusy;
    private boolean navigationHeld;
    private int foregroundToken;
    private int userInteractionGeneration;
    private Trigger lastTrigger = Trigger.RESTART;
    private final int[] generations = new int[Source.values().length];

    private StartupReadinessCoordinator(Context context) {
        this.context = context;
        this.bootstrapper = new MediaSourceBootstrapper(context);
    }

    // ---- signals -------------------------------------------------------------------------

    void onTrigger(Trigger trigger) {
        if (Looper.myLooper() != Looper.getMainLooper()) { handler.post(() -> onTrigger(trigger)); return; }
        long now = SystemClock.uptimeMillis();
        if (!policy.acceptTrigger(trigger, now)) {
            MediaEventTrace.record("startup", "trigger-deduplicated", trigger.name());
            return;
        }
        long boot = bootCount();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long seen = prefs.getLong(KEY_BOOT_COUNT, -2L);
        StartupReadinessPolicy.Kind kind = StartupReadinessPolicy.classify(trigger, boot, seen);
        if (kind == StartupReadinessPolicy.Kind.FULL_BOOT && boot != seen) {
            policy.resetForBoot();
            cancelForeground("new-boot");
            prefs.edit().putLong(KEY_BOOT_COUNT, boot).apply();
        }
        lastTrigger = trigger;
        MediaEventTrace.record("startup", "trigger",
                trigger.name() + " kind=" + kind + " mode=" + mode() + " home=" + homeResumed
                        + (trigger == Trigger.SCREEN_ON ? " acc=unverified" : ""));
        reconcile(trigger);
    }

    void attachNavigationHook(NavigationHook hook) { navigationHook = hook; }

    void detachNavigationHook(NavigationHook hook) {
        if (navigationHook == hook) { navigationHook = null; navigationHeld = false; }
    }

    void onHomeResumed(Activity activity) {
        homeActivity = new WeakReference<>(activity);
        homeResumed = true;
        onTrigger(Trigger.HOME_RESUME);
        runForegroundSoon(INTER_STEP_SETTLE_MS);
    }

    void onHomePaused() { homeResumed = false; }

    /** Any genuine user touch/key on HOME yields pending (not yet started) foreground steps. */
    void noteUserInteraction() {
        userInteractionGeneration++;
        if (!foregroundBusy && !foregroundQueue.isEmpty()) cancelForeground("user-interaction");
    }

    /** The HOME controller reports observed task adoption (or its first failing boundary). */
    void reportNavigation(boolean ready, String boundary) {
        long now = SystemClock.uptimeMillis();
        StartupReadinessPolicy.Record r = policy.record(Source.NAVIGATION);
        if (ready) {
            if (policy.observedReady(Source.NAVIGATION, now))
                MediaEventTrace.record("startup", "ready", "NAVIGATION method=TASK_ADOPTION elapsed="
                        + r.elapsedMs(now) + "ms");
        } else if (r.state == StartupReadinessPolicy.State.UNCERTAIN && r.firstFailingBoundary.isEmpty()) {
            r.firstFailingBoundary = boundary == null ? "" : boundary;
        }
    }

    String summary() { return policy.summary(SystemClock.uptimeMillis()); }

    // ---- reconcile -----------------------------------------------------------------------

    private void reconcile(Trigger trigger) {
        if (!UiPersonalizationPrefs.mediaStartupWarmup(context)) {
            MediaEventTrace.record("startup", "disabled-by-preference");
            return;
        }
        verifyHealth();
        long now = SystemClock.uptimeMillis();
        for (Source source : new Source[] {Source.MUSIC, Source.RADIO, Source.NAVIGATION}) {
            String pkg = packageFor(source);
            if (pkg.isEmpty()) continue;
            if (!policy.admit(source, trigger, now)) continue;
            switch (source) {
                case MUSIC: startMusic(pkg, trigger); break;
                case RADIO: startRadio(pkg, trigger); break;
                default: startNavigation(pkg, trigger); break;
            }
        }
    }

    /** A healthy source is left alone; a lost session/process re-opens admission. */
    private void verifyHealth() {
        for (Source source : new Source[] {Source.MUSIC, Source.RADIO}) {
            StartupReadinessPolicy.State state = policy.record(source).state;
            if (state != StartupReadinessPolicy.State.READY && state != StartupReadinessPolicy.State.ACTIVE) continue;
            String pkg = packageFor(source);
            if (!pkg.isEmpty() && !bootstrapper.hasUsableController(pkg)) {
                MediaEventTrace.record("startup", "session-lost", source.name());
                policy.sessionLost(source);
            }
        }
    }

    private void startMusic(String pkg, Trigger trigger) {
        if (!MediaListenerService.hasNotificationAccess(context)) {
            int g = policy.begin(Source.MUSIC, trigger, Method.SESSION, SystemClock.uptimeMillis());
            policy.block(Source.MUSIC, g, "notification-access", SystemClock.uptimeMillis());
            return;
        }
        if (bootstrapper.hasUsableQueue(pkg)) {
            int g = policy.begin(Source.MUSIC, trigger, Method.SESSION, SystemClock.uptimeMillis());
            policy.ready(Source.MUSIC, g, SystemClock.uptimeMillis());
            MediaEventTrace.record("startup", "ready", "MUSIC method=SESSION");
            return;
        }
        final int g = policy.begin(Source.MUSIC, trigger, Method.BROWSER, SystemClock.uptimeMillis());
        bootstrapper.warm(pkg);
        awaitReady(Source.MUSIC, g, pkg, SystemClock.uptimeMillis() + MUSIC_BACKGROUND_MS,
                () -> bootstrapper.hasUsableQueue(pkg), "service-session-not-usable");
    }

    private void startRadio(String pkg, Trigger trigger) {
        if (!MediaListenerService.hasNotificationAccess(context)) {
            int g = policy.begin(Source.RADIO, trigger, Method.SESSION, SystemClock.uptimeMillis());
            policy.block(Source.RADIO, g, "notification-access", SystemClock.uptimeMillis());
            return;
        }
        // NavRadio passive service start is not proven non-disruptive: background phase only
        // observes an existing exact session. Stock TW Radio exposes no service: session only.
        final int g = policy.begin(Source.RADIO, trigger, Method.SESSION, SystemClock.uptimeMillis());
        awaitReady(Source.RADIO, g, pkg, SystemClock.uptimeMillis() + RADIO_BACKGROUND_MS,
                () -> bootstrapper.hasUsableController(pkg), "session-absent");
    }

    private void startNavigation(String pkg, Trigger trigger) {
        long now = SystemClock.uptimeMillis();
        int g = policy.begin(Source.NAVIGATION, trigger, Method.TASK_ADOPTION, now);
        generations[Source.NAVIGATION.ordinal()] = g;
        if (navigationHook != null && navigationHook.hasNavigationTask()) {
            policy.ready(Source.NAVIGATION, g, now);
            MediaEventTrace.record("startup", "ready", "NAVIGATION method=TASK_ADOPTION");
            return;
        }
        if (!StartupReadinessPolicy.foregroundAllowed(mode())) {
            policy.block(Source.NAVIGATION, g, "foreground-bootstrap-disabled", now);
            return;
        }
        enqueueForeground(Source.NAVIGATION, g);
    }

    private interface Check { boolean ok(); }

    private void awaitReady(Source source, int generation, String pkg, long deadline, Check check,
                            String boundary) {
        handler.post(new Runnable() {
            @Override public void run() {
                StartupReadinessPolicy.Record r = policy.record(source);
                if (r.generation != generation || r.state != StartupReadinessPolicy.State.STARTING) return;
                long now = SystemClock.uptimeMillis();
                if (check.ok()) {
                    policy.ready(source, generation, now);
                    MediaEventTrace.record("startup", "ready",
                            source + " method=" + r.method + " elapsed=" + r.elapsedMs(now) + "ms");
                    return;
                }
                if (now < deadline) { handler.postDelayed(this, POLL_MS); return; }
                if (StartupReadinessPolicy.foregroundAllowed(mode()) && !r.activityLaunchAccepted) {
                    policy.changeMethod(source, Method.ACTIVITY_BOOTSTRAP);
                    MediaEventTrace.record("startup", "background-insufficient",
                            source + " boundary=" + boundary);
                    enqueueForeground(source, generation);
                } else if (r.activityLaunchAccepted) {
                    policy.uncertain(source, generation, boundary, now);
                } else {
                    policy.fail(source, generation, boundary, now);
                    MediaEventTrace.record("startup", "failed", source + " boundary=" + boundary
                            + " elapsed=" + r.elapsedMs(now) + "ms");
                }
            }
        });
    }

    // ---- foreground bootstrap ------------------------------------------------------------

    private void enqueueForeground(Source source, int generation) {
        if (!foregroundQueue.contains(source)) foregroundQueue.add(source);
        if (source == Source.NAVIGATION && navigationHook != null && !navigationHeld) {
            navigationHeld = true;
            navigationHook.holdNavigation(true);
        }
        runForegroundSoon(INTER_STEP_SETTLE_MS);
    }

    private void runForegroundSoon(long delay) {
        handler.postDelayed(this::runForeground, delay);
    }

    private void runForeground() {
        if (foregroundBusy || foregroundQueue.isEmpty() || !homeResumed) return;
        if (!StartupReadinessPolicy.foregroundAllowed(mode())) { cancelForeground("mode-background-only"); return; }
        if (inCall()) { blockQueue("call-active"); return; }
        final Source source = foregroundQueue.poll();
        final int generation = policy.record(source).generation;
        if (policy.record(source).state != StartupReadinessPolicy.State.STARTING) { runForegroundSoon(1L); return; }
        final String pkg = packageFor(source);
        if (pkg.isEmpty() || !policy.markActivityLaunch(source)) {
            policy.fail(source, generation, pkg.isEmpty() ? "package-missing" : "launch-already-accepted",
                    SystemClock.uptimeMillis());
            runForegroundSoon(1L);
            return;
        }
        foregroundBusy = true;
        final int token = ++foregroundToken;
        final int interactionAtStart = userInteractionGeneration;
        final boolean wasPlaying = source != Source.NAVIGATION && bootstrapper.isPlaying(pkg);
        final long started = SystemClock.uptimeMillis();
        MediaEventTrace.record("startup", "foreground-launch", source + " pkg=" + pkg);
        if (!AppResolver.launchPackageQuietly(context, pkg)) {
            if (mode() == Mode.AGGRESSIVE) launchWithRoot(pkg);
            else {
                finishForeground(source, generation, token, false, "launch-rejected", false);
                return;
            }
        }
        long dwell = source == Source.NAVIGATION ? NAVIGATION_DWELL_MS
                : source == Source.MUSIC ? MUSIC_FOREGROUND_MS : RADIO_FOREGROUND_MS;
        final long deadline = started + dwell;
        handler.post(new Runnable() {
            @Override public void run() {
                if (token != foregroundToken) return;
                long now = SystemClock.uptimeMillis();
                if (inCall()) { finishForeground(source, generation, token, false, "call-active", wasPlaying); return; }
                if (source == Source.NAVIGATION) {
                    if (now < deadline) { handler.postDelayed(this, POLL_MS); return; }
                    finishForeground(source, generation, token, true, "", wasPlaying);
                    return;
                }
                Check check = source == Source.MUSIC
                        ? () -> bootstrapper.hasUsableQueue(pkg) : () -> bootstrapper.hasUsableController(pkg);
                if (check.ok()) { finishForeground(source, generation, token, true, "", wasPlaying); return; }
                if (now < deadline) { handler.postDelayed(this, POLL_MS); return; }
                finishForeground(source, generation, token, false,
                        source == Source.MUSIC ? "activity-no-usable-queue" : "activity-no-session", wasPlaying);
            }
        });
    }

    private void finishForeground(Source source, int generation, int token, boolean success,
                                  String boundary, boolean wasPlaying) {
        if (token != foregroundToken) return;
        String pkg = packageFor(source);
        if (source != Source.NAVIGATION && !wasPlaying) bootstrapper.pauseUnrequestedPlayback(pkg);
        long now = SystemClock.uptimeMillis();
        if (success) {
            if (source == Source.NAVIGATION) policy.uncertain(source, generation, "awaiting-task-adoption", now);
            else policy.ready(source, generation, now);
        } else {
            policy.fail(source, generation, boundary, now);
        }
        MediaEventTrace.record("startup", success ? "foreground-done" : "foreground-failed",
                source + (boundary.isEmpty() ? "" : " boundary=" + boundary));
        restoreHome(source, generation, token);
    }

    /** Restore the existing HOME task (singleTask CLEAR_TOP); never kills or restarts the source. */
    private void restoreHome(Source source, int generation, int token) {
        boolean resumedBefore = homeResumed;
        if (!resumedBefore) HomeMode.bringLauncherToFront(context);
        final long deadline = SystemClock.uptimeMillis() + HOME_RESTORE_MS;
        final boolean[] rootTried = {false};
        handler.post(new Runnable() {
            @Override public void run() {
                if (token != foregroundToken) return;
                if (homeResumed) {
                    foregroundBusy = false;
                    if (source == Source.NAVIGATION) releaseNavigation();
                    else if (foregroundQueue.isEmpty()) releaseNavigation();
                    runForegroundSoon(INTER_STEP_SETTLE_MS);
                    return;
                }
                if (SystemClock.uptimeMillis() >= deadline) {
                    if (!rootTried[0] && mode() == Mode.AGGRESSIVE) {
                        rootTried[0] = true;
                        restoreHomeWithRoot();
                        handler.postDelayed(this, HOME_RESTORE_MS);
                        return;
                    }
                    // HOME did not return: someone else (user, call, camera, SystemUI) owns the screen.
                    foregroundBusy = false;
                    blockQueue("home-restore-yielded");
                    return;
                }
                handler.postDelayed(this, POLL_MS);
            }
        });
    }

    private void releaseNavigation() {
        if (navigationHeld && navigationHook != null) {
            navigationHeld = false;
            navigationHook.holdNavigation(false);
        } else navigationHeld = false;
    }

    private void blockQueue(String boundary) {
        long now = SystemClock.uptimeMillis();
        while (!foregroundQueue.isEmpty()) {
            Source source = foregroundQueue.poll();
            StartupReadinessPolicy.Record r = policy.record(source);
            if (r.state == StartupReadinessPolicy.State.STARTING) policy.block(source, r.generation, boundary, now);
        }
        MediaEventTrace.record("startup", "foreground-yielded", boundary);
        releaseNavigation();
    }

    private void cancelForeground(String boundary) {
        foregroundToken++;
        foregroundBusy = false;
        blockQueue(boundary);
    }

    private void launchWithRoot(String pkg) {
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null || launch.getComponent() == null) return;
        final String component = launch.getComponent().flattenToShortString();
        if (!COMPONENT.matcher(component).matches()) return;
        rootExecutor.execute(() -> RootShell.runWithin("am start --user 0 -n " + component, 1800L));
    }

    private void restoreHomeWithRoot() {
        final String component = context.getPackageName() + "/.LauncherActivity";
        rootExecutor.execute(() -> RootShell.runWithin("am start --user 0 -a android.intent.action.MAIN"
                + " -c android.intent.category.HOME -f 0x14000000 -n " + component, 1800L));
    }

    // ---- helpers -------------------------------------------------------------------------

    Mode mode() { return StartupReadinessPolicy.parseMode(TestingProfiles.startup(context)); }

    private boolean inCall() {
        try {
            AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            int m = audio == null ? AudioManager.MODE_NORMAL : audio.getMode();
            return m == AudioManager.MODE_IN_CALL || m == AudioManager.MODE_IN_COMMUNICATION
                    || m == AudioManager.MODE_RINGTONE;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private long bootCount() {
        try {
            return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        } catch (RuntimeException ignored) {
            return -1L;
        }
    }

    private String packageFor(Source source) {
        switch (source) {
            case MUSIC: {
                String pkg = LauncherPrefs.packageFor(context, LauncherPrefs.KEY_MUSIC);
                return pkg.isEmpty() ? TopwayAdapter.defaultMusicPackage(context) : pkg;
            }
            case RADIO:
                return RadioProvider.resolvePackage(context);
            default: {
                String pkg = LauncherPrefs.packageFor(context, LauncherPrefs.KEY_NAV);
                if (pkg.isEmpty() && NavigationProvider.hasLauncherActivity(context,
                        NavigationProvider.ORGANIC_MAPS_INCAR)) pkg = NavigationProvider.ORGANIC_MAPS_INCAR;
                return pkg;
            }
        }
    }

    void destroy() {
        handler.removeCallbacksAndMessages(null);
        bootstrapper.destroy();
    }
}
