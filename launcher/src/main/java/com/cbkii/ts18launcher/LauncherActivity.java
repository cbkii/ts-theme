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
import android.widget.TextView;

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
    private TextView radioText;
    private TextView mediaText;
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
        appearanceController = new AppearanceController(this, mode -> runOnUiThread(this::recreate));

        root = new FrameLayout(this);
        root.setBackgroundColor(AutomotiveUi.color(this, R.color.ui_black));
        setContentView(root);
        buildRail();
        buildRadioPanel();
        buildMusicPanel();
        buildDate();
        root.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                applyGeometry(right - left, bottom - top));
        root.post(() -> applyGeometry(root.getWidth(), root.getHeight()));
    }

    @Override protected void onStart() {
        super.onStart();
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
            quickButtons[i].setImageResource(RoleIconCatalog.icon(role));
            quickButtons[i].setContentDescription(RoleIconCatalog.label(role));
            if (visible) focus.add(quickButtons[i]);
        }
        focus.add(navigationButton);
        AutomotiveUi.linkVertical(focus);
        if (root != null) applyGeometry(root.getWidth(), root.getHeight());
        if (mapPanel != null) mapPanel.applyPreferences();
    }

    private void buildRadioPanel() {
        radioPanel = stripPanel();
        ImageView role = roleIcon(R.drawable.ic_radio, "Radio");
        radioText = stripText("RADIO");
        radioPrevious = controlButton(R.drawable.ic_previous, "Previous station", false);
        radioPlayPause = controlButton(R.drawable.ic_play, "Play or pause radio", true);
        radioNext = controlButton(R.drawable.ic_next, "Next station", false);
        radioPrevious.setOnClickListener(v -> MediaListenerService.sendRadio(MediaListenerService.Command.PREVIOUS));
        radioPlayPause.setOnClickListener(v -> MediaListenerService.sendRadio(MediaListenerService.Command.PLAY_PAUSE));
        radioNext.setOnClickListener(v -> MediaListenerService.sendRadio(MediaListenerService.Command.NEXT));
        radioText.setOnClickListener(v -> openConfigured(LauncherPrefs.KEY_RADIO));
        radioText.setOnLongClickListener(v -> { openSettings(); return true; });
        radioPanel.addView(role, fixed(48));
        radioPanel.addView(radioText, weighted());
        radioPanel.addView(radioPrevious, fixed(84));
        radioPanel.addView(radioPlayPause, fixed(88));
        radioPanel.addView(radioNext, fixed(84));
        AutomotiveUi.linkHorizontal(java.util.Arrays.asList(radioPrevious, radioPlayPause, radioNext));
        root.addView(radioPanel);
    }

    private void buildMusicPanel() {
        musicPanel = stripPanel();
        ImageView role = roleIcon(R.drawable.ic_music, "Music");
        mediaText = stripText("Grant media access");
        mediaPrevious = controlButton(R.drawable.ic_previous, "Previous track", false);
        playPause = controlButton(R.drawable.ic_play, "Play or pause", true);
        mediaNext = controlButton(R.drawable.ic_next, "Next track", false);
        mediaPrevious.setOnClickListener(v -> MediaListenerService.sendGeneric(MediaListenerService.Command.PREVIOUS));
        playPause.setOnClickListener(v -> MediaListenerService.sendGeneric(MediaListenerService.Command.PLAY_PAUSE));
        mediaNext.setOnClickListener(v -> MediaListenerService.sendGeneric(MediaListenerService.Command.NEXT));
        mediaText.setOnClickListener(v -> openCurrentMedia());
        mediaText.setOnLongClickListener(v -> {
            if (!MediaListenerService.hasNotificationAccess(this)) {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } else openSettings();
            return true;
        });
        musicPanel.addView(role, fixed(48));
        musicPanel.addView(mediaText, weighted());
        musicPanel.addView(mediaPrevious, fixed(84));
        musicPanel.addView(playPause, fixed(88));
        musicPanel.addView(mediaNext, fixed(84));
        AutomotiveUi.linkHorizontal(java.util.Arrays.asList(mediaPrevious, playPause, mediaNext));
        root.addView(musicPanel);
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
        panel.setPadding(AutomotiveUi.dimen(this, R.dimen.driver_gap), 0,
                AutomotiveUi.dimen(this, R.dimen.ui_card_inset), 0);
        panel.setBackground(AutomotiveUi.cardBackground(this));
        return panel;
    }

    private ImageView roleIcon(int iconRes, String description) {
        ImageView view = new ImageView(this);
        view.setImageResource(iconRes);
        view.setColorFilter(AutomotiveUi.color(this, R.color.ui_icon));
        view.setContentDescription(description);
        int padding = AutomotiveUi.dimen(this, R.dimen.driver_gap);
        view.setPadding(padding, padding, padding, padding);
        return view;
    }

    private ImageButton controlButton(int icon, String description, boolean primary) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(icon);
        button.setContentDescription(description);
        AutomotiveUi.styleTransportButton(this, button, primary);
        return button;
    }

    private TextView stripText(String text) {
        SlowMarqueeTextView view = new SlowMarqueeTextView(this);
        view.setText(text);
        view.setTextColor(AutomotiveUi.color(this, R.color.ui_text));
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.ui_metadata_text));
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(AutomotiveUi.dimen(this, R.dimen.driver_gap), 0,
                AutomotiveUi.dimen(this, R.dimen.driver_gap), 0);
        return view;
    }

    private LinearLayout.LayoutParams fixed(int widthPx) {
        return new LinearLayout.LayoutParams(widthPx, LinearLayout.LayoutParams.MATCH_PARENT);
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
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
        Ts18Geometry.Layout g = Ts18Geometry.resolve(width, height, LauncherPrefs.railOnRight(this));
        place(rail, g.railX, g.top, g.railWidth(), g.railHeight());
        placeCard(radioPanel, g.radioX(), g.top, g.radioWidth, g.stripHeight);
        placeCard(musicPanel, g.musicX(), g.top, g.musicWidth, g.stripHeight);
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
        boolean exactRadioSession = !radioPackage.isEmpty() && radioPackage.equals(radioSnapshot.packageName);
        radioText.setText(exactRadioSession && !radioSnapshot.displayText().isEmpty()
                ? radioSnapshot.displayText()
                : AppResolver.labelFor(this, radioPackage, radioPackage.isEmpty() ? "Set radio" : "Radio"));
        radioPlayPause.setImageResource(radioSnapshot.playing ? R.drawable.ic_pause : R.drawable.ic_play);
        setMediaButtonState(radioPrevious, exactRadioSession && radioSnapshot.supports(MediaListenerService.Command.PREVIOUS));
        setMediaButtonState(radioPlayPause, exactRadioSession && radioSnapshot.supports(MediaListenerService.Command.PLAY_PAUSE));
        setMediaButtonState(radioNext, exactRadioSession && radioSnapshot.supports(MediaListenerService.Command.NEXT));

        if (!MediaListenerService.hasNotificationAccess(this)) {
            mediaText.setText("Grant media access");
            playPause.setImageResource(R.drawable.ic_play);
            setMediaButtonState(mediaPrevious, false);
            setMediaButtonState(playPause, false);
            setMediaButtonState(mediaNext, false);
            return;
        }
        String display = genericSnapshot.displayText();
        if (!display.isEmpty()) mediaText.setText(display);
        else {
            String fallback = LauncherPrefs.packageFor(this, LauncherPrefs.KEY_MUSIC);
            if (fallback.isEmpty()) fallback = TopwayAdapter.defaultMusicPackage(this);
            mediaText.setText(AppResolver.labelFor(this, fallback, "No active media session"));
        }
        playPause.setImageResource(genericSnapshot.playing ? R.drawable.ic_pause : R.drawable.ic_play);
        setMediaButtonState(mediaPrevious, genericSnapshot.supports(MediaListenerService.Command.PREVIOUS));
        setMediaButtonState(playPause, genericSnapshot.supports(MediaListenerService.Command.PLAY_PAUSE));
        setMediaButtonState(mediaNext, genericSnapshot.supports(MediaListenerService.Command.NEXT));
    }

    private void setMediaButtonState(ImageButton button, boolean enabled) {
        if (button == null) return;
        button.setEnabled(enabled);
    }

    private String resolveQuickPackage(int index) {
        String direct = LauncherPrefs.packageFor(this, LauncherPrefs.QUICK_KEYS[index]);
        if (!direct.isEmpty()) return direct;
        return RoleIconCatalog.fallbackPackage(this, LauncherPrefs.quickRole(this, index));
    }

    private void openQuick(int index) {
        String pkg = resolveQuickPackage(index);
        if (!AppResolver.launchPackage(this, pkg)) openPicker(LauncherPrefs.QUICK_KEYS[index]);
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
        runOnUiThread(this::updateLabels);
    }

    private void openCurrentMedia() {
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
