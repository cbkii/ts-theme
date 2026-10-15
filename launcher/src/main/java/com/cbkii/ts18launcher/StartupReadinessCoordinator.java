package com.cbkii.ts18launcher;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.TelephonyManager;

import com.cbkii.ts18launcher.StartupReadinessPolicy.Mode;
import com.cbkii.ts18launcher.StartupReadinessPolicy.Source;
import com.cbkii.ts18launcher.StartupReadinessPolicy.State;
import com.cbkii.ts18launcher.StartupReadinessPolicy.Trigger;
import com.cbkii.ts18launcher.platform.TopwayAdapter;

/**
 * Application-scoped, event-driven startup reconciliation for Music and Radio (Navigation is
 * observed, its cold start remains owned by NavigationWindowController). It is not a service: it
 * runs on the main thread only while triggered, with bounded deadlines and no polling loop.
 *
 * Background preparation uses the existing MediaSourceBootstrapper (one admission per source,
 * never autoplay). If that fails and the testing mode allows it, ONE foreground Activity bootstrap
 * per source per startup generation runs while HOME is resumed, then the ts-theme HOME task is
 * brought back. Nothing here plays audio, owns focus, force-stops, taps or clears data.
 */
final class StartupReadinessCoordinator {
    private static final long CHECK_INTERVAL_MS = 750L;
    private static StartupReadinessCoordinator instance;

    private final Context app;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MediaSourceBootstrapper bootstrapper;
    private Source music;
    private Source radio;
    private final java.util.LinkedHashMap<String, String> navigation = new java.util.LinkedHashMap<>();
    private String navigationState = "UNOBSERVED";
    private Trigger lastTrigger;
    private long lastRunMs;
    private boolean homeResumed;
    private boolean userBusy;
    private Source foreground;
    private long foregroundDeadlineMs;
    private String homeComponentClass = LauncherActivity.class.getName();
    private int checkGeneration;

    static synchronized StartupReadinessCoordinator get(Context context) {
        if (instance == null) instance = new StartupReadinessCoordinator(context.getApplicationContext());
        return instance;
    }

    private StartupReadinessCoordinator(Context context) {
        app = context;
        bootstrapper = new MediaSourceBootstrapper(context);
        music = new Source(musicPackage());
        radio = new Source(RadioProvider.resolvePackage(context));
    }

    private String musicPackage() {
        String configured = LauncherPrefs.packageFor(app, LauncherPrefs.KEY_MUSIC);
        return configured.isEmpty() ? TopwayAdapter.defaultMusicPackage(app) : configured;
    }

    /** Called once per process. Distinguishes full boot from a same-boot launcher restart. */
    void onProcessStart() {
        int current = Settings.Global.getInt(app.getContentResolver(), "boot_count", -1);
        android.content.SharedPreferences prefs = app.getSharedPreferences("startup_readiness", Context.MODE_PRIVATE);
        int stored = prefs.getInt("boot_count", -1);
        Trigger trigger = StartupReadinessPolicy.classifyProcessStart(stored, current);
        if (current >= 0) prefs.edit().putInt("boot_count", current).apply();
        MediaEventTrace.record("startup", "process-start", trigger + " boot=" + current + " prev=" + stored);
        onTrigger(trigger, "process");
    }

    void onHomeResumed(boolean resumed) {
        homeResumed = resumed;
        if (resumed) {
            userBusy = false;
            if (foreground != null) finishForeground(null, "home-resumed");
            onTrigger(Trigger.RESUME, "home");
        }
    }

    /** An explicit user command/app launch always wins; foreground bootstrap yields. */
    void onUserCommand() {
        userBusy = true;
        if (foreground != null) finishForeground(null, "user-command");
    }

    /** Late USB mount: a healthy source is not restarted, but Auxio re-runs its own PREPARE/library path. */
    void onStorageMounted() {
        onTrigger(Trigger.USB_MOUNT, "storage-mounted");
        if (music.state == State.READY || music.state == State.ACTIVE) bootstrapper.warm(music.packageName);
    }

