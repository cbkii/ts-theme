package com.cbkii.ts18launcher;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import com.cbkii.ts18launcher.platform.TopwayAdapter;

@SuppressLint("SetTextI18n")
public class LauncherActivity extends Activity implements MediaListenerService.Observer {
    private static final int REQUEST_LOCATION = 4101;
    private static final int QUICK_SLOT_COUNT = 4;
    private static final long MEDIA_REFRESH_INTERVAL_MS = 1000L;

    private FrameLayout root;
    private LinearLayout rail;
    private LinearLayout radioPanel;
    private LinearLayout musicPanel;
    private TextClock dateView;
    private TextView radioText;
    private TextView mediaText;
    private Button radioPrevious;
    private Button radioPlayPause;
    private Button radioNext;
    private Button mediaPrevious;
    private Button playPause;
    private Button mediaNext;
    private final Button[] quickButtons = new Button[QUICK_SLOT_COUNT];
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
        root.setBackgroundColor(Color.BLACK);
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
        updateQuickLabels();
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
        rail.setBackgroundColor(0xFF090909);

        for (int i = 0; i < QUICK_SLOT_COUNT; i++) {
            final int index = i;
            Button button = railButton("APP" + (i + 1),
                    v -> openQuick(index),
                    v -> {
                        openPicker(LauncherPrefs.QUICK_KEYS[index]);
                        return true;
                    });
            quickButtons[i] = button;
            rail.addView(button);
        }
        rail.addView(railButton("APPS", v -> toggleAppDrawer(), v -> {
            openSettings();
            return true;
        }));
        rail.addView(railButton("SET", v -> openSettings(), v -> {
            openSettings();
            return true;
        }));
        root.addView(rail);
    }

    private Button railButton(String text, View.OnClickListener click, View.OnLongClickListener longClick) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13f);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(4, 0, 4, 0);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setBackground(touchFeedback());
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setOnClickListener(click);
        button.setOnLongClickListener(longClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        button.setLayoutParams(lp);
        return button;
    }

    private void buildRadioPanel() {
        radioPanel = stripPanel();
        radioPrevious = controlButton("‹");
        radioPlayPause = controlButton("▶");
        radioNext = controlButton("›");
        radioText = stripText("RADIO");
        radioText.setSingleLine(true);
        radioText.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        radioText.setMarqueeRepeatLimit(-1);
        radioText.setSelected(true);

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

        radioPanel.addView(radioPrevious,
                new LinearLayout.LayoutParams(56, LinearLayout.LayoutParams.MATCH_PARENT));
        radioPanel.addView(radioPlayPause,
                new LinearLayout.LayoutParams(60, LinearLayout.LayoutParams.MATCH_PARENT));
        radioPanel.addView(radioNext,
                new LinearLayout.LayoutParams(56, LinearLayout.LayoutParams.MATCH_PARENT));
        radioPanel.addView(radioText,
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        root.addView(radioPanel);
    }

    private void buildMusicPanel() {
        musicPanel = stripPanel();
        mediaPrevious = controlButton("‹");
        playPause = controlButton("▶");
        mediaNext = controlButton("›");
        mediaText = stripText("Grant media access");
        mediaText.setSingleLine(true);
        mediaText.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        mediaText.setMarqueeRepeatLimit(-1);
        mediaText.setSelected(true);

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

        musicPanel.addView(mediaPrevious,
                new LinearLayout.LayoutParams(56, LinearLayout.LayoutParams.MATCH_PARENT));
        musicPanel.addView(playPause,
                new LinearLayout.LayoutParams(60, LinearLayout.LayoutParams.MATCH_PARENT));
        musicPanel.addView(mediaNext,
                new LinearLayout.LayoutParams(56, LinearLayout.LayoutParams.MATCH_PARENT));
        musicPanel.addView(mediaText,
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        root.addView(musicPanel);
    }

    private void buildDate() {
        dateView = new TextClock(this);
        dateView.setFormat12Hour("dd MMM");
        dateView.setFormat24Hour("dd MMM");
        dateView.setTextColor(Color.WHITE);
        dateView.setTextSize(20f);
        dateView.setTypeface(Typeface.DEFAULT_BOLD);
        dateView.setGravity(Gravity.CENTER);
        dateView.setBackground(touchFeedback(0xFF170C09));
        dateView.setOnClickListener(v -> openSettings());
        root.addView(dateView);
    }

    private RippleDrawable touchFeedback() {
        return touchFeedback(Color.TRANSPARENT);
    }

    private RippleDrawable touchFeedback(int baseColor) {
        return new RippleDrawable(
                ColorStateList.valueOf(0x55FFFFFF),
                new ColorDrawable(baseColor),
                new ColorDrawable(Color.WHITE));
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

    private LinearLayout stripPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setBackgroundColor(0xFF120A08);
        return panel;
    }

    private Button controlButton(String text) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(28f);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setBackground(touchFeedback());
        return button;
    }

    private TextView stripText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(16f);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(10, 0, 10, 0);
        return view;
    }

    private void applyGeometry(int width, int height) {
        if (width <= 0 || height <= 0) return;
        Ts18Geometry.Layout g = Ts18Geometry.resolve(width, height);

        place(rail, 0, g.top, g.left, g.railHeight());
        place(radioPanel, g.radioX(), g.top, g.radioWidth, g.stripHeight);
        place(musicPanel, g.musicX(), g.top, g.musicWidth, g.stripHeight);
        place(dateView, g.dateX(), g.top, g.dateWidth(), g.stripHeight);
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

    private void updateLabels() {
        String radioPackage = RadioProvider.resolvePackage(this);
        boolean exactRadioSession = !radioPackage.isEmpty()
                && radioPackage.equals(radioSnapshot.packageName);
        if (exactRadioSession && !radioSnapshot.displayText().isEmpty()) {
            radioText.setText(radioSnapshot.displayText());
        } else {
            radioText.setText(AppResolver.labelFor(this, radioPackage,
                    radioPackage.isEmpty() ? "SET RADIO" : "RADIO"));
        }
        radioPlayPause.setText(radioSnapshot.playing ? "Ⅱ" : "▶");
        setMediaButtonState(radioPrevious,
                exactRadioSession && radioSnapshot.supports(MediaListenerService.Command.PREVIOUS));
        setMediaButtonState(radioPlayPause,
                exactRadioSession && radioSnapshot.supports(MediaListenerService.Command.PLAY_PAUSE));
        setMediaButtonState(radioNext,
                exactRadioSession && radioSnapshot.supports(MediaListenerService.Command.NEXT));

        if (!MediaListenerService.hasNotificationAccess(this)) {
            mediaText.setText("Grant media access");
            playPause.setText("▶");
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
        playPause.setText(genericSnapshot.playing ? "Ⅱ" : "▶");
        setMediaButtonState(mediaPrevious,
                genericSnapshot.supports(MediaListenerService.Command.PREVIOUS));
        setMediaButtonState(playPause,
                genericSnapshot.supports(MediaListenerService.Command.PLAY_PAUSE));
        setMediaButtonState(mediaNext,
                genericSnapshot.supports(MediaListenerService.Command.NEXT));
    }

    private void setMediaButtonState(Button button, boolean enabled) {
        if (button == null) return;
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.35f);
    }

    private void updateQuickLabels() {
        for (int i = 0; i < QUICK_SLOT_COUNT; i++) {
            String fallback = "APP" + (i + 1);
            String pkg = resolveQuickPackage(i);
            String label = AppResolver.labelFor(this, pkg, fallback);
            if (label.length() > 9) label = label.substring(0, 9);
            quickButtons[i].setText(label);
        }
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
