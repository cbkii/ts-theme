package com.cbkii.ts18launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Foreground subscriptions to one process-owned, cached appearance snapshot. No sensor on TS18. */
final class AppearanceController {
    interface Callback { void onAppearanceChanged(String mode); }
    // Engine retains only the application context; Activity subscriptions are removed onStop.
    @android.annotation.SuppressLint("StaticFieldLeak")
    private static Engine engine;
    private final Engine owner;
    private final Callback callback;
    private String deliveredMode;

    AppearanceController(Context context, Callback callback) {
        owner = engine(context);
        this.callback = callback;
        deliveredMode = resolvedMode(context);
    }

    private static Engine engine(Context context) {
        if (engine == null) engine = new Engine(context.getApplicationContext());
        return engine;
    }

    void start() { owner.add(this); }
    void stop() { owner.remove(this); }

    static String resolvedMode(Context context) {
        Engine owner = engine(context);
        return owner.observers.isEmpty() ? resolve(context) : owner.mode;
    }

    private static String resolve(Context context) {
        String configured = LauncherPrefs.appearanceMode(context);
        if (!LauncherPrefs.APPEARANCE_AUTO.equals(configured)) return configured;
        Calendar calendar = Calendar.getInstance();
        return AppearanceSchedule.resolve(calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE),
                LauncherPrefs.appearanceDayStartMinutes(context), LauncherPrefs.appearanceNightStartMinutes(context));
    }

    private void deliver(String mode) {
        if (mode.equals(deliveredMode)) return;
        deliveredMode = mode;
        if (callback != null) callback.onAppearanceChanged(mode);
    }

    private static final class Engine {
        final Context context;
        final List<AppearanceController> observers = new ArrayList<>();
        final Handler handler = new Handler(Looper.getMainLooper());
        String mode;
        final Runnable tick = this::refresh;
        final SharedPreferences.OnSharedPreferenceChangeListener preferences = (prefs, key) -> {
            if (key != null && !key.startsWith("ui.appearance.")) return;
            // Imports may notify off-main. Coalesce them and read the complete preference transaction.
            handler.removeCallbacks(tick);
            handler.post(tick);
        };
        final BroadcastReceiver time = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) { refresh(); }
        };

        Engine(Context context) { this.context = context; mode = resolve(context); }

        void add(AppearanceController observer) {
            if (observers.contains(observer)) return;
            boolean first = observers.isEmpty();
            observers.add(observer);
            if (first) {
                LauncherPrefs.prefs(context).registerOnSharedPreferenceChangeListener(preferences);
                IntentFilter filter = new IntentFilter(Intent.ACTION_TIME_CHANGED);
                filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
                filter.addAction(Intent.ACTION_SCREEN_ON);
                context.registerReceiver(time, filter);
            }
            refresh();
            observer.deliver(mode);
        }

        void remove(AppearanceController observer) {
            if (!observers.remove(observer) || !observers.isEmpty()) return;
            handler.removeCallbacks(tick);
            LauncherPrefs.prefs(context).unregisterOnSharedPreferenceChangeListener(preferences);
            context.unregisterReceiver(time);
        }

        void refresh() {
            handler.removeCallbacks(tick);
            if (observers.isEmpty()) return;
            mode = resolve(context);
            for (AppearanceController observer : new ArrayList<>(observers)) observer.deliver(mode);
            if (LauncherPrefs.APPEARANCE_AUTO.equals(LauncherPrefs.appearanceMode(context)))
                handler.postDelayed(tick, 60_000L - System.currentTimeMillis() % 60_000L);
        }
    }
}
