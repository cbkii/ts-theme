package com.cbkii.ts18launcher;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.cbkii.ts18launcher.platform.TopwayAdapter;

@SuppressLint("SetTextI18n")
public final class SettingsActivity extends Activity {
    private interface ChoiceSetter { void set(String value); }
    private interface BooleanSetter { void set(boolean value); }

    private LinearLayout content;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(AutomotiveUi.color(this, R.color.ui_black));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int gutter = AutomotiveUi.dimen(this, R.dimen.ui_gutter);
        content.setPadding(gutter * 2, gutter, gutter * 2, gutter * 2);
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
        content.addView(text("TS18 Launcher", R.dimen.ui_settings_title, R.color.ui_text));
        content.addView(text(
                TopwayAdapter.isTopwayEnvironment(this)
                        ? "Automotive UI · DoFun retained as recovery HOME"
                        : "Automotive UI · generic Android mode",
                R.dimen.ui_settings_value, R.color.ui_text_secondary));

        addSection("Appearance");
        addChoiceRow(R.drawable.ic_apps, "Rail position", railPositionLabel(),
                v -> choose("Rail position",
                        new String[] {"Driver side (right)", "Left", "Right"},
                        new String[] {LauncherPrefs.RAIL_DRIVER, LauncherPrefs.RAIL_LEFT, LauncherPrefs.RAIL_RIGHT},
                        LauncherPrefs.railPosition(this), value -> {
                            LauncherPrefs.setRailPosition(this, value);
                            render();
                        }));
        addChoiceRow(R.drawable.ic_shortcut, "Quick-launch slots",
                Integer.toString(LauncherPrefs.quickCount(this)),
                v -> choose("Quick-launch slots",
                        new String[] {"3", "4", "5", "6"},
                        new String[] {"3", "4", "5", "6"},
                        Integer.toString(LauncherPrefs.quickCount(this)), value -> {
                            LauncherPrefs.setQuickCount(this, Integer.parseInt(value));
                            render();
                        }));
        addSwitchRow(R.drawable.ic_navigation, "Dashboard map", "Show the Leaflet map on HOME",
                LauncherPrefs.mapEnabled(this), checked -> LauncherPrefs.setMapEnabled(this, checked));
        addSwitchRow(R.drawable.ic_my_location, "Map controls", "Show zoom, follow and open-navigation buttons",
                LauncherPrefs.mapControlsEnabled(this), checked -> LauncherPrefs.setMapControlsEnabled(this, checked));
        addChoiceRow(R.drawable.ic_my_location, "Map appearance", mapAppearanceLabel(),
                v -> choose("Map appearance",
                        new String[] {"Auto", "Normal", "Dim"},
                        new String[] {LauncherPrefs.MAP_APPEARANCE_AUTO,
                                LauncherPrefs.MAP_APPEARANCE_NORMAL,
                                LauncherPrefs.MAP_APPEARANCE_DIM},
                        LauncherPrefs.mapAppearance(this), value -> {
                            LauncherPrefs.setMapAppearance(this, value);
                            render();
                        }));

        addSection("HOME quick launch");
        for (int i = 0; i < LauncherPrefs.QUICK_KEYS.length; i++) {
            addPickerRow(AutomotiveUi.quickRoleIcon(i), quickLabel(i), LauncherPrefs.QUICK_KEYS[i]);
        }

        addSection("Drawer quick access");
        for (int i = 0; i < LauncherPrefs.DRAWER_QUICK_KEYS.length; i++) {
            addPickerRow(AutomotiveUi.drawerQuickRoleIcon(i), drawerQuickLabel(i),
                    LauncherPrefs.DRAWER_QUICK_KEYS[i]);
        }

        addSection("Media");
        addActionRow(R.drawable.ic_music, "Notification access",
                MediaListenerService.hasNotificationAccess(this) ? "Granted" : "Required for media sessions",
                v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        addChoiceRow(R.drawable.ic_music, "Generic media selection", mediaModeLabel(),
                v -> choose("Generic media selection",
                        new String[] {"Auto", "Prefer music app"},
                        new String[] {LauncherPrefs.MEDIA_MODE_AUTO, LauncherPrefs.MEDIA_MODE_PREFER_MUSIC},
                        LauncherPrefs.mediaMode(this), value -> {
                            LauncherPrefs.setMediaMode(this, value);
                            MediaListenerService.refreshActiveSessions();
                            render();
                        }));
        addPickerRow(R.drawable.ic_music, "Preferred / fallback music", LauncherPrefs.KEY_MUSIC);
        addPickerRow(R.drawable.ic_radio, "Radio app", LauncherPrefs.KEY_RADIO);
        addActionRow(R.drawable.ic_shortcut, "Media session diagnostics", "Read-only active-session view",
                v -> showMediaDiagnostics());

        addSection("App roles");
        addPickerRow(R.drawable.ic_navigation, "Navigation app", LauncherPrefs.KEY_NAV);
        addPickerRow(R.drawable.ic_bluetooth, "Bluetooth app", LauncherPrefs.KEY_BLUETOOTH);

        addSection("Permissions");
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            addActionRow(R.drawable.ic_my_location, "Location permission", "Required for the HOME map",
                    v -> requestPermissions(new String[] {
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    }, 9201));
        } else {
            addInfoRow(R.drawable.ic_my_location, "Location permission", "Granted");
        }

