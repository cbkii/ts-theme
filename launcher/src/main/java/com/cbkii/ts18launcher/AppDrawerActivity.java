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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@SuppressLint("SetTextI18n")
public final class AppDrawerActivity extends Activity {
    public static final String EXTRA_PICK_KEY = "pick_key";

    private final List<Entry> entries = new ArrayList<>();
    private String pickKey;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        pickKey = getIntent().getStringExtra(EXTRA_PICK_KEY);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        TextView header = new TextView(this);
        header.setText(pickKey == null ? "Apps" : "Choose app");
        header.setTextColor(Color.WHITE);
        header.setTextSize(24f);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(24, 8, 24, 8);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 56));

        GridView grid = new GridView(this);
        grid.setNumColumns(5);
        grid.setHorizontalSpacing(8);
        grid.setVerticalSpacing(8);
        grid.setPadding(12, 8, 12, 8);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        root.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        loadEntries();
        grid.setAdapter(new AppsAdapter());
        grid.setOnItemClickListener((parent, view, position, id) -> onEntry(entries.get(position)));
    }

    private void loadEntries() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(query, PackageManager.MATCH_ALL);
        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null || getPackageName().equals(info.activityInfo.packageName)) continue;
            CharSequence label = info.loadLabel(pm);
            entries.add(new Entry(
                    info.activityInfo.packageName,
                    info.activityInfo.name,
                    label == null ? info.activityInfo.packageName : label.toString()));
        }
        Collections.sort(entries, Comparator.comparing(e -> e.label.toLowerCase(java.util.Locale.ROOT)));
    }

    private void onEntry(Entry entry) {
        if (pickKey != null && !pickKey.isEmpty()) {
            LauncherPrefs.setPackage(this, pickKey, entry.packageName);
            Toast.makeText(this, entry.label + " selected", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(entry.packageName, entry.activityName);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
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
        @Override public int getCount() { return entries.size(); }
        @Override public Object getItem(int position) { return entries.get(position); }
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
                cell = new LinearLayout(AppDrawerActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(6, 8, 6, 8);
                icon = new ImageView(AppDrawerActivity.this);
                label = new TextView(AppDrawerActivity.this);
                label.setTextColor(Color.WHITE);
                label.setTextSize(13f);
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(2);
                cell.addView(icon, new LinearLayout.LayoutParams(56, 56));
                cell.addView(label, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 48));
            }
            Entry entry = entries.get(position);
            if (entry.icon == null) {
                PackageManager pm = getPackageManager();
                try {
                    entry.icon = pm.getActivityIcon(
                            new ComponentName(entry.packageName, entry.activityName));
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
            return cell;
        }
    }
}
