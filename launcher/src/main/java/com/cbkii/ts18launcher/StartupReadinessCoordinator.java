package com.cbkii.ts18launcher;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.media.session.PlaybackState;
import android.provider.Settings;
import com.cbkii.ts18launcher.StartupReadinessPolicy.*;
import com.cbkii.ts18launcher.media.MediaSourceKey;
import com.cbkii.ts18launcher.media.MediaSourceState;

/** Application readiness owner. Maps is admitted first; media progresses independently.
 * Retries are bounded, and only the operator-authorised closed-app fallback leases the mask. */
final class StartupReadinessCoordinator {
    interface NavigationHook {
        boolean ready();
        boolean busy();
        void reconcile();
        default void deferAutomaticPresentation(boolean defer) { }
        default int taskId() { return -1; }
        default void refreshEvidence() { }
        default void watchEvidence() { refreshEvidence(); }
        default void cancelAutomaticColdStart() { }
        default void finishLoaderPresentation() { }
        default void yieldForMediaPreparation() { cancelAutomaticColdStart(); }
    }
    interface LoaderUi {
        void render(String status, boolean homeVisible);
        void mask(boolean show);
        void hide();
        default boolean canMask() { return false; }
        default boolean ownsMask() { return false; }
        default void whenMaskDrawn(Runnable ready) { }
        default void incomplete() { }
    }

    @android.annotation.SuppressLint("StaticFieldLeak") // Application context only.
    private static StartupReadinessCoordinator instance;
    static synchronized StartupReadinessCoordinator get(Context context) {
        if (instance == null) instance = new StartupReadinessCoordinator(context.getApplicationContext());
        return instance;
    }
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final StartupReadinessPolicy policy = new StartupReadinessPolicy();
    private NavigationHook navigation;
    private LoaderUi loaderUi;
    private long epoch;
    private boolean homeResumed, yielded, destroyed;
    private final java.util.Map<MediaSourceKey, String> configured = new java.util.EnumMap<>(MediaSourceKey.class);
    private final java.util.Map<MediaSourceKey, Runnable> recovery = new java.util.EnumMap<>(MediaSourceKey.class);
    private boolean screenOff;
    private boolean initialGraceUsed;
    private Runnable initialAdmission;
    private MaskedSourcePreparation staging;
    private String navigationBoundary = "not observed";

    private StartupReadinessCoordinator(Context context) { this.context = context; }

