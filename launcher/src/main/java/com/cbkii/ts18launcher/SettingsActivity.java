package com.cbkii.ts18launcher;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.cbkii.ts18launcher.platform.TopwayAdapter;

@SuppressLint("SetTextI18n")
public final class SettingsActivity extends Activity {
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(24, 16, 24, 24);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Ts18SafeArea.setContent(this, scroll);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content != null) render();
    }

    private void render() {
        content.removeAllViews();

        TextView title = text("TS18 Launcher settings", 26f, Color.WHITE);
        content.addView(title);

        TextView environment = text(
                TopwayAdapter.isTopwayEnvironment(this)
                        ? "Topway/DoFun environment detected. DoFun remains installed as recovery HOME."
                        : "Topway/DoFun host not detected. Generic Android paths remain available.",
                14f, 0xFFB9B9B9);
        content.addView(environment);

        addSection("HOME");
        addButton(HomeMode.isDefaultHome(this) ? "Current HOME: TS18 Launcher" : "Set as HOME (system UI)",
                v -> HomeMode.requestHomeRole(this));
        addButton("Set as HOME with Magisk root",
                v -> setHomeWithRoot());
        addButton("Disable HOME candidate / keep app installed",
                v -> {
                    HomeMode.setHomeAliasEnabled(this, false);
                    Toast.makeText(this,
                            "HOME alias disabled. App remains installed.",
                            Toast.LENGTH_LONG).show();
                });

        addSection("Quick launch");
        addPicker("Quick 1 (Navigation fallback)", LauncherPrefs.KEY_QUICK_1);
        addPicker("Quick 2 (Radio fallback)", LauncherPrefs.KEY_QUICK_2);
        addPicker("Quick 3 (Music fallback)", LauncherPrefs.KEY_QUICK_3);
        addPicker("Quick 4 (Bluetooth fallback)", LauncherPrefs.KEY_QUICK_4);
        content.addView(text(
                "The HOME rail has four configurable app slots plus Apps and Settings. "
                        + "An unset slot uses the corresponding role shown below.",
                13f, 0xFFB9B9B9));

        addSection("Apps and media");
        addButton(MediaListenerService.hasNotificationAccess(this)
                        ? "Notification access: granted"
                        : "Grant notification access",
                v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        addPicker("Navigation app", LauncherPrefs.KEY_NAV);
        addPicker("Radio app", LauncherPrefs.KEY_RADIO);
        addPicker("Bluetooth app", LauncherPrefs.KEY_BLUETOOTH);
        addPicker("Fallback music app", LauncherPrefs.KEY_MUSIC);

        addSection("Map");
        boolean mapEnabled = LauncherPrefs.mapEnabled(this);
        addButton("Dashboard map: " + (mapEnabled ? "ON" : "OFF"),
                v -> {
                    LauncherPrefs.setMapEnabled(this, !mapEnabled);
                    render();
                });

        TextView mapNote = text(
                "The map is a local lightweight WebView using OpenStreetMap raster tiles. "
                        + "It reuses its tile layer while panning, requests GPS only while visible, "
                        + "and hands OPEN NAV to the configured navigation app.",
                13f, 0xFFB9B9B9);
        content.addView(mapNote);

        addSection("Permissions");
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            addButton("Grant location permission",
                    v -> requestPermissions(
                            new String[] {
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                            }, 9201));
        } else {
            content.addView(text("Location permission: granted", 14f, 0xFFEDEDED));
        }
    }

    private void setHomeWithRoot() {
        Toast.makeText(this, "Requesting Magisk superuser…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            RootShell.Result result = HomeMode.setHomeWithRoot(this);
            runOnUiThread(() -> {
                if (result.success() && HomeMode.isDefaultHome(this)) {
                    Toast.makeText(this, "TS18 Launcher is now HOME", Toast.LENGTH_LONG).show();
                    render();
                } else {
                    String detail = result.output == null || result.output.isEmpty()
                            ? "root HOME command was not accepted"
                            : result.output;
                    Toast.makeText(this,
                            detail + "; opening Android HOME settings",
                            Toast.LENGTH_LONG).show();
                    HomeMode.requestHomeRole(this);
                }
            });
        }, "ts18-root-home").start();
    }

    private void addPicker(String label, String key) {
        String pkg = LauncherPrefs.packageFor(this, key);
        String current;
        if (pkg.isEmpty() && LauncherPrefs.KEY_MUSIC.equals(key)) {
            String topwayDefault = TopwayAdapter.defaultMusicPackage(this);
            current = topwayDefault.isEmpty()
                    ? "not set"
                    : AppResolver.labelFor(this, topwayDefault, topwayDefault) + " (Topway fallback)";
        } else {
            current = pkg.isEmpty() ? "not set" : AppResolver.labelFor(this, pkg, pkg);
        }
        addButton(label + ": " + current, v -> {
            Intent intent = new Intent(this, AppDrawerActivity.class);
            intent.putExtra(AppDrawerActivity.EXTRA_PICK_KEY, key);
            startActivity(intent);
        });
    }

    private void addSection(String title) {
        TextView view = text(title, 17f, 0xFFFF8A65);
        view.setPadding(0, 18, 0, 4);
        content.addView(view);
    }

    private void addButton(String label, android.view.View.OnClickListener listener) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15f);
        button.setBackgroundColor(0xFF2A1712);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 58);
        lp.topMargin = 6;
        content.addView(button, lp);
    }

    private TextView text(String value, float sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sp);
        view.setPadding(0, 6, 0, 6);
        return view;
    }
}
