package com.cbkii.ts18launcher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** In-HOME app list. It overlays only the map surface, leaving the media strip visible. */
@SuppressLint({"SetTextI18n", "ViewConstructor"})
final class AppDrawerPanel extends android.widget.FrameLayout {
    private final Activity activity;
    private final Runnable onDismiss;
    private final List<Entry> allEntries = new ArrayList<>();
    private final List<Entry> visibleEntries = new ArrayList<>();
    private final LinearLayout quickRow;
    private final EditText search;
    private final GridView grid;
    private final AppsAdapter adapter = new AppsAdapter();
    private boolean loaded;

    AppDrawerPanel(Activity activity, Runnable onDismiss) {
        super(activity);
        this.activity = activity;
        this.onDismiss = onDismiss;
        setBackgroundColor(0xF7050505);

        int gap = AutomotiveUi.dimen(activity, R.dimen.driver_gap);
        int target = AutomotiveUi.dimen(activity, R.dimen.driver_target_min);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(gap, gap, gap, gap);
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(activity);
        title.setText("Apps");
        title.setTextColor(AutomotiveUi.color(activity, R.color.ui_text));
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                activity.getResources().getDimension(R.dimen.ui_drawer_header_text));
        title.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium", android.graphics.Typeface.NORMAL));
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        ImageButton settings = iconButton(R.drawable.ic_settings, "Settings");
        settings.setOnClickListener(v -> activity.startActivity(new Intent(activity, SettingsActivity.class)));
        header.addView(settings, new LinearLayout.LayoutParams(target, target));
        ImageButton close = iconButton(R.drawable.ic_close, "Close apps");
        close.setOnClickListener(v -> hidePanel());
        header.addView(close, new LinearLayout.LayoutParams(target, target));
        AutomotiveUi.linkHorizontal(java.util.Arrays.asList(settings, close));
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                AutomotiveUi.dimen(activity, R.dimen.ui_drawer_header_height)));

        quickRow = new LinearLayout(activity);
        quickRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(quickRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                AutomotiveUi.dimen(activity, R.dimen.ui_drawer_quick_height)));

        LinearLayout searchRow = new LinearLayout(activity);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setBackground(AutomotiveUi.cardBackground(activity));
        ImageView searchIcon = new ImageView(activity);
        searchIcon.setImageResource(R.drawable.ic_search);
        searchIcon.setColorFilter(AutomotiveUi.color(activity, R.color.ui_icon));
        searchIcon.setPadding(gap, gap, gap, gap);
        searchRow.addView(searchIcon, new LinearLayout.LayoutParams(56, 56));
        search = new EditText(activity);
        search.setSingleLine(true);
        search.setHint("Search apps");
        search.setHintTextColor(AutomotiveUi.color(activity, R.color.ui_text_secondary));
        search.setTextColor(AutomotiveUi.color(activity, R.color.ui_text));
        search.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                activity.getResources().getDimension(R.dimen.ui_search_text));
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setImeOptions(EditorInfo.IME_ACTION_DONE);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        searchRow.addView(search, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        ImageButton voice = iconButton(R.drawable.ic_mic, "Voice search");
        boolean voiceAvailable = VoiceSearch.available(activity);
        voice.setEnabled(voiceAvailable);
        voice.setContentDescription(voiceAvailable ? "Voice search" : "Voice search unavailable");
        voice.setOnClickListener(v -> activity.startActivityForResult(VoiceSearch.intent(), VoiceSearch.REQUEST_CODE));
        searchRow.addView(voice, new LinearLayout.LayoutParams(target, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(searchRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                AutomotiveUi.dimen(activity, R.dimen.ui_search_height)));

        grid = new GridView(activity);
        grid.setNumColumns(5);
        grid.setHorizontalSpacing(gap);
        grid.setVerticalSpacing(gap);
        grid.setPadding(gap, gap, gap, gap);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> launch(visibleEntries.get(position)));
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setVisibility(View.GONE);
    }

    boolean isOpen() { return getVisibility() == View.VISIBLE; }

    void showPanel() {
        if (!loaded) { loadEntries(); loaded = true; }
        refreshPreferences();
        if (search.length() == 0) filter(""); else search.setText("");
        search.clearFocus();
        setAlpha(0f);
        setVisibility(View.VISIBLE);
        bringToFront();
        animate().cancel();
        animate().alpha(1f).setDuration(AutomotiveUi.DRAWER_MS).start();
    }

    void hidePanel() {
        if (!isOpen()) return;
        dismissKeyboard();
        animate().cancel();
        animate().alpha(0f).setDuration(AutomotiveUi.DRAWER_MS).withEndAction(() -> {
            setVisibility(View.GONE);
            setAlpha(1f);
            if (onDismiss != null) onDismiss.run();
        }).start();
    }

    void hideImmediately() {
        animate().cancel();
        dismissKeyboard();
        setAlpha(1f);
        setVisibility(View.GONE);
    }

    void restoreDashboardRoot() {
        hideImmediately();
        search.setText("");
        filter("");
        grid.setSelection(0);
    }

    void applyAppearance() {
        setBackgroundColor(AutomotiveUi.color(activity, R.color.ui_black));
        recolour(this);
        refreshPreferences();
        adapter.notifyDataSetChanged();
    }

    private void recolour(View view) {
        if (view instanceof TextView) ((TextView) view).setTextColor(AutomotiveUi.color(activity, R.color.ui_text));
        if (view instanceof EditText) ((EditText) view).setHintTextColor(AutomotiveUi.color(activity, R.color.ui_text_secondary));
        if (view instanceof ImageButton) AutomotiveUi.styleRailButton(activity, (ImageButton) view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) recolour(group.getChildAt(i));
        }
    }

    void applyVoiceSearch(String query) {
        if (!isOpen() || query == null || query.trim().isEmpty()) return;
        search.setText(query.trim());
        search.setSelection(search.length());
        search.clearFocus();
        dismissKeyboard();
    }

    void refreshPreferences() {
        quickRow.removeAllViews();
        List<View> focus = new ArrayList<>();
        int target = AutomotiveUi.dimen(activity, R.dimen.driver_target_min);
        for (int i = 0; i < LauncherPrefs.DRAWER_QUICK_KEYS.length; i++) {
            final int index = i;
            LinearLayout cell = new LinearLayout(activity);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            String role = LauncherPrefs.drawerQuickRole(activity, i);
            ImageButton button = iconButton(RoleIconCatalog.icon(role), RoleIconCatalog.label(role));
            ShortcutSlot.bind(activity, button, LauncherPrefs.DRAWER_QUICK_KEYS[i]);
            button.setOnClickListener(v -> openDrawerQuick(index));
            button.setOnLongClickListener(v -> { openPicker(LauncherPrefs.DRAWER_QUICK_KEYS[index]); return true; });
            TextView label = new TextView(activity);
            label.setText(drawerQuickLabel(i));
            label.setTextColor(AutomotiveUi.color(activity, R.color.ui_text_secondary));
            label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                    activity.getResources().getDimension(R.dimen.ui_drawer_quick_label_text));
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            cell.addView(button, new LinearLayout.LayoutParams(target, target));
            cell.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 34));
            quickRow.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            focus.add(button);
        }
        AutomotiveUi.linkHorizontal(focus);
    }

    private ImageButton iconButton(int res, String description) {
        ImageButton button = new ImageButton(activity);
        button.setImageResource(res);
        button.setContentDescription(description);
        AutomotiveUi.styleRailButton(activity, button);
        return button;
    }

    private void dismissKeyboard() {
        search.clearFocus();
        InputMethodManager input = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (input != null) input.hideSoftInputFromWindow(search.getWindowToken(), 0);
    }

    private void loadEntries() {
        allEntries.clear();
        PackageManager pm = activity.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(query, PackageManager.MATCH_ALL);
        Set<String> seenPackages = new HashSet<>();
        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null) continue;
            String packageName = info.activityInfo.packageName;
            if (activity.getPackageName().equals(packageName) || seenPackages.contains(packageName)) continue;
            Intent canonical = pm.getLaunchIntentForPackage(packageName);
            ComponentName component = canonical == null ? null : canonical.getComponent();
            if (component == null) continue;
            seenPackages.add(packageName);
            CharSequence label = info.activityInfo.applicationInfo.loadLabel(pm);
            allEntries.add(new Entry(packageName, component.getClassName(),
                    label == null ? packageName : label.toString()));
        }
        Collections.sort(allEntries, Comparator.comparing(e -> e.label.toLowerCase(Locale.ROOT)));
    }

    private void filter(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        visibleEntries.clear();
        for (Entry entry : allEntries) {
            if (needle.isEmpty() || entry.label.toLowerCase(Locale.ROOT).contains(needle)
                    || entry.packageName.toLowerCase(Locale.ROOT).contains(needle)) visibleEntries.add(entry);
        }
        adapter.notifyDataSetChanged();
    }

    private String drawerQuickLabel(int index) {
        return ShortcutSlot.label(activity, LauncherPrefs.DRAWER_QUICK_KEYS[index]);
    }

    private void openDrawerQuick(int index) {
        if (!ShortcutSlot.launch(activity, LauncherPrefs.DRAWER_QUICK_KEYS[index])) openPicker(LauncherPrefs.DRAWER_QUICK_KEYS[index]);
    }

    private void openPicker(String key) {
        Intent intent = new Intent(activity, AppDrawerActivity.class);
        intent.putExtra(AppDrawerActivity.EXTRA_PICK_KEY, key);
        activity.startActivity(intent);
    }

    private void launch(Entry entry) {
        if (AppResolver.launchComponent(activity, entry.packageName, entry.activityName)) {
            dismissKeyboard();
            setVisibility(View.GONE);
            return;
        }
        Toast.makeText(activity, "App unavailable", Toast.LENGTH_SHORT).show();
    }

    private static final class Entry {
        final String packageName;
        final String activityName;
        final String label;
        Drawable icon;
        Entry(String packageName, String activityName, String label) {
            this.packageName = packageName; this.activityName = activityName; this.label = label;
        }
    }

    private final class AppsAdapter extends BaseAdapter {
        @Override public int getCount() { return visibleEntries.size(); }
        @Override public Object getItem(int position) { return visibleEntries.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout cell;
            ImageView icon;
            TextView label;
            if (convertView instanceof LinearLayout) {
                cell = (LinearLayout) convertView;
                icon = (ImageView) cell.getChildAt(0);
                label = (TextView) cell.getChildAt(1);
            } else {
                cell = new LinearLayout(activity);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                icon = new ImageView(activity);
                icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                label = new TextView(activity);
                label.setTextColor(AutomotiveUi.color(activity, R.color.ui_text));
                label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                        activity.getResources().getDimension(R.dimen.ui_drawer_label_text));
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(2);
                cell.addView(icon, new LinearLayout.LayoutParams(
                        AutomotiveUi.dimen(activity, R.dimen.ui_drawer_icon),
                        AutomotiveUi.dimen(activity, R.dimen.ui_drawer_icon)));
                cell.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 46));
            }
            Entry entry = visibleEntries.get(position);
            if (entry.icon == null) {
                PackageManager pm = activity.getPackageManager();
                try { entry.icon = pm.getActivityIcon(new ComponentName(entry.packageName, entry.activityName)); }
                catch (PackageManager.NameNotFoundException ignored) {
                    try { entry.icon = pm.getApplicationIcon(entry.packageName); }
                    catch (PackageManager.NameNotFoundException ignoredAgain) { entry.icon = activity.getDrawable(R.drawable.ic_launcher); }
                }
            }
            icon.setImageDrawable(entry.icon);
            label.setText(entry.label);
            cell.setMinimumHeight(116);
            return cell;
        }
    }
}
