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
        default void deferAutomaticPresentation(boolean defer) { }
        default void refreshEvidence() { }
        default void cancelAutomaticColdStart() { }
        default void yieldForMediaPreparation() { cancelAutomaticColdStart(); }
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
    private boolean stagedForExplicitPlay;
    private int navigationWaitGeneration = -1;
    private int navigationWaitEpoch;
    private java.util.concurrent.atomic.AtomicBoolean stageLease = new java.util.concurrent.atomic.AtomicBoolean(false);
    // RESERVED=0, DISPATCHING=1, CANCELLED=2. Cancellation can release only a proven
    // unadmitted journal; reading a boolean before a root worker starts is insufficient.
    private java.util.concurrent.atomic.AtomicInteger stageAdmission = new java.util.concurrent.atomic.AtomicInteger(2);
    private final StartupRootFallback rootFallback;
    private String lastMediaObservation = "";
    private boolean foregroundScheduled;
    private long interactionUntil;
    private boolean homeHasReceivedFocus;
    private long initialHomeFocusUntil;
    private long explicitMusicPreparationUntil;

    private StartupReadinessCoordinator(Context context) {
        this.context = context.getApplicationContext();
        rootFallback = new StartupRootFallback(this.context);
        ProcessMediaSessionMonitor.addObserver(this.context, this);
    }
    private MediaSourceBootstrapper media() {
        MediaSourceBootstrapper owner = Ts18LauncherApplication.mediaSources(context);
        owner.setStartupObserver(this::onMediaReadinessChanged);
        owner.setPreparationRequester(this::prepareForPlay);
        return owner;
    }

    /** Join the existing owner; a user Play may prepare even when passive warm-up is disabled. */
    void prepareForPlay(String pkg, long deadlineElapsedMs) {
        if (destroyed || !MediaSourceAdapter.AUXIO_PACKAGE.equals(pkg)
                || !pkg.equals(packageFor(Source.MUSIC))
                || deadlineElapsedMs <= SystemClock.elapsedRealtime()) return;
        explicitMusicPreparationUntil = deadlineElapsedMs;
        yielded = false;
        StartupReadinessPolicy.Record r = policy.record(Source.MUSIC);
        if (ready(Source.MUSIC, pkg) || r.activityLaunchAccepted) return;
        if (r.state != State.STARTING) {
            packages[Source.MUSIC.ordinal()] = pkg;
            policy.begin(Source.MUSIC, Trigger.READINESS_CHANGED, Method.ACTIVITY_BOOTSTRAP,
                    SystemClock.uptimeMillis());
        }
        if (!foregroundQueue.contains(Source.MUSIC) && foregroundSource != Source.MUSIC)
            foregroundQueue.addFirst(Source.MUSIC);
        if (navigation != null && navigation.busy()) navigation.yieldForMediaPreparation();
        updateNavigationAdmission();
        runForegroundSoon();
    }

    private boolean explicitMusicPreparation() {
        // Once admitted, finish the bounded HOME-return transaction even if Play expires.
        return SystemClock.elapsedRealtime() < explicitMusicPreparationUntil
                || explicitMusicPreparationUntil > 0L && foregroundBusy && foregroundSource == Source.MUSIC;
    }

    void onTrigger(Trigger trigger) {
        if (Looper.myLooper() != Looper.getMainLooper()) { handler.post(() -> onTrigger(trigger)); return; }
        if (destroyed) return;
        if (navigation != null && (trigger == Trigger.HOME_RESUME || trigger == Trigger.SCREEN_ON
                || trigger == Trigger.ACC_ON)) navigation.refreshEvidence();
        long now = SystemClock.uptimeMillis(), boot = bootCount();
        SharedPreferences prefs = context.getSharedPreferences("startup_readiness", Context.MODE_PRIVATE);
        long seen = prefs.getLong("boot_count", -1L);
        int pendingBoot = prefs.getInt("pending_boot_v1", -1);
        if (trigger == Trigger.HOME_RESUME && pendingBoot >= 0) {
            prefs.edit().remove("pending_boot_v1").apply();
            MediaEventTrace.record("startup", "durable-boot-reconcile", "pending=" + pendingBoot + " current=" + boot);
        }
        Kind kind = StartupReadinessPolicy.classify(trigger, boot, seen);
        // BOOT_COMPLETED can arrive after process/HOME startup: never reset active admissions twice.
        if (boot >= 0 && boot != seen) {
            cancelForeground("new-boot"); policy.resetForBoot(); yielded = false;
            prefs.edit().putLong("boot_count", boot).apply();
        }
        boolean resumedYield = yielded && (trigger == Trigger.SCREEN_ON || trigger == Trigger.ACC_ON || trigger == Trigger.HOME_RESUME);
        if (trigger == Trigger.SCREEN_ON || trigger == Trigger.ACC_ON || trigger == Trigger.HOME_RESUME) yielded = false;
        if (trigger != Trigger.SESSION_LOST && trigger != Trigger.READINESS_CHANGED && !policy.acceptTrigger(trigger, now) && !resumedYield) {
            updateNavigationAdmission(); runForegroundSoon(); return;
        }
        MediaEventTrace.record("startup", "trigger", trigger + " kind=" + kind + " mode=" + mode()
                + (trigger == Trigger.SCREEN_ON ? " acc=unverified" : ""));
        if (!UiPersonalizationPrefs.mediaStartupWarmup(context) && !explicitMusicPreparation())
            cancelForeground("media-disabled-by-preference");
        for (Source source : Source.values()) {
            if (source != Source.NAVIGATION && !UiPersonalizationPrefs.mediaStartupWarmup(context)
                    && !(source == Source.MUSIC && explicitMusicPreparation())) continue;
            String pkg = packageFor(source);
            int index = source.ordinal();
            if (!pkg.equals(packages[index])) {
                if (foregroundSource == source) cancelForeground("source-changed");
                foregroundQueue.remove(source); policy.configurationChanged(source); packages[index] = pkg;
            }
            if (pkg.isEmpty()) continue;
            if (releasingAdmissions.contains(source)) continue;
            if (foregroundSource == source) continue;
            if (source != Source.NAVIGATION) restoreMediaAdmission(source, pkg);
            if (ready(source, pkg)) {
                policy.observedReady(source, now);
                if (source != Source.NAVIGATION) noteMediaReady(source, pkg);
                continue;
            }
            State state = policy.record(source).state;
            if (state == State.READY || state == State.ACTIVE) {
                // Metadata/queue churn is not session death and must not trigger Activity replay.
                if (source != Source.NAVIGATION && !media().startupSessionAbsent(pkg)) {
                    policy.observedUnavailable(source, "session-present-content-not-ready", now);
                    continue;
                }
                policy.sessionLost(source);
            }
            if (!policy.admit(source, trigger, now)) continue;
            int g = policy.begin(source, trigger, source == Source.NAVIGATION ? Method.TASK_ADOPTION : Method.SESSION, now);
            if (source == Source.NAVIGATION) {
                // No second Maps launch, fixed Splash dwell, or parallel root command.
                // Start its observation deadline only after media foreground admission settles.
                // Otherwise the compact Maps task takes focus and starves media staging.
            } else {
                if (source == Source.MUSIC) {
                    policy.changeMethod(source, Method.BROWSER); media().warm(pkg);
                    if (foregroundAllowed() && MediaSourceAdapter.AUXIO_PACKAGE.equals(pkg)
                            && !policy.record(source).activityLaunchAccepted) {
                        policy.changeMethod(source, Method.ACTIVITY_BOOTSTRAP);
                        if (!foregroundQueue.contains(source)) foregroundQueue.addFirst(source);
                    }
                }
                await(source, g, pkg, now + (source == Source.MUSIC ? 5000L : 2500L));
            }
        }
        updateNavigationAdmission();
        runForegroundSoon();
    }

    private void updateNavigationAdmission() {
        if (navigation == null) return;
        // The native Maps helper takes focus. Reserve the lane before presenting Maps when
        // a qualified source may need a bounded Activity stage after its background deadline.
        // Read-only preparation stays concurrent; unqualified/disabled sources hold no lease.
        boolean defer = foregroundBusy || needsForegroundReservation();
        navigation.deferAutomaticPresentation(defer);
        if (defer) {
            // A media Activity owns the lane. Invalidate the old wait, then give navigation
            // its full observation budget when foreground staging releases it.
            navigationWaitGeneration = -1;
            navigationWaitEpoch++;
        }
        StartupReadinessPolicy.Record r = policy.record(Source.NAVIGATION);
        if (!defer && !yielded && r.state == State.STARTING && navigationWaitGeneration != r.generation) {
            navigationWaitGeneration = r.generation;
            navigation.reconcile();
            await(Source.NAVIGATION, r.generation, packageFor(Source.NAVIGATION), SystemClock.uptimeMillis() + 18000L);
        }
    }

    private boolean needsForegroundReservation() {
        Activity activity = home.get();
        if (!foregroundAllowed() || !safeScreen() || !HomeMode.isDefaultHome(context)) return false;
        if (!releasingAdmissions.isEmpty()) return true;
        // Reserve before Activity.onResume renders the native panel. Application's resumed
        // callback can arrive afterwards; waiting for it races the Maps cold admission.
        if (activity == null && foregroundQueue.contains(Source.MUSIC)
                && policy.record(Source.MUSIC).state == State.STARTING
                && !policy.record(Source.MUSIC).activityLaunchAccepted) return true;
        if (!homeResumed || activity == null || activity.isFinishing()) return false;
        if (!activity.hasWindowFocus()) {
            // onResume can precede a new HOME's first focus callback. Retain the already
            // reserved Music turn across that gap, without authorising any Activity launch.
            // A lost focus after the first callback, or a missing callback past the bound,
            // is not an eligible media owner and must release Maps.
            return !homeHasReceivedFocus && SystemClock.uptimeMillis() < initialHomeFocusUntil
                    && foregroundQueue.contains(Source.MUSIC)
                    && policy.record(Source.MUSIC).state == State.STARTING
                    && !policy.record(Source.MUSIC).activityLaunchAccepted;
        }
        for (Source source : new Source[] {Source.RADIO, Source.MUSIC}) {
            if (source == Source.RADIO && navigation != null && !navigation.ready()) continue;
            String pkg = packageFor(source);
            if (policy.record(source).state != State.STARTING || policy.record(source).activityLaunchAccepted
                    || ready(source, pkg)) continue;
            if (MediaSourceAdapter.AUXIO_PACKAGE.equals(pkg)
                    || MediaSourceAdapter.NAVRADIO_PACKAGE.equals(pkg)
                        && MediaListenerService.hasNotificationAccess(context)) return true;
        }
        return false;
    }

    void attachNavigationHook(NavigationHook hook) { navigation = hook; updateNavigationAdmission(); }
    void detachNavigationHook(NavigationHook hook) { if (navigation == hook) navigation = null; }
    void onHomeResumed(Activity activity) {
        if (home.get() != activity) {
            homeHasReceivedFocus = activity.hasWindowFocus();
            initialHomeFocusUntil = SystemClock.uptimeMillis() + 1000L;
            handler.postDelayed(() -> {
                if (!destroyed && home.get() == activity) {
                    updateNavigationAdmission(); runForegroundSoon();
                }
            }, 1000L);
        } else if (activity.hasWindowFocus()) homeHasReceivedFocus = true;
        home = new WeakReference<>(activity); homeResumed = true; onTrigger(Trigger.HOME_RESUME);
    }
    void onHomePaused() { homeResumed = false; updateNavigationAdmission(); }
    void onHomeFocusChanged() {
        Activity activity = home.get();
        if (activity != null && activity.hasWindowFocus()) homeHasReceivedFocus = true;
        updateNavigationAdmission(); runForegroundSoon();
    }
    void onScreenOff() { yielded = true; cancelForeground("screen-off"); }
    void noteUserInteraction() {
        // A transport/drawer tap is not permission to disable Maps for the rest of the boot.
        interactionUntil = SystemClock.uptimeMillis() + 300L;
        runForegroundSoon();
    }
    void noteExternalNavigation() {
        explicitMusicPreparationUntil = 0L;
        yielded = true;
        if (navigation != null) navigation.cancelAutomaticColdStart();
        cancelForeground("external-navigation");
    }
    void reportNavigation(boolean success, String boundary) {
        recordResultNavigation(success, boundary);
    }
    private void recordResultNavigation(boolean success, String boundary) {
        StartupReadinessPolicy.Record r = policy.record(Source.NAVIGATION);
        if (success) policy.observedReady(Source.NAVIGATION, SystemClock.uptimeMillis());
        else {
            if (r.state == State.READY || r.state == State.ACTIVE)
                policy.observedUnavailable(Source.NAVIGATION, boundary, SystemClock.uptimeMillis());
            else policy.block(Source.NAVIGATION, r.generation, boundary, SystemClock.uptimeMillis());
        }
        MediaEventTrace.record("startup", "navigation", summary());
        runForegroundSoon();
    }
    String summary() { return policy.summary(SystemClock.uptimeMillis()); }
    Mode mode() { return StartupReadinessPolicy.parseMode(TestingProfiles.startup(context)); }

    @Override public void onProcessMediaStateChanged(MediaListenerService.Snapshot music,
            MediaListenerService.Snapshot radio, boolean authoritative) {
        onMediaReadinessChanged();
    }

    private void onMediaReadinessChanged() {
        if (destroyed) return;
        String music = packageFor(Source.MUSIC), radio = packageFor(Source.RADIO);
        String observed = music + ":" + ready(Source.MUSIC, music) + ":" + media().startupSessionAbsent(music)
                + "/" + radio + ":" + ready(Source.RADIO, radio) + ":" + media().startupSessionAbsent(radio);
        if (observed.equals(lastMediaObservation)) return;
        lastMediaObservation = observed;
        onTrigger(Trigger.READINESS_CHANGED);
    }

    private boolean ready(Source source, String pkg) {
        if (source == Source.NAVIGATION) return navigation != null && navigation.ready();
        // Queue/station content and Play capability, never a token alone or audible-output claim.
        return media().hasUsableQueue(pkg);
    }
    private void await(Source source, int g, String pkg, long deadline) {
        final int waitEpoch = navigationWaitEpoch;
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (source == Source.NAVIGATION && waitEpoch != navigationWaitEpoch) return;
                StartupReadinessPolicy.Record r = policy.record(source);
                if (destroyed || r.generation != g || r.state != State.STARTING || !pkg.equals(packageFor(source))) return;
                long now = SystemClock.uptimeMillis();
                if (ready(source, pkg)) {
                    policy.ready(source, g, now);
                    if (source != Source.NAVIGATION) noteMediaReady(source, pkg);
                    MediaEventTrace.record("startup", "ready", summary()); updateNavigationAdmission(); return;
                }
                if (now < deadline) { handler.postDelayed(this, 500L); return; }
                if (source == Source.NAVIGATION) {
                    policy.uncertain(source, g, "compact-task-not-verified", now); return;
                }
                boolean qualifiedPackage = MediaSourceAdapter.AUXIO_PACKAGE.equals(pkg)
                        || MediaSourceAdapter.NAVRADIO_PACKAGE.equals(pkg);
                if (foregroundAllowed() && qualifiedPackage && !r.activityLaunchAccepted) {
                    policy.changeMethod(source, Method.ACTIVITY_BOOTSTRAP);
                    if (!foregroundQueue.contains(source)) foregroundQueue.add(source);
                    runForegroundSoon();
                    handler.postDelayed(() -> {
                        if (policy.record(source).generation == g && foregroundQueue.remove(source)) policy.block(source, g, "foreground-admission-expired", SystemClock.uptimeMillis());
                        updateNavigationAdmission();
                    }, 20000L);
                } else {
                    policy.block(source, g, "background-not-ready-or-staging-yielded", now);
                    updateNavigationAdmission();
                }
            }
        }, 500L);
    }

    private boolean foregroundAllowed() {
        return !yielded && (explicitMusicPreparation()
                || StartupReadinessPolicy.foregroundAllowed(mode())
                    && UiPersonalizationPrefs.mediaStartupWarmup(context));
    }
    private void runForegroundSoon() {
        if (destroyed || foregroundScheduled) return;
        foregroundScheduled = true;
        handler.postDelayed(() -> { foregroundScheduled = false; runForeground(); },
                Math.max(0L, interactionUntil - SystemClock.uptimeMillis()));
    }
    private void runForeground() {
        if (destroyed || foregroundBusy || foregroundQueue.isEmpty() || !homeResumed) return;
        if (SystemClock.uptimeMillis() < interactionUntil) { runForegroundSoon(); return; }
        Activity activity = home.get();
        if (!foregroundAllowed() || !safeScreen() || activity == null || activity.isFinishing()
                || !activity.hasWindowFocus() || !HomeMode.isDefaultHome(context)) return;
        if (navigation != null && navigation.busy()) {
            handler.postDelayed(this::runForegroundSoon, 100L); return;
        }
        Source source = foregroundQueue.poll();
        if (source == Source.RADIO && navigation != null && !navigation.ready()) {
            foregroundQueue.addLast(source); updateNavigationAdmission(); return;
        }
        StartupReadinessPolicy.Record r = policy.record(source);
        if (r.state != State.STARTING) { updateNavigationAdmission(); runForegroundSoon(); return; }
        String pkg = packageFor(source);
        if (ready(source, pkg)) { policy.ready(source, r.generation, SystemClock.uptimeMillis()); updateNavigationAdmission(); runForegroundSoon(); return; }
        String opposite = packageFor(source == Source.MUSIC ? Source.RADIO : Source.MUSIC);
        if (source == Source.RADIO && !MediaListenerService.hasNotificationAccess(context)) {
            policy.block(source, r.generation, "notification-access-required", SystemClock.uptimeMillis());
            updateNavigationAdmission(); runForegroundSoon(); return;
        }
        if (media().startupPlaying(pkg) || media().startupPlaying(opposite)
                && !(source == Source.MUSIC && explicitMusicPreparation())) {
            policy.block(source, r.generation, "playing-source-protected", SystemClock.uptimeMillis()); updateNavigationAdmission(); runForegroundSoon(); return;
        }
        if (source == Source.MUSIC && MediaSourceAdapter.AUXIO_PACKAGE.equals(pkg)
                && !AuxioPreparationContract.supported(context)) {
            policy.block(source, r.generation, "auxio-prepare-capability-required", SystemClock.uptimeMillis());
            updateNavigationAdmission(); runForegroundSoon(); return;
        }
        final Intent entry;
        try { entry = context.getPackageManager().getLaunchIntentForPackage(pkg); }
        catch (RuntimeException unknown) {
            policy.block(source, r.generation, "entry-lookup-unavailable", SystemClock.uptimeMillis());
            updateNavigationAdmission(); runForegroundSoon(); return;
        }
        if (entry == null || entry.getComponent() == null) {
            policy.block(source, r.generation, "entry-unavailable", SystemClock.uptimeMillis()); updateNavigationAdmission(); runForegroundSoon(); return;
        }
        foregroundBusy = true; foregroundSource = source;
        updateNavigationAdmission();
        stageLease = new java.util.concurrent.atomic.AtomicBoolean(true);
        stageAdmission = new java.util.concurrent.atomic.AtomicInteger(0);
        int token = ++foregroundToken, g = r.generation;
        homeTask = activity.getTaskId(); stagedTask = -1;
        stagedEntry = AuxioPreparationContract.canonicalEntry(context, entry.getComponent());
        if (stagedEntry.isEmpty()) {
            yieldStage(source, r.generation, "entry-target-unresolved"); return;
        }
        stagedCommands = media().userCommandGeneration(); pauseSent = false;
        stagedForExplicitPlay = source == Source.MUSIC && explicitMusicPreparation()
                && pkg.equals(media().userCommandPackage());
        claimMediaAdmission(source, pkg, token, () -> observe(token, top -> {
            if (!homeResumed || !foregroundAllowed() || !safeScreen() || top == null
                    || !(top.matches(AndroidUserId.current(), homeTask, context.getPackageName() + "/.LauncherActivity")
                            || top.matches(AndroidUserId.current(), homeTask, context.getPackageName() + "/.HomeAlias"))) {
                yieldStage(source, g, "home-ownership-unconfirmed"); return;
            }
            if (source == Source.RADIO && !MediaListenerService.hasNotificationAccess(context)) {
                yieldStage(source, g, "notification-access-lost"); return;
            }
            if (media().isPlaying(pkg) || media().isPlaying(opposite)
                    && !(source == Source.MUSIC && explicitMusicPreparation())) {
                yieldStage(source, g, "playing-source-arrived"); return;
            }
            if (!policy.markActivityLaunch(source)) { yieldStage(source, g, "launch-already-accepted"); return; }
            MediaEventTrace.record("startup", "foreground-launch", source + " " + stagedEntry);
            if (source == Source.MUSIC && MediaSourceAdapter.AUXIO_PACKAGE.equals(pkg))
                startWithRoot(source, g, pkg, token, false);
            else if (mode() == Mode.AGGRESSIVE) startWithRoot(source, g, pkg, token, true);
            else startNormally(source, g, pkg, token, true);
        }));
    }

    private String mediaJournalPrefix(String pkg) {
        long boot = bootCount();
        return boot < 0 ? "" : boot + ":" + AndroidUserId.current() + ":" + pkg + ":";
    }
    private String mediaJournalKey(Source source) { return "media_admission_v1_" + source.name(); }
    private void restoreMediaAdmission(Source source, String pkg) {
        SharedPreferences prefs = context.getSharedPreferences("startup_readiness", Context.MODE_PRIVATE);
        String prefix = mediaJournalPrefix(pkg);
        String value = prefs.getString(mediaJournalKey(source), "");
        boolean claimed = !prefix.isEmpty() && value.startsWith(prefix);
        if (claimed && value.equals(prefix + "ready") && media().startupSessionAbsent(pkg)) {
            // A previously usable session is now authoritatively absent: legitimate source recovery.
            prefs.edit().remove(mediaJournalKey(source)).apply(); claimed = false;
            policy.sessionLost(source);
        }
        if (claimed || prefix.isEmpty()) policy.record(source).activityLaunchAccepted = true;
    }
    private void noteMediaReady(Source source, String pkg) {
        SharedPreferences prefs = context.getSharedPreferences("startup_readiness", Context.MODE_PRIVATE);
        String prefix = mediaJournalPrefix(pkg);
        if (!prefix.isEmpty() && policy.record(source).activityLaunchAccepted
                && !prefs.getString(mediaJournalKey(source), "").equals(prefix + "ready"))
            prefs.edit().putString(mediaJournalKey(source), prefix + "ready").apply();
    }
    private void claimMediaAdmission(Source source, String pkg, int token, Runnable admitted) {
        String prefix = mediaJournalPrefix(pkg);
        if (prefix.isEmpty() || policy.record(source).activityLaunchAccepted) {
            yieldStage(source, policy.record(source).generation, "media-admission-retained"); return;
        }
        final java.util.concurrent.atomic.AtomicBoolean lease = stageLease;
        observer.execute(() -> {
            if (!lease.get()) return;
            boolean saved = false;
            try {
                SharedPreferences prefs = context.getSharedPreferences("startup_readiness", Context.MODE_PRIVATE);
                // Commit before dispatch, off-main. Uncertain persistence never admits an Activity.
                saved = !prefs.getString(mediaJournalKey(source), "").startsWith(prefix)
                        && prefs.edit().putString(mediaJournalKey(source), prefix + "claimed").commit();
            } catch (RuntimeException unavailable) { /* fail closed */ }
            final boolean accepted = saved;
            handler.post(() -> {
                if (destroyed || token != foregroundToken || !lease.get()) return;
                if (accepted) admitted.run();
                else yieldStage(source, policy.record(source).generation, "media-journal-unavailable");
            });
        });
    }

    private void startNormally(Source source, int g, String pkg, int token, boolean allowRoot) {
        if (!validStage(token) || !homeResumed) { yieldStage(source, g, "normal-admission-cancelled"); return; }
        if (!stageAdmission.compareAndSet(0, 1)) return;
        AppResolver.LaunchOutcome outcome = AppResolver.launchForStartup(context, pkg);
        if (outcome == AppResolver.LaunchOutcome.REJECTED) stageAdmission.set(0);
        MediaEventTrace.record("startup", "normal-admission", source + " " + outcome);
        if (outcome == AppResolver.LaunchOutcome.ACCEPTED) observeSourceAfterLaunch(source, g, pkg, token);
        else if (outcome == AppResolver.LaunchOutcome.REJECTED && allowRoot) {
            stageAdmission.set(0); startWithRoot(source, g, pkg, token, false);
        }
        else yieldStage(source, g, "normal-launch-" + outcome);
    }
    private void startWithRoot(Source source, int g, String pkg, int token, boolean allowNormal) {
        final java.util.concurrent.atomic.AtomicBoolean lease = stageLease;
        final java.util.concurrent.atomic.AtomicInteger admission = stageAdmission;
        final String entry = stagedEntry;
        final int targetHome = homeTask;
        observer.execute(() -> {
            if (!lease.get() || !admission.compareAndSet(0, 1)) return;
            NavigationHelperResult result = rootFallback.launch(pkg, entry, targetHome);
            handler.post(() -> {
                if (token != foregroundToken || !lease.get()) return;
                if (StartupRootFallback.definitelyNotStarted(result)) admission.set(0);
                MediaEventTrace.record("startup", "root-admission", source + " " + result.code);
                if (result.success && "STARTUP_SOURCE_DISPATCHED".equals(result.code))
                    observeSourceAfterLaunch(source, g, pkg, token);
                else if (allowNormal && StartupRootFallback.definitelyNotStarted(result) && validStage(token)) {
                    // A different caller is safe only after proof that no root Activity was admitted.
                    observe(token, top -> {
                        if (!homeResumed || top == null || !top.matches(AndroidUserId.current(), targetHome,
                                context.getPackageName() + "/.LauncherActivity")) {
                            yieldStage(source, g, "normal-fallback-owner-changed"); return;
                        }
                        startNormally(source, g, pkg, token, false);
                    });
                } else yieldStage(source, g, "root-launch-" + result.code);
            });
        });
    }
    private void observeSourceAfterLaunch(Source source, int g, String pkg, int token) {
        awaitSourceEntry(source, g, pkg, token, SystemClock.uptimeMillis() + 4000L);
    }
    private void awaitSourceEntry(Source source, int g, String pkg, int token, long deadline) {
        handler.postDelayed(() -> observe(token, owner -> {
            if (!validStage(token)) {
                yieldStage(source, g, "source-entry-interrupted"); return;
            }
            if (owner == null || !owner.isEntry(AndroidUserId.current(), stagedEntry)) {
                // An accepted launch can still be between the NoDisplay entry and MainActivity.
                // Observe only: never replay it. A known foreign/permission owner yields at once.
                boolean waitingAtHome = owner != null && owner.matches(AndroidUserId.current(),
                        homeTask, context.getPackageName() + "/.LauncherActivity");
                if ((owner == null || waitingAtHome) && SystemClock.uptimeMillis() < deadline) {
                    awaitSourceEntry(source, g, pkg, token, deadline); return;
                }
                yieldStage(source, g, "source-entry-not-foreground"); return;
            }
            stagedTask = owner.task;
            if (source == Source.MUSIC && MediaSourceAdapter.AUXIO_PACKAGE.equals(pkg))
                finishStage(source, g, pkg, token);
            else awaitStage(source, g, pkg, token, SystemClock.uptimeMillis() + 8000L);
        }), 150L);
    }
    private boolean validStage(int token) {
        return token == foregroundToken && foregroundAllowed() && safeScreen()
                && foregroundSource != null
                && (foregroundSource != Source.RADIO || MediaListenerService.hasNotificationAccess(context))
                && packageFor(foregroundSource).equals(packages[foregroundSource.ordinal()])
                && (media().userCommandGeneration() == stagedCommands
                    || foregroundSource == Source.MUSIC && packageFor(Source.MUSIC).equals(media().userCommandPackage()));
    }
    private void awaitStage(Source source, int g, String pkg, int token, long deadline) {
        if (token != foregroundToken) return;
        if (!validStage(token)) { yieldStage(source, g, "user-or-system-interruption"); return; }
        // Do not pause a newly observed player without rechecking foreground ownership first.
        if (ready(source, pkg) || media().startupPlaying(pkg) || SystemClock.uptimeMillis() >= deadline) {
            observe(token, top -> {
                if (!validStage(token) || top == null || !top.matches(AndroidUserId.current(), stagedTask, stagedEntry)) {
                    yieldStage(source, g, "foreground-owner-changed"); return;
                }
                if (!pauseSent && !stagedForExplicitPlay && media().startupPlaying(pkg)) {
                    pauseSent = media().pauseUnrequestedPlayback(pkg, stagedCommands);
                    awaitSilence(source, g, pkg, token, SystemClock.uptimeMillis() + 1600L);
                } else finishStage(source, g, pkg, token);
            });
        } else handler.postDelayed(() -> awaitStage(source, g, pkg, token, deadline), 500L);
    }
    private void awaitSilence(Source source, int g, String pkg, int token, long deadline) {
        if (token != foregroundToken) return;
        if (!validStage(token)) { yieldStage(source, g, "silence-check-interrupted"); return; }
        if (media().startupPlaying(pkg) && SystemClock.uptimeMillis() < deadline) {
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
            // Restore the exact existing HOME task. No root am-start or component relaunch.
            Activity activity = home.get();
            if (activity == null || activity.getTaskId() != homeTask || activity.isFinishing()) {
                yieldStage(source, g, "home-task-gone"); return;
            }
            if (source == Source.MUSIC && MediaSourceAdapter.AUXIO_PACKAGE.equals(pkg)) {
                restoreHomeWithRoot(source, g, pkg, token); return;
            }
            try {
                android.app.ActivityManager manager = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                manager.moveTaskToFront(homeTask, 0);
            } catch (SecurityException rejected) {
                restoreHomeWithRoot(source, g, pkg, token); return;
            } catch (RuntimeException uncertain) { yieldStage(source, g, "home-return-outcome-unconfirmed"); return; }
            verifyHomeReturn(source, g, token);

        });
    }
    private void restoreHomeWithRoot(Source source, int g, String pkg, int token) {
        final java.util.concurrent.atomic.AtomicBoolean lease = stageLease;
        final String entry = stagedEntry;
        final int sourceTask = stagedTask, targetHome = homeTask;
        observer.execute(() -> {
            if (!lease.get()) return;
            NavigationHelperResult result = rootFallback.restoreHome(pkg, entry, sourceTask, targetHome);
            handler.post(() -> {
                if (token != foregroundToken || !lease.get()) return;
                MediaEventTrace.record("startup", "root-home", result.code);
                if (result.success && "STARTUP_HOME_RESTORED".equals(result.code)) verifyHomeReturn(source, g, token);
                else yieldStage(source, g, "root-home-" + result.code);
            });
        });
    }
    private void verifyHomeReturn(Source source, int g, int token) {
        handler.postDelayed(() -> {
            if (token != foregroundToken) return;
            Activity activity = home.get();
            if (!homeResumed || activity == null || activity.getTaskId() != homeTask || !activity.hasWindowFocus()) {
                yieldStage(source, g, "home-return-unconfirmed"); return;
            }
            String pkg = packageFor(source);
            if (ready(source, pkg) && (!media().isPlaying(pkg)
                    || stagedForExplicitPlay
                    || media().userCommandGeneration() != stagedCommands && pkg.equals(media().userCommandPackage()))) {
                policy.ready(source, g, SystemClock.uptimeMillis()); noteMediaReady(source, pkg);
                if (media().isPlaying(pkg)) policy.active(source, SystemClock.uptimeMillis());
            }
            else policy.uncertain(source, g, "content-or-silence-unconfirmed", SystemClock.uptimeMillis());
            stageLease.set(false);
            foregroundBusy = false; foregroundSource = null; updateNavigationAdmission(); runForegroundSoon();
        }, 150L);
    }
    private interface Observation { void accept(StartupForegroundPolicy top); }
    private void observe(int token, Observation callback) { observe(token, callback, 2); }
    private void observe(int token, Observation callback, int attempts) {
        observer.execute(() -> {
            NavigationHelperResult fast = rootFallback.observe();
            StartupForegroundPolicy top;
            if (fast != null) top = StartupForegroundPolicy.fromHelper(fast);
            else {
                RootShell.Result result = RootShell.runWithin(
                    "dumpsys activity activities | /system/bin/toybox grep 'mResumedActivity:'; "
                    + "dumpsys window displays | /system/bin/toybox grep 'mCurrentFocus='", 1800L);
                top = result.success() ? StartupForegroundPolicy.parse(result.output) : null;
            }
            final StartupForegroundPolicy observed = top;
            handler.post(() -> {
                if (token != foregroundToken || destroyed) return;
                if (observed == null && attempts > 1) handler.postDelayed(() -> observe(token, callback, attempts - 1), 200L);
                else callback.accept(observed);
            });
        });
    }
    private void yieldStage(Source source, int g, String boundary) {
        policy.uncertain(source, g, boundary, SystemClock.uptimeMillis());
        yielded = true; cancelForeground(boundary);
    }
    private void cancelForeground(String boundary) {
        stageLease.set(false);
        int admission = stageAdmission.getAndSet(2);
        foregroundToken++; foregroundBusy = false;
        long now = SystemClock.uptimeMillis();
        if (foregroundSource != null) {
            StartupReadinessPolicy.Record r = policy.record(foregroundSource);
            policy.uncertain(foregroundSource, r.generation, boundary, now);
            if (admission == 0) releaseUnadmittedJournal(foregroundSource, r.generation, packageFor(foregroundSource));
        }
        foregroundSource = null;
        while (!foregroundQueue.isEmpty()) {
            Source s = foregroundQueue.poll(); policy.block(s, policy.record(s).generation, boundary, now);
        }
        updateNavigationAdmission();
        MediaEventTrace.record("startup", "yield", boundary);
    }
    private final java.util.Set<Source> releasingAdmissions = java.util.EnumSet.noneOf(Source.class);
    private void releaseUnadmittedJournal(Source source, int generation, String pkg) {
        String prefix = mediaJournalPrefix(pkg);
        if (prefix.isEmpty()) return;
        releasingAdmissions.add(source);
        try {
            observer.execute(() -> {
                SharedPreferences prefs = context.getSharedPreferences("startup_readiness", Context.MODE_PRIVATE);
                boolean released = !prefs.getString(mediaJournalKey(source), "").equals(prefix + "claimed")
                        || prefs.edit().remove(mediaJournalKey(source)).commit();
                handler.post(() -> {
                    releasingAdmissions.remove(source);
                    if (destroyed || policy.record(source).generation != generation) return;
                    if (released) policy.releaseUnadmitted(source, generation);
                    if (homeResumed && !yielded) onTrigger(Trigger.READINESS_CHANGED);
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException unavailable) {
            releasingAdmissions.remove(source);
            MediaEventTrace.record("startup", "unadmitted-journal-cleanup-deferred");
        }
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
        ProcessMediaSessionMonitor.removeObserver(this); media().setStartupObserver(() -> { }); observer.shutdownNow();
    }
}
