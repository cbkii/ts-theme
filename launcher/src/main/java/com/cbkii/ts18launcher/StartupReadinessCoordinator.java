package com.cbkii.ts18launcher;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;

import com.cbkii.ts18launcher.StartupReadinessPolicy.Mode;
import com.cbkii.ts18launcher.StartupReadinessPolicy.Session;
import com.cbkii.ts18launcher.StartupReadinessPolicy.State;
import com.cbkii.ts18launcher.StartupReadinessPolicy.Trigger;
import com.cbkii.ts18launcher.platform.TopwayAdapter;

import java.util.ArrayDeque;

/**
 * Application-scoped, event-driven source readiness (Music and Radio). Triggers are deduplicated,
 * each source has at most one in-flight admission, healthy sources are never touched, and a
 * bounded sequential foreground Activity stage (never auto-Play) is used only after the
 * background method failed. Navigation keeps its own controller; this class never launches Maps.
 * No polling outside a bounded in-process stage window; no root; no audio-focus use.
 */
final class StartupReadinessCoordinator implements ProcessMediaSessionMonitor.Observer {
    private static final long STAGE_POLL_MS = 500L;
    private static final long STAGE_GAP_MS = 1500L;
    private static final long HOME_RETURN_RETRY_MS = 30000L;

    private static final class Source {
        final String role;
        String packageName = "";
        State state = State.UNAVAILABLE;
        String method = "none";
        int generation;
        String firstFailure = "";
        long startedMs;
        long lastAdmitMs;
        int stagedGeneration = -1;
        Source(String role) { this.role = role; }
    }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Source music = new Source("music");
    private final Source radio = new Source("radio");
    private MediaSourceBootstrapper bootstrapper;
    private final ArrayDeque<Source> stageQueue = new ArrayDeque<>();
    private Source staging;
    private boolean stageHomeForeground;
    private boolean stageCancelled;
    private boolean homeResumed;
    private int generation;
    private Session generationSession = Session.HOME_RETURN;
    private Trigger lastTrigger;
    private long lastTriggerMs;
    private boolean bootHandled;

    StartupReadinessCoordinator(Context context) {
        this.context = context.getApplicationContext();
    }

    void setHomeResumed(boolean resumed) { homeResumed = resumed; }

    void onTrigger(Trigger trigger) {
        if (!UiPersonalizationPrefs.mediaStartupWarmup(context)) return;
        long now = SystemClock.uptimeMillis();
        if (StartupReadinessPolicy.debounced(trigger, lastTrigger, now, lastTriggerMs)) {
            MediaEventTrace.record("startup", "trigger-debounced", trigger.name());
            return;
        }
        lastTrigger = trigger;
        lastTriggerMs = now;
        boolean newBoot = !bootHandled && isNewBoot();
        Session session = StartupReadinessPolicy.classify(trigger, newBoot);
        if (session != Session.HOME_RETURN) {
            generation++;
            generationSession = session;
        }
        // Boot is consumed only once HOME is usable, so a receiver-time signal is not lost.
        if (newBoot && homeResumed) markBootHandled();
        else if (newBoot) generationSession = Session.FULL_BOOT;
        MediaEventTrace.record("startup", "trigger", trigger.name() + " session="
                + (session == Session.HOME_RETURN ? generationSession : session)
                + " gen=" + generation);
        Session effective = session == Session.HOME_RETURN ? generationSession : session;
        reconcile(refresh(music, configuredMusic()), trigger, effective);
        reconcile(refresh(radio, RadioProvider.resolvePackage(context)), trigger, effective);
    }

    void onStorageMounted() { onTrigger(Trigger.STORAGE_MOUNT); }

    void onScreenOff() {
        if (staging != null) cancelStage("screen-off");
        stageQueue.clear();
    }

    @Override public void onProcessMediaStateChanged(MediaListenerService.Snapshot genericMedia,
            MediaListenerService.Snapshot radioSnapshot, boolean authoritative) {
        if (!authoritative) return;
        Source[] all = {music, radio};
        for (Source source : all) {
            if (!StartupReadinessPolicy.isReadyState(source.state) || source.packageName.isEmpty()) continue;
            if (!bootstrapper().isReadyForPlay(source.packageName)) {
                source.state = State.UNCERTAIN;
                source.firstFailure = "session-lost";
                MediaEventTrace.record("startup", "source-lost", source.role);
                handler.postDelayed(() -> onTrigger(Trigger.SOURCE_LOST), 2000L);
            }
        }
    }

    String summary() {
        return "gen=" + generation + " " + describe(music) + " " + describe(radio);
    }

