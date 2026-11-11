package com.cbkii.ts18launcher;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import com.cbkii.ts18launcher.StartupReadinessPolicy.*;
import com.cbkii.ts18launcher.media.MediaSourceKey;

/** Trigger classification only. Sources own readiness; navigation owns its task and focus.
 * There is no foreground preparation lane, source Activity, overlay lease or polling loop. */
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
    private long lastPrepare = -15000L;
    private long preparedBoot = Long.MIN_VALUE;
    private boolean homeResumed, yielded, destroyed;
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
        if (!policy.acceptTrigger(trigger, now)) return;
        if (trigger == Trigger.HOME_RESUME || trigger == Trigger.SCREEN_ON || trigger == Trigger.ACC_ON)
            yielded = false;
        if (trigger == Trigger.HOME_RESUME) {
            journal.edit().remove("pending_boot_v1").apply();
        }
        StartupTimeline.mark("startup.kind", "trigger=" + trigger + " kind=" + kind);
        // A media failure cannot reserve a focus lane or delay Maps.
        if (navigation != null && homeResumed && !yielded) {
            navigation.deferAutomaticPresentation(false);
            navigation.watchEvidence();
            if (!navigation.busy()) navigation.reconcile();
        }
        if (!directPrepareTrigger(trigger) || !UiPersonalizationPrefs.mediaStartupWarmup(context)) return;
        // BOOT_COMPLETED often follows the first HOME. It does not create a second epoch.
        if (trigger == Trigger.BOOT && preparedBoot == boot) return;
        boolean changedBoot = boot != preparedBoot;
        if (!changedBoot && now - lastPrepare < 15000L && trigger != Trigger.STORAGE_MOUNT) return;
        lastPrepare = now; preparedBoot = boot;
        Ts18LauncherApplication.prepareDirectSources(context, ++epoch);
        // Explicit Legacy rollback retains background browser readiness only. No staging.
        for (MediaSourceKey key : MediaSourceKey.values()) {
            if (!Ts18LauncherApplication.legacyMediaEngineOwns(context, key == MediaSourceKey.RADIO)) continue;
            String pkg = MediaEngineSelector.configuredPackage(context, key);
            if (!pkg.isEmpty()) Ts18LauncherApplication.mediaSources(context).warm(pkg);
        }
        MediaEventTrace.record("startup", "background-epoch", "epoch=" + epoch + " trigger=" + trigger);
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
    void attachNavigationHook(NavigationHook hook) { navigation = hook; }
    void detachNavigationHook(NavigationHook hook) { if (navigation == hook) navigation = null; }
    void onHomeResumed(Activity activity) { homeResumed = true; onTrigger(Trigger.HOME_RESUME); }
    void onHomePaused() { homeResumed = false; }
    void onHomeFocusChanged() { }
    void onScreenOff() { yielded = true; }
    void noteUserInteraction() { StartupTimeline.mark("home.first_input"); }
    void noteExternalNavigation() {
        yielded = true;
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
    Mode mode() { return Mode.BACKGROUND_ONLY; }
    void attachLoader(LoaderUi ui) { loaderUi = ui; if (ui != null) ui.hide(); }
    void detachLoader(LoaderUi ui) { if (loaderUi == ui) { ui.hide(); loaderUi = null; } }
    boolean dismissLoader() { if (loaderUi != null) loaderUi.hide(); return false; }
    void destroy() { destroyed = true; navigation = null; main.removeCallbacksAndMessages(null); }
}
