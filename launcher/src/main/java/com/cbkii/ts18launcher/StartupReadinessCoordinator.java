package com.cbkii.ts18launcher;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import com.cbkii.ts18launcher.StartupReadinessPolicy.*;
import com.cbkii.ts18launcher.platform.TopwayAdapter;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;

/** One startup ledger; media commands and Maps launch/task authority remain in their existing owners. */
final class StartupReadinessCoordinator implements ProcessMediaSessionMonitor.Observer {
    interface NavigationHook {
        boolean ready();
        boolean busy();
        void reconcile();
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
    private final java.util.concurrent.ExecutorService observer =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "ts18-startup-observe"); t.setDaemon(true); return t;
            });
    private final String[] packages = new String[Source.values().length];
    private WeakReference<Activity> home = new WeakReference<>(null);
    private NavigationHook navigation;
    private boolean homeResumed, foregroundBusy, yielded, destroyed;
    private int foregroundToken;
    private Source foregroundSource;
    private int stagedTask = -1;
    private String stagedEntry = "";
    private int homeTask = -1;
    private long stagedCommands;
    private boolean pauseSent;

    private StartupReadinessCoordinator(Context context) {
        this.context = context;
        ProcessMediaSessionMonitor.addObserver(context, this);
    }
    private MediaSourceBootstrapper media() { return Ts18LauncherApplication.mediaSources(context); }

    void onTrigger(Trigger trigger) {
        if (Looper.myLooper() != Looper.getMainLooper()) { handler.post(() -> onTrigger(trigger)); return; }
        if (destroyed) return;
        long now = SystemClock.uptimeMillis(), boot = bootCount();
        SharedPreferences prefs = context.getSharedPreferences("startup_readiness", Context.MODE_PRIVATE);
        long seen = prefs.getLong("boot_count", -1L);
        Kind kind = StartupReadinessPolicy.classify(trigger, boot, seen);
        // BOOT_COMPLETED can arrive after process/HOME startup: never reset active admissions twice.
        if (boot >= 0 && boot != seen) {
            cancelForeground("new-boot"); policy.resetForBoot(); yielded = false;
            prefs.edit().putLong("boot_count", boot).apply();
        }
        if (!policy.acceptTrigger(trigger, now)) return;
        if (trigger == Trigger.SCREEN_ON || trigger == Trigger.ACC_ON) yielded = false;
        MediaEventTrace.record("startup", "trigger", trigger + " kind=" + kind + " mode=" + mode()
                + (trigger == Trigger.SCREEN_ON ? " acc=unverified" : ""));
        if (!UiPersonalizationPrefs.mediaStartupWarmup(context)) {
            cancelForeground("disabled-by-preference"); return;
        }
        for (Source source : Source.values()) {
            String pkg = packageFor(source);
            int index = source.ordinal();
            if (!pkg.equals(packages[index])) {
                if (foregroundSource == source) cancelForeground("source-changed");
                foregroundQueue.remove(source); policy.configurationChanged(source); packages[index] = pkg;
            }
            if (pkg.isEmpty()) continue;
            if (foregroundSource == source) continue;
            if (ready(source, pkg)) { policy.observedReady(source, now); continue; }
            State state = policy.record(source).state;
            if (state == State.READY || state == State.ACTIVE) {
                // Metadata/queue churn is not session death and must not trigger Activity replay.
                if (source != Source.NAVIGATION && media().hasSessionIdentity(pkg)) continue;
                policy.sessionLost(source);
            }
            if (!policy.admit(source, trigger, now)) continue;
            int g = policy.begin(source, trigger, source == Source.NAVIGATION ? Method.TASK_ADOPTION : Method.SESSION, now);
            if (source == Source.NAVIGATION) {
                // No second launch, fixed Splash dwell, or parallel root command. N0/N1/N2 remain comparators.
                if (navigation != null) navigation.reconcile();
                await(source, g, pkg, now + 18000L);
            } else {
                if (source == Source.MUSIC) { policy.changeMethod(source, Method.BROWSER); media().warm(pkg); }
                await(source, g, pkg, now + (source == Source.MUSIC ? 9000L : 2500L));
            }
        }
        runForegroundSoon();
    }

    void attachNavigationHook(NavigationHook hook) { navigation = hook; }
    void detachNavigationHook(NavigationHook hook) { if (navigation == hook) navigation = null; }
    void onHomeResumed(Activity activity) {
        home = new WeakReference<>(activity); homeResumed = true; onTrigger(Trigger.HOME_RESUME);
    }
    void onHomePaused() { homeResumed = false; }
    void onScreenOff() { yielded = true; cancelForeground("screen-off"); }
    void noteUserInteraction() { yielded = true; cancelForeground("user-interaction"); }
    void reportNavigation(boolean success, String boundary) {
        recordResultNavigation(success, boundary);
    }
    private void recordResultNavigation(boolean success, String boundary) {
        StartupReadinessPolicy.Record r = policy.record(Source.NAVIGATION);
        if (success) policy.observedReady(Source.NAVIGATION, SystemClock.uptimeMillis());
        else {
            policy.block(Source.NAVIGATION, r.generation, boundary, SystemClock.uptimeMillis());
            if (r.state == State.READY) policy.sessionLost(Source.NAVIGATION);
        }
        MediaEventTrace.record("startup", "navigation", summary());
    }
    String summary() { return policy.summary(SystemClock.uptimeMillis()); }
    Mode mode() { return StartupReadinessPolicy.parseMode(TestingProfiles.startup(context)); }

    @Override public void onProcessMediaStateChanged(MediaListenerService.Snapshot music,
            MediaListenerService.Snapshot radio, boolean authoritative) {
        if (!destroyed && authoritative) onTrigger(Trigger.SESSION_LOST);
    }

    private boolean ready(Source source, String pkg) {
        if (source == Source.NAVIGATION) return navigation != null && navigation.ready();
        // Queue/station content and Play capability, never a token alone or audible-output claim.
        return media().hasUsableQueue(pkg);
    }
    private void await(Source source, int g, String pkg, long deadline) {
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                StartupReadinessPolicy.Record r = policy.record(source);
                if (destroyed || r.generation != g || r.state != State.STARTING || !pkg.equals(packageFor(source))) return;
                long now = SystemClock.uptimeMillis();
                if (ready(source, pkg)) {
                    policy.ready(source, g, now);
                    MediaEventTrace.record("startup", "ready", summary()); return;
                }
                if (now < deadline) { handler.postDelayed(this, 500L); return; }
                if (source == Source.NAVIGATION) {
                    policy.uncertain(source, g, "compact-task-not-verified", now); return;
                }
                boolean qualifiedPackage = MediaSourceAdapter.AUXIO_PACKAGE.equals(pkg)
                        || MediaSourceAdapter.NAVRADIO_PACKAGE.equals(pkg);
                if (foregroundAllowed() && qualifiedPackage && !r.activityLaunchAccepted) {
                    policy.changeMethod(source, Method.ACTIVITY_BOOTSTRAP);
                    foregroundQueue.add(source); runForegroundSoon();
                    handler.postDelayed(() -> {
                        if (policy.record(source).generation == g && foregroundQueue.remove(source)) policy.block(source, g, "foreground-admission-expired", SystemClock.uptimeMillis());
                    }, 20000L);
                } else policy.block(source, g, "background-not-ready-or-staging-yielded", now);
            }
        }, 500L);
    }

    private boolean foregroundAllowed() {
        return StartupReadinessPolicy.foregroundAllowed(mode()) && !yielded
                && UiPersonalizationPrefs.mediaStartupWarmup(context);
    }
    private void runForegroundSoon() { handler.postDelayed(this::runForeground, 1200L); }
    private void runForeground() {
        if (destroyed || foregroundBusy || foregroundQueue.isEmpty() || !homeResumed) return;
        Activity activity = home.get();
        if (!foregroundAllowed() || !safeScreen() || activity == null || activity.isFinishing()
                || !activity.hasWindowFocus() || !HomeMode.isDefaultHome(context)) return;
        if (navigation != null && navigation.busy()) { runForegroundSoon(); return; }
        Source source = foregroundQueue.poll();
        StartupReadinessPolicy.Record r = policy.record(source);
        if (r.state != State.STARTING) { runForegroundSoon(); return; }
        String pkg = packageFor(source);
        if (ready(source, pkg)) { policy.ready(source, r.generation, SystemClock.uptimeMillis()); runForegroundSoon(); return; }
        String opposite = packageFor(source == Source.MUSIC ? Source.RADIO : Source.MUSIC);
        if (source == Source.RADIO && !MediaListenerService.hasNotificationAccess(context)) {
            policy.block(source, r.generation, "notification-access-required", SystemClock.uptimeMillis());
            runForegroundSoon(); return;
        }
        if (media().isPlaying(pkg) || media().isPlaying(opposite)) {
            policy.block(source, r.generation, "playing-source-protected", SystemClock.uptimeMillis()); runForegroundSoon(); return;
        }
        Intent entry = context.getPackageManager().getLaunchIntentForPackage(pkg);
        if (entry == null || entry.getComponent() == null) {
            policy.block(source, r.generation, "entry-unavailable", SystemClock.uptimeMillis()); runForegroundSoon(); return;
        }
        foregroundBusy = true; foregroundSource = source;
        int token = ++foregroundToken, g = r.generation;
        homeTask = activity.getTaskId(); stagedTask = -1;
        stagedEntry = entry.getComponent().flattenToShortString(); stagedCommands = media().userCommandGeneration(); pauseSent = false;
        observe(token, top -> {
            if (!homeResumed || !foregroundAllowed() || !safeScreen() || top == null
                    || !(top.matches(AndroidUserId.current(), homeTask, context.getPackageName() + "/.LauncherActivity")
                            || top.matches(AndroidUserId.current(), homeTask, context.getPackageName() + "/.HomeAlias"))) {
                yieldStage(source, g, "home-ownership-unconfirmed"); return;
            }
            if (source == Source.RADIO && !MediaListenerService.hasNotificationAccess(context)) {
                yieldStage(source, g, "notification-access-lost"); return;
            }
            if (media().isPlaying(pkg) || media().isPlaying(opposite)) {
                yieldStage(source, g, "playing-source-arrived"); return;
            }
            if (!policy.markActivityLaunch(source)) { yieldStage(source, g, "launch-already-accepted"); return; }
            MediaEventTrace.record("startup", "foreground-launch", source + " " + stagedEntry);
            // Even an exception can leave an uncertain launch. Never issue a root duplicate.
            if (!AppResolver.launchPackageQuietly(context, pkg)) { yieldStage(source, g, "launch-outcome-unconfirmed"); return; }
            handler.postDelayed(() -> observe(token, owner -> {
                if (!validStage(token) || owner == null || !owner.isEntry(AndroidUserId.current(), stagedEntry)) {
                    yieldStage(source, g, "source-entry-not-foreground"); return;
                }
                stagedTask = owner.task;
                awaitStage(source, g, pkg, token, SystemClock.uptimeMillis() + 8000L);
            }), 1000L);
        });
    }
    private boolean validStage(int token) {
        return token == foregroundToken && foregroundAllowed() && safeScreen()
                && foregroundSource != null
                && (foregroundSource != Source.RADIO || MediaListenerService.hasNotificationAccess(context))
                && packageFor(foregroundSource).equals(packages[foregroundSource.ordinal()])
                && media().userCommandGeneration() == stagedCommands;
    }
    private void awaitStage(Source source, int g, String pkg, int token, long deadline) {
        if (token != foregroundToken) return;
        if (!validStage(token)) { yieldStage(source, g, "user-or-system-interruption"); return; }
        // Do not pause a newly observed player without rechecking foreground ownership first.
        if (ready(source, pkg) || media().isPlaying(pkg) || SystemClock.uptimeMillis() >= deadline) {
            observe(token, top -> {
                if (!validStage(token) || top == null || !top.matches(AndroidUserId.current(), stagedTask, stagedEntry)) {
                    yieldStage(source, g, "foreground-owner-changed"); return;
                }
                if (!pauseSent && media().isPlaying(pkg)) {
                    pauseSent = media().pauseUnrequestedPlayback(pkg, stagedCommands);
                    awaitSilence(source, g, pkg, token, SystemClock.uptimeMillis() + 1600L);
                } else finishStage(source, g, pkg, token);
            });
        } else handler.postDelayed(() -> awaitStage(source, g, pkg, token, deadline), 500L);
    }
    private void awaitSilence(Source source, int g, String pkg, int token, long deadline) {
        if (token != foregroundToken) return;
        if (!validStage(token)) { yieldStage(source, g, "silence-check-interrupted"); return; }
        if (media().isPlaying(pkg) && SystemClock.uptimeMillis() < deadline) {
            handler.postDelayed(() -> awaitSilence(source, g, pkg, token, deadline), 250L);
        } else finishStage(source, g, pkg, token);
    }
    private void finishStage(Source source, int g, String pkg, int token) {
        if (token != foregroundToken) return;
        // A pause dispatch is not proof of silence. Leave uncertain rather than recording READY.
        observe(token, top -> {
            if (!validStage(token) || top == null || !top.matches(AndroidUserId.current(), stagedTask, stagedEntry)) {
                yieldStage(source, g, "home-return-owner-changed"); return;
            }
            if (ready(source, pkg) && !media().isPlaying(pkg)) policy.ready(source, g, SystemClock.uptimeMillis());
            else policy.uncertain(source, g, "content-or-silence-unconfirmed", SystemClock.uptimeMillis());
            // Restore the exact existing HOME task. No root am-start or component relaunch.
            Activity activity = home.get();
            if (activity == null || activity.getTaskId() != homeTask || activity.isFinishing()) {
                yieldStage(source, g, "home-task-gone"); return;
            }
            try {
                android.app.ActivityManager manager = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                manager.moveTaskToFront(homeTask, 0);
            } catch (RuntimeException e) { yieldStage(source, g, "home-return-refused"); return; }
            handler.postDelayed(() -> {
                if (token != foregroundToken) return;
                if (!homeResumed || !activity.hasWindowFocus()) { yieldStage(source, g, "home-return-unconfirmed"); return; }
                foregroundBusy = false; foregroundSource = null; runForegroundSoon();
            }, 1200L);
        });
    }
    private interface Observation { void accept(StartupForegroundPolicy top); }
    private void observe(int token, Observation callback) {
        observer.execute(() -> {
            RootShell.Result result = RootShell.runWithin(
                    "dumpsys activity activities | /system/bin/toybox grep 'mResumedActivity:'; "
                    + "dumpsys window windows | /system/bin/toybox grep 'mCurrentFocus='", 1800L);
            StartupForegroundPolicy top = result.success() ? StartupForegroundPolicy.parse(result.output) : null;
            handler.post(() -> { if (token == foregroundToken && !destroyed) callback.accept(top); });
        });
    }
    private void yieldStage(Source source, int g, String boundary) {
        policy.uncertain(source, g, boundary, SystemClock.uptimeMillis());
        yielded = true; cancelForeground(boundary);
    }
    private void cancelForeground(String boundary) {
        foregroundToken++; foregroundBusy = false;
        long now = SystemClock.uptimeMillis();
        if (foregroundSource != null) {
            StartupReadinessPolicy.Record r = policy.record(foregroundSource);
            policy.uncertain(foregroundSource, r.generation, boundary, now);
        }
        foregroundSource = null;
        while (!foregroundQueue.isEmpty()) {
            Source s = foregroundQueue.poll(); policy.block(s, policy.record(s).generation, boundary, now);
        }
        MediaEventTrace.record("startup", "yield", boundary);
    }
    private boolean safeScreen() {
        try {
            PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            return power != null && power.isInteractive() && keyguard != null && !keyguard.isKeyguardLocked()
                    && audio != null && audio.getMode() == AudioManager.MODE_NORMAL;
        } catch (RuntimeException ignored) { return false; }
    }
    private long bootCount() {
        try { return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1); }
        catch (RuntimeException ignored) { return -1L; }
    }
    private String packageFor(Source source) {
        if (source == Source.MUSIC) {
            String pkg = LauncherPrefs.packageFor(context, LauncherPrefs.KEY_MUSIC);
            return pkg.isEmpty() ? TopwayAdapter.defaultMusicPackage(context) : pkg;
        }
        if (source == Source.RADIO) return RadioProvider.resolvePackage(context);
        String pkg = LauncherPrefs.packageFor(context, LauncherPrefs.KEY_NAV);
        return pkg.isEmpty() && NavigationProvider.hasLauncherActivity(context, NavigationProvider.ORGANIC_MAPS_INCAR)
                ? NavigationProvider.ORGANIC_MAPS_INCAR : pkg;
    }
    void destroy() {
        destroyed = true; cancelForeground("destroyed"); handler.removeCallbacksAndMessages(null);
        ProcessMediaSessionMonitor.removeObserver(this); observer.shutdownNow();
    }
}