    private static String describe(Source s) {
        return s.role + "=" + s.state + "/" + s.method
                + (s.firstFailure.isEmpty() ? "" : "!" + s.firstFailure);
    }

    private MediaSourceBootstrapper bootstrapper() {
        if (bootstrapper == null) bootstrapper = new MediaSourceBootstrapper(context);
        return bootstrapper;
    }

    private String configuredMusic() {
        String pkg = LauncherPrefs.packageFor(context, LauncherPrefs.KEY_MUSIC);
        return pkg.isEmpty() ? TopwayAdapter.defaultMusicPackage(context) : pkg;
    }

    private Source refresh(Source source, String packageName) {
        if (!packageName.equals(source.packageName)) {
            source.packageName = packageName;
            source.state = packageName.isEmpty() ? State.UNAVAILABLE : State.UNCERTAIN;
            source.firstFailure = "";
        }
        if (packageName.isEmpty()) return source;
        if (bootstrapper().isPlaying(packageName)) {
            source.state = State.ACTIVE;
        } else if (bootstrapper().isReadyForPlay(packageName)) {
            source.state = State.READY;
            source.firstFailure = "";
        } else if (StartupReadinessPolicy.isReadyState(source.state)) {
            source.state = State.UNCERTAIN;
            source.firstFailure = "session-lost";
        }
        return source;
    }

    private void reconcile(Source source, Trigger trigger, Session session) {
        if (source.packageName.isEmpty() || !StartupReadinessPolicy.shouldAdmit(source.state)) return;
        long now = SystemClock.uptimeMillis();
        if (session == Session.HOME_RETURN && source.lastAdmitMs > 0L
                && now - source.lastAdmitMs < HOME_RETURN_RETRY_MS) return;
        MediaSourceAdapter adapter = MediaSourceAdapter.resolve(context, source.packageName);
        if (adapter.kind == MediaSourceAdapter.Kind.SESSION_ONLY && !adapter.passiveWarmSafe
                && !canStageActivity(source.packageName)) {
            source.state = State.BLOCKED;
            source.firstFailure = "no-launchable-contract";
            return;
        }
        source.state = State.STARTING;
        source.method = adapter.passiveWarmSafe ? "background-warm" : "existing-session";
        source.generation = generation;
        source.startedMs = now;
        source.lastAdmitMs = now;
        source.firstFailure = "";
        MediaEventTrace.record("startup", "admit", source.role + " " + source.packageName
                + " method=" + source.method + " gen=" + generation + " by=" + trigger);
        bootstrapper().warm(source.packageName);
        final int expected = generation;
        handler.postDelayed(() -> afterBackground(source, expected, session),
                StartupReadinessPolicy.BACKGROUND_WINDOW_MS);
    }

    private boolean canStageActivity(String packageName) {
        return context.getPackageManager().getLaunchIntentForPackage(packageName) != null;
    }

    private void afterBackground(Source source, int expected, Session session) {
        if (source.state != State.STARTING || source.generation != expected) return;
        refresh(source, source.packageName);
        if (StartupReadinessPolicy.isReadyState(source.state)) {
            MediaEventTrace.record("startup", "ready", source.role + " method=" + source.method
                    + " ms=" + (SystemClock.uptimeMillis() - source.startedMs));
            return;
        }
        source.state = State.UNCERTAIN;
        source.firstFailure = firstFailingBoundary(source);
        Mode mode = StartupReadinessPolicy.mode(TestingProfiles.startup(context));
        if (StartupReadinessPolicy.mayStageForeground(mode, session, true, true, true, true,
                source.stagedGeneration == expected) && !stageQueue.contains(source)) {
            stageQueue.add(source);
            MediaEventTrace.record("startup", "stage-queued", source.role);
            runNextStage();
        } else {
            source.state = State.FAILED;
            MediaEventTrace.record("startup", "background-failed",
                    source.role + " boundary=" + source.firstFailure + " mode=" + mode);
        }
    }

    private String firstFailingBoundary(Source source) {
        MediaSourceAdapter adapter = MediaSourceAdapter.resolve(context, source.packageName);
        if (adapter.kind == MediaSourceAdapter.Kind.SESSION_ONLY || !adapter.passiveWarmSafe)
            return "service-not-admitted";
        return bootstrapper().hasController(source.packageName) ? "queue-or-play-not-ready"
                : "session-absent";
    }

