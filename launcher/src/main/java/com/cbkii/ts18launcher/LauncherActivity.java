package com.cbkii.ts18launcher;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import com.cbkii.ts18launcher.platform.TopwayAdapter;

@SuppressLint("SetTextI18n")
public class LauncherActivity extends Activity implements MediaListenerService.Observer {
    private static final int REQUEST_LOCATION = 4101;
    private static final int MAX_QUICK_SLOTS = 6;
    private static final long MEDIA_REFRESH_INTERVAL_MS = 1000L;

    private FrameLayout root;
    private LinearLayout rail;
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
        @Override
        public void run() {
            if (!mediaRefreshPolling) return;
            MediaListenerService.refreshActiveSessions();
            mediaRefreshHandler.postDelayed(this, MEDIA_REFRESH_INTERVAL_MS);
        }
    };
    private MapPanel mapPanel;
    private AppDrawerPanel appDrawerPanel;
    private boolean launchedAsHome;
    private boolean locationPermissionRequested;
    private boolean mediaRefreshPolling;
    private MediaListenerService.Snapshot genericSnapshot =
            new MediaListenerService.Snapshot("", "", "", false);
    private MediaListenerService.Snapshot radioSnapshot =
            new MediaListenerService.Snapshot("", "", "", false);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        launchedAsHome = getIntent() != null && getIntent().hasCategory(Intent.CATEGORY_HOME);

        root = new FrameLayout(this);
        root.setBackgroundColor(AutomotiveUi.color(this, R.color.ui_black));
        setContentView(root);

        buildRail();
        buildRadioPanel();
        buildMusicPanel();
        buildDate();

        root.addOnLayoutChangeListener((v, left, top, right, bottom,
                                        oldLeft, oldTop, oldRight, oldBottom) ->
                applyGeometry(right - left, bottom - top));
        root.post(() -> applyGeometry(root.getWidth(), root.getHeight()));
    }

    @Override
    protected void onStart() {
        super.onStart();
        MediaListenerService.addObserver(this);
        MediaListenerService.refreshActiveSessions();
        startMediaRefreshPolling();
        applyRailConfiguration();
        if (appDrawerPanel != null) appDrawerPanel.refreshPreferences();
        root.post(this::updateMapVisibility);
        updateLabels();
    }

    @Override
    protected void onStop() {
        stopMediaRefreshPolling();
        MediaListenerService.removeObserver(this);
        if (appDrawerPanel != null && appDrawerPanel.isOpen()) {
            appDrawerPanel.setVisibility(View.GONE);
        }
        if (mapPanel != null) mapPanel.stop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        stopMediaRefreshPolling();
        if (mapPanel != null) mapPanel.destroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
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
        rail.setGravity(Gravity.CENTER);
        rail.setPadding(AutomotiveUi.dimen(this, R.dimen.ui_card_inset),
                AutomotiveUi.dimen(this, R.dimen.ui_card_inset),
                AutomotiveUi.dimen(this, R.dimen.ui_card_inset),
                AutomotiveUi.dimen(this, R.dimen.ui_card_inset));
        rail.setBackgroundColor(AutomotiveUi.color(this, R.color.ui_black));

        for (int i = 0; i < MAX_QUICK_SLOTS; i++) {
            final int index = i;
            ImageButton button = railButton(AutomotiveUi.quickRoleIcon(i), "Quick app " + (i + 1));
            button.setOnClickListener(v -> openQuick(index));
            button.setOnLongClickListener(v -> {
                openPicker(LauncherPrefs.QUICK_KEYS[index]);
                return true;
            });
            quickButtons[i] = button;
            rail.addView(button);
        }
        ImageButton apps = railButton(R.drawable.ic_apps, "Apps");
        apps.setOnClickListener(v -> toggleAppDrawer());
        rail.addView(apps);
        root.addView(rail);
    }

    private ImageButton railButton(int icon, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(icon);
        button.setContentDescription(description);
        AutomotiveUi.styleIconButton(this, button, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        int gap = AutomotiveUi.dimen(this, R.dimen.ui_card_inset);
        lp.topMargin = gap;
        lp.bottomMargin = gap;
        button.setLayoutParams(lp);
        return button;
    }

    private void applyRailConfiguration() {
        int count = LauncherPrefs.quickCount(this);
        for (int i = 0; i < MAX_QUICK_SLOTS; i++) {
            quickButtons[i].setVisibility(i < count ? View.VISIBLE : View.GONE);
            quickButtons[i].setImageResource(AutomotiveUi.quickRoleIcon(i));
        }
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

        radioPrevious.setOnClickListener(v ->
                MediaListenerService.sendRadio(MediaListenerService.Command.PREVIOUS));
        radioPlayPause.setOnClickListener(v ->
                MediaListenerService.sendRadio(MediaListenerService.Command.PLAY_PAUSE));
        radioNext.setOnClickListener(v ->
                MediaListenerService.sendRadio(MediaListenerService.Command.NEXT));
        radioText.setOnClickListener(v -> openConfigured(LauncherPrefs.KEY_RADIO));
        radioText.setOnLongClickListener(v -> {
            openSettings();
            return true;
        });

        radioPanel.addView(role, fixed(56));
        radioPanel.addView(radioText, weighted());
        radioPanel.addView(radioPrevious, fixed(84));
        radioPanel.addView(radioPlayPause, fixed(88));
        radioPanel.addView(radioNext, fixed(84));
        root.addView(radioPanel);
    }

    private void buildMusicPanel() {
        musicPanel = stripPanel();
        ImageView role = roleIcon(R.drawable.ic_music, "Music");
        mediaText = stripText("Grant media access");
        mediaPrevious = controlButton(R.drawable.ic_previous, "Previous track", false);
        playPause = controlButton(R.drawable.ic_play, "Play or pause", true);
        mediaNext = controlButton(R.drawable.ic_next, "Next track", false);

        mediaPrevious.setOnClickListener(v ->
                MediaListenerService.sendGeneric(MediaListenerService.Command.PREVIOUS));
        playPause.setOnClickListener(v ->
                MediaListenerService.sendGeneric(MediaListenerService.Command.PLAY_PAUSE));
        mediaNext.setOnClickListener(v ->
                MediaListenerService.sendGeneric(MediaListenerService.Command.NEXT));
        mediaText.setOnClickListener(v -> openCurrentMedia());
        mediaText.setOnLongClickListener(v -> {
            if (!MediaListenerService.hasNotificationAccess(this)) {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } else {
                openSettings();
            }
            return true;
        });

        musicPanel.addView(role, fixed(56));
        musicPanel.addView(mediaText, weighted());
        musicPanel.addView(mediaPrevious, fixed(84));
        musicPanel.addView(playPause, fixed(88));
        musicPanel.addView(mediaNext, fixed(84));
        root.addView(musicPanel);
    }

    private void buildDate() {
        dateView = new TextClock(this);
        dateView.setFormat12Hour("dd MMM");
        dateView.setFormat24Hour("dd MMM");
        dateView.setTextColor(AutomotiveUi.color(this, R.color.ui_text));
        dateView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.ui_date_text));
        dateView.setTypeface(Typeface.DEFAULT_BOLD);
        dateView.setGravity(Gravity.CENTER);
        dateView.setBackground(AutomotiveUi.interactiveBackground(this, false));
        AutomotiveUi.attachFeedback(dateView);
        dateView.setOnClickListener(v -> openSettings());
        root.addView(dateView);
    }

    private LinearLayout stripPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(AutomotiveUi.dimen(this, R.dimen.ui_gutter), 0,
                AutomotiveUi.dimen(this, R.dimen.ui_card_inset), 0);
        panel.setBackground(AutomotiveUi.cardBackground(this));
        return panel;
    }

    private ImageView roleIcon(int iconRes, String description) {
        ImageView view = new ImageView(this);
        view.setImageResource(iconRes);
        view.setColorFilter(AutomotiveUi.color(this, R.color.ui_icon));
        view.setContentDescription(description);
        int padding = AutomotiveUi.dimen(this, R.dimen.ui_gutter);
        view.setPadding(padding, padding, padding, padding);
        return view;
    }

    private ImageButton controlButton(int icon, String description, boolean primary) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(icon);
        button.setContentDescription(description);
        AutomotiveUi.styleIconButton(this, button, primary);
        return button;
    }

    private TextView stripText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(AutomotiveUi.color(this, R.color.ui_text));
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.ui_metadata_text));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(AutomotiveUi.dimen(this, R.dimen.ui_gutter), 0,
                AutomotiveUi.dimen(this, R.dimen.ui_gutter), 0);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        view.setMarqueeRepeatLimit(-1);
        view.setSelected(true);
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
            if (mapPanel != null) {
                mapPanel.stop();
                mapPanel.setVisibility(View.GONE);
            }
            return;
        }
        if (mapPanel == null) {
            mapPanel = new MapPanel(this, this::openNavigation);
            root.addView(mapPanel);
            applyGeometry(root.getWidth(), root.getHeight());
        }
        mapPanel.applyPreferences();
        mapPanel.setVisibility(View.VISIBLE);
        if (appDrawerPanel != null && appDrawerPanel.isOpen()) {
            mapPanel.stop();
            return;
        }
        requestMapLocationIfNeeded();
        mapPanel.resumeWebView();
    }

    private void requestMapLocationIfNeeded() {
        if (locationPermissionRequested
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) return;
        locationPermissionRequested = true;
        requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
    }

    private void applyGeometry(int width, int height) {
        if (width <= 0 || height <= 0) return;
        boolean railRight = LauncherPrefs.railOnRight(this);
        Ts18Geometry.Layout g = Ts18Geometry.resolve(width, height, railRight);

        place(rail, g.railX, g.top, g.railWidth(), g.railHeight());
        placeCard(radioPanel, g.radioX(), g.top, g.radioWidth, g.stripHeight);
        placeCard(musicPanel, g.musicX(), g.top, g.musicWidth, g.stripHeight);
        placeCard(dateView, g.dateX(), g.top, g.dateWidth(), g.stripHeight);
        if (mapPanel != null) place(mapPanel, g.mapX(), g.mapY(), g.mapWidth(), g.mapHeight());
        if (appDrawerPanel != null) {
            place(appDrawerPanel, g.mapX(), g.mapY(), g.mapWidth(), g.mapHeight());
        }
    }

    private void place(View view, int x, int y, int width, int height) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                Math.max(1, width), Math.max(1, height));
        lp.leftMargin = Math.max(0, x);
        lp.topMargin = Math.max(0, y);
        view.setLayoutParams(lp);
    }

    private void placeCard(View view, int x, int y, int width, int height) {
        int inset = AutomotiveUi.dimen(this, R.dimen.ui_card_inset);
        place(view, x + inset, y + inset,
                Math.max(1, width - inset * 2), Math.max(1, height - inset * 2));
    }

    private void updateLabels() {
        String radioPackage = RadioProvider.resolvePackage(this);
        boolean exactRadioSession = !radioPackage.isEmpty()
                && radioPackage.equals(radioSnapshot.packageName);
        if (exactRadioSession && !radioSnapshot.displayText().isEmpty()) {
            radioText.setText(radioSnapshot.displayText());
        } else {
            radioText.setText(AppResolver.labelFor(this, radioPackage,
                    radioPackage.isEmpty() ? "Set radio" : "Radio"));
        }
        radioPlayPause.setImageResource(radioSnapshot.playing ? R.drawable.ic_pause : R.drawable.ic_play);
        setMediaButtonState(radioPrevious,
                exactRadioSession && radioSnapshot.supports(MediaListenerService.Command.PREVIOUS));
        setMediaButtonState(radioPlayPause,
                exactRadioSession && radioSnapshot.supports(MediaListenerService.Command.PLAY_PAUSE));
        setMediaButtonState(radioNext,
                exactRadioSession && radioSnapshot.supports(MediaListenerService.Command.NEXT));

        if (!MediaListenerService.hasNotificationAccess(this)) {
            mediaText.setText("Grant media access");
            playPause.setImageResource(R.drawable.ic_play);
            setMediaButtonState(mediaPrevious, false);
            setMediaButtonState(playPause, false);
            setMediaButtonState(mediaNext, false);
            return;
        }

        String display = genericSnapshot.displayText();
        if (!display.isEmpty()) {
            mediaText.setText(display);
        } else {
            String fallbackPackage = LauncherPrefs.packageFor(this, LauncherPrefs.KEY_MUSIC);
            if (fallbackPackage.isEmpty()) fallbackPackage = TopwayAdapter.defaultMusicPackage(this);
            mediaText.setText(AppResolver.labelFor(
                    this, fallbackPackage, "No active media session"));
        }
        playPause.setImageResource(genericSnapshot.playing ? R.drawable.ic_pause : R.drawable.ic_play);
        setMediaButtonState(mediaPrevious,
                genericSnapshot.supports(MediaListenerService.Command.PREVIOUS));
        setMediaButtonState(playPause,
                genericSnapshot.supports(MediaListenerService.Command.PLAY_PAUSE));
        setMediaButtonState(mediaNext,
                genericSnapshot.supports(MediaListenerService.Command.NEXT));
    }

    private void setMediaButtonState(ImageButton button, boolean enabled) {
        if (button == null) return;
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.35f);
    }

    private String resolveQuickPackage(int index) {
        String direct = LauncherPrefs.packageFor(this, LauncherPrefs.QUICK_KEYS[index]);
        if (!direct.isEmpty()) return direct;
        switch (index) {
            case 0:
                return LauncherPrefs.packageFor(this, LauncherPrefs.KEY_NAV);
            case 1:
                return RadioProvider.resolvePackage(this);
            case 2:
                String music = LauncherPrefs.packageFor(this, LauncherPrefs.KEY_MUSIC);
                return music.isEmpty() ? TopwayAdapter.defaultMusicPackage(this) : music;
            case 3:
                return LauncherPrefs.packageFor(this, LauncherPrefs.KEY_BLUETOOTH);
            default:
                return "";
        }
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
        if (appDrawerPanel.isOpen()) {
            appDrawerPanel.hidePanel();
        } else {
            if (mapPanel != null) mapPanel.stop();
            appDrawerPanel.refreshPreferences();
            appDrawerPanel.showPanel();
        }
    }

    @Override
    public void onMediaStateChanged(
            MediaListenerService.Snapshot genericMedia,
            MediaListenerService.Snapshot radio) {
        genericSnapshot = genericMedia == null
                ? new MediaListenerService.Snapshot("", "", "", false) : genericMedia;
        radioSnapshot = radio == null
                ? new MediaListenerService.Snapshot("", "", "", false) : radio;
        runOnUiThread(this::updateLabels);
    }

    private void openCurrentMedia() {
        if (!genericSnapshot.packageName.isEmpty()
                && AppResolver.launchPackage(this, genericSnapshot.packageName)) return;
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
        if (LauncherPrefs.KEY_RADIO.equals(key)) {
            pkg = RadioProvider.resolvePackage(this);
        } else {
            pkg = LauncherPrefs.packageFor(this, key);
            if (LauncherPrefs.KEY_MUSIC.equals(key) && pkg.isEmpty()) {
                pkg = TopwayAdapter.defaultMusicPackage(this);
            }
        }
        if (!AppResolver.launchPackage(this, pkg)) openPicker(key);
    }

    private void openPicker(String key) {
        Intent intent = new Intent(this, AppDrawerActivity.class);
        intent.putExtra(AppDrawerActivity.EXTRA_PICK_KEY, key);
        startActivity(intent);
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION && mapPanel != null) {
            mapPanel.onLocationPermissionResult();
        }
    }
}
