package com.cbkii.ts18launcher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
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
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.cbkii.ts18launcher.platform.TopwayAdapter;

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
    private final GridView grid;
    private final AppsAdapter adapter = new AppsAdapter();
    private boolean loaded;

    AppDrawerPanel(Activity activity, Runnable onDismiss) {
        super(activity);
        this.activity = activity;
        this.onDismiss = onDismiss;
        setBackgroundColor(0xF7000000);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int gutter = AutomotiveUi.dimen(activity, R.dimen.ui_gutter);
        root.setPadding(gutter, gutter, gutter, gutter);
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(activity);
        title.setText("Apps");
        title.setTextColor(AutomotiveUi.color(activity, R.color.ui_text));
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                activity.getResources().getDimension(R.dimen.ui_drawer_header_text));
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        ImageButton settings = iconButton(R.drawable.ic_settings, "Settings");
        settings.setOnClickListener(v -> activity.startActivity(new Intent(activity, SettingsActivity.class)));
        header.addView(settings, new LinearLayout.LayoutParams(76, 76));
        ImageButton close = iconButton(R.drawable.ic_close, "Close apps");
        close.setOnClickListener(v -> hidePanel());
        header.addView(close, new LinearLayout.LayoutParams(76, 76));
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
        searchIcon.setPadding(gutter, gutter, gutter, gutter);
        searchRow.addView(searchIcon, new LinearLayout.LayoutParams(56, 56));
        EditText search = new EditText(activity);
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
        root.addView(searchRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                AutomotiveUi.dimen(activity, R.dimen.ui_search_height)));

        grid = new GridView(activity);
        grid.setNumColumns(5);
        grid.setHorizontalSpacing(gutter);
        grid.setVerticalSpacing(gutter);
        grid.setPadding(gutter, gutter, gutter, gutter);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> launch(visibleEntries.get(position)));
        root.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setVisibility(View.GONE);
    }

    boolean isOpen() { return getVisibility() == View.VISIBLE; }

    void showPanel() {
        if (!loaded) {
            loadEntries();
            loaded = true;
        }
        refreshPreferences();
        filter("");
        setVisibility(View.VISIBLE);
        bringToFront();
    }

    void hidePanel() {
        if (!isOpen()) return;
        setVisibility(View.GONE);
        if (onDismiss != null) onDismiss.run();
    }

    void refreshPreferences() {
        quickRow.removeAllViews();
        for (int i = 0; i < LauncherPrefs.DRAWER_QUICK_KEYS.length; i++) {
            final int index = i;
            LinearLayout cell = new LinearLayout(activity);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            ImageButton button = iconButton(AutomotiveUi.drawerQuickRoleIcon(i), "Quick access " + (i + 1));
            button.setOnClickListener(v -> openDrawerQuick(index));
            button.setOnLongClickListener(v -> {
                openPicker(LauncherPrefs.DRAWER_QUICK_KEYS[index]);
                return true;
            });
            TextView label = new TextView(activity);
            label.setText(drawerQuickLabel(i));
            label.setTextColor(AutomotiveUi.color(activity, R.color.ui_text_secondary));
            label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                    activity.getResources().getDimension(R.dimen.ui_drawer_quick_label_text));
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            cell.addView(button, new LinearLayout.LayoutParams(60, 60));
            cell.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 28));
            quickRow.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
    }

    private ImageButton iconButton(int res, String description) {
        ImageButton button = new ImageButton(activity);
        button.setImageResource(res);
        button.setContentDescription(description);
        AutomotiveUi.styleIconButton(activity, button, false);
        return button;
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
                    || entry.packageName.toLowerCase(Locale.ROOT).contains(needle)) {
                visibleEntries.add(entry);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private String resolveDrawerQuickPackage(int index) {
        String direct = LauncherPrefs.packageFor(activity, LauncherPrefs.DRAWER_QUICK_KEYS[index]);
        if (!direct.isEmpty()) return direct;
        switch (index) {
            case 0: return LauncherPrefs.packageFor(activity, LauncherPrefs.KEY_NAV);
            case 1: return RadioProvider.resolvePackage(activity);
            case 2:
                String music = LauncherPrefs.packageFor(activity, LauncherPrefs.KEY_MUSIC);
                return music.isEmpty() ? TopwayAdapter.defaultMusicPackage(activity) : music;
            case 3: return LauncherPrefs.packageFor(activity, LauncherPrefs.KEY_BLUETOOTH);
            default: return "";
        }
    }

    private String drawerQuickLabel(int index) {
        String pkg = resolveDrawerQuickPackage(index);
        String fallback;
        switch (index) {
            case 0: fallback = "Nav"; break;
            case 1: fallback = "Radio"; break;
            case 2: fallback = "Music"; break;
            case 3: fallback = "BT"; break;
            default: fallback = "Extra"; break;
        }
        return AppResolver.labelFor(activity, pkg, fallback);
    }

    private void openDrawerQuick(int index) {
        String pkg = resolveDrawerQuickPackage(index);
        if (!AppResolver.launchPackage(activity, pkg)) openPicker(LauncherPrefs.DRAWER_QUICK_KEYS[index]);
    }

    private void openPicker(String key) {
        Intent intent = new Intent(activity, AppDrawerActivity.class);
        intent.putExtra(AppDrawerActivity.EXTRA_PICK_KEY, key);
        activity.startActivity(intent);
    }

    private void launch(Entry entry) {
        if (AppResolver.launchComponent(activity, entry.packageName, entry.activityName)) {
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
            this.packageName = packageName;
            this.activityName = activityName;
            this.label = label;
        }
    }

    private final class AppsAdapter extends BaseAdapter {
        @Override public int getCount() { return visibleEntries.size(); }
        @Override public Object getItem(int position) { return visibleEntries.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
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
                int pad = AutomotiveUi.dimen(activity, R.dimen.ui_card_inset);
                cell.setPadding(pad, pad, pad, pad);
                icon = new ImageView(activity);
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
                try {
                    entry.icon = pm.getActivityIcon(new ComponentName(entry.packageName, entry.activityName));
                } catch (PackageManager.NameNotFoundException ignored) {
                    try {
                        entry.icon = pm.getApplicationIcon(entry.packageName);
                    } catch (PackageManager.NameNotFoundException ignoredAgain) {
                        entry.icon = activity.getDrawable(R.drawable.ic_launcher);
                    }
                }
            }
            icon.setImageDrawable(entry.icon);
            label.setText(entry.label);
            cell.setMinimumHeight(112);
            return cell;
        }
    }
}