    private void runNextStage() {
        if (staging != null) return;
        final Source source = stageQueue.poll();
        if (source == null) return;
        Mode mode = StartupReadinessPolicy.mode(TestingProfiles.startup(context));
        // Gates are evaluated at launch time: HOME in front, screen on, no call, nothing audible.
        final boolean screen = screenInteractive();
        final boolean audioIdle = audioIdle();
        if (!StartupReadinessPolicy.mayStageForeground(mode, generationSession, true,
                homeResumed, screen, audioIdle, source.stagedGeneration == generation)) {
            source.state = State.UNCERTAIN;
            source.firstFailure = "stage-gated:" + (!homeResumed ? "home-not-resumed"
                    : !screen ? "screen-off" : "audio-active");
            MediaEventTrace.record("startup", "stage-skipped", source.role + " " + source.firstFailure);
            if (!stageQueue.isEmpty()) runNextStage();
            return;
        }
        staging = source;
        stageHomeForeground = homeResumed;
        stageCancelled = false;
        source.stagedGeneration = generation;
        source.state = State.STARTING;
        source.method = "foreground-stage";
        source.startedMs = SystemClock.uptimeMillis();
        MediaEventTrace.record("startup", "stage-launch", source.role + " " + source.packageName);
        if (!AppResolver.launchPackageQuietly(context, source.packageName)) {
            source.state = State.FAILED;
            source.firstFailure = "activity-launch-rejected";
            finishStage(source);
            return;
        }
        final long deadline = source.startedMs + StartupReadinessPolicy.STAGE_DWELL_MS;
        handler.postDelayed(() -> pollStage(source, deadline), STAGE_POLL_MS);
    }

    private void pollStage(Source source, long deadline) {
        if (staging != source) return;
        if (stageCancelled) { source.state = State.UNCERTAIN; finishStage(source); return; }
        if (!screenInteractive()) { cancelStage("screen-off"); return; }
        if (!callIdle()) { cancelStage("call"); return; }
        ProcessMediaSessionMonitor.refresh(context);
        long now = SystemClock.uptimeMillis();
        boolean ready = bootstrapper().isReadyForPlay(source.packageName);
        if (ready || now >= deadline) {
            if (ready) {
                source.state = State.READY;
                source.firstFailure = "";
            } else {
                source.state = State.FAILED;
                source.firstFailure = bootstrapper().hasController(source.packageName)
                        ? "queue-or-play-not-ready" : "session-absent-after-activity";
            }
            finishStage(source);
            return;
        }
        handler.postDelayed(() -> pollStage(source, deadline), STAGE_POLL_MS);
    }

    private void cancelStage(String reason) {
        Source source = staging;
        if (source == null) return;
        stageCancelled = true;
        source.state = State.UNCERTAIN;
        source.firstFailure = "cancelled:" + reason;
        MediaEventTrace.record("startup", "stage-cancelled", source.role + " " + reason);
        finishStage(source);
    }

    private void finishStage(Source source) {
        MediaEventTrace.record("startup", "stage-finished", describe(source)
                + " ms=" + (SystemClock.uptimeMillis() - source.startedMs));
        boolean restore = StartupReadinessPolicy.shouldRestoreHome(
                stageHomeForeground, stageCancelled, screenInteractive()) && callIdle();
        staging = null;
        if (restore) restoreHome();
        handler.postDelayed(this::runNextStage, STAGE_GAP_MS);
    }

    /** Brings the existing ts-theme HOME task forward; source tasks/services stay untouched. */
    private void restoreHome() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setPackage(context.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            context.startActivity(home);
            MediaEventTrace.record("startup", "home-restore");
        } catch (RuntimeException error) {
            MediaEventTrace.record("startup", "home-restore-failed", error.getClass().getSimpleName());
        }
    }

    private boolean screenInteractive() {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm == null || pm.isInteractive();
    }

    private boolean callIdle() {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        return am == null || am.getMode() == AudioManager.MODE_NORMAL;
    }

    private boolean audioIdle() {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        return callIdle() && (am == null || !am.isMusicActive());
    }

    private boolean isNewBoot() {
        int current = Settings.Global.getInt(context.getContentResolver(), "boot_count", -1);
        if (current < 0) return false;
        return current != context.getSharedPreferences("startup_state", Context.MODE_PRIVATE)
                .getInt("handled_boot_count", -1);
    }

    private void markBootHandled() {
        bootHandled = true;
        int current = Settings.Global.getInt(context.getContentResolver(), "boot_count", -1);
        context.getSharedPreferences("startup_state", Context.MODE_PRIVATE).edit()
                .putInt("handled_boot_count", current).apply();
    }

    void destroy() {
        handler.removeCallbacksAndMessages(null);
        if (bootstrapper != null) bootstrapper.destroy();
        bootstrapper = null;
    }
}
