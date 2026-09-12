package com.cbkii.ts18launcher;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;

import com.cbkii.ts18launcher.platform.TopwayAdapter;

import java.util.ArrayList;
import java.util.List;

@SuppressLint("SetTextI18n")
public class LauncherActivity extends Activity implements MediaListenerService.Observer {
    private static final int REQUEST_LOCATION = 4101;
    private static final int MAX_QUICK_SLOTS = 6;
    private static final long MEDIA_REFRESH_INTERVAL_MS = 1000L;

    private FrameLayout root;
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
    private final Handler mediaRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable mediaRefreshPoll = new Runnable() {
        @Override public void run() {
            if (!mediaRefreshPolling) return;
            MediaListenerService.refreshActiveSessions();
            mediaRefreshHandler.postDelayed(this, MEDIA_REFRESH_INTERVAL_MS);
        }
    };
    private MapPanel mapPanel;
    private AppDrawerPanel appDrawerPanel;
    private AppearanceController appearanceController;
    private boolean launchedAsHome;
    private boolean locationPermissionRequested;
    private boolean mediaRefreshPolling;
    private MediaListenerService.Snapshot genericSnapshot =
            new MediaListenerService.Snapshot("", "", "", false);
    private MediaListenerService.Snapshot radioSnapshot =
            new MediaListenerService.Snapshot("", "", "", false);

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        launchedAsHome = getIntent() != null && getIntent().hasCategory(Intent.CATEGORY_HOME);
        mediaSelection = new MediaSelection(LauncherPrefs.lastSource(this));
        appearanceController = new AppearanceController(this, mode -> applyAppearance());

