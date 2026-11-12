package com.cbkii.ts18launcher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextClock;

import com.cbkii.ts18launcher.media.MediaEngine;
import com.cbkii.ts18launcher.media.MediaFeatureFacade;
import com.cbkii.ts18launcher.media.MediaSourceKey;
import com.cbkii.ts18launcher.media.MediaSourceState;
import com.cbkii.ts18launcher.platform.TopwayAdapter;

import java.util.ArrayList;
import java.util.List;

@SuppressLint("SetTextI18n")
public class LauncherActivity extends Activity implements MediaListenerService.Observer,
        ProcessMediaSessionMonitor.Observer {
    private static final int MAX_QUICK_SLOTS = 6;
    private static final long MEDIA_STATUS_MS = 1800L;
    private static final long MEDIA_READY_RECONCILE_DELAY_MS = 250L;

    private FrameLayout root;
    private final Ts18Geometry.ChangeTracker geometryChanges = new Ts18Geometry.ChangeTracker();
    private final int[] rootWindowLocation = new int[2];
    private boolean geometryUpdatePending;
    private final Runnable geometryUpdate = () -> {
        geometryUpdatePending = false;
        if (root != null && !isDestroyed()) applyGeometry(root.getWidth(), root.getHeight());
    };
    private LinearLayout rail;
    private LinearLayout quickRail;
    private ImageButton appsButton;
    private ImageButton navigationButton;
    private LinearLayout radioPanel;
    private LinearLayout musicPanel;
    private TextClock dateView;
    private ImageButton radioRole;
    private ImageButton musicRole;
    private MediaSelection mediaSelection;
    private MediaMetadataView mediaText;
    private ImageButton radioPrevious;
    private ImageButton radioPlayPause;
    private ImageButton radioNext;
    private ImageButton mediaPrevious;
    private ImageButton playPause;
    private ImageButton mediaNext;
    private final ImageButton[] quickButtons = new ImageButton[MAX_QUICK_SLOTS];
    private final FrameLayout[] quickCells = new FrameLayout[MAX_QUICK_SLOTS];
    private final Handler mediaRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable mediaReadyReconcile = this::runMediaReadiness;
    private NativeNavigationPanel nativeNavigationPanel;
    private NavigationWindowController navigationWindowController;
    private AppDrawerPanel appDrawerPanel;
    private AppearanceController appearanceController;
    private MediaSourceBootstrapper mediaBootstrapper;
    private StartupReadinessCoordinator.NavigationHook startupHook;
    private StartupReadinessLoader readinessLoader;
    private StartupReadinessCoordinator.LoaderUi loaderUi;
    private boolean launchedAsHome;
    private boolean redirectingToHome;
    private boolean mediaStatusActive;
    private int mediaStatusGeneration;
    private String mediaRadioPackage = "";
    private String mediaMusicPackage = "";
    private volatile MediaListenerService.Snapshot listenerGenericSnapshot = emptyMediaSnapshot();
    private volatile MediaListenerService.Snapshot listenerRadioSnapshot = emptyMediaSnapshot();
    private volatile MediaListenerService.Snapshot processGenericSnapshot = emptyMediaSnapshot();
    private volatile MediaListenerService.Snapshot processRadioSnapshot = emptyMediaSnapshot();
    private volatile boolean processSnapshotAuthoritative;
    /** Last failed direct-engine action, shown briefly in place of metadata. */
    private String directFailureText = "";
    private MediaSourceKey directFailureKey;
    private MediaListenerService.Snapshot genericSnapshot = emptyMediaSnapshot();
    private MediaListenerService.Snapshot radioSnapshot = emptyMediaSnapshot();

    @Override protected void onCreate(Bundle state) {
        StartupTimeline.mark("home.create");
        setTheme(AutomotiveUi.activityTheme(this));
        super.onCreate(state);
        if (redirectToCanonicalHome()) return;
        launchedAsHome = getIntent() != null && getIntent().hasCategory(Intent.CATEGORY_HOME);
        mediaSelection = new MediaSelection(LauncherPrefs.lastSource(this));
        appearanceController = new AppearanceController(this, mode -> applyAppearance());
        if (Ts18LauncherApplication.legacyMediaEngineOwns(this, false)
                || Ts18LauncherApplication.legacyMediaEngineOwns(this, true))
            mediaBootstrapper = Ts18LauncherApplication.mediaSources(this);
        else mediaBootstrapper = null;
        rememberMediaConfiguration();
        root = new FrameLayout(this);
        root.setBackgroundColor(AutomotiveUi.color(this, R.color.ui_black));
        setContentView(root);

        nativeNavigationPanel = new NativeNavigationPanel(this);
        root.addView(nativeNavigationPanel);
        navigationWindowController = new NavigationWindowController(this, nativeNavigationPanel);
        navigationWindowController.setExplicitNavigationListener(() ->
                StartupReadinessCoordinator.get(this).noteExternalNavigation());
        navigationWindowController.setStartupListener((ready, boundary) ->
                StartupReadinessCoordinator.get(this).reportNavigation(ready, boundary));
        startupHook = new StartupReadinessCoordinator.NavigationHook() {
            @Override public void refreshEvidence() { navigationWindowController.reconcileStartupEvidence(); }
            @Override public void watchEvidence() { navigationWindowController.watchStartupEvidence(); }
            @Override public boolean ready() { return navigationWindowController.startupNavigationReady(); }
            @Override public int taskId() { return navigationWindowController.startupTaskId(); }
            @Override public boolean busy() { return navigationWindowController.startupNavigationBusy(); }
            @Override public void reconcile() { navigationWindowController.reconcileStartup(); }
            @Override public void cancelAutomaticColdStart() { navigationWindowController.cancelAutomaticColdStart(); }
            @Override public void finishLoaderPresentation() { navigationWindowController.finishLoaderPresentation(); }
            @Override public void yieldForMediaPreparation() { navigationWindowController.yieldForMediaPreparation(); }
            @Override public void deferAutomaticPresentation(boolean defer) {
                navigationWindowController.deferAutomaticStartupPresentation(defer);
            }
        };
        StartupReadinessCoordinator.get(this).attachNavigationHook(startupHook);

        buildRail();
        buildRadioPanel();
        buildMusicPanel();
        mediaText = metadataView("Music", "");
        mediaText.setClickable(false);
        mediaText.setFocusable(false);
        mediaText.setLongClickable(false);
        root.addView(mediaText);
        buildDate();
        readinessLoader = new StartupReadinessLoader(this, root,
                () -> StartupReadinessCoordinator.get(this).dismissLoader());
        loaderUi = new StartupReadinessCoordinator.LoaderUi() {
            @Override public void render(String status, boolean visible) { readinessLoader.show(status, visible); }
            @Override public void mask(boolean show) { if (show) readinessLoader.showMask(); else readinessLoader.hideMask(); }
            @Override public boolean canMask() { return readinessLoader.canMask(); }
            @Override public boolean ownsMask() { return readinessLoader.ownsMask(); }
            @Override public void whenMaskDrawn(Runnable ready) { readinessLoader.whenMaskDrawn(ready); }
            @Override public void hide() { readinessLoader.hide(); }
            @Override public void incomplete() {
                android.widget.Toast.makeText(LauncherActivity.this, R.string.startup_loader_incomplete,
                        android.widget.Toast.LENGTH_LONG).show();
            }
        };
        StartupReadinessCoordinator.get(this).attachLoader(loaderUi);
        updateMediaPresentation();
        root.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                scheduleGeometry());
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            scheduleGeometry();
            return insets;
        });
        root.requestApplyInsets();
        scheduleGeometry();
        StartupTimeline.mark("home.content");
        watchFirstDraw();
    }

    /** First drawn HOME frame: the earliest moment the dashboard is visible to the driver. */
    private void watchFirstDraw() {
        android.view.ViewTreeObserver.OnDrawListener[] holder = new android.view.ViewTreeObserver.OnDrawListener[1];
        holder[0] = () -> root.post(() -> {
            if (holder[0] == null) return;
            root.getViewTreeObserver().removeOnDrawListener(holder[0]);
            holder[0] = null;
            if (StartupTimeline.mark("home.first_draw")) {
                try { reportFullyDrawn(); } catch (RuntimeException ignored) { }
            }
            // A genuinely new install (no apps chosen yet) is offered setup once, after Home is
            // already usable. Configured or upgraded launchers are never interrupted.
            if (LauncherPrefs.packageFor(this, LauncherPrefs.KEY_MUSIC).isEmpty()
                    && LauncherPrefs.packageFor(this, LauncherPrefs.KEY_RADIO).isEmpty()
                    && LauncherPrefs.packageFor(this, LauncherPrefs.KEY_NAV).isEmpty()) {
                root.postDelayed(() -> { if (!isFinishing()) SetupActivity.offerOnce(this); }, 1500L);
            }
        });
        root.getViewTreeObserver().addOnDrawListener(holder[0]);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (redirectingToHome || root == null) return;
        launchedAsHome = intent != null && intent.hasCategory(Intent.CATEGORY_HOME);
        if (launchedAsHome) {
            StartupReadinessCoordinator.get(this).dismissLoader();
            if (navigationWindowController != null) navigationWindowController.cancelLauncherOverlay();
            if (appDrawerPanel != null) appDrawerPanel.restoreDashboardRoot();
            root.clearFocus();
            appsButton.requestFocus();
            root.bringToFront();
            root.post(this::updateNavigationSurface);
            ProcessMediaSessionMonitor.refresh(this);
            scheduleMediaReadiness();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (redirectingToHome || root == null) return;
        ProcessMediaSessionMonitor.refresh(this);
        mediaFacade().refresh();
        scheduleMediaReadiness();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) StartupTimeline.mark("home.focus");
        StartupReadinessCoordinator.get(this).onHomeFocusChanged();
        if (hasFocus && navigationWindowController != null) navigationWindowController.onHomeFocusAvailable();
        if (!hasFocus && navigationWindowController != null
                && (readinessLoader == null || !readinessLoader.ownsMask()))
            navigationWindowController.onHomeFocusLost();
        if (root != null && hasFocus) scheduleGeometry();
        if (root != null && hasFocus && !redirectingToHome
                && (launchedAsHome || HomeMode.isDefaultHome(this))) {
            ProcessMediaSessionMonitor.refresh(this);
            scheduleMediaReadiness();
        }
    }

    private void applyAppearance() {
        if (root == null || mediaText == null) return;
        root.setBackgroundColor(AutomotiveUi.color(this, R.color.ui_black));
        rail.setBackgroundColor(AutomotiveUi.color(this, R.color.ui_black));
        mediaText.applyAppearance();
        dateView.setTextColor(AutomotiveUi.color(this, R.color.ui_text));
        dateView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        for (ImageButton button : new ImageButton[] {radioRole, radioPrevious, radioNext,
                musicRole, mediaPrevious, mediaNext}) AutomotiveUi.styleTransportButton(this, button, false);
        updateMediaPresentation();
        AutomotiveUi.styleRailButton(this, appsButton, true);
        AutomotiveUi.styleRailButton(this, navigationButton, true);
        for (ImageButton button : quickButtons) AutomotiveUi.styleRailButton(this, button, false);
        applyRailConfiguration();
        if (nativeNavigationPanel != null) nativeNavigationPanel.applyAppearance(this);
        if (appDrawerPanel != null) appDrawerPanel.applyAppearance();
    }

    private final java.util.function.Consumer<java.util.List<AppCatalog.Entry>> catalogChanged = entries -> {
        if (root != null) applyRailConfiguration();
    };

    @Override protected void onStart() {
        super.onStart();
        if (redirectingToHome || redirectToCanonicalHome()) return;
        AppCatalog.get(this).subscribe(catalogChanged);
        reconcileMediaConfiguration();
        mediaSelection.select(LauncherPrefs.lastSource(this));
        appearanceController.start();
        processSnapshotAuthoritative = false;
        genericSnapshot = emptyMediaSnapshot(); radioSnapshot = emptyMediaSnapshot();
        ProcessMediaSessionMonitor.addObserver(this, this);
        ProcessMediaSessionMonitor.setVisible(this, true);
        MediaNotificationAccessBootstrapper.ensureConnected(this);
        MediaListenerService.addObserver(this);
        ProcessMediaSessionMonitor.refresh(this);
        MediaListenerService.refreshActiveSessions();
        // A source served by the direct engine reports through the facade, never through the
        // legacy observers above.
        mediaFacade().setStateListener(state -> applyEffectiveMediaState());
        mediaFacade().refresh();
        applyRailConfiguration();
        scheduleMediaReadiness();
        root.post(this::updateNavigationSurface);
        updateLabels();
    }

    @Override protected void onStop() {
        if (root == null) { super.onStop(); return; }
        AppCatalog.get(this).unsubscribe(catalogChanged);
        appearanceController.stop();
        mediaRefreshHandler.removeCallbacks(mediaReadyReconcile);
        mediaStatusGeneration++;
        mediaStatusActive = false;
        ProcessMediaSessionMonitor.setVisible(this, false);
        ProcessMediaSessionMonitor.removeObserver(this);
        MediaListenerService.removeObserver(this);
        mediaFacade().setStateListener(null);
        directFailureText = "";
        if (appDrawerPanel != null && appDrawerPanel.isOpen()) appDrawerPanel.hideImmediately();
        if (navigationWindowController != null) navigationWindowController.onHomeStopped();
        super.onStop();
    }

    @Override public void onUserInteraction() {
        super.onUserInteraction();
        StartupReadinessCoordinator.get(this).noteUserInteraction();
    }

    @Override protected void onDestroy() {
        StartupReadinessCoordinator.get(this).detachLoader(loaderUi);
        if (readinessLoader != null) readinessLoader.destroy();
        StartupReadinessCoordinator.get(this).detachNavigationHook(startupHook);
        if (root != null) root.removeCallbacks(geometryUpdate);
        mediaRefreshHandler.removeCallbacksAndMessages(null);
        if (appDrawerPanel != null) appDrawerPanel.destroy();
        if (navigationWindowController != null) navigationWindowController.destroy();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (StartupReadinessCoordinator.get(this).dismissLoader()) return;
        if (appDrawerPanel != null && appDrawerPanel.isOpen()) { appDrawerPanel.hidePanel(); return; }
        if (launchedAsHome || HomeMode.isDefaultHome(this)) return;
        super.onBackPressed();
    }

    private void runMediaReadiness() {
        if (mediaBootstrapper != null) {
            StartupReadinessCoordinator.get(this).onTrigger(StartupReadinessPolicy.Trigger.HOME_RESUME);
        }
    }

    private void scheduleMediaReadiness() {
        mediaRefreshHandler.removeCallbacks(mediaReadyReconcile);
        if (mediaBootstrapper != null)
            mediaRefreshHandler.postDelayed(mediaReadyReconcile, MEDIA_READY_RECONCILE_DELAY_MS);
    }

    private void reconcileMediaConfiguration() {
        String radioPackage = RadioProvider.resolvePackage(this);
        String musicPackage = configuredMusicPackage();
        if (!Ts18LauncherApplication.legacyMediaEngineOwns(this, false)
                && !Ts18LauncherApplication.legacyMediaEngineOwns(this, true)) {
            mediaBootstrapper = null; return;
        }
        MediaSourceBootstrapper current = Ts18LauncherApplication.mediaSources(this);
        if (current == mediaBootstrapper && radioPackage.equals(mediaRadioPackage)
                && musicPackage.equals(mediaMusicPackage)) return;
        mediaStatusGeneration++;
        mediaStatusActive = false;
        resetMediaBootstrapper();
    }

    private void resetMediaBootstrapper() {
        if (Ts18LauncherApplication.legacyMediaEngineOwns(this, false)
                || Ts18LauncherApplication.legacyMediaEngineOwns(this, true))
            mediaBootstrapper = Ts18LauncherApplication.mediaSources(this);
        else mediaBootstrapper = null;
        rememberMediaConfiguration();
    }

    private void rememberMediaConfiguration() {
        mediaRadioPackage = RadioProvider.resolvePackage(this);
        mediaMusicPackage = configuredMusicPackage();
    }

    private String configuredMusicPackage() {
        String configured = LauncherPrefs.packageFor(this, LauncherPrefs.KEY_MUSIC);
        return configured.isEmpty() ? TopwayAdapter.defaultMusicPackage(this) : configured;
    }

    private void buildRail() {
        rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.setBackgroundColor(AutomotiveUi.color(this, R.color.ui_black));
        appsButton = railButton(R.drawable.ic_apps, "Apps");
        AutomotiveUi.styleRailButton(this, appsButton, true);
        appsButton.setOnClickListener(v -> toggleAppDrawer());
        appsButton.setOnLongClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        });
        rail.addView(appsButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ts18Geometry.RAIL_ENDPOINT_HEIGHT));

        quickRail = new LinearLayout(this);
        quickRail.setOrientation(LinearLayout.VERTICAL);
        quickRail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.addView(quickRail, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        for (int i = 0; i < MAX_QUICK_SLOTS; i++) {
            final int index = i;
            String role = LauncherPrefs.quickRole(this, i);
            ImageButton button = railButton(RoleIconCatalog.icon(role), RoleIconCatalog.label(role));
            button.setOnClickListener(v -> openQuick(index));
            button.setOnLongClickListener(v -> { openPicker(LauncherPrefs.QUICK_KEYS[index]); return true; });
            quickButtons[i] = button;

            FrameLayout cell = new FrameLayout(this);
            quickCells[i] = cell;
            cell.addView(button, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, Ts18Geometry.RAIL_QUICK_HEIGHT, Gravity.CENTER));
            quickRail.addView(cell, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }

        navigationButton = railButton(R.drawable.ic_navigation_filled, "Navigation");
        AutomotiveUi.styleRailButton(this, navigationButton, true);
        navigationButton.setOnClickListener(v -> openNavigation(null));
        rail.addView(navigationButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ts18Geometry.RAIL_ENDPOINT_HEIGHT));
        root.addView(rail);
    }

    private ImageButton railButton(int icon, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(icon);
        button.setContentDescription(description);
        AutomotiveUi.styleRailButton(this, button, false);
        return button;
    }

    private void applyRailConfiguration() {
        int count = LauncherPrefs.quickCount(this);
        boolean shortcutsEnabled = UiPersonalizationPrefs.homeShortcutsEnabled(this);
        List<View> focus = new ArrayList<>();
        focus.add(appsButton);
        for (int i = 0; i < MAX_QUICK_SLOTS; i++) {
            boolean visible = shortcutsEnabled && i < count;
            quickCells[i].setVisibility(visible ? View.VISIBLE : View.GONE);
            if (visible) {
                ShortcutSlot.bind(this, quickButtons[i], LauncherPrefs.QUICK_KEYS[i]);
                AutomotiveUi.styleRailButton(this, quickButtons[i], false);
                focus.add(quickButtons[i]);
            }
        }
        focus.add(navigationButton);
        AutomotiveUi.styleRailButton(this, navigationButton, true);
        AutomotiveUi.linkVertical(focus);
        scheduleGeometry();
    }

    private void buildRadioPanel() {
        radioPanel = stripPanel();
        radioRole = controlButton(R.drawable.ic_radio, "Open radio source", false);
        radioPrevious = controlButton(R.drawable.ic_previous, "Previous station", false);
        radioPlayPause = controlButton(R.drawable.ic_play, "Play or pause radio", false);
        radioNext = controlButton(R.drawable.ic_next, "Next station", false);
        radioRole.setOnClickListener(v -> openConfigured(LauncherPrefs.KEY_RADIO));
        radioPrevious.setOnClickListener(v -> radioCommand(MediaListenerService.Command.PREVIOUS));
        radioPlayPause.setOnClickListener(v -> radioCommand(MediaListenerService.Command.PLAY_PAUSE));
        radioNext.setOnClickListener(v -> radioCommand(MediaListenerService.Command.NEXT));
        populateControls(radioPanel, radioRole, radioPrevious, radioPlayPause, radioNext);
        root.addView(radioPanel);
    }

    private void buildMusicPanel() {
        musicPanel = stripPanel();
        musicRole = controlButton(R.drawable.ic_music, "Open configured music source", false);
        mediaPrevious = controlButton(R.drawable.ic_previous, "Previous track", false);
        playPause = controlButton(R.drawable.ic_play, "Play or pause music", false);
        mediaNext = controlButton(R.drawable.ic_next, "Next track", false);
        musicRole.setOnClickListener(v -> openConfigured(LauncherPrefs.KEY_MUSIC));
        mediaPrevious.setOnClickListener(v -> musicCommand(MediaListenerService.Command.PREVIOUS));
        playPause.setOnClickListener(v -> musicCommand(MediaListenerService.Command.PLAY_PAUSE));
        mediaNext.setOnClickListener(v -> musicCommand(MediaListenerService.Command.NEXT));
        populateControls(musicPanel, musicRole, mediaPrevious, playPause, mediaNext);
        root.addView(musicPanel);
    }

    private void populateControls(LinearLayout panel, ImageButton source, ImageButton previous,
                                  ImageButton primary, ImageButton next) {
        panel.addView(source, fixed(Ts18Geometry.SOURCE_TARGET));
        panel.addView(previous, fixed(Ts18Geometry.TRANSPORT_TARGET));
        panel.addView(primary, fixed(Ts18Geometry.PRIMARY_TARGET));
        panel.addView(next, fixed(Ts18Geometry.TRANSPORT_TARGET));
        AutomotiveUi.linkHorizontal(java.util.Arrays.asList(source, previous, primary, next));
    }

    private void selectSource(String source) {
        LauncherPrefs.selectSource(this, source);
        mediaSelection.select(source);
        updateLabels();
    }

    private void radioCommand(MediaListenerService.Command command) {
        reconcileMediaConfiguration();
        selectSource(MediaSelection.RADIO);
        String radioPackage = RadioProvider.resolvePackage(this);
        if (radioPackage.isEmpty()) { openPicker(LauncherPrefs.KEY_RADIO); return; }
        // Both sources legacy: the legacy engine keeps its own nominate-then-commit handover.
        // Any other combination is committed by the facade after observed replacement playback.
        if (mediaFacade().legacyOwnsHandover(MediaSourceKey.RADIO)
                && command == MediaListenerService.Command.PLAY_PAUSE && !mediaBootstrapper.isPlaying(radioPackage)) {
            String musicPackage = musicControlPackage();
            if (!musicPackage.isEmpty()) mediaBootstrapper.pausePackage(musicPackage);
        }
        runSourceCommand(MediaSourceKey.RADIO, "Radio", radioPackage, command);
    }

    private void musicCommand(MediaListenerService.Command command) {
        reconcileMediaConfiguration();
        selectSource(MediaSelection.MUSIC);
        String musicPackage = musicControlPackage();
        if (musicPackage.isEmpty()) { openPicker(LauncherPrefs.KEY_MUSIC); return; }
        LauncherPrefs.rememberMusic(this, musicPackage);
        if (mediaFacade().legacyOwnsHandover(MediaSourceKey.MUSIC)
                && command == MediaListenerService.Command.PLAY_PAUSE && !mediaBootstrapper.isPlaying(musicPackage)) {
            String radioPackage = RadioProvider.resolvePackage(this);
            if (!radioPackage.isEmpty()) mediaBootstrapper.pausePackage(radioPackage);
        }
        runSourceCommand(MediaSourceKey.MUSIC, "Music", musicPackage, command);
    }

    private MediaFeatureFacade mediaFacade() { return Ts18LauncherApplication.mediaFacade(this); }

    private String musicControlPackage() {
        // Only the legacy engine may follow a globally selected generic player. Every other
        // engine controls the exact configured Music package and nothing else.
        if (mediaFacade().engine(MediaSourceKey.MUSIC) == MediaEngine.LEGACY
                && LauncherPrefs.MEDIA_MODE_AUTO.equals(LauncherPrefs.mediaMode(this))) {
            String selected = ProcessMediaSessionMonitor.selectedMusicPackage();
            if (!selected.isEmpty()) return selected;
            if (!genericSnapshot.packageName.isEmpty()) return genericSnapshot.packageName;
        }
        return configuredMusicPackage();
    }

    private boolean snapshotSupports(String packageName, MediaListenerService.Command command) {
        return packageName != null && ((!genericSnapshot.packageName.isEmpty()
                && packageName.equals(genericSnapshot.packageName) && genericSnapshot.supports(command))
                || (!radioSnapshot.packageName.isEmpty()
                && packageName.equals(radioSnapshot.packageName) && radioSnapshot.supports(command)));
    }

    private void runSourceCommand(MediaSourceKey key, String label, String packageName,
                                  MediaListenerService.Command command) {
        StartupReadinessCoordinator.get(this).onExplicitSource(packageName);
        int generation = ++mediaStatusGeneration;
        mediaStatusActive = true;
        String appLabel = AppResolver.labelFor(this, packageName, label);
        mediaText.setMetadata(command == MediaListenerService.Command.PLAY_PAUSE
                ? "Preparing " + label + "…" : label, appLabel);
        dispatchSourceCommand(key, label, packageName, command, generation,
                android.os.SystemClock.elapsedRealtime() + 22000L, false);
    }

    private void dispatchSourceCommand(MediaSourceKey key, String label, String packageName,
                                       MediaListenerService.Command command, int generation,
                                       long deadline, boolean staged) {
        // One entry point for every engine. The legacy engine receives exactly the call it always
        // did (same shared owner, label, package and command); only the routing is new.
        com.cbkii.ts18launcher.media.MediaBackend.ResultCallback completion = result -> {
            if (isFinishing() || isDestroyed() || generation != mediaStatusGeneration) return;
            ProcessMediaSessionMonitor.refresh(this);
            MediaListenerService.refreshActiveSessions();
            if (result.settledWithoutError()) {
                mediaStatusActive = false;
                updateLabels();
            } else {
                if (!staged && !result.dispatched && command == MediaListenerService.Command.PLAY_PAUSE
                        && !result.playingObserved && key == MediaSourceKey.RADIO
                        && StartupReadinessCoordinator.get(this).prepareInteractively(this, packageName, deadline, ready -> {
                            if (isFinishing() || isDestroyed() || generation != mediaStatusGeneration) return;
                            if (ready && android.os.SystemClock.elapsedRealtime() < deadline)
                                dispatchSourceCommand(key, label, packageName, command, generation, deadline, true);
                            else settleSourceCommandFailure(key, label, packageName, command, generation,
                                    "Radio could not become ready · check setup permissions");
                        })) return;
                MediaEventTrace.record("readiness", "background-only-command-failed", packageName);
                settleSourceCommandFailure(key, label, packageName, command, generation, result.message);
            }
        };
        if (staged) mediaFacade().resumePreparedSource(key, packageName, completion);
        else mediaFacade().command(key, packageName, LegacyMediaBackend.userAction(command), completion);
    }

    private void settleSourceCommandFailure(MediaSourceKey key, String label, String packageName,
            MediaListenerService.Command command, int generation, String message) {
        if (mediaFacade().engine(key) == MediaEngine.DIRECT) {
            directFailureKey = key;
            directFailureText = message == null ? "" : message;
            updateLabels();
        }
        if (command == MediaListenerService.Command.PLAY_PAUSE)
            mediaSelection.reconcileAfterFailedSwitch(radioSnapshot.playing, genericSnapshot.playing);
        MediaEventTrace.record("command", "failed", packageName + " · " + message);
        mediaText.setMetadata(label, message);
        mediaRefreshHandler.postDelayed(() -> {
            if (generation != mediaStatusGeneration) return;
            mediaStatusActive = false;
            directFailureText = "";
            updateLabels();
        }, MEDIA_STATUS_MS);
    }

    private void buildDate() {
        dateView = new TextClock(this);
        dateView.setFormat12Hour("dd MMM");
        dateView.setFormat24Hour("dd MMM");
        dateView.setTextColor(AutomotiveUi.color(this, R.color.ui_text));
        dateView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.ui_date_text));
        dateView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        dateView.setGravity(Gravity.CENTER);
        dateView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        dateView.setContentDescription("Date");
        dateView.setFocusable(false);
        dateView.setClickable(false);
        root.addView(dateView);
    }

    private LinearLayout stripPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(0, 0, 0, 0);
        panel.setBackground(AutomotiveUi.cardBackground(this));
        return panel;
    }

    private ImageButton controlButton(int icon, String description, boolean primary) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(icon);
        button.setTag(icon);
        button.setContentDescription(description);
        AutomotiveUi.styleTransportButton(this, button, primary);
        button.setEnabled(true);
        return button;
    }

    private MediaMetadataView metadataView(String title, String subtitle) {
        MediaMetadataView view = new MediaMetadataView(this);
        view.setMetadata(title, subtitle);
        return view;
    }

    private LinearLayout.LayoutParams fixed(int widthPx) {
        return new LinearLayout.LayoutParams(widthPx, LinearLayout.LayoutParams.MATCH_PARENT);
    }

    /** Presents the single HOME navigation surface: the native panel and its window owner. */
    private void updateNavigationSurface() {
        if (redirectingToHome || isFinishing()) return;
        nativeNavigationPanel.setVisibility(View.VISIBLE);
        if (appDrawerPanel != null && appDrawerPanel.isOpen()) {
            navigationWindowController.onLauncherOverlayOpened();
            return;
        }
        navigationWindowController.onHomeVisible();
    }

    private void scheduleGeometry() {
        if (root == null || isDestroyed() || geometryUpdatePending) return;
        geometryUpdatePending = true;
        root.post(geometryUpdate);
    }

    private void applyGeometry(int width, int height) {
        if (width <= 0 || height <= 0) return;
        // Insets are in window coordinates; decor-fitted content has already consumed them.
        // Intersect only the residual inset region with this root, rather than adding bars twice.
        WindowInsets insets = root.getRootWindowInsets();
        root.getLocationInWindow(rootWindowLocation);
        View decor = getWindow().getDecorView();
        int leftInset = insets == null ? 0 : Ts18Geometry.residualStartInset(
                insets.getSystemWindowInsetLeft(), rootWindowLocation[0]);
        int topInset = insets == null ? 0 : Ts18Geometry.residualStartInset(
                insets.getSystemWindowInsetTop(), rootWindowLocation[1]);
        int rightInset = insets == null ? 0 : Ts18Geometry.residualEndInset(
                insets.getSystemWindowInsetRight(), rootWindowLocation[0], width, decor.getWidth());
        int bottomInset = insets == null ? 0 : Ts18Geometry.residualEndInset(
                insets.getSystemWindowInsetBottom(), rootWindowLocation[1], height, decor.getHeight());
        Ts18Geometry.Inputs inputs = new Ts18Geometry.Inputs(width, height, leftInset, topInset,
                rightInset, bottomInset, LauncherPrefs.railOnRight(this), LauncherPrefs.radioOnRight(this));
        if (!geometryChanges.shouldApply(inputs)) return;
        Ts18Geometry.Layout g = Ts18Geometry.resolve(inputs);
        place(rail, g.railX, g.top, g.railWidth(), g.railHeight());
        place(radioPanel, g.radioX(), g.top, g.radioWidth, g.stripHeight);
        place(musicPanel, g.musicX(), g.top, g.musicWidth, g.stripHeight);
        place(mediaText, g.metadataX(), g.top, g.metadataWidth(), g.stripHeight);
        placeCard(dateView, g.dateX(), g.top, g.dateWidth(), g.stripHeight);
        if (nativeNavigationPanel != null)
            place(nativeNavigationPanel, g.mapX(), g.mapY(), g.mapWidth(), g.mapHeight());
        if (appDrawerPanel != null) place(appDrawerPanel, g.mapX(), g.mapY(), g.mapWidth(), g.mapHeight());
        if (readinessLoader != null) readinessLoader.geometry(leftInset, topInset, width - leftInset - rightInset, height - topInset - bottomInset);
    }

    private void place(View view, int x, int y, int width, int height) {
        if (view == null) return;
        int desiredWidth = Math.max(1, width);
        int desiredHeight = Math.max(1, height);
        int desiredLeft = Math.max(0, x);
        int desiredTop = Math.max(0, y);
        android.view.ViewGroup.LayoutParams existing = view.getLayoutParams();
        if (existing instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams current = (FrameLayout.LayoutParams) existing;
            if (current.width == desiredWidth && current.height == desiredHeight
                    && current.leftMargin == desiredLeft && current.topMargin == desiredTop) {
                return;
            }
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(desiredWidth, desiredHeight);
        lp.leftMargin = desiredLeft;
        lp.topMargin = desiredTop;
        view.setLayoutParams(lp);
    }

    private void placeCard(View view, int x, int y, int width, int height) {
        int inset = AutomotiveUi.dimen(this, R.dimen.ui_card_inset);
        place(view, x + inset, y + inset, Math.max(1, width - inset * 2), Math.max(1, height - inset * 2));
    }

    private void updateLabels() {
        if (LauncherPrefs.MEDIA_MODE_PREFER_MUSIC.equals(LauncherPrefs.mediaMode(this))
                && !configuredMusicPackage().equals(genericSnapshot.packageName))
            genericSnapshot = emptyMediaSnapshot();
        boolean tickerRadio = MediaSelection.RADIO.equals(mediaSelection.displayed());
        mediaText.setSnapshot(tickerRadio ? radioSnapshot : genericSnapshot, mediaSelection.displayed());
        String radioPackage = RadioProvider.resolvePackage(this);
        String radioLabel = AppResolver.labelFor(this, radioPackage, radioPackage.isEmpty() ? "Set radio" : "Radio");
        updatePlaybackButton(radioPlayPause, radioSnapshot, getString(R.string.media_source_radio));
        setMediaButtonsReady(radioPrevious, radioPlayPause, radioNext);
        if (!MediaListenerService.hasNotificationAccess(this)) {
            if (!mediaStatusActive) {
                if (MediaSelection.MUSIC.equals(mediaSelection.displayed()))
                    mediaText.setMetadata("Media access required", "Use Settings gear · Notification access");
                else mediaText.setMetadata(radioLabel, "Radio");
            }
            updatePlaybackButton(playPause, genericSnapshot, getString(R.string.media_source_music));
            setMediaButtonsReady(mediaPrevious, playPause, mediaNext);
            applyDirectSourcePresentation();
            updateMediaPresentation();
            return;
        }
        String fallback = configuredMusicPackage();
        String sourcePackage = genericSnapshot.packageName.isEmpty() ? fallback : genericSnapshot.packageName;
        String sourceLabel = AppResolver.labelFor(this, sourcePackage,
                sourcePackage.isEmpty() ? "No active media session" : sourcePackage);
        boolean showRadio = MediaSelection.RADIO.equals(mediaSelection.displayed());
        MediaListenerService.Snapshot displayed = showRadio ? radioSnapshot : genericSnapshot;
        String label = showRadio ? radioLabel : sourceLabel;
        if (!mediaStatusActive) {
            mediaText.setMetadata(primaryMetadata(displayed, label), secondaryMetadata(displayed, label));
        }
        updatePlaybackButton(playPause, genericSnapshot, getString(R.string.media_source_music));
        setMediaButtonsReady(mediaPrevious, playPause, mediaNext);
        applyDirectSourcePresentation();
        updateMediaPresentation();
    }

    /**
     * Truthful presentation for sources on the direct engine: a non-live source never shows a
     * ready Play button, and the selected source explains in one line why it cannot be controlled.
     */
    private void applyDirectSourcePresentation() {
        MediaFeatureFacade facade = mediaFacade();
        MediaSourceKey displayedKey = MediaEngineSelector.keyFor(mediaSelection.displayed());
        String status = "";
        String statusDetail = "";
        for (MediaSourceKey key : MediaSourceKey.values()) {
            if (facade.engine(key) != MediaEngine.DIRECT) continue;
            MediaSourceState state = facade.state(key);
            ImageButton button = key == MediaSourceKey.RADIO ? radioPlayPause : playPause;
            String sourceName = getString(key == MediaSourceKey.RADIO
                    ? R.string.media_source_radio : R.string.media_source_music);
            if (!state.live()) {
                String reason = DirectMediaPresentation.absentText(state);
                // Startable shows Play, an attempt in progress shows connecting, and only a source
                // that cannot be started from here shows unavailable.
                int icon = state.starting ? R.drawable.ic_media_connecting
                        : state.startable ? R.drawable.ic_play : R.drawable.ic_media_unavailable;
                button.setTag(icon);
                button.setImageResource(icon);
                button.setContentDescription(sourceName + ". " + reason);
                if (key == displayedKey) {
                    status = state.packageName.isEmpty() ? sourceName
                            : AppResolver.labelFor(this, state.packageName, sourceName);
                    statusDetail = reason;
                }
            }
            if (key == displayedKey && key == directFailureKey && !directFailureText.isEmpty()) {
                status = directFailureText;
                statusDetail = "";
            }
        }
        mediaText.setStatus(status, statusDetail);
    }

    private void updatePlaybackButton(ImageButton button, MediaListenerService.Snapshot snapshot, String source) {
        int icon = MediaPlaybackPresentation.icon(snapshot.state);
        // A transition must not leave the previous PLAY/PAUSE claim visible during a crossfade.
        if (!(button.getTag() instanceof Integer) || ((Integer) button.getTag()) != icon) {
            button.setTag(icon);
            button.setImageResource(icon);
        }
        button.setContentDescription(getString(MediaPlaybackPresentation.description(snapshot.state), source));
    }

    private void updateMediaPresentation() {
        if (radioPanel == null || musicPanel == null || radioPlayPause == null || playPause == null) return;
        boolean radioActive = MediaSelection.RADIO.equals(mediaSelection.displayed());
        boolean radioOnRight = LauncherPrefs.radioOnRight(this);
        radioPanel.setBackground(AutomotiveUi.mediaGroupBackground(this, radioActive, !radioOnRight));
        musicPanel.setBackground(AutomotiveUi.mediaGroupBackground(this, !radioActive, radioOnRight));
        AutomotiveUi.styleSourcePrimaryButton(this, radioPlayPause, radioActive);
        AutomotiveUi.styleSourcePrimaryButton(this, playPause, !radioActive);
        radioRole.setImageTintList(ColorStateList.valueOf(radioActive ? AutomotiveUi.accentForeground(this)
                : AutomotiveUi.color(this, R.color.ui_text_secondary)));
        musicRole.setImageTintList(ColorStateList.valueOf(radioActive ? AutomotiveUi.color(this, R.color.ui_text_secondary)
                : AutomotiveUi.accentForeground(this)));
    }

    private void setMediaButtonsReady(ImageButton... buttons) {
        for (ImageButton button : buttons) if (button != null) button.setEnabled(true);
    }

    private String primaryMetadata(MediaListenerService.Snapshot snapshot, String fallback) {
        if (!snapshot.title.isEmpty()) return snapshot.title;
        if (!snapshot.artist.isEmpty()) return snapshot.artist;
        return fallback;
    }

    private String secondaryMetadata(MediaListenerService.Snapshot snapshot, String sourceLabel) {
        if (!snapshot.title.isEmpty() && !snapshot.artist.isEmpty()) return snapshot.artist;
        String primary = primaryMetadata(snapshot, sourceLabel);
        return sourceLabel.equals(primary) ? "" : sourceLabel;
    }

    private void openQuick(int index) {
        launchAfterNavigation(() -> {
            if (!ShortcutSlot.launch(this, LauncherPrefs.QUICK_KEYS[index])) openPicker(LauncherPrefs.QUICK_KEYS[index]);
            mediaSelection.select(LauncherPrefs.lastSource(this));
            updateLabels();
        });
    }

    private void toggleAppDrawer() {
        MediaEventTrace.record("drawer", "open-request");
        StartupReadinessCoordinator.get(this).noteExternalNavigation();
        Runnable show = () -> {
            if (!isFinishing() && !isDestroyed()) startActivity(new Intent(this, AppDrawerActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        };
        navigationWindowController.openLauncherOverlay(show);
    }

    private boolean redirectToCanonicalHome() {
        if (getComponentName().getClassName().endsWith(".HomeAlias")
                || !HomeMode.isDefaultHome(this)) return false;
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setClassName(this, getPackageName() + ".HomeAlias")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            redirectingToHome = true;
            if (navigationWindowController != null) navigationWindowController.destroy();
            finish();
            return true;
        } catch (RuntimeException e) {
            android.util.Log.w("TS18Nav", "HOME redirect unavailable", e);
            return false;
        }
    }

    @Override public void onMediaStateChanged(MediaListenerService.Snapshot genericMedia,
                                                MediaListenerService.Snapshot radio) {
        listenerGenericSnapshot = normaliseSnapshot(genericMedia);
        listenerRadioSnapshot = normaliseSnapshot(radio);
        applyEffectiveMediaState();
    }

    @Override public void onProcessMediaStateChanged(MediaListenerService.Snapshot genericMedia,
                                                      MediaListenerService.Snapshot radio,
                                                      boolean authoritative) {
        processGenericSnapshot = normaliseSnapshot(genericMedia);
        processRadioSnapshot = normaliseSnapshot(radio);
        processSnapshotAuthoritative = authoritative;
        applyEffectiveMediaState();
    }

    private void applyEffectiveMediaState() {
        runOnUiThread(() -> {
            MediaFeatureFacade facade = mediaFacade();
            boolean musicDirect = facade.engine(MediaSourceKey.MUSIC) == MediaEngine.DIRECT;
            boolean radioDirect = facade.engine(MediaSourceKey.RADIO) == MediaEngine.DIRECT;
            // Each source is rendered only from the engine selected for it; the other engine's
            // observation of that source is discarded here and never merged.
            genericSnapshot = musicDirect
                    ? DirectMediaPresentation.snapshot(facade.state(MediaSourceKey.MUSIC))
                    : mergeSessionSnapshot(
                    processGenericSnapshot, listenerGenericSnapshot, processSnapshotAuthoritative);
            radioSnapshot = radioDirect
                    ? DirectMediaPresentation.snapshot(facade.state(MediaSourceKey.RADIO))
                    : mergeSessionSnapshot(
                    processRadioSnapshot, listenerRadioSnapshot, processSnapshotAuthoritative);
            if ((musicDirect || processSnapshotAuthoritative) && (radioDirect || processSnapshotAuthoritative))
                mediaSelection.reconcileObservedPlayback(radioSnapshot.playing, genericSnapshot.playing,
                        MediaCommandPolicy.usesPauseAction(radioSnapshot.state)
                                || MediaCommandPolicy.usesPauseAction(genericSnapshot.state));
            updateLabels();
        });
    }

    private static MediaListenerService.Snapshot normaliseSnapshot(MediaListenerService.Snapshot value) {
        return value == null ? emptyMediaSnapshot() : value;
    }

    private static MediaListenerService.Snapshot mergeSessionSnapshot(
            MediaListenerService.Snapshot core, MediaListenerService.Snapshot enriched,
            boolean coreAuthoritative) {
        MediaListenerService.Snapshot secondary = normaliseSnapshot(enriched);
        if (!coreAuthoritative) return emptyMediaSnapshot();
        MediaListenerService.Snapshot primary = normaliseSnapshot(core);
        if (primary.packageName.isEmpty()) return primary;
        // Generic music has exactly one publisher; title equality cannot prove an item match.
        if (!RadioFrequency.NAVRADIO.equals(primary.packageName)) return primary;
        if (secondary.packageName.isEmpty() || !primary.packageName.equals(secondary.packageName)) {
            return primary;
        }
        if (primary.sessionIdentity == null || secondary.sessionIdentity == null
                || !primary.sessionIdentity.equals(secondary.sessionIdentity)) {
            return primary;
        }
        String primaryStation = MediaListenerService.meaningfulRadioTitle(primary);
        if (RadioFrequency.NAVRADIO.equals(primary.packageName)
                && !primaryStation.isEmpty()
                && !primaryStation.equalsIgnoreCase(secondary.title)) return primary;
        if (RadioFrequency.NAVRADIO.equals(primary.packageName)
                && primary.radioFrequency != null && secondary.radioFrequency != null
                && !primary.radioFrequency.display().equals(secondary.radioFrequency.display())) return primary;
        // The process controller is the selected live authority. Listener data may only
        // fill a missing field for the same current track/station, never replace it.
        boolean placeholder = RadioFrequency.NAVRADIO.equals(primary.packageName)
                && primaryStation.isEmpty();
        String title = placeholder ? secondary.title : primary.title;
        boolean sameTrack = !primary.title.isEmpty() && primary.title.equalsIgnoreCase(secondary.title);
        String artist = primary.artist.isEmpty() && (sameTrack || placeholder)
                ? secondary.artist : primary.artist;
        if (RadioFrequency.NAVRADIO.equals(primary.packageName) && sameTrack
                && primary.radioFrequency == null && secondary.radioFrequency != null) {
            artist = primary.artist + (primary.artist.isEmpty() ? "" : " ") + secondary.radioFrequency.display();
        }
        return primary.withMetadata(title, artist);
    }

    private static MediaListenerService.Snapshot emptyMediaSnapshot() {
        return new MediaListenerService.Snapshot("", "", "", false);
    }

    private void openNavigation(Location location) {
        if (navigationWindowController != null
                && navigationWindowController.openFullscreen(location)) return;
        String pkg = LauncherPrefs.packageFor(this, LauncherPrefs.KEY_NAV);
        if (!NavigationProvider.open(this, pkg, location)) openPicker(LauncherPrefs.KEY_NAV);
    }

    private void openConfigured(String key) {
        launchAfterNavigation(() -> openConfiguredNow(key));
    }

    private void launchAfterNavigation(Runnable launch) {
        StartupReadinessCoordinator.get(this).noteExternalNavigation();
        if (navigationWindowController == null) launch.run();
        else navigationWindowController.launchAfterSuspension(launch);
    }

    private void openConfiguredNow(String key) {
        if (LauncherPrefs.KEY_RADIO.equals(key)) selectSource(MediaSelection.RADIO);
        else if (LauncherPrefs.KEY_MUSIC.equals(key)) selectSource(MediaSelection.MUSIC);
        String pkg;
        if (LauncherPrefs.KEY_RADIO.equals(key)) pkg = RadioProvider.resolvePackage(this);
        else {
            pkg = LauncherPrefs.packageFor(this, key);
            if (LauncherPrefs.KEY_MUSIC.equals(key) && pkg.isEmpty()) pkg = TopwayAdapter.defaultMusicPackage(this);
        }
        if (!AppResolver.launchPackage(this, pkg)) openPicker(key);
    }

    private void openPicker(String key) {
        Intent intent = new Intent(this, AppDrawerActivity.class);
        intent.putExtra(AppDrawerActivity.EXTRA_PICK_KEY, key);
        startActivity(intent);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VoiceSearch.REQUEST_CODE && resultCode == RESULT_OK && appDrawerPanel != null)
            appDrawerPanel.applyVoiceSearch(VoiceSearch.firstResult(data));
    }
}
