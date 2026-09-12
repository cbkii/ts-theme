package com.cbkii.ts18launcher;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
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

import java.util.Locale;

@SuppressLint("SetTextI18n")
public final class SettingsActivity extends Activity {
    private interface ChoiceSetter { void set(String value); }
    private interface BooleanSetter { void set(boolean value); }

    private LinearLayout content;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(AutomotiveUi.color(this, R.color.ui_black));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int gap = AutomotiveUi.dimen(this, R.dimen.driver_gap);
        content.setPadding(gap * 2, gap, gap * 2, gap * 2);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Ts18SafeArea.setContent(this, scroll);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (content != null) render();
    }

    private void render() {
        content.removeAllViews();
        content.addView(text("TS18 Mono Drive", R.dimen.ui_settings_title, R.color.ui_text));
        content.addView(text(TopwayAdapter.isTopwayEnvironment(this)
                        ? "Automotive UI · DoFun retained as recovery HOME"
                        : "Automotive UI · generic Android mode",
                R.dimen.ui_settings_value, R.color.ui_text_secondary));

        addSection("Appearance");
        addChoiceRow(R.drawable.ic_apps, "Rail position", railPositionLabel(),
                v -> choose("Rail position",
                        new String[] {"Driver side (right)", "Left", "Right"},
                        new String[] {LauncherPrefs.RAIL_DRIVER, LauncherPrefs.RAIL_LEFT, LauncherPrefs.RAIL_RIGHT},
                        LauncherPrefs.railPosition(this), value -> {
                            LauncherPrefs.setRailPosition(this, value); render();
                        }));
        addChoiceRow(R.drawable.ic_shortcut, "Middle quick slots",
                Integer.toString(LauncherPrefs.quickCount(this)),
                v -> choose("Middle quick slots",
                        new String[] {"3", "4", "5", "6"},
                        new String[] {"3", "4", "5", "6"},
                        Integer.toString(LauncherPrefs.quickCount(this)), value -> {
                            LauncherPrefs.setQuickCount(this, Integer.parseInt(value)); render();
                        }));
        addChoiceRow(R.drawable.ic_settings, "Display appearance", appearanceLabel(),
                v -> choose("Display appearance",
                        new String[] {"Auto", "Day", "High contrast", "Dim", "Night"},
                        new String[] {LauncherPrefs.APPEARANCE_AUTO, LauncherPrefs.APPEARANCE_DAY,
                                LauncherPrefs.APPEARANCE_HIGH_CONTRAST, LauncherPrefs.APPEARANCE_DIM,
                                LauncherPrefs.APPEARANCE_NIGHT},
                        LauncherPrefs.appearanceMode(this), value -> {
                            LauncherPrefs.setAppearanceMode(this, value); recreate();
                        }));
        addChoiceRow(R.drawable.ic_utility, "Auto appearance source", autoSourceLabel(),
                v -> choose("Auto appearance source",
                        new String[] {"Ambient light sensor", "Schedule"},
                        new String[] {LauncherPrefs.AUTO_SOURCE_SENSOR, LauncherPrefs.AUTO_SOURCE_SCHEDULE},
                        LauncherPrefs.appearanceAutoSource(this), value -> {
                            LauncherPrefs.setAppearanceAutoSource(this, value); render();
                        }));
        addChoiceRow(R.drawable.ic_utility, "Day starts", scheduleValue(true),
                v -> chooseTime(true));
        addChoiceRow(R.drawable.ic_utility, "Night starts", scheduleValue(false),
                v -> chooseTime(false));
        addActionRow(R.drawable.ic_close, "Use generic day/night schedule",
                "07:00 day · 19:00 night · dim around transitions",
                v -> { LauncherPrefs.clearAppearanceSchedule(this); render(); });

        addSwitchRow(R.drawable.ic_navigation, "Dashboard map", "Show Leaflet map on HOME",
                LauncherPrefs.mapEnabled(this), checked -> LauncherPrefs.setMapEnabled(this, checked));
        addSwitchRow(R.drawable.ic_my_location, "Map controls", "Show zoom, follow and navigation buttons",
                LauncherPrefs.mapControlsEnabled(this), checked -> LauncherPrefs.setMapControlsEnabled(this, checked));

        addSection("HOME middle quick launch");
        for (int i = 0; i < LauncherPrefs.QUICK_KEYS.length; i++) {
            final int index = i;
            addPickerRow(RoleIconCatalog.icon(LauncherPrefs.quickRole(this, i)),
                    "Quick " + (i + 1) + " app", LauncherPrefs.QUICK_KEYS[i]);
            addChoiceRow(RoleIconCatalog.icon(LauncherPrefs.quickRole(this, i)),
                    "Quick " + (i + 1) + " icon", RoleIconCatalog.label(LauncherPrefs.quickRole(this, i)),
                    v -> chooseRole(false, index));
        }

        addSection("Drawer quick access");
        for (int i = 0; i < LauncherPrefs.DRAWER_QUICK_KEYS.length; i++) {
            final int index = i;
            addPickerRow(RoleIconCatalog.icon(LauncherPrefs.drawerQuickRole(this, i)),
                    "Drawer quick " + (i + 1) + " app", LauncherPrefs.DRAWER_QUICK_KEYS[i]);
            addChoiceRow(RoleIconCatalog.icon(LauncherPrefs.drawerQuickRole(this, i)),
                    "Drawer quick " + (i + 1) + " icon",
                    RoleIconCatalog.label(LauncherPrefs.drawerQuickRole(this, i)),
                    v -> chooseRole(true, index));
        }

        addSection("Media");
        addChoiceRow(R.drawable.ic_shortcut, "Media controls side", mediaControlsSideLabel(),
                v -> choose("Media controls side",
                        new String[] {"Left", "Right"},
                        new String[] {LauncherPrefs.MEDIA_CONTROLS_LEFT, LauncherPrefs.MEDIA_CONTROLS_RIGHT},
                        LauncherPrefs.mediaControlsSide(this), value -> {
                            LauncherPrefs.setMediaControlsSide(this, value); render();
                        }));
        addActionRow(R.drawable.ic_music, "Notification access",
                MediaListenerService.hasNotificationAccess(this) ? "Granted" : "Required for media sessions",
                v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        addChoiceRow(R.drawable.ic_music, "Generic media selection", mediaModeLabel(),
                v -> choose("Generic media selection",
                        new String[] {"Auto", "Prefer music app"},
                        new String[] {LauncherPrefs.MEDIA_MODE_AUTO, LauncherPrefs.MEDIA_MODE_PREFER_MUSIC},
                        LauncherPrefs.mediaMode(this), value -> {
                            LauncherPrefs.setMediaMode(this, value);
                            MediaListenerService.refreshActiveSessions(); render();
                        }));
        addPickerRow(R.drawable.ic_music, "Preferred / fallback music", LauncherPrefs.KEY_MUSIC);
        addPickerRow(R.drawable.ic_radio, "Radio app", LauncherPrefs.KEY_RADIO);
        addActionRow(R.drawable.ic_shortcut, "Media session diagnostics", "Read-only active-session view",
                v -> showMediaDiagnostics());

        addSection("App roles");
        addPickerRow(R.drawable.ic_navigation, "Navigation app", LauncherPrefs.KEY_NAV);
        addPickerRow(R.drawable.ic_bluetooth, "Bluetooth app", LauncherPrefs.KEY_BLUETOOTH);

        addSection("Permissions");
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            addActionRow(R.drawable.ic_my_location, "Location permission", "Required for HOME map",
                    v -> requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION}, 9201));
        } else addInfoRow(R.drawable.ic_my_location, "Location permission", "Granted");
        addInfoRow(R.drawable.ic_mic, "Voice search",
                VoiceSearch.available(this) ? "Available through installed speech recogniser" : "No compatible speech recogniser");
        addInfoRow(R.drawable.ic_utility, "Ambient light sensor",
                AppearanceController.sensorAvailable(this) ? "Available" : "Unavailable · Auto falls back to schedule");

        addSection("Advanced HOME / recovery");
        addActionRow(R.drawable.ic_settings,
                HomeMode.isDefaultHome(this) ? "Current HOME: TS18 Launcher" : "Set as HOME",
                "Use Android HOME selection UI", v -> HomeMode.requestHomeRole(this));
        addActionRow(R.drawable.ic_settings, "Set as HOME with Magisk root",
                "Bounded one-time root command; DoFun remains installed", v -> confirmRootHome());
        addActionRow(R.drawable.ic_close, "Disable HOME candidate",
                "Keep app installed and retain DoFun recovery", v -> confirmDisableHome());
    }

    private void chooseRole(boolean drawer, int index) {
        String current = drawer ? LauncherPrefs.drawerQuickRole(this, index) : LauncherPrefs.quickRole(this, index);
        choose("Role icon", RoleIconCatalog.LABELS, RoleIconCatalog.VALUES, current, role -> {
            if (drawer) LauncherPrefs.setRole(this, LauncherPrefs.DRAWER_ROLE_KEYS[index], role);
            else LauncherPrefs.setRole(this, LauncherPrefs.QUICK_ROLE_KEYS[index], role);
            render();
        });
    }

    private void chooseTime(boolean day) {
        int stored = day ? LauncherPrefs.appearanceDayStartMinutes(this) : LauncherPrefs.appearanceNightStartMinutes(this);
        int fallback = day ? LauncherPrefs.DEFAULT_DAY_START_MINUTES : LauncherPrefs.DEFAULT_NIGHT_START_MINUTES;
        int initial = stored < 0 ? fallback : stored;
        new TimePickerDialog(this, (view, hour, minute) -> {
            int value = hour * 60 + minute;
            if (day) LauncherPrefs.setAppearanceDayStartMinutes(this, value);
            else LauncherPrefs.setAppearanceNightStartMinutes(this, value);
            render();
        }, initial / 60, initial % 60, true).show();
    }

    private void addSection(String title) {
        TextView view = text(title, R.dimen.ui_settings_section, R.color.ui_accent);
        int gap = AutomotiveUi.dimen(this, R.dimen.driver_gap);
        view.setPadding(0, gap * 2, 0, gap / 2);
        content.addView(view);
    }

    private void addSwitchRow(int icon, String title, String value, boolean checked, BooleanSetter listener) {
        LinearLayout row = baseRow(icon, title, value);
        Switch toggle = new Switch(this);
        toggle.setChecked(checked); toggle.setShowText(false);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> listener.set(isChecked));
        row.addView(toggle, new LinearLayout.LayoutParams(88, ViewGroup.LayoutParams.MATCH_PARENT));
        row.setBackground(AutomotiveUi.interactiveBackground(this, false));
        row.setFocusable(true); row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
        AutomotiveUi.attachFeedback(row); addRow(row);
    }

    private void addPickerRow(int icon, String label, String key) {
        String pkg = LauncherPrefs.packageFor(this, key);
        String current = pkg.isEmpty() ? pickerFallback(key) : AppResolver.labelFor(this, pkg, pkg);
        addChoiceRow(icon, label, current, v -> {
            Intent intent = new Intent(this, AppDrawerActivity.class);
            intent.putExtra(AppDrawerActivity.EXTRA_PICK_KEY, key); startActivity(intent);
        });
    }

    private void addChoiceRow(int icon, String title, String value, View.OnClickListener listener) {
        LinearLayout row = baseRow(icon, title, value);
        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron_right);
        chevron.setColorFilter(AutomotiveUi.color(this, R.color.ui_icon));
        int pad = AutomotiveUi.dimen(this, R.dimen.driver_gap) * 2;
        chevron.setPadding(pad, pad, pad, pad);
        row.addView(chevron, new LinearLayout.LayoutParams(64, ViewGroup.LayoutParams.MATCH_PARENT));
        row.setBackground(AutomotiveUi.interactiveBackground(this, false));
        row.setFocusable(true); row.setOnClickListener(listener); AutomotiveUi.attachFeedback(row); addRow(row);
    }

    private void addActionRow(int icon, String title, String value, View.OnClickListener listener) {
        addChoiceRow(icon, title, value, listener);
    }
    private void addInfoRow(int icon, String title, String value) { addRow(baseRow(icon, title, value)); }

    private LinearLayout baseRow(int iconRes, String title, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(AutomotiveUi.cardBackground(this));
        int gap = AutomotiveUi.dimen(this, R.dimen.driver_gap);
        row.setPadding(gap, 0, gap, 0);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes); icon.setColorFilter(AutomotiveUi.color(this, R.color.ui_icon));
        icon.setPadding(gap, gap, gap, gap);
        row.addView(icon, new LinearLayout.LayoutParams(56, 56));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL); labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.addView(text(title, R.dimen.ui_settings_label, R.color.ui_text));
        labels.addView(text(value, R.dimen.ui_settings_value, R.color.ui_text_secondary));
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        return row;
    }

    private void addRow(LinearLayout row) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                AutomotiveUi.dimen(this, R.dimen.ui_settings_row_height));
        lp.topMargin = AutomotiveUi.dimen(this, R.dimen.ui_card_inset);
        lp.bottomMargin = AutomotiveUi.dimen(this, R.dimen.ui_card_inset);
        content.addView(row, lp);
    }

    private TextView text(String value, int dimenId, int colorId) {
        TextView view = new TextView(this); view.setText(value);
        view.setTextColor(AutomotiveUi.color(this, colorId));
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(dimenId));
        view.setPadding(0, 4, 0, 4); return view;
    }

    private void choose(String title, String[] labels, String[] values, String current, ChoiceSetter setter) {
        int checked = -1;
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) checked = i;
        new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels, checked, (dialog, which) -> {
            setter.set(values[which]); dialog.dismiss();
        }).setNegativeButton("Cancel", null).show();
    }

    private String railPositionLabel() {
        String value = LauncherPrefs.railPosition(this);
        if (LauncherPrefs.RAIL_LEFT.equals(value)) return "Left";
        if (LauncherPrefs.RAIL_RIGHT.equals(value)) return "Right";
        return "Driver side · right on this TS18";
    }
    private String mediaControlsSideLabel() {
        return LauncherPrefs.MEDIA_CONTROLS_LEFT.equals(LauncherPrefs.mediaControlsSide(this))
                ? "Left · independent of rail" : "Right · independent of rail";
    }
    private String appearanceLabel() {
        String value = LauncherPrefs.appearanceMode(this);
        if (LauncherPrefs.APPEARANCE_DAY.equals(value)) return "Day";
        if (LauncherPrefs.APPEARANCE_HIGH_CONTRAST.equals(value)) return "High contrast";
        if (LauncherPrefs.APPEARANCE_DIM.equals(value)) return "Dim";
        if (LauncherPrefs.APPEARANCE_NIGHT.equals(value)) return "Night";
        return "Auto · " + autoSourceLabel();
    }
    private String autoSourceLabel() {
        return LauncherPrefs.AUTO_SOURCE_SCHEDULE.equals(LauncherPrefs.appearanceAutoSource(this))
                ? "Schedule" : "Ambient light sensor · schedule fallback";
    }
    private String scheduleValue(boolean day) {
        int value = day ? LauncherPrefs.appearanceDayStartMinutes(this) : LauncherPrefs.appearanceNightStartMinutes(this);
        if (value < 0) return day ? "07:00 · generic default" : "19:00 · generic default";
        return String.format(Locale.US, "%02d:%02d", value / 60, value % 60);
    }
    private String mediaModeLabel() {
        return LauncherPrefs.MEDIA_MODE_PREFER_MUSIC.equals(LauncherPrefs.mediaMode(this))
                ? "Prefer music app" : "Auto";
    }

    private String pickerFallback(String key) {
        for (int i = 0; i < LauncherPrefs.QUICK_KEYS.length; i++) {
            if (LauncherPrefs.QUICK_KEYS[i].equals(key)) {
                String role = LauncherPrefs.quickRole(this, i);
                String pkg = RoleIconCatalog.fallbackPackage(this, role);
                return pkg.isEmpty() ? "Not set · " + RoleIconCatalog.label(role)
                        : AppResolver.labelFor(this, pkg, pkg) + " · " + RoleIconCatalog.label(role) + " role";
            }
        }
        for (int i = 0; i < LauncherPrefs.DRAWER_QUICK_KEYS.length; i++) {
            if (LauncherPrefs.DRAWER_QUICK_KEYS[i].equals(key)) {
                String role = LauncherPrefs.drawerQuickRole(this, i);
                String pkg = RoleIconCatalog.fallbackPackage(this, role);
                return pkg.isEmpty() ? "Not set · " + RoleIconCatalog.label(role)
                        : AppResolver.labelFor(this, pkg, pkg) + " · " + RoleIconCatalog.label(role) + " role";
            }
        }
        if (LauncherPrefs.KEY_MUSIC.equals(key)) {
            String pkg = TopwayAdapter.defaultMusicPackage(this);
            if (!pkg.isEmpty()) return AppResolver.labelFor(this, pkg, pkg) + " · role fallback";
        }
        if (LauncherPrefs.KEY_RADIO.equals(key)) {
            String pkg = RadioProvider.resolvePackage(this);
            if (!pkg.isEmpty()) return AppResolver.labelFor(this, pkg, pkg) + " · role fallback";
        }
        return "Not set";
    }

    private void showMediaDiagnostics() {
        MediaListenerService.refreshActiveSessions();
        new AlertDialog.Builder(this).setTitle("Active media sessions")
                .setMessage(MediaListenerService.sessionDiagnostics(this)).setPositiveButton("Close", null).show();
    }
    private void confirmRootHome() {
        new AlertDialog.Builder(this).setTitle("Set TS18 Launcher as HOME?")
                .setMessage("This uses a bounded Magisk root command. DoFun remains installed for rollback.")
                .setNegativeButton("Cancel", null).setPositiveButton("Continue", (dialog, which) -> setHomeWithRoot()).show();
    }
    private void confirmDisableHome() {
        new AlertDialog.Builder(this).setTitle("Disable HOME candidate?")
                .setMessage("The launcher stays installed. DoFun remains available as recovery HOME.")
                .setNegativeButton("Cancel", null).setPositiveButton("Disable", (dialog, which) -> {
                    HomeMode.setHomeAliasEnabled(this, false);
                    Toast.makeText(this, "HOME alias disabled", Toast.LENGTH_LONG).show(); render();
                }).show();
    }
    private void setHomeWithRoot() {
        Toast.makeText(this, "Requesting Magisk superuser…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            RootShell.Result result = HomeMode.setHomeWithRoot(this);
            runOnUiThread(() -> {
                if (result.success() && HomeMode.isDefaultHome(this)) {
                    Toast.makeText(this, "TS18 Launcher is now HOME", Toast.LENGTH_LONG).show(); render();
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
