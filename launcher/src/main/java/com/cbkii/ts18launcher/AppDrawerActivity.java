package com.cbkii.ts18launcher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
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

@SuppressLint("SetTextI18n")
public final class AppDrawerActivity extends Activity {
    public static final String EXTRA_PICK_KEY = "pick_key";

    private final List<Entry> allEntries = new ArrayList<>();
    private final List<Entry> visibleEntries = new ArrayList<>();
    private final AppsAdapter adapter = new AppsAdapter();
    private String pickKey;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        pickKey = getIntent().getStringExtra(EXTRA_PICK_KEY);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(AutomotiveUi.color(this, R.color.ui_black));
        int gutter = AutomotiveUi.dimen(this, R.dimen.ui_gutter);
        root.setPadding(gutter, gutter, gutter, gutter);

        TextView header = new TextView(this);
        header.setText(pickKey == null ? "Apps" : "Choose app");
        header.setTextColor(AutomotiveUi.color(this, R.color.ui_text));
        header.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.ui_drawer_header_text));
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(gutter, 0, gutter, 0);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                AutomotiveUi.dimen(this, R.dimen.ui_drawer_header_height)));

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setBackground(AutomotiveUi.cardBackground(this));
        ImageView searchIcon = new ImageView(this);
        searchIcon.setImageResource(R.drawable.ic_search);
        searchIcon.setColorFilter(AutomotiveUi.color(this, R.color.ui_icon));
        searchIcon.setPadding(gutter, gutter, gutter, gutter);
        searchRow.addView(searchIcon, new LinearLayout.LayoutParams(56, 56));
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search apps");
        search.setHintTextColor(AutomotiveUi.color(this, R.color.ui_text_secondary));
        search.setTextColor(AutomotiveUi.color(this, R.color.ui_text));
        search.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.ui_search_text));
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
                AutomotiveUi.dimen(this, R.dimen.ui_search_height)));

        GridView grid = new GridView(this);
        grid.setNumColumns(5);
        grid.setHorizontalSpacing(gutter);
        grid.setVerticalSpacing(gutter);
        grid.setPadding(gutter, gutter, gutter, gutter);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> onEntry(visibleEntries.get(position)));
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Ts18SafeArea.setContent(this, root);
        loadEntries();
        filter("");
    }

    private void loadEntries() {
        allEntries.clear();
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(query, PackageManager.MATCH_ALL);
        Set<String> seenPackages = new HashSet<>();
        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null) continue;
            String packageName = info.activityInfo.packageName;
            if (getPackageName().equals(packageName) || seenPackages.contains(packageName)) continue;
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

    private void onEntry(Entry entry) {
        if (pickKey != null && !pickKey.isEmpty()) {
            LauncherPrefs.setPackage(this, pickKey, entry.packageName);
            Toast.makeText(this, entry.label + " selected", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (!AppResolver.launchComponent(this, entry.packageName, entry.activityName)) {
            Toast.makeText(this, "App unavailable", Toast.LENGTH_SHORT).show();
        }
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
        public android.view.View getView(int position, android.view.View convertView, ViewGroup parent) {
            LinearLayout cell;
            ImageView icon;
            TextView label;
            if (convertView instanceof LinearLayout) {
                cell = (LinearLayout) convertView;
                icon = (ImageView) cell.getChildAt(0);
                label = (TextView) cell.getChildAt(1);
            } else {
                cell = new LinearLayout(AppDrawerActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                icon = new ImageView(AppDrawerActivity.this);
                label = new TextView(AppDrawerActivity.this);
                label.setTextColor(AutomotiveUi.color(AppDrawerActivity.this, R.color.ui_text));
                label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimension(R.dimen.ui_drawer_label_text));
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(2);
                cell.addView(icon, new LinearLayout.LayoutParams(
                        AutomotiveUi.dimen(AppDrawerActivity.this, R.dimen.ui_drawer_icon),
                        AutomotiveUi.dimen(AppDrawerActivity.this, R.dimen.ui_drawer_icon)));
                cell.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 46));
            }
            Entry entry = visibleEntries.get(position);
            if (entry.icon == null) {
                PackageManager pm = getPackageManager();
                try {
                    entry.icon = pm.getActivityIcon(new ComponentName(entry.packageName, entry.activityName));
                } catch (PackageManager.NameNotFoundException ignored) {
                    try {
                        entry.icon = pm.getApplicationIcon(entry.packageName);
                    } catch (PackageManager.NameNotFoundException ignoredAgain) {
                        entry.icon = getDrawable(R.drawable.ic_launcher);
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
