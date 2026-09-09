package com.cbkii.ts18launcher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
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
import java.util.Set;

/** In-HOME app list. It overlays only the map surface, leaving the media strip visible. */
@SuppressLint({"SetTextI18n", "ViewConstructor"})
final class AppDrawerPanel extends FrameLayout {
    private final Activity activity;
    private final Runnable onDismiss;
    private final List<Entry> entries = new ArrayList<>();
    private final GridView grid;
    private boolean loaded;

    AppDrawerPanel(Activity activity, Runnable onDismiss) {
        super(activity);
        this.activity = activity;
        this.onDismiss = onDismiss;
        setBackgroundColor(0xF7111111);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(16, 0, 8, 0);
        TextView title = new TextView(activity);
        title.setText("Apps");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20f);
        header.addView(title, new LinearLayout.LayoutParams(0, 52, 1f));
        Button close = new Button(activity);
        close.setAllCaps(false);
        close.setText("CLOSE");
        close.setTextColor(Color.WHITE);
        close.setTextSize(12f);
        close.setBackgroundColor(Color.TRANSPARENT);
        close.setOnClickListener(v -> hidePanel());
        header.addView(close, new LinearLayout.LayoutParams(88, 52));
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 52));

        grid = new GridView(activity);
        grid.setNumColumns(5);
        grid.setHorizontalSpacing(8);
        grid.setVerticalSpacing(8);
        grid.setPadding(12, 6, 12, 10);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setOnItemClickListener((parent, view, position, id) -> launch(entries.get(position)));
        root.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setVisibility(View.GONE);
    }

    boolean isOpen() {
        return getVisibility() == View.VISIBLE;
    }

    void showPanel() {
        if (!loaded) {
            loadEntries();
            grid.setAdapter(new AppsAdapter());
            loaded = true;
        }
        setVisibility(View.VISIBLE);
        bringToFront();
    }

    void hidePanel() {
        if (!isOpen()) return;
        setVisibility(View.GONE);
        if (onDismiss != null) onDismiss.run();
    }

    private void loadEntries() {
        entries.clear();
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
            entries.add(new Entry(
                    packageName,
                    component.getClassName(),
                    label == null ? packageName : label.toString()));
        }
        Collections.sort(entries, Comparator.comparing(e -> e.label.toLowerCase(java.util.Locale.ROOT)));
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
                cell = new LinearLayout(activity);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(5, 6, 5, 6);
                icon = new ImageView(activity);
                label = new TextView(activity);
                label.setTextColor(Color.WHITE);
                label.setTextSize(12f);
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(2);
                cell.addView(icon, new LinearLayout.LayoutParams(52, 52));
                cell.addView(label, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 42));
            }
            Entry entry = entries.get(position);
            if (entry.icon == null) {
                PackageManager pm = activity.getPackageManager();
                try {
                    entry.icon = pm.getActivityIcon(
                            new ComponentName(entry.packageName, entry.activityName));
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
            return cell;
        }
    }
}
