package com.cbkii.ts18launcher;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
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

    private FrameLayout root;
    private LinearLayout rail;
    private LinearLayout radioPanel;
    private LinearLayout musicPanel;
    private TextClock dateView;
    private TextView radioText;
    private TextView mediaText;
    private Button playPause;
    private MapPanel mapPanel;
    private boolean launchedAsHome;
    private MediaListenerService.Snapshot genericSnapshot =
            new MediaListenerService.Snapshot("", "", "", false);
    private MediaListenerService.Snapshot radioSnapshot =
            new MediaListenerService.Snapshot("", "", "", false);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        launchedAsHome = getIntent() != null
                && getIntent().hasCategory(Intent.CATEGORY_HOME);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        buildRail();
        buildRadioPanel();
        buildMusicPanel();
        buildDate();

        root.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                applyGeometry(right - left, bottom - top));
        root.post(() -> applyGeometry(root.getWidth(), root.getHeight()));

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQUEST_LOCATION);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        MediaListenerService.addObserver(this);
        root.post(this::updateMapVisibility);
        updateLabels();
    }

    @Override
    protected void onStop() {
        MediaListenerService.removeObserver(this);
        if (mapPanel != null) mapPanel.stop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (mapPanel != null) mapPanel.destroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (launchedAsHome || HomeMode.isDefaultHome(this)) {
            return;
        }
        super.onBackPressed();
    }

    private void buildRail() {
        rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER);
        rail.setBackgroundColor(0xFF090909);

        rail.addView(railButton("NAV", v -> openConfigured(LauncherPrefs.KEY_NAV),
                v -> {
                    openSettings();
                    return true;
                }));
        rail.addView(railButton("APPS", v -> startActivity(new Intent(this, AppDrawerActivity.class)),
                v -> {
                    openSettings();
                    return true;
                }));
        rail.addView(railButton("BT", v -> openConfigured(LauncherPrefs.KEY_BLUETOOTH),
                v -> {
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
        button.setTextSize(12f);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setOnClickListener(click);
        button.setOnLongClickListener(longClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        button.setLayoutParams(lp);
        return button;
    }

    private void buildRadioPanel() {
        radioPanel = stripPanel();
        Button previous = controlButton("‹");
        Button next = controlButton("›");
        radioText = stripText("RADIO");
        previous.setOnClickListener(v -> {
            if (!MediaListenerService.sendRadio(MediaListenerService.Command.PREVIOUS)) {
                openConfigured(LauncherPrefs.KEY_RADIO);
            }
        });
        next.setOnClickListener(v -> {
            if (!MediaListenerService.sendRadio(MediaListenerService.Command.NEXT)) {
                openConfigured(LauncherPrefs.KEY_RADIO);
            }
        });
        radioText.setOnClickListener(v -> openConfigured(LauncherPrefs.KEY_RADIO));
        radioText.setOnLongClickListener(v -> {
            openSettings();
            return true;
        });

        radioPanel.addView(previous, new LinearLayout.LayoutParams(52, LinearLayout.LayoutParams.MATCH_PARENT));
        radioPanel.addView(radioText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        radioPanel.addView(next, new LinearLayout.LayoutParams(52, LinearLayout.LayoutParams.MATCH_PARENT));
        root.addView(radioPanel);
    }

    private void buildMusicPanel() {
        musicPanel = stripPanel();
        Button previous = controlButton("‹");
        playPause = controlButton("▶");
        Button next = controlButton("›");
        mediaText = stripText("Grant media access");
        mediaText.setSingleLine(true);
        mediaText.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        mediaText.setMarqueeRepeatLimit(-1);
        mediaText.setSelected(true);

        previous.setOnClickListener(v ->
                MediaListenerService.sendGeneric(MediaListenerService.Command.PREVIOUS));
        playPause.setOnClickListener(v ->
                MediaListenerService.sendGeneric(MediaListenerService.Command.PLAY_PAUSE));
        next.setOnClickListener(v ->
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

        musicPanel.addView(previous, new LinearLayout.LayoutParams(52, LinearLayout.LayoutParams.MATCH_PARENT));
        musicPanel.addView(playPause, new LinearLayout.LayoutParams(56, LinearLayout.LayoutParams.MATCH_PARENT));
        musicPanel.addView(next, new LinearLayout.LayoutParams(52, LinearLayout.LayoutParams.MATCH_PARENT));
        musicPanel.addView(mediaText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
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
        dateView.setBackgroundColor(0xFF170C09);
        dateView.setOnClickListener(v -> openSettings());
        root.addView(dateView);
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
            mapPanel = new MapPanel(this, () -> openConfigured(LauncherPrefs.KEY_NAV));
            root.addView(mapPanel);
            applyGeometry(root.getWidth(), root.getHeight());
        }
        mapPanel.setVisibility(View.VISIBLE);
        mapPanel.resumeWebView();
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
        button.setTextSize(24f);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setBackgroundColor(Color.TRANSPARENT);
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
        if (mapPanel != null) {
            place(mapPanel, g.mapX(), g.mapY(), g.mapWidth(), g.mapHeight());
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
        String radioPackage = LauncherPrefs.packageFor(this, LauncherPrefs.KEY_RADIO);
        if (!radioSnapshot.displayText().isEmpty()) {
            radioText.setText(radioSnapshot.displayText());
        } else {
            radioText.setText(AppResolver.labelFor(this, radioPackage,
                    radioPackage.isEmpty() ? "SET RADIO" : "RADIO"));
        }

        if (!MediaListenerService.hasNotificationAccess(this)) {
            mediaText.setText("Grant media access");
            playPause.setText("▶");
            return;
        }
        String display = genericSnapshot.displayText();
        if (!display.isEmpty()) {
            mediaText.setText(display);
        } else {
            String fallbackPackage = LauncherPrefs.packageFor(this, LauncherPrefs.KEY_MUSIC);
            if (fallbackPackage.isEmpty()) {
                fallbackPackage = TopwayAdapter.defaultMusicPackage(this);
            }
            mediaText.setText(AppResolver.labelFor(
                    this, fallbackPackage, "No active media session"));
        }
        playPause.setText(genericSnapshot.playing ? "Ⅱ" : "▶");
    }

    @Override
    public void onMediaStateChanged(
            MediaListenerService.Snapshot genericMedia,
            MediaListenerService.Snapshot radio) {
        genericSnapshot = genericMedia == null
                ? new MediaListenerService.Snapshot("", "", "", false)
                : genericMedia;
        radioSnapshot = radio == null
                ? new MediaListenerService.Snapshot("", "", "", false)
                : radio;
        runOnUiThread(this::updateLabels);
    }

    private void openCurrentMedia() {
        if (!genericSnapshot.packageName.isEmpty()
                && AppResolver.launchPackage(this, genericSnapshot.packageName)) {
            return;
        }
        String fallback = LauncherPrefs.packageFor(this, LauncherPrefs.KEY_MUSIC);
        if (fallback.isEmpty()) fallback = TopwayAdapter.defaultMusicPackage(this);
        if (!AppResolver.launchPackage(this, fallback)) openSettings();
    }

    private void openConfigured(String key) {
        String pkg = LauncherPrefs.packageFor(this, key);
        if (LauncherPrefs.KEY_MUSIC.equals(key) && pkg.isEmpty()) {
            pkg = TopwayAdapter.defaultMusicPackage(this);
        }
        if (!AppResolver.launchPackage(this, pkg)) {
            openPicker(key);
        }
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