        root = new FrameLayout(this);
        root.setBackgroundColor(AutomotiveUi.color(this, R.color.ui_black));
        setContentView(root);
        buildRail();
        buildRadioPanel();
        buildMusicPanel();
        mediaText = metadataView("Music", "");
        mediaText.setOnClickListener(v -> {
            if (MediaSelection.RADIO.equals(mediaSelection.displayed())) openConfigured(LauncherPrefs.KEY_RADIO);
            else openCurrentMedia();
        });
        mediaText.setOnLongClickListener(v -> { openSettings(); return true; });
        root.addView(mediaText);
        buildDate();
        root.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                applyGeometry(right - left, bottom - top));
        root.post(() -> applyGeometry(root.getWidth(), root.getHeight()));
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        launchedAsHome = intent != null && intent.hasCategory(Intent.CATEGORY_HOME);
        if (launchedAsHome) {
            if (appDrawerPanel != null) appDrawerPanel.restoreDashboardRoot();
            root.clearFocus();
            appsButton.requestFocus();
            root.bringToFront();
            if (mapPanel != null && LauncherPrefs.mapEnabled(this)) mapPanel.resumeWebView();
        }
    }

    private void applyAppearance() {
        if (root == null || mediaText == null) return;
        root.setBackgroundColor(AutomotiveUi.color(this, R.color.ui_black));
        rail.setBackgroundColor(AutomotiveUi.color(this, R.color.ui_black));
        radioPanel.setBackground(AutomotiveUi.chipBackground(this));
        musicPanel.setBackground(AutomotiveUi.cardBackground(this));
        mediaText.applyAppearance();
        dateView.setTextColor(AutomotiveUi.color(this, R.color.ui_text));
        dateView.setBackground(AutomotiveUi.interactiveBackground(this, false));
        for (ImageButton button : new ImageButton[] {radioRole, radioPrevious, radioNext,
                musicRole, mediaPrevious, mediaNext}) AutomotiveUi.styleTransportButton(this, button, false);
        AutomotiveUi.styleTransportButton(this, radioPlayPause, true);
        AutomotiveUi.styleTransportButton(this, playPause, true);
        AutomotiveUi.styleRailButton(this, appsButton);
        AutomotiveUi.styleRailButton(this, navigationButton);
        for (ImageButton button : quickButtons) AutomotiveUi.styleRailButton(this, button);
        applyRailConfiguration();
        if (appDrawerPanel != null) appDrawerPanel.applyAppearance();
        if (mapPanel != null) mapPanel.applyAppearance();
    }

    // Compatibility note for migrated preference readers: the former "Media controls side"
    // setting is intentionally not used. Radio/Music sides is the sole independent cluster
    // preference and remains unrelated to the rail and driver-side settings.
    // Historical source contract: LauncherPrefs.mediaControlsOnLeft(this) was removed with
    // the old two-panel model; do not reintroduce it as a second setting.

    @Override protected void onStart() {
        super.onStart();
        mediaSelection.select(LauncherPrefs.lastSource(this));
        appearanceController.start();
        MediaListenerService.addObserver(this);
        MediaListenerService.refreshActiveSessions();
        startMediaRefreshPolling();
        applyRailConfiguration();

        if (appDrawerPanel != null) appDrawerPanel.refreshPreferences();
        root.post(this::updateMapVisibility);
        updateLabels();
    }

    @Override protected void onStop() {
        appearanceController.stop();
        stopMediaRefreshPolling();
        MediaListenerService.removeObserver(this);
        if (appDrawerPanel != null && appDrawerPanel.isOpen()) appDrawerPanel.hideImmediately();
        if (mapPanel != null) mapPanel.stop();
        super.onStop();
    }

    @Override protected void onDestroy() {
        stopMediaRefreshPolling();
        if (mapPanel != null) mapPanel.destroy();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (appDrawerPanel != null && appDrawerPanel.isOpen()) {
            appDrawerPanel.hidePanel();
            return;
        }
        if (launchedAsHome || HomeMode.isDefaultHome(this)) return;
        super.onBackPressed();
    }

    private void startMediaRefreshPolling() {
        if (mediaRefreshPolling) return;
        mediaRefreshPolling = true;
        mediaRefreshHandler.removeCallbacks(mediaRefreshPoll);
        mediaRefreshHandler.postDelayed(mediaRefreshPoll, MEDIA_REFRESH_INTERVAL_MS);
    }

    private void stopMediaRefreshPolling() {
        mediaRefreshPolling = false;
        mediaRefreshHandler.removeCallbacks(mediaRefreshPoll);
    }

    private void buildRail() {
        rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.setBackgroundColor(AutomotiveUi.color(this, R.color.ui_black));

        appsButton = railButton(R.drawable.ic_apps, "Apps");
        appsButton.setOnClickListener(v -> toggleAppDrawer());
        rail.addView(appsButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ts18Geometry.RAIL_ENDPOINT_HEIGHT));

        quickRail = new LinearLayout(this);
        quickRail.setOrientation(LinearLayout.VERTICAL);
        quickRail.setGravity(Gravity.CENTER);
        rail.addView(quickRail, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        for (int i = 0; i < MAX_QUICK_SLOTS; i++) {
            final int index = i;
            String role = LauncherPrefs.quickRole(this, i);
            ImageButton button = railButton(RoleIconCatalog.icon(role), RoleIconCatalog.label(role));
            button.setOnClickListener(v -> openQuick(index));
            button.setOnLongClickListener(v -> {
                openPicker(LauncherPrefs.QUICK_KEYS[index]);
                return true;
            });
            quickButtons[i] = button;
            quickRail.addView(button, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Ts18Geometry.RAIL_QUICK_HEIGHT));
        }

        navigationButton = railButton(R.drawable.ic_navigation, "Navigation");
        navigationButton.setOnClickListener(v -> openNavigation(null));
        rail.addView(navigationButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ts18Geometry.RAIL_ENDPOINT_HEIGHT));
        root.addView(rail);
    }

    private ImageButton railButton(int icon, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(icon);
        button.setContentDescription(description);
        AutomotiveUi.styleRailButton(this, button);
        return button;
    }

    private void applyRailConfiguration() {
        int count = LauncherPrefs.quickCount(this);
        List<View> focus = new ArrayList<>();
        focus.add(appsButton);
        for (int i = 0; i < MAX_QUICK_SLOTS; i++) {
            boolean visible = i < count;
            quickButtons[i].setVisibility(visible ? View.VISIBLE : View.GONE);
            String role = LauncherPrefs.quickRole(this, i);
            ShortcutSlot.bind(this, quickButtons[i], LauncherPrefs.QUICK_KEYS[i]);
            if (visible) focus.add(quickButtons[i]);
        }
        focus.add(navigationButton);
        AutomotiveUi.linkVertical(focus);
        if (root != null) applyGeometry(root.getWidth(), root.getHeight());
        if (mapPanel != null) mapPanel.applyPreferences();
    }

    private void buildRadioPanel() {
        radioPanel = stripPanel();
        radioPanel.setBackground(AutomotiveUi.chipBackground(this));
        radioRole = controlButton(R.drawable.ic_radio, "Open radio source", false);
        radioPrevious = controlButton(R.drawable.ic_previous, "Previous station", false);
        radioPlayPause = controlButton(R.drawable.ic_play, "Play or pause radio", true);
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
        musicRole = controlButton(R.drawable.ic_music, "Open music source", false);
        mediaPrevious = controlButton(R.drawable.ic_previous, "Previous track", false);
        playPause = controlButton(R.drawable.ic_play, "Play or pause music", true);
        mediaNext = controlButton(R.drawable.ic_next, "Next track", false);
        musicRole.setOnClickListener(v -> openCurrentMedia());
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
        selectSource(MediaSelection.RADIO);
        MediaListenerService.sendRadio(command);
    }

    private void musicCommand(MediaListenerService.Command command) {
        selectSource(MediaSelection.MUSIC);
        if (!genericSnapshot.packageName.isEmpty()) LauncherPrefs.rememberMusic(this, genericSnapshot.packageName);
        MediaListenerService.sendGeneric(command);
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
        dateView.setBackground(AutomotiveUi.interactiveBackground(this, false));
        dateView.setContentDescription("Settings");
        AutomotiveUi.attachFeedback(dateView);
        dateView.setOnClickListener(v -> openSettings());
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

    private void updateMapVisibility() {
        if (!LauncherPrefs.mapEnabled(this)) {
            if (mapPanel != null) { mapPanel.stop(); mapPanel.setVisibility(View.GONE); }
            return;
        }
        if (mapPanel == null) {
            mapPanel = new MapPanel(this, this::openNavigation);
            root.addView(mapPanel);
            applyGeometry(root.getWidth(), root.getHeight());
        }
        mapPanel.applyPreferences();
        mapPanel.setVisibility(View.VISIBLE);
        if (appDrawerPanel != null && appDrawerPanel.isOpen()) { mapPanel.stop(); return; }
        requestMapLocationIfNeeded();
        mapPanel.resumeWebView();
    }

    private void requestMapLocationIfNeeded() {
        if (locationPermissionRequested || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) return;
        locationPermissionRequested = true;
        requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
    }

    private void applyGeometry(int width, int height) {
        if (width <= 0 || height <= 0) return;
        Ts18Geometry.Layout g = Ts18Geometry.resolve(width, height, LauncherPrefs.railOnRight(this), LauncherPrefs.radioOnRight(this));
        place(rail, g.railX, g.top, g.railWidth(), g.railHeight());
        place(radioPanel, g.radioX(), g.top, g.radioWidth, g.stripHeight);
        place(musicPanel, g.musicX(), g.top, g.musicWidth, g.stripHeight);
        place(mediaText, g.metadataX(), g.top, g.metadataWidth(), g.stripHeight);
        placeCard(dateView, g.dateX(), g.top, g.dateWidth(), g.stripHeight);
        if (mapPanel != null) place(mapPanel, g.mapX(), g.mapY(), g.mapWidth(), g.mapHeight());
        if (appDrawerPanel != null) place(appDrawerPanel, g.mapX(), g.mapY(), g.mapWidth(), g.mapHeight());
    }

    private void place(View view, int x, int y, int width, int height) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.max(1, width), Math.max(1, height));
        lp.leftMargin = Math.max(0, x);
        lp.topMargin = Math.max(0, y);
        view.setLayoutParams(lp);
    }

    private void placeCard(View view, int x, int y, int width, int height) {
        int inset = AutomotiveUi.dimen(this, R.dimen.ui_card_inset);
        place(view, x + inset, y + inset, Math.max(1, width - inset * 2), Math.max(1, height - inset * 2));
    }

    private void updateLabels() {
        String radioPackage = RadioProvider.resolvePackage(this);
        String radioLabel = AppResolver.labelFor(this, radioPackage,
                radioPackage.isEmpty() ? "Set radio" : "Radio");
        boolean exactRadioSession = !radioPackage.isEmpty() && radioPackage.equals(radioSnapshot.packageName);
        AutomotiveUi.setIconWithCrossfade(this, radioPlayPause,
                radioSnapshot.playing ? R.drawable.ic_pause : R.drawable.ic_play);
        setMediaButtonState(radioPrevious, exactRadioSession && radioSnapshot.supports(MediaListenerService.Command.PREVIOUS));
        setMediaButtonState(radioPlayPause, exactRadioSession && radioSnapshot.supports(MediaListenerService.Command.PLAY_PAUSE));
        setMediaButtonState(radioNext, exactRadioSession && radioSnapshot.supports(MediaListenerService.Command.NEXT));

        if (!MediaListenerService.hasNotificationAccess(this)) {
            if (MediaSelection.MUSIC.equals(mediaSelection.displayed())) mediaText.setMetadata("Grant media access", "Settings · Notification access");
            else mediaText.setMetadata(radioLabel, "Radio");
            AutomotiveUi.setIconWithCrossfade(this, playPause, R.drawable.ic_play);
            setMediaButtonState(mediaPrevious, false);
            setMediaButtonState(playPause, false);
            setMediaButtonState(mediaNext, false);
            return;
        }

        String fallback = LauncherPrefs.packageFor(this, LauncherPrefs.KEY_MUSIC);
        if (fallback.isEmpty()) fallback = TopwayAdapter.defaultMusicPackage(this);
        String sourcePackage = genericSnapshot.packageName.isEmpty() ? fallback : genericSnapshot.packageName;
        String sourceLabel = AppResolver.labelFor(this, sourcePackage,
                sourcePackage.isEmpty() ? "No active media session" : sourcePackage);
        boolean showRadio = MediaSelection.RADIO.equals(mediaSelection.displayed());
        MediaListenerService.Snapshot displayed = showRadio ? radioSnapshot : genericSnapshot;
        String label = showRadio ? radioLabel : sourceLabel;
        mediaText.setMetadata(primaryMetadata(displayed, label), secondaryMetadata(displayed, label));
        AutomotiveUi.setIconWithCrossfade(this, playPause,
                genericSnapshot.playing ? R.drawable.ic_pause : R.drawable.ic_play);
        setMediaButtonState(mediaPrevious, genericSnapshot.supports(MediaListenerService.Command.PREVIOUS));
        setMediaButtonState(playPause, genericSnapshot.supports(MediaListenerService.Command.PLAY_PAUSE));
        setMediaButtonState(mediaNext, genericSnapshot.supports(MediaListenerService.Command.NEXT));
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

    private void setMediaButtonState(ImageButton button, boolean enabled) {
        if (button == null) return;
        button.setEnabled(enabled);
    }

    private void openQuick(int index) {
        if (!ShortcutSlot.launch(this, LauncherPrefs.QUICK_KEYS[index])) openPicker(LauncherPrefs.QUICK_KEYS[index]);
        mediaSelection.select(LauncherPrefs.lastSource(this));
        updateLabels();
    }

    private void toggleAppDrawer() {
        if (appDrawerPanel == null) {
            appDrawerPanel = new AppDrawerPanel(this, () -> {
                if (mapPanel != null && LauncherPrefs.mapEnabled(this)) {
                    requestMapLocationIfNeeded();
                    mapPanel.resumeWebView();
                }
            });
            root.addView(appDrawerPanel);
            applyGeometry(root.getWidth(), root.getHeight());
        }
        if (appDrawerPanel.isOpen()) appDrawerPanel.hidePanel();
        else {
            if (mapPanel != null) mapPanel.stop();
            appDrawerPanel.refreshPreferences();
            appDrawerPanel.showPanel();
        }
    }

    @Override public void onMediaStateChanged(MediaListenerService.Snapshot genericMedia,
                                                MediaListenerService.Snapshot radio) {
        genericSnapshot = genericMedia == null ? new MediaListenerService.Snapshot("", "", "", false) : genericMedia;
        radioSnapshot = radio == null ? new MediaListenerService.Snapshot("", "", "", false) : radio;
        runOnUiThread(() -> {
            mediaSelection.reconcile(radioSnapshot.state == android.media.session.PlaybackState.STATE_PLAYING,
                    genericSnapshot.state == android.media.session.PlaybackState.STATE_PLAYING);
            updateLabels();
        });
    }

    private void openCurrentMedia() {
        selectSource(MediaSelection.MUSIC);
        if (!genericSnapshot.packageName.isEmpty() && AppResolver.launchPackage(this, genericSnapshot.packageName)) return;
        String fallback = LauncherPrefs.packageFor(this, LauncherPrefs.KEY_MUSIC);
        if (fallback.isEmpty()) fallback = TopwayAdapter.defaultMusicPackage(this);
        if (!AppResolver.launchPackage(this, fallback)) openSettings();
    }

    private void openNavigation(Location location) {
        String pkg = LauncherPrefs.packageFor(this, LauncherPrefs.KEY_NAV);
        if (!NavigationProvider.open(this, pkg, location)) openPicker(LauncherPrefs.KEY_NAV);
    }

    private void openConfigured(String key) {
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

    private void openSettings() { startActivity(new Intent(this, SettingsActivity.class)); }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VoiceSearch.REQUEST_CODE && resultCode == RESULT_OK && appDrawerPanel != null) {
            appDrawerPanel.applyVoiceSearch(VoiceSearch.firstResult(data));
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION && mapPanel != null) mapPanel.onLocationPermissionResult();
    }
}