    void onTrigger(Trigger trigger) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(() -> onTrigger(trigger)); return; }
        if (destroyed) return;
        long now = SystemClock.elapsedRealtime();
        long boot = bootCount();
        SharedPreferences journal = context.getSharedPreferences("startup_readiness", Context.MODE_PRIVATE);
        long seen = journal.getLong("boot_count", -1L);
        Kind kind = StartupReadinessPolicy.classify(trigger, boot, seen);
        if (boot >= 0L && boot != seen) {
            policy.resetForBoot(); journal.edit().putLong("boot_count", boot).apply();
        }
        boolean waking = screenOff && (trigger == Trigger.HOME_RESUME || trigger == Trigger.SCREEN_ON
                || trigger == Trigger.ACC_ON);
        if (trigger == Trigger.HOME_RESUME || trigger == Trigger.SCREEN_ON || trigger == Trigger.ACC_ON) {
            yielded = false;
            if (screenOff) {
                screenOff = false;
                for (MediaSourceKey key : MediaSourceKey.values()) policy.configurationChanged(source(key));
            }
        }
        if (!policy.acceptTrigger(trigger, now) && !waking) return;
        if (trigger == Trigger.HOME_RESUME) {
            journal.edit().remove("pending_boot_v1").apply();
        }
        StartupTimeline.mark("startup.kind", "trigger=" + trigger + " kind=" + kind);
        // A media failure cannot reserve a focus lane or delay Maps.
        if (navigation != null && homeResumed && !yielded && staging == null) {
            navigation.deferAutomaticPresentation(false);
            navigation.watchEvidence();
            if (!navigation.busy()) navigation.reconcile();
        }
        if ((!directPrepareTrigger(trigger) && trigger != Trigger.SESSION_LOST
                && trigger != Trigger.READINESS_CHANGED) || screenOff
                || !UiPersonalizationPrefs.mediaStartupWarmup(context)) return;
        // Application.onCreate precedes the Maps Activity host. Give its first frame a bounded
        // admission grace; an unavailable host must not indefinitely block safe media readiness.
        if (initialAdmission != null) return;
        if (!initialGraceUsed && navigation == null
                && (trigger == Trigger.RESTART || trigger == Trigger.BOOT)) {
            initialGraceUsed = true;
            initialAdmission = () -> {
                initialAdmission = null;
                for (MediaSourceKey key : MediaSourceKey.values())
                    reconcileSource(key, trigger, SystemClock.elapsedRealtime());
            };
            main.postDelayed(initialAdmission, 750L);
            return;
        }
        // Every source has independent admission/recovery. A boot journal is never a success receipt.
        for (MediaSourceKey key : MediaSourceKey.values()) reconcileSource(key, trigger, now);
    }

    private static Source source(MediaSourceKey key) { return key == MediaSourceKey.MUSIC ? Source.MUSIC : Source.RADIO; }

    private void reconcileSource(MediaSourceKey key, Trigger trigger, long now) {
        if (destroyed || screenOff || initialAdmission != null
                || !UiPersonalizationPrefs.mediaStartupWarmup(context)) return;
        String pkg = MediaEngineSelector.configuredPackage(context, key);
        if (pkg == null || pkg.isEmpty()) return;
        MediaSourceState state = Ts18LauncherApplication.mediaFacade(context).state(key);
        String identity = pkg + ":" + state.engine;
        if (!identity.equals(configured.put(key, identity))) policy.configurationChanged(source(key));
        if (ready(state)) {
            cancelRecovery(key);
            policy.observedReady(source(key), now);
            if (state.playing()) policy.active(source(key), now);
            return;
        }
        StartupReadinessPolicy.Record record = policy.record(source(key));
        if (record.state == State.READY || record.state == State.ACTIVE) policy.sessionLost(source(key));
        if (!policy.admit(source(key), trigger, now)) return;
        int generation = policy.begin(source(key), trigger, Method.BROWSER, now);
        if (Ts18LauncherApplication.legacyMediaEngineOwns(context, key == MediaSourceKey.RADIO))
            Ts18LauncherApplication.mediaSources(context).warm(pkg);
        else Ts18LauncherApplication.prepareDirectSource(context, key, ++epoch);
        cancelRecovery(key);
        Runnable check = () -> {
            recovery.remove(key);
            if (destroyed || screenOff || policy.record(source(key)).generation != generation) return;
            MediaSourceState latest = Ts18LauncherApplication.mediaFacade(context).state(key);
            if (ready(latest)) { policy.observedReady(source(key), SystemClock.elapsedRealtime()); return; }
            policy.fail(source(key), generation, "readiness not observed", SystemClock.elapsedRealtime());
            // At most three attempts in this session. Later wake/config/source recovery rearms it.
            if (policy.record(source(key)).attempts < StartupReadinessPolicy.MAX_ATTEMPTS_PER_BOOT) {
                Runnable retry = () -> { recovery.remove(key); reconcileSource(key, Trigger.READINESS_CHANGED, SystemClock.elapsedRealtime()); };
                recovery.put(key, retry);
                main.postDelayed(retry, StartupReadinessPolicy.FAILED_RETRY_SPACING_MS);
            }
        };
        recovery.put(key, check);
        main.postDelayed(check, 14000L);
        MediaEventTrace.record("startup", "prepare", key + " epoch=" + epoch + " generation=" + generation);
    }

    static boolean ready(MediaSourceState state) {
        return state != null && state.live() && !state.preparing && !state.starting
                && (state.playing() || state.nativeState == PlaybackState.STATE_PAUSED && !state.itemId.isEmpty()
                    && (state.actions & (PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PLAY_PAUSE)) != 0L);
    }

    void onMediaStateChanged(MediaSourceState state) {
        // Publisher callbacks may be synchronous during facade construction or reconciliation.
        main.post(() -> {
            if (destroyed || screenOff) return;
            MediaSourceState latest = state == null ? null : Ts18LauncherApplication.mediaFacade(context).state(state.key);
            if (state != null && !(latest.packageName + ":" + latest.engine).equals(configured.get(state.key))) {
                reconcileSource(state.key, Trigger.READINESS_CHANGED, SystemClock.elapsedRealtime()); return;
            }
            if (state != null && ready(latest)) {
                cancelRecovery(state.key);
                policy.observedReady(source(state.key), SystemClock.elapsedRealtime());
            } else if (state != null && (latest.availability == MediaSourceState.Availability.SOURCE_LOST
                    || policy.record(source(state.key)).state == State.READY
                    || policy.record(source(state.key)).state == State.ACTIVE)) {
                policy.sessionLost(source(state.key));
                reconcileSource(state.key, Trigger.SESSION_LOST, SystemClock.elapsedRealtime());
            }
        });
    }

    private void cancelRecovery(MediaSourceKey key) {
        Runnable pending = recovery.remove(key);
        if (pending != null) main.removeCallbacks(pending);
    }

    boolean prepareInteractively(Activity activity, String pkg, long deadline, java.util.function.Consumer<Boolean> done) {
        if (staging != null && staging.serves(pkg)) { staging.join(done); return true; }
        if (destroyed || screenOff || staging != null || loaderUi == null || !loaderUi.canMask()
                || !"com.navimods.radio".equals(pkg)) return false;
        staging = new MaskedSourcePreparation(activity, loaderUi, navigation, () -> staging = null);
        staging.begin(pkg, deadline, done);
        return true;
    }

    static boolean directPrepareTrigger(Trigger trigger) {
        return trigger == Trigger.BOOT || trigger == Trigger.RESTART || trigger == Trigger.USER_UNLOCKED
                || trigger == Trigger.SCREEN_ON || trigger == Trigger.ACC_ON || trigger == Trigger.HOME_RESUME
                || trigger == Trigger.STORAGE_MOUNT;
    }
    private long bootCount() {
        try { return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1); }
        catch (RuntimeException unavailable) { return -1L; }
    }
    // Legacy command fallback remains background-only and keeps its original caller deadline.
    void prepareForPlay(String pkg, long deadlineElapsedMs) {
        if (!destroyed && deadlineElapsedMs > SystemClock.elapsedRealtime()
                && pkg.equals(MediaEngineSelector.configuredPackage(context, MediaSourceKey.MUSIC)))
            Ts18LauncherApplication.mediaSources(context).warm(pkg);
    }
    void onExplicitSource(String pkg) { if (staging != null && !staging.serves(pkg)) staging.cancel(); }
    void attachNavigationHook(NavigationHook hook) { navigation = hook; }
    void detachNavigationHook(NavigationHook hook) { if (navigation == hook) navigation = null; }
    void onHomeResumed(Activity activity) {
        homeResumed = true;
        if (initialAdmission != null) { main.removeCallbacks(initialAdmission); initialAdmission = null; }
        onTrigger(Trigger.HOME_RESUME);
    }
    void onHomePaused() { homeResumed = false; }
    void onHomeFocusChanged() { }
    void onScreenOff() {
        yielded = true; screenOff = true;
        if (initialAdmission != null) { main.removeCallbacks(initialAdmission); initialAdmission = null; }
        for (MediaSourceKey key : MediaSourceKey.values()) cancelRecovery(key);
        if (staging != null) staging.cancel();
    }
    void noteUserInteraction() { StartupTimeline.mark("home.first_input"); }
    void noteExternalNavigation() {
        yielded = true;
        if (staging != null) staging.cancel();
        if (navigation != null) navigation.cancelAutomaticColdStart();
    }
    void reportNavigation(boolean success, String boundary) {
        navigationBoundary = boundary;
        if (success) StartupTimeline.mark("nav.window.verified", boundary);
    }
    String summary() {
        return "Background startup epoch=" + epoch + " navigation=" + navigationBoundary
                + "\n" + Ts18LauncherApplication.mediaFacade(context).state(MediaSourceKey.MUSIC).availability
                + " / " + Ts18LauncherApplication.mediaFacade(context).state(MediaSourceKey.RADIO).availability;
    }
    Mode mode() { return Mode.AUTOMATIC_FALLBACK; }
    void attachLoader(LoaderUi ui) { loaderUi = ui; if (ui != null) ui.hide(); }
    void detachLoader(LoaderUi ui) { if (loaderUi == ui) { if (staging != null) staging.cancel(); ui.hide(); loaderUi = null; } }
    boolean dismissLoader() { if (staging != null) { staging.cancel(); return true; } if (loaderUi != null) loaderUi.hide(); return false; }
    void destroy() { if (staging != null) staging.cancel(); destroyed = true; navigation = null; main.removeCallbacksAndMessages(null); }
}
