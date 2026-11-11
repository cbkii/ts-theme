package com.cbkii.ts18launcher;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** One process-scoped catalogue. Package queries, labels and icon decoding run off the UI thread. */
final class AppCatalog {
    static final class Entry {
        final String packageName, activityName, label;
        Entry(String pkg, String activity, String label) {
            this.packageName = pkg; this.activityName = activity; this.label = label;
        }
        String target() { return new ComponentName(packageName, activityName).flattenToString(); }
    }

    @android.annotation.SuppressLint("StaticFieldLeak") // Application context only.
    private static AppCatalog instance;
    static synchronized AppCatalog get(Context context) {
        if (instance == null) instance = new AppCatalog(context.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ts18-app-catalog");
        thread.setDaemon(true); thread.setPriority(Thread.MIN_PRIORITY); return thread;
    });
    private final Set<Consumer<List<Entry>>> listeners = new LinkedHashSet<>();
    private final LruCache<String, Bitmap> icons = new LruCache<String, Bitmap>(2 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getAllocationByteCount(); }
    };
    private final Map<String, List<Consumer<Bitmap>>> pendingIcons = new LinkedHashMap<>();
    private List<Entry> snapshot;
    private boolean loading;
    private int generation, iconEpoch;
    private final Map<String, Integer> revisions = new LinkedHashMap<>();
    private final Set<String> changedPackages = new LinkedHashSet<>();

    private AppCatalog(Context app) {
        this.app = app;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED); filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED); filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        app.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                String pkg = intent.getData() == null ? null : intent.getData().getSchemeSpecificPart();
                if (pkg == null || pkg.isEmpty()) invalidate(); else invalidatePackage(pkg);
            }
        }, filter);
    }

    void subscribe(Consumer<List<Entry>> listener) {
        listeners.add(listener);
        if (snapshot != null) listener.accept(snapshot);
        if (snapshot == null || !changedPackages.isEmpty()) load();
    }
    void unsubscribe(Consumer<List<Entry>> listener) { listeners.remove(listener); }
    void trim() { icons.evictAll(); }
    void invalidate() {
        generation++; iconEpoch++; snapshot = null; icons.evictAll();
        revisions.clear(); changedPackages.clear();
        AppResolver.invalidateLabels();
        RadioProvider.invalidate();
        com.cbkii.ts18launcher.platform.TopwayAdapter.invalidatePackages();
        if (!listeners.isEmpty()) load();
    }
    private void invalidatePackage(String pkg) {
        generation++; changedPackages.add(pkg);
        revisions.put(pkg, revisions.getOrDefault(pkg, 0) + 1);
        for (String key : icons.snapshot().keySet()) if (key.startsWith(pkg + ":")) icons.remove(key);
        AppResolver.invalidateLabels(); RadioProvider.invalidate();
        com.cbkii.ts18launcher.platform.TopwayAdapter.invalidatePackages();
        if (!listeners.isEmpty()) load();
    }
    private void load() {
        if (loading) return;
        loading = true;
        int request = generation;
        List<Entry> prior = snapshot;
        Set<String> changed = new LinkedHashSet<>(changedPackages);
        worker.execute(() -> {
            List<Entry> entries;
            if (prior == null) entries = discover(app);
            else {
                entries = new ArrayList<>();
                for (Entry entry : prior) if (!changed.contains(entry.packageName)) entries.add(entry);
                for (String pkg : changed) entries.addAll(discover(app, pkg));
                sort(entries);
            }
            main.post(() -> {
                loading = false;
                if (request != generation) { if (!listeners.isEmpty()) load(); return; }
                changedPackages.clear();
                snapshot = Collections.unmodifiableList(entries);
                for (Consumer<List<Entry>> listener : new ArrayList<>(listeners)) listener.accept(snapshot);
            });
        });
    }

    static List<Entry> discover(Context context) {
        return discover(context, null);
    }
    static List<Entry> discover(Context context, String pkg) {
        PackageManager pm = context.getPackageManager();
        List<Entry> entries = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        if (pkg != null) query.setPackage(pkg);
        try {
            for (ResolveInfo info : pm.queryIntentActivities(query, 0)) {
                ActivityInfo activity = info.activityInfo;
                if (activity != null && pkg != null && !pkg.equals(activity.packageName)) continue;
                if (!launchable(context, activity) || context.getPackageName().equals(activity.packageName)) continue;
                ComponentName component = new ComponentName(activity.packageName, activity.name);
                if (!seen.add(component.flattenToString())) continue;
                CharSequence label = info.loadLabel(pm);
                entries.add(new Entry(activity.packageName, activity.name,
                        label == null ? activity.packageName : label.toString()));
            }
        } catch (RuntimeException unavailable) { return entries; }
        sort(entries);
        return entries;
    }
    private static void sort(List<Entry> entries) {
        Collator collator = Collator.getInstance();
        entries.sort((a, b) -> { int order = collator.compare(a.label, b.label);
            return order != 0 ? order : a.target().compareTo(b.target()); });
    }

    static boolean launchable(Context context, ActivityInfo activity) {
        return activity != null && activity.enabled && activity.exported
                && activity.applicationInfo != null && activity.applicationInfo.enabled
                && (activity.permission == null || activity.permission.isEmpty()
                    || context.checkSelfPermission(activity.permission) == PackageManager.PERMISSION_GRANTED);
    }

    void bindIcon(ImageView view, String target, int size) {
        int pixels = Math.max(1, Math.min(256, size));
        String pkg = AppResolver.packagePart(target);
        int revision = revisions.getOrDefault(pkg, 0);
        int epoch = iconEpoch;
        String key = pkg + ":" + epoch + ":" + revision + ":" + target + ":" + pixels;
        view.setTag(R.id.catalog_icon_target, key);
        Bitmap cached = icons.get(key);
        if (cached != null) { view.setImageDrawable(new BitmapDrawable(app.getResources(), cached)); return; }
        view.setImageResource(R.drawable.ic_shortcut);
        WeakReference<ImageView> reference = new WeakReference<>(view);
        Consumer<Bitmap> deliver = bitmap -> {
            ImageView image = reference.get();
            if (image != null && key.equals(image.getTag(R.id.catalog_icon_target)) && bitmap != null)
                image.setImageDrawable(new BitmapDrawable(app.getResources(), bitmap));
        };
        List<Consumer<Bitmap>> waiting = pendingIcons.get(key);
        if (waiting != null) { waiting.add(deliver); return; }
        waiting = new ArrayList<>(); waiting.add(deliver); pendingIcons.put(key, waiting);
        int request = revision;
        worker.execute(() -> {
            Bitmap bitmap = null;
            try {
                ComponentName component = ComponentName.unflattenFromString(target);
                Drawable icon;
                try { icon = component == null ? app.getPackageManager().getApplicationIcon(pkg)
                        : app.getPackageManager().getActivityIcon(component); }
                catch (PackageManager.NameNotFoundException missingComponent) {
                    icon = app.getPackageManager().getApplicationIcon(pkg);
                }
                bitmap = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888);
                icon.setBounds(0, 0, pixels, pixels); icon.draw(new Canvas(bitmap));
            } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) { /* placeholder */ }
            Bitmap result = bitmap;
            main.post(() -> {
                List<Consumer<Bitmap>> callbacks = pendingIcons.remove(key);
                if (epoch != iconEpoch || request != revisions.getOrDefault(pkg, 0)) return;
                if (result != null) icons.put(key, result);
                if (callbacks != null) for (Consumer<Bitmap> callback : callbacks) callback.accept(result);
            });
        });
    }
}