    void onNavigationState(String state, String detail) {
        navigationState = state == null ? "UNOBSERVED" : state;
        navigation.put(navigationState, detail == null ? "" : detail);
        if (navigation.size() > 8) navigation.remove(navigation.keySet().iterator().next());
    }

    void onTrigger(Trigger trigger, String detail) {
        if (!UiPersonalizationPrefs.mediaStartupWarmup(app)) return;
        long now = SystemClock.uptimeMillis();
        if (!StartupReadinessPolicy.shouldReconcile(trigger, now, lastRunMs, lastTrigger)) {
            MediaEventTrace.record("startup", "deduplicated", trigger + " " + detail);
            return;
        }
        lastRunMs = now; lastTrigger = trigger;
        MediaEventTrace.record("startup", "trigger", trigger + " " + detail);
        boolean newGeneration = trigger == Trigger.BOOT || trigger == Trigger.WAKE
                || trigger == Trigger.USB_MOUNT || trigger == Trigger.SESSION_LOST
                || trigger == Trigger.RESTART;
        if (trigger == Trigger.SESSION_LOST) {
            music.state = StartupReadinessPolicy.onSessionLost(music.state);
            radio.state = StartupReadinessPolicy.onSessionLost(radio.state);
        }
        refreshPackages();
        admit(music, trigger, newGeneration, now);
        admit(radio, trigger, newGeneration, now);
        scheduleCheck(++checkGeneration);
    }

    private void admit(Source source, Trigger trigger, boolean newGeneration, long now) {
        if (source.packageName.isEmpty()) { source.state = State.UNAVAILABLE; return; }
        observe(source, now);
        if (!StartupReadinessPolicy.needsAdmission(source.state, trigger, now, source.lastFailureMs)
                && !(source.state == State.UNAVAILABLE)) return;
        if (!source.begin("background", now, newGeneration)) return;
        MediaEventTrace.record("startup", "admit", source.summary(now));
        bootstrapper.warm(source.packageName);
    }

    private void refreshPackages() {
        // Package identity follows user configuration; a change starts a fresh record.
        String m = musicPackage(), r = RadioProvider.resolvePackage(app);
        if (!m.equals(music.packageName)) music = new Source(m);
        if (!r.equals(radio.packageName)) radio = new Source(r);
    }

    private void observe(Source source, long now) {
        if (source.packageName.isEmpty()) return;
        if (bootstrapper.hasUsableController(source.packageName)) {
            if (source.state != State.READY && source.state != State.ACTIVE) {
                source.ready(now);
                MediaEventTrace.record("startup", "ready", source.summary(now));
            }
            if (bootstrapper.isPlaying(source.packageName)) source.state = State.ACTIVE;
        }
    }

    private void scheduleCheck(int generation) {
        handler.postDelayed(() -> check(generation), CHECK_INTERVAL_MS);
    }

    private void check(int generation) {
        if (generation != checkGeneration) return;
        long now = SystemClock.uptimeMillis();
        boolean pending = false;
        for (Source source : new Source[] {music, radio}) {
            if (source.packageName.isEmpty()) continue;
            observe(source, now);
            if (source.state != State.STARTING) continue;
            if (source.elapsedMs(now) >= StartupReadinessPolicy.BACKGROUND_DEADLINE_MS) {
                MediaCommandPolicy.Phase phase = bootstrapper.status(source.packageName).phase;
                if (phase == MediaCommandPolicy.Phase.BLOCKED) {
                    source.fail(State.BLOCKED, "background-blocked", now);
                } else if (phase == MediaCommandPolicy.Phase.IDLE) {
                    source.fail(State.UNCERTAIN, "background-not-admitted", now);
                } else source.fail(State.FAILED, "session-not-ready:" + phase, now);
                MediaEventTrace.record("startup", "background-failed", source.summary(now));
                maybeForeground(source == radio, source);
            } else pending = true;
        }
        if (pending || foreground != null) scheduleCheck(generation);
    }