        addSection("Advanced HOME / recovery");
        addActionRow(R.drawable.ic_settings,
                HomeMode.isDefaultHome(this) ? "Current HOME: TS18 Launcher" : "Set as HOME",
                "Use Android HOME selection UI", v -> HomeMode.requestHomeRole(this));
        addActionRow(R.drawable.ic_settings, "Set as HOME with Magisk root",
                "Bounded one-time root command; DoFun remains installed", v -> confirmRootHome());
        addActionRow(R.drawable.ic_close, "Disable HOME candidate",
                "Keep the application installed and retain DoFun recovery", v -> confirmDisableHome());
    }

    private void addSection(String title) {
        TextView view = text(title, R.dimen.ui_settings_section, R.color.ui_accent);
        int gutter = AutomotiveUi.dimen(this, R.dimen.ui_gutter);
        view.setPadding(0, gutter * 2, 0, gutter / 2);
        content.addView(view);
    }

    private void addSwitchRow(int icon, String title, String value, boolean checked,
                              BooleanSetter listener) {
        LinearLayout row = baseRow(icon, title, value);
        Switch toggle = new Switch(this);
        toggle.setChecked(checked);
        toggle.setShowText(false);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> listener.set(isChecked));
        row.addView(toggle, new LinearLayout.LayoutParams(88, ViewGroup.LayoutParams.MATCH_PARENT));
        row.setBackground(AutomotiveUi.interactiveBackground(this, false));
        row.setFocusable(true);
        row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
        AutomotiveUi.attachFeedback(row);
        addRow(row);
    }

    private void addPickerRow(int icon, String label, String key) {
        String pkg = LauncherPrefs.packageFor(this, key);
        String current = pkg.isEmpty() ? pickerFallback(key) : AppResolver.labelFor(this, pkg, pkg);
        addChoiceRow(icon, label, current, v -> {
            Intent intent = new Intent(this, AppDrawerActivity.class);
            intent.putExtra(AppDrawerActivity.EXTRA_PICK_KEY, key);
            startActivity(intent);
        });
    }

    private void addChoiceRow(int icon, String title, String value, View.OnClickListener listener) {
        LinearLayout row = baseRow(icon, title, value);
        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron_right);
        chevron.setColorFilter(AutomotiveUi.color(this, R.color.ui_icon));
        int pad = AutomotiveUi.dimen(this, R.dimen.ui_gutter) * 2;
        chevron.setPadding(pad, pad, pad, pad);
        row.addView(chevron, new LinearLayout.LayoutParams(64, ViewGroup.LayoutParams.MATCH_PARENT));
        row.setBackground(AutomotiveUi.interactiveBackground(this, false));
        row.setFocusable(true);
        row.setOnClickListener(listener);
        AutomotiveUi.attachFeedback(row);
        addRow(row);
    }

    private void addActionRow(int icon, String title, String value, View.OnClickListener listener) {
        addChoiceRow(icon, title, value, listener);
    }

    private void addInfoRow(int icon, String title, String value) {
        addRow(baseRow(icon, title, value));
    }

    private LinearLayout baseRow(int iconRes, String title, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(AutomotiveUi.cardBackground(this));
        row.setPadding(AutomotiveUi.dimen(this, R.dimen.ui_gutter), 0,
                AutomotiveUi.dimen(this, R.dimen.ui_gutter), 0);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(AutomotiveUi.color(this, R.color.ui_icon));
        int pad = AutomotiveUi.dimen(this, R.dimen.ui_gutter);
        icon.setPadding(pad, pad, pad, pad);
        row.addView(icon, new LinearLayout.LayoutParams(56, 56));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        TextView primary = text(title, R.dimen.ui_settings_label, R.color.ui_text);
        TextView secondary = text(value, R.dimen.ui_settings_value, R.color.ui_text_secondary);
        primary.setPadding(0, 0, 0, 0);
        secondary.setPadding(0, 0, 0, 0);
        labels.addView(primary);
        labels.addView(secondary);
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        return row;
    }

    private void addRow(LinearLayout row) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                AutomotiveUi.dimen(this, R.dimen.ui_settings_row_height));
        lp.topMargin = AutomotiveUi.dimen(this, R.dimen.ui_card_inset);
        lp.bottomMargin = AutomotiveUi.dimen(this, R.dimen.ui_card_inset);
        content.addView(row, lp);
    }

    private TextView text(String value, int dimenId, int colorId) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(AutomotiveUi.color(this, colorId));
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(dimenId));
        view.setPadding(0, 4, 0, 4);
        return view;
    }

    private void choose(String title, String[] labels, String[] values,
                        String current, ChoiceSetter setter) {
        int checked = -1;
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) checked = i;
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    setter.set(values[which]);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String railPositionLabel() {
        String value = LauncherPrefs.railPosition(this);
        if (LauncherPrefs.RAIL_LEFT.equals(value)) return "Left";
        if (LauncherPrefs.RAIL_RIGHT.equals(value)) return "Right";
        return "Driver side · right on this TS18";
    }

    private String mapAppearanceLabel() {
        String value = LauncherPrefs.mapAppearance(this);
        if (LauncherPrefs.MAP_APPEARANCE_NORMAL.equals(value)) return "Normal";
        if (LauncherPrefs.MAP_APPEARANCE_DIM.equals(value)) return "Dim";
        return "Auto";
    }

    private String mediaModeLabel() {
        return LauncherPrefs.MEDIA_MODE_PREFER_MUSIC.equals(LauncherPrefs.mediaMode(this))
                ? "Prefer music app" : "Auto";
    }

    private String quickLabel(int index) {
        switch (index) {
            case 0: return "Quick 1 · Navigation role";
            case 1: return "Quick 2 · Radio role";
            case 2: return "Quick 3 · Music role";
            case 3: return "Quick 4 · Bluetooth role";
            case 4: return "Quick 5 · Extra";
            default: return "Quick 6 · Extra";
        }
    }

    private String drawerQuickLabel(int index) {
        switch (index) {
            case 0: return "Drawer quick · Navigation";
            case 1: return "Drawer quick · Radio";
            case 2: return "Drawer quick · Music";
            case 3: return "Drawer quick · Bluetooth";
            default: return "Drawer quick · Extra";
        }
    }

    private String pickerFallback(String key) {
        String pkg = "";
        if (LauncherPrefs.KEY_QUICK_1.equals(key)
                || LauncherPrefs.KEY_DRAWER_QUICK_1.equals(key)) {
            pkg = LauncherPrefs.packageFor(this, LauncherPrefs.KEY_NAV);
        } else if (LauncherPrefs.KEY_MUSIC.equals(key)
                || LauncherPrefs.KEY_QUICK_3.equals(key)
                || LauncherPrefs.KEY_DRAWER_QUICK_3.equals(key)) {
            pkg = LauncherPrefs.packageFor(this, LauncherPrefs.KEY_MUSIC);
            if (pkg.isEmpty()) pkg = TopwayAdapter.defaultMusicPackage(this);
        } else if (LauncherPrefs.KEY_RADIO.equals(key)
                || LauncherPrefs.KEY_QUICK_2.equals(key)
                || LauncherPrefs.KEY_DRAWER_QUICK_2.equals(key)) {
            pkg = RadioProvider.resolvePackage(this);
        } else if (LauncherPrefs.KEY_QUICK_4.equals(key)
                || LauncherPrefs.KEY_DRAWER_QUICK_4.equals(key)) {
            pkg = LauncherPrefs.packageFor(this, LauncherPrefs.KEY_BLUETOOTH);
        }
        return pkg.isEmpty()
                ? "Not set"
                : AppResolver.labelFor(this, pkg, pkg) + " · role fallback";
    }

    private void showMediaDiagnostics() {
        MediaListenerService.refreshActiveSessions();
        new AlertDialog.Builder(this)
                .setTitle("Active media sessions")
                .setMessage(MediaListenerService.sessionDiagnostics(this))
                .setPositiveButton("Close", null)
                .show();
    }

    private void confirmRootHome() {
        new AlertDialog.Builder(this)
                .setTitle("Set TS18 Launcher as HOME?")
                .setMessage("This uses a bounded Magisk root command. DoFun remains installed for rollback.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (dialog, which) -> setHomeWithRoot())
                .show();
    }

    private void confirmDisableHome() {
        new AlertDialog.Builder(this)
                .setTitle("Disable HOME candidate?")
                .setMessage("The launcher stays installed. DoFun remains available as recovery HOME.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Disable", (dialog, which) -> {
                    HomeMode.setHomeAliasEnabled(this, false);
                    Toast.makeText(this, "HOME alias disabled", Toast.LENGTH_LONG).show();
                    render();
                })
                .show();
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
                            ? "root HOME command was not accepted" : result.output;
                    Toast.makeText(this, detail + "; opening Android HOME settings", Toast.LENGTH_LONG).show();
                    HomeMode.requestHomeRole(this);
                }
            });
        }, "ts18-root-home").start();
    }
}
