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
import android.text.TextUtils;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressLint("SetTextI18n")
public final class SettingsActivity extends Activity {
    private interface ChoiceSetter { void set(String value); }
    private interface BooleanSetter { void set(boolean value); }

    private static final int EXPORT_CONFIG = 9301;
    private static final int IMPORT_CONFIG = 9302;
    private static final int CATEGORY_HOME = 0;
    private static final int CATEGORY_APPEARANCE = 1;
    private static final int CATEGORY_MEDIA = 2;
    private static final int CATEGORY_ADVANCED = 3;
    private static final String STATE_CATEGORY = "settings.category";
    private static final String[] CATEGORY_LABELS = {"Home screen", "Appearance", "Media", "Advanced"};

    private final java.util.concurrent.ExecutorService configurationIo =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private LinearLayout navigation;
    private ScrollView contentScroll;
    private LinearLayout content;
    private boolean configurationBusy;
    private int selectedCategory = CATEGORY_HOME;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            selectedCategory = Math.max(CATEGORY_HOME,
                    Math.min(CATEGORY_ADVANCED, state.getInt(STATE_CATEGORY, CATEGORY_HOME)));
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(AutomotiveUi.color(this, R.color.ui_black));
        int gap = AutomotiveUi.dimen(this, R.dimen.driver_gap);
        root.setPadding(gap, gap, gap, gap);

        navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.VERTICAL);
        navigation.setPadding(0, 0, 0, gap);
        root.addView(navigation, new LinearLayout.LayoutParams(
                AutomotiveUi.dimen(this, R.dimen.ui_settings_nav_width),
                ViewGroup.LayoutParams.MATCH_PARENT));

        contentScroll = new ScrollView(this);
        contentScroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(gap * 2, 0, gap * 2, gap * 2);
        contentScroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        contentParams.leftMargin = gap;
        root.addView(contentScroll, contentParams);

        Ts18SafeArea.setContent(this, root);
        render();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt(STATE_CATEGORY, selectedCategory);
        super.onSaveInstanceState(state);
    }

    @Override protected void onResume() {
        super.onResume();
        if (content != null) render();
    }

    private void render() {
        renderNavigation();
        content.removeAllViews();
        content.addView(text(CATEGORY_LABELS[selectedCategory], R.dimen.ui_settings_title, R.color.ui_text));
        if (selectedCategory == CATEGORY_HOME) renderHomeScreen();
        else if (selectedCategory == CATEGORY_APPEARANCE) renderAppearance();
        else if (selectedCategory == CATEGORY_MEDIA) renderMedia();
        else renderAdvanced();
    }

    private void renderNavigation() {
        navigation.removeAllViews();
        TextView heading = text("Settings", R.dimen.ui_settings_title, R.color.ui_text);
        int gap = AutomotiveUi.dimen(this, R.dimen.driver_gap);
        heading.setPadding(gap, gap, gap, gap * 2);
        navigation.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        List<View> items = new ArrayList<>();
        for (int i = 0; i < CATEGORY_LABELS.length; i++) {
            final int category = i;
            TextView item = new TextView(this);
            item.setText(CATEGORY_LABELS[i]);
            item.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimension(R.dimen.ui_settings_label));
            item.setTextColor(AutomotiveUi.color(this,
                    selectedCategory == i ? R.color.ui_accent : R.color.ui_text));
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setSingleLine(true);
            item.setMaxLines(1);
            item.setEllipsize(TextUtils.TruncateAt.END);
            item.setPadding(gap * 2, 0, gap, 0);
            item.setSelected(selectedCategory == i);
            item.setBackground(AutomotiveUi.followModeBackground(this));
            item.setFocusable(true);
            item.setOnClickListener(v -> {
                if (selectedCategory == category) return;
                selectedCategory = category;
                contentScroll.scrollTo(0, 0);
                render();
            });
            AutomotiveUi.attachFeedback(item);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    AutomotiveUi.dimen(this, R.dimen.ui_settings_nav_item_height));
            lp.topMargin = AutomotiveUi.dimen(this, R.dimen.ui_card_inset);
            lp.bottomMargin = AutomotiveUi.dimen(this, R.dimen.ui_card_inset);
            navigation.addView(item, lp);
            items.add(item);
        }
        AutomotiveUi.linkVertical(items);
    }

    private void renderHomeScreen() {
        addSection("Layout");
        addChoiceRow(R.drawable.ic_apps, "Rail position", railPositionLabel(),
                v -> choose("Rail position",
                        new String[] {"Driver side", "Left", "Right"},
                        new String[] {LauncherPrefs.RAIL_DRIVER, LauncherPrefs.RAIL_LEFT, LauncherPrefs.RAIL_RIGHT},
                        LauncherPrefs.railPosition(this), value -> {
                            LauncherPrefs.setRailPosition(this, value); render();
                        }));
        addChoiceRow(R.drawable.ic_radio, "Radio and music position",
                LauncherPrefs.radioOnRight(this) ? "Radio right" : "Radio left",
                v -> choose("Radio and music position",
                        new String[] {"Radio left", "Radio right"},
                        new String[] {LauncherPrefs.RAIL_LEFT, LauncherPrefs.RAIL_RIGHT},
                        LauncherPrefs.radioOnRight(this) ? LauncherPrefs.RAIL_RIGHT : LauncherPrefs.RAIL_LEFT,
                        value -> { LauncherPrefs.setRadioSide(this, value); render(); }));
        addChoiceRow(R.drawable.ic_navigation, "Navigation display",
                HomeNavigationSurfacePolicy.label(HomeNavigationSurfacePolicy.mode(this)),
                v -> choose("Navigation display",
                        new String[] {"Native navigation window · TESTING",
                                "Fullscreen only · safe fallback",
                                "Legacy online map fallback · Internet required"},
                        new String[] {HomeNavigationSurfacePolicy.NATIVE_WINDOW,
                                HomeNavigationSurfacePolicy.FULLSCREEN,
                                HomeNavigationSurfacePolicy.LEAFLET},
                        HomeNavigationSurfacePolicy.mode(this), value -> {
                            HomeNavigationSurfacePolicy.setMode(this, value); recreate();
                        }));

        addSection("Home widget apps");
        addPickerRow(R.drawable.ic_navigation, "Navigation", LauncherPrefs.KEY_NAV);
        String navigationPackage = LauncherPrefs.packageFor(this, LauncherPrefs.KEY_NAV);
        if (navigationPackage.isEmpty()
                && NavigationProvider.hasLauncherActivity(this, NavigationProvider.ORGANIC_MAPS_INCAR)) {
            navigationPackage = NavigationProvider.ORGANIC_MAPS_INCAR;
        }
        addInfoRow(R.drawable.ic_navigation, "Navigation compatibility",
                NavigationCompatibilityPolicy.settingsMessage(
                        navigationPackage, HomeNavigationSurfacePolicy.mode(this)));
        addPickerRow(R.drawable.ic_music, "Music", LauncherPrefs.KEY_MUSIC);
        addPickerRow(R.drawable.ic_radio, "Radio", LauncherPrefs.KEY_RADIO);
        addPickerRow(R.drawable.ic_bluetooth, "Bluetooth", LauncherPrefs.KEY_BLUETOOTH);

        addSection("Home sidebar shortcuts");
        boolean homeShortcuts = UiPersonalizationPrefs.homeShortcutsEnabled(this);
        addSwitchRow(R.drawable.ic_shortcut, "Show Home sidebar shortcuts",
                "Choose whether shortcut buttons appear on the Home sidebar.",
                homeShortcuts, checked -> {
                    UiPersonalizationPrefs.setHomeShortcutsEnabled(this, checked);
                    render();
                });
        if (homeShortcuts) {
            addChoiceRow(R.drawable.ic_shortcut, "Shortcut count",
                    Integer.toString(LauncherPrefs.quickCount(this)),
                    v -> choose("Home sidebar shortcut count",
                            new String[] {"3", "4", "5", "6"},
                            new String[] {"3", "4", "5", "6"},
                            Integer.toString(LauncherPrefs.quickCount(this)), value -> {
                                LauncherPrefs.setQuickCount(this, Integer.parseInt(value)); render();
                            }));
            content.addView(text("Choose what each shortcut opens and the icon it uses.",
                    R.dimen.ui_settings_value, R.color.ui_text_secondary));
            for (int i = 0; i < LauncherPrefs.QUICK_KEYS.length; i++) addShortcutEditor(false, i);
        }

        addSection("App drawer shortcuts");
        for (int i = 0; i < LauncherPrefs.DRAWER_QUICK_KEYS.length; i++) addShortcutEditor(true, i);
    }

    private void renderAppearance() {
        addChoiceRow(R.drawable.ic_settings, "Display mode", appearanceLabel(),
                v -> choose("Display mode",
                        new String[] {"Automatic", "Day", "High contrast", "Dim", "Night"},
                        new String[] {LauncherPrefs.APPEARANCE_AUTO, LauncherPrefs.APPEARANCE_DAY,
                                LauncherPrefs.APPEARANCE_HIGH_CONTRAST, LauncherPrefs.APPEARANCE_DIM,
                                LauncherPrefs.APPEARANCE_NIGHT},
                        LauncherPrefs.appearanceMode(this), value -> {
                            LauncherPrefs.setAppearanceMode(this, value); recreate();
                        }));
        addAccentPaletteRow();

        if (LauncherPrefs.APPEARANCE_AUTO.equals(LauncherPrefs.appearanceMode(this))) {
            addChoiceRow(R.drawable.ic_utility, "Automatic switching", autoSourceLabel(),
                    v -> choose("Automatic switching",
                            new String[] {"Ambient light", "Schedule"},
                            new String[] {LauncherPrefs.AUTO_SOURCE_SENSOR, LauncherPrefs.AUTO_SOURCE_SCHEDULE},
                            LauncherPrefs.appearanceAutoSource(this), value -> {
                                LauncherPrefs.setAppearanceAutoSource(this, value); render();
                            }));
            if (LauncherPrefs.AUTO_SOURCE_SENSOR.equals(LauncherPrefs.appearanceAutoSource(this))) {
                addInfoRow(R.drawable.ic_lightbulb, "Ambient light",
                        AppearanceController.sensorAvailable(this) ? "Available" : "Unavailable");
            } else {
                addChoiceRow(R.drawable.ic_utility, "Day starts", scheduleValue(true), v -> chooseTime(true));
                addChoiceRow(R.drawable.ic_utility, "Night starts", scheduleValue(false), v -> chooseTime(false));
                addActionRow(R.drawable.ic_close, "Reset schedule",
                        "Use 07:00 for day and 19:00 for night.",
                        v -> { LauncherPrefs.clearAppearanceSchedule(this); render(); });
            }
        }
    }

    private void renderMedia() {
        addSection("Playback");
        addSwitchRow(R.drawable.ic_power, "Prepare music and radio at startup",
                "Keeps music and radio ready. On a true cold start, configured apps may be "
                        + "briefly initialised behind the startup mask when overlay access is granted.",
                UiPersonalizationPrefs.mediaStartupWarmup(this),
                checked -> UiPersonalizationPrefs.setMediaStartupWarmup(this, checked));
        addChoiceRow(R.drawable.ic_music, "Media control priority", mediaModeLabel(),
                v -> choose("Media control priority",
                        new String[] {"Automatic", "Prefer music"},
                        new String[] {LauncherPrefs.MEDIA_MODE_AUTO, LauncherPrefs.MEDIA_MODE_PREFER_MUSIC},
                        LauncherPrefs.mediaMode(this), value -> {
                            LauncherPrefs.setMediaMode(this, value);
                            MediaListenerService.refreshActiveSessions(); render();
                        }));

        addSection("Access");
        addSystemRow(R.drawable.ic_apps, "Startup splash overlay",
                StartupMaskController.hasOverlayAccess(this)
                        ? "Granted · cold app preparation can stay hidden"
                        : "Allow Display over other apps for masked cold-start preparation.",
                v -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:" + getPackageName()));
                    try { startActivity(intent); }
                    catch (RuntimeException ignored) {
                        Toast.makeText(this, "Overlay settings unavailable", Toast.LENGTH_SHORT).show();
                    }
                });
        addSystemRow(R.drawable.ic_notifications, "Notification access",
                MediaListenerService.hasNotificationAccess(this) ? "Granted" : "Allow access for media controls.",
                v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
    }

    private void renderAdvanced() {
        addSection("Navigation access");
        addSwitchRow(R.drawable.ic_my_location, "Pre-grant navigation location permissions (root)",
                "Optional. Grants declared location access to the selected navigation app before launch.",
                UiPersonalizationPrefs.navigationRootPermissionGrant(this), checked -> {
                    UiPersonalizationPrefs.setNavigationRootPermissionGrant(this, checked);
                    if (checked) NavigationPermissionBootstrapper.ensureEarly(this, result ->
                            Toast.makeText(this, result.detail, Toast.LENGTH_LONG).show());
                });

        addSection("Experimental map");
        boolean mapEnabled = ExperimentalMapPolicy.enabled(this);
        addInfoRow(R.drawable.ic_map, "Experimental Home map",
                mapEnabled ? "Enabled as the legacy navigation display." : "Off");
        if (mapEnabled) {
            addSwitchRow(R.drawable.ic_my_location, "Map controls", "Show zoom and follow controls.",
                    LauncherPrefs.mapControlsEnabled(this),
                    checked -> LauncherPrefs.setMapControlsEnabled(this, checked));
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                addActionRow(R.drawable.ic_my_location, "Location access",
                        "Allow location while using the experimental map.",
                        v -> requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION}, 9201));
            } else {
                addInfoRow(R.drawable.ic_my_location, "Location access", "Granted");
            }
        }

        addSection("Backup & reset");
        addActionRow(R.drawable.ic_download, "Export settings", "Save a settings file.",
                v -> chooseConfigurationDocument(true));
        addActionRow(R.drawable.ic_folder, "Import settings", "Review a settings file before applying it.",
                v -> chooseConfigurationDocument(false));
        addDestructiveRow(R.drawable.ic_close, "Reset settings",
                "Restore launcher settings to their defaults.",
                v -> new AlertDialog.Builder(this).setTitle("Reset settings?")
                        .setMessage("This resets Home apps, shortcuts, layout, media and appearance. "
                                + "Your Android Home app choice will not change.")
                        .setNegativeButton("Cancel", null).setPositiveButton("Reset", (dialog, which) ->
                                commitConfiguration(java.util.Collections.emptyMap(), true)).show());

        addSection("Diagnostics & system");
        addActionRow(R.drawable.ic_shortcut, "Media diagnostics", "View active media sessions.",
                v -> showMediaDiagnostics());
        addInfoRow(R.drawable.ic_mic, "Voice search",
                VoiceSearch.available(this) ? "Available" : "Unavailable");

        addSection("Home & recovery");
        addSystemRow(R.drawable.ic_home, "Default Home app",
                HomeMode.isDefaultHome(this) ? "TS18 Launcher" : "Choose in Android settings.",
                v -> HomeMode.requestHomeRole(this));
        addActionRow(R.drawable.ic_settings, "Set as Home using root",
                "Use Magisk to set TS18 Launcher as the default Home app.",
                v -> confirmRootHome());
        addDestructiveRow(R.drawable.ic_close, "Disable launcher as Home",
                "Keep the app installed and use another Home app.",
                v -> confirmDisableHome());
    }

    private void addAccentPaletteRow() {
        AccentPaletteRow row = new AccentPaletteRow(this, this::recreate);
        addRow(row);
    }

    private void addShortcutEditor(boolean drawer, int index) {
        ShortcutEditorView editor = new ShortcutEditorView(this, drawer, index, this::render);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                AutomotiveUi.dimen(this, R.dimen.ui_settings_shortcut_editor_height));
        lp.topMargin = AutomotiveUi.dimen(this, R.dimen.ui_card_inset);
        lp.bottomMargin = AutomotiveUi.dimen(this, R.dimen.ui_card_inset);
        content.addView(editor, lp);
    }

    private void chooseConfigurationDocument(boolean export) {
        if (configurationBusy) return;
        Intent intent = new Intent(export ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        if (export) intent.putExtra(Intent.EXTRA_TITLE, "TS18-launcher-settings-v1.json");
        try { startActivityForResult(intent, export ? EXPORT_CONFIG : IMPORT_CONFIG); }
        catch (RuntimeException ignored) { configurationMessage("Document picker unavailable"); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if ((request != EXPORT_CONFIG && request != IMPORT_CONFIG) || result != RESULT_OK
                || data == null || data.getData() == null || configurationBusy) return;
        android.net.Uri uri = data.getData();
        if (!"content".equals(uri.getScheme())) { configurationMessage("This file could not be opened"); return; }
        configurationBusy = true;
        configurationIo.execute(() -> {
            try {
                if (request == EXPORT_CONFIG) {
                    String json = ConfigurationCodec.encode(ConfigurationStore.read(this));
                    try (java.io.OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                        if (out == null) throw new java.io.IOException("Document unavailable");
                        out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                    runOnUiThread(() -> configurationMessage("Settings exported"));
                } else {
                    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                    try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
                        if (input == null) throw new java.io.IOException("Document unavailable");
                        byte[] buffer = new byte[4096];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            if (bytes.size() + count > ConfigurationCodec.MAX_BYTES)
                                throw new java.io.IOException("Settings file is too large");
                            bytes.write(buffer, 0, count);
                        }
                    }
                    ConfigurationCodec.Preview preview = ConfigurationCodec.decode(
                            bytes.toString("UTF-8"), this::configurationPackageAvailable);
                    runOnUiThread(() -> showConfigurationPreview(preview));
                }
            } catch (Exception error) {
                runOnUiThread(() -> configurationMessage(
                        "Settings unchanged: this file is not compatible or could not be read"));
            } finally { runOnUiThread(() -> configurationBusy = false); }
        });
    }

    private boolean configurationPackageAvailable(String name) {
        return !getPackageName().equals(name) && getPackageManager().getLaunchIntentForPackage(name) != null;
    }

    private void showConfigurationPreview(ConfigurationCodec.Preview preview) {
        if (isFinishing() || isDestroyed()) return;
        ScrollView scroll = new ScrollView(this);
        TextView detail = text(importSummary(preview), R.dimen.ui_settings_value, R.color.ui_text);
        scroll.addView(detail);
        new AlertDialog.Builder(this).setTitle("Import settings?").setView(scroll)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Import", (dialog, which) -> commitConfiguration(preview.values, false)).show();
    }

    private String importSummary(ConfigurationCodec.Preview preview) {
        StringBuilder text = new StringBuilder("Import ")
                .append(preview.values.size())
                .append(" saved settings? Settings not included in the file will return to defaults.");
        if (!preview.unavailable.isEmpty()) {
            text.append("\n\n")
                    .append(preview.unavailable.size())
                    .append(preview.unavailable.size() == 1
                            ? " app selection is not available and will return to its default."
                            : " app selections are not available and will return to their defaults.");
        }
        return text.toString();
    }

    private void commitConfiguration(java.util.Map<String, Object> values, boolean reset) {
        if (configurationBusy) return;
        configurationBusy = true;
        configurationIo.execute(() -> {
            boolean success = false;
            try {
                for (java.util.Map.Entry<String, Object> entry : values.entrySet()) {
                    if (ConfigurationCodec.KEYS.get(entry.getKey()) == ConfigurationCodec.Type.PACKAGE
                            && !configurationPackageAvailable((String) entry.getValue()))
                        throw new IllegalArgumentException("App availability changed; import again");
                }
                success = ConfigurationStore.replace(this, values);
                if (success && reset) LauncherPrefs.prefs(this).edit()
                        .remove(LauncherPrefs.KEY_LAST_MUSIC).remove(LauncherPrefs.KEY_LAST_SOURCE)
                        .remove(LauncherPrefs.KEY_MEDIA_CONTROLS_SIDE).apply();
            } catch (RuntimeException ignored) { /* No partial import. */ }
            final boolean applied = success;
            runOnUiThread(() -> {
                configurationBusy = false;
                if (isFinishing() || isDestroyed()) return;
                configurationMessage(applied
                        ? (reset ? "Settings reset" : "Settings imported")
                        : "Settings were not changed");
                if (applied) {
                    MediaListenerService.refreshActiveSessions();
                    if (!reset) NavigationPermissionBootstrapper.ensureEarly(this);
                    recreate();
                }
            });
        });
    }

    private void configurationMessage(String message) {
        if (!isFinishing() && !isDestroyed()) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override protected void onDestroy() {
        configurationIo.shutdownNow();
        super.onDestroy();
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
        makeInteractive(row, v -> toggle.setChecked(!toggle.isChecked()));
        addRow(row);
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
        addTrailingIcon(row, R.drawable.ic_chevron_right, R.color.ui_icon);
        makeInteractive(row, listener);
        addRow(row);
    }

    private void addSystemRow(int icon, String title, String value, View.OnClickListener listener) {
        LinearLayout row = baseRow(icon, title, value);
        addTrailingIcon(row, R.drawable.ic_settings, R.color.ui_accent);
        makeInteractive(row, listener);
        addRow(row);
    }

    private void addActionRow(int icon, String title, String value, View.OnClickListener listener) {
        LinearLayout row = baseRow(icon, title, value);
        makeInteractive(row, listener);
        addRow(row);
    }

    private void addDestructiveRow(int icon, String title, String value, View.OnClickListener listener) {
        LinearLayout row = baseRow(icon, title, value);
        ImageView leading = (ImageView) row.getChildAt(0);
        leading.setColorFilter(AutomotiveUi.color(this, R.color.ui_destructive));
        LinearLayout labels = (LinearLayout) row.getChildAt(1);
        ((TextView) labels.getChildAt(0)).setTextColor(AutomotiveUi.color(this, R.color.ui_destructive));
        makeInteractive(row, listener);
        addRow(row);
    }

    private void addInfoRow(int icon, String title, String value) {
        addRow(baseRow(icon, title, value));
    }

    private void addTrailingIcon(LinearLayout row, int drawable, int color) {
        ImageView trailing = new ImageView(this);
        trailing.setImageResource(drawable);
        trailing.setColorFilter(AutomotiveUi.color(this, color));
        int pad = AutomotiveUi.dimen(this, R.dimen.driver_gap) * 2;
        trailing.setPadding(pad, pad, pad, pad);
        row.addView(trailing, new LinearLayout.LayoutParams(64, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void makeInteractive(LinearLayout row, View.OnClickListener listener) {
        row.setBackground(AutomotiveUi.interactiveBackground(this, false));
        row.setFocusable(true);
        row.setOnClickListener(listener);
        AutomotiveUi.attachFeedback(row);
    }

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
        TextView titleView = text(title, R.dimen.ui_settings_label, R.color.ui_text);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        TextView valueView = text(value, R.dimen.ui_settings_value, R.color.ui_text_secondary);
        valueView.setMaxLines(2);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(titleView);
        labels.addView(valueView);
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
        return "Driver side";
    }

    private String appearanceLabel() {
        String value = LauncherPrefs.appearanceMode(this);
        if (LauncherPrefs.APPEARANCE_DAY.equals(value)) return "Day";
        if (LauncherPrefs.APPEARANCE_HIGH_CONTRAST.equals(value)) return "High contrast";
        if (LauncherPrefs.APPEARANCE_DIM.equals(value)) return "Dim";
        if (LauncherPrefs.APPEARANCE_NIGHT.equals(value)) return "Night";
        return "Automatic";
    }

    private String autoSourceLabel() {
        return LauncherPrefs.AUTO_SOURCE_SCHEDULE.equals(LauncherPrefs.appearanceAutoSource(this))
                ? "Schedule" : "Ambient light";
    }

    private String scheduleValue(boolean day) {
        int value = day ? LauncherPrefs.appearanceDayStartMinutes(this) : LauncherPrefs.appearanceNightStartMinutes(this);
        if (value < 0) value = day ? LauncherPrefs.DEFAULT_DAY_START_MINUTES : LauncherPrefs.DEFAULT_NIGHT_START_MINUTES;
        return String.format(Locale.US, "%02d:%02d", value / 60, value % 60);
    }

    private String mediaModeLabel() {
        return LauncherPrefs.MEDIA_MODE_PREFER_MUSIC.equals(LauncherPrefs.mediaMode(this))
                ? "Prefer music" : "Automatic";
    }

    private String pickerFallback(String key) {
        if (LauncherPrefs.KEY_MUSIC.equals(key)) {
            String pkg = TopwayAdapter.defaultMusicPackage(this);
            if (!pkg.isEmpty()) return AppResolver.labelFor(this, pkg, pkg);
            return "Automatic";
        }
        if (LauncherPrefs.KEY_RADIO.equals(key)) {
            String pkg = RadioProvider.resolvePackage(this);
            if (!pkg.isEmpty()) return AppResolver.labelFor(this, pkg, pkg);
            return "Automatic";
        }
        if (LauncherPrefs.KEY_NAV.equals(key)) return "Automatic";
        return "Not set";
    }

    private void showMediaDiagnostics() {
        MediaListenerService.refreshActiveSessions();
        String sessions = MediaListenerService.sessionDiagnostics(this);
        String trace = MediaEventTrace.dump(40);
        new AlertDialog.Builder(this).setTitle("Media diagnostics")
                .setMessage(sessions
                        + "\n\nRecent media events"
                        + "\nPlayback acknowledgement is not audible-output proof.\n"
                        + trace)
                .setPositiveButton("Close", null).show();
    }

    private void confirmRootHome() {
        new AlertDialog.Builder(this).setTitle("Set TS18 Launcher as Home?")
                .setMessage("This uses Magisk root to set TS18 Launcher as the default Home app. "
                        + "DoFun stays installed for recovery.")
                .setNegativeButton("Cancel", null).setPositiveButton("Continue", (dialog, which) -> setHomeWithRoot()).show();
    }

    private void confirmDisableHome() {
        new AlertDialog.Builder(this).setTitle("Disable TS18 Launcher as a Home app?")
                .setMessage("The app stays installed. DoFun remains available as a recovery Home app.")
                .setNegativeButton("Cancel", null).setPositiveButton("Disable", (dialog, which) -> {
                    HomeMode.setHomeAliasEnabled(this, false);
                    Toast.makeText(this, "TS18 Launcher Home option disabled", Toast.LENGTH_LONG).show(); render();
                }).show();
    }

    private void setHomeWithRoot() {
        Toast.makeText(this, "Requesting Magisk access…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            RootShell.Result result = HomeMode.setHomeWithRoot(this);
            runOnUiThread(() -> {
                if (result.success() && HomeMode.isDefaultHome(this)) {
                    Toast.makeText(this, "TS18 Launcher is now the default Home app", Toast.LENGTH_LONG).show(); render();
                } else {
                    Toast.makeText(this, "Root setup did not complete; opening Android Home settings",
                            Toast.LENGTH_LONG).show();
                    HomeMode.requestHomeRole(this);
                }
            });
        }, "ts18-root-home").start();
    }
}