    private void maybeForeground(boolean isRadio, Source source) {
        Mode mode = Mode.parse(TestingProfiles.startup(app));
        boolean call = callActive();
        if (!StartupReadinessPolicy.foregroundAllowed(mode, isRadio, source.state,
                source.foregroundAttempted, homeResumed, userBusy, call, foreground != null)) {
            MediaEventTrace.record("startup", "foreground-skipped",
                    source.packageName + " mode=" + mode + " home=" + homeResumed + " call=" + call);
            return;
        }
        source.foregroundAttempted = true;
        long now = SystemClock.uptimeMillis();
        foreground = source; foregroundDeadlineMs = now + StartupReadinessPolicy.FOREGROUND_DEADLINE_MS;
        source.begin("foreground-activity", now, false);
        MediaEventTrace.record("startup", "foreground-launch", source.summary(now));
        // Quiet launch: does not change the selected HOME source and is dispatched exactly once.
        if (!AppResolver.launchPackageQuietly(app, source.packageName)) {
            finishForeground(State.FAILED, "foreground-launch-rejected");
            return;
        }
        scheduleForegroundPoll(checkGeneration);
    }

    private void scheduleForegroundPoll(int generation) {
        handler.postDelayed(() -> {
            if (foreground == null || generation != checkGeneration) return;
            long now = SystemClock.uptimeMillis();
            if (callActive()) { finishForeground(State.BLOCKED, "call-active", false); return; }
            if (bootstrapper.hasUsableController(foreground.packageName)) {
                finishForeground(State.READY, ""); return;
            }
            if (now >= foregroundDeadlineMs) { finishForeground(State.FAILED, "foreground-session-timeout"); return; }
            scheduleForegroundPoll(generation);
        }, CHECK_INTERVAL_MS);
    }

    private void finishForeground(State result, String boundary) { finishForeground(result, boundary, true); }

    /** result==null means externally interrupted: just release ownership, no restore, no state claim. */
    private void finishForeground(State result, String boundary, boolean restoreHome) {
        Source source = foreground;
        if (source == null) return;
        foreground = null;
        long now = SystemClock.uptimeMillis();
        if (result == State.READY) source.ready(now);
        else if (result != null) source.fail(result, boundary, now);
        else { source.state = State.UNCERTAIN; if (source.firstFailingBoundary.isEmpty()) source.firstFailingBoundary = boundary; }
        MediaEventTrace.record("startup", "foreground-finished", source.summary(now));
        if (result != null && restoreHome) restoreHome();
        // Sequential: the next source (e.g. Radio after Music) is evaluated after restoration.
        if (result != null) handler.postDelayed(() -> check(checkGeneration), CHECK_INTERVAL_MS * 2);
    }

    /** Brings the existing ts-theme HOME task forward; never creates another task or kills a source. */
    private void restoreHome() {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setClassName(app, app.getPackageName() + ".HomeAlias")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!HomeMode.isDefaultHome(app)) {
            intent = new Intent(Intent.ACTION_MAIN).setClassName(app, homeComponentClass)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            app.startActivity(intent);
            MediaEventTrace.record("startup", "home-restore", "dispatched");
        } catch (RuntimeException e) {
            MediaEventTrace.record("startup", "home-restore", "blocked " + e.getClass().getSimpleName());
        }
    }

    private boolean callActive() {
        try {
            TelephonyManager tm = (TelephonyManager) app.getSystemService(Context.TELEPHONY_SERVICE);
            return tm != null && tm.getCallState() != TelephonyManager.CALL_STATE_IDLE;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Compact, bounded text for the diagnostics export. */
    String diagnostics() {
        long now = SystemClock.uptimeMillis();
        return "startup trigger=" + lastTrigger + " mode=" + TestingProfiles.startup(app)
                + "\n music: " + music.summary(now) + "\n radio: " + radio.summary(now)
                + "\n navigation: " + navigationState;
    }
}
