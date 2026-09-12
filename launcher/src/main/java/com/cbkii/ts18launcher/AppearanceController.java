package com.cbkii.ts18launcher;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.Calendar;

final class AppearanceController implements SensorEventListener {
    interface Callback { void onAppearanceChanged(String mode); }

    private static final long SENSOR_STALE_MS = 5 * 60_000L;
    private static volatile float lastLux = Float.NaN;
    private static volatile long lastLuxElapsedRealtime;

    private final Context context;
    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable scheduleTick = this::reevaluateAndSchedule;
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private String deliveredMode;
    private boolean started;

    AppearanceController(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
        // Capture the mode used by the Activity while it is being constructed. If Settings
        // changes the effective mode while HOME is stopped, start() updates colours in place.
        deliveredMode = resolvedMode(this.context);
    }

    void start() {
        if (started) return;
        started = true;

        String currentMode = resolvedMode(context);
        if (!currentMode.equals(deliveredMode)) {
            deliveredMode = currentMode;
            if (callback != null) callback.onAppearanceChanged(currentMode);
        }

        if (LauncherPrefs.APPEARANCE_AUTO.equals(LauncherPrefs.appearanceMode(context))
                && LauncherPrefs.AUTO_SOURCE_SENSOR.equals(LauncherPrefs.appearanceAutoSource(context))) {
            sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            lightSensor = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            if (lightSensor != null) {
                sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }
        handler.post(scheduleTick);
    }

    void stop() {
        started = false;
        handler.removeCallbacks(scheduleTick);
        if (sensorManager != null) sensorManager.unregisterListener(this);
        sensorManager = null;
        lightSensor = null;
    }

    static String resolvedMode(Context context) {
        String configured = LauncherPrefs.appearanceMode(context);
        if (!LauncherPrefs.APPEARANCE_AUTO.equals(configured)) return configured;

        if (LauncherPrefs.AUTO_SOURCE_SENSOR.equals(LauncherPrefs.appearanceAutoSource(context))
                && sensorSampleUsable()) {
            if (lastLux >= 12000f) return LauncherPrefs.APPEARANCE_HIGH_CONTRAST;
            if (lastLux <= 15f) return LauncherPrefs.APPEARANCE_NIGHT;
            if (lastLux <= 250f) return LauncherPrefs.APPEARANCE_DIM;
            return LauncherPrefs.APPEARANCE_DAY;
        }

        Calendar calendar = Calendar.getInstance();
        int minute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
        return AppearanceSchedule.resolve(minute,
                LauncherPrefs.appearanceDayStartMinutes(context),
                LauncherPrefs.appearanceNightStartMinutes(context));
    }

    static boolean sensorAvailable(Context context) {
        SensorManager manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        return manager != null && manager.getDefaultSensor(Sensor.TYPE_LIGHT) != null;
    }

    private static boolean sensorSampleUsable() {
        if (Float.isNaN(lastLux) || lastLuxElapsedRealtime <= 0L) return false;
        return SystemClock.elapsedRealtime() - lastLuxElapsedRealtime <= SENSOR_STALE_MS;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.values == null || event.values.length == 0) return;
        float sample = Math.max(0f, event.values[0]);
        lastLux = Float.isNaN(lastLux) ? sample : (lastLux * 0.75f + sample * 0.25f);
        lastLuxElapsedRealtime = SystemClock.elapsedRealtime();
        reevaluate();
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void reevaluateAndSchedule() {
        if (!started) return;
        reevaluate();
        handler.postDelayed(scheduleTick, 60_000L);
    }

    private void reevaluate() {
        String mode = resolvedMode(context);
        if (!mode.equals(deliveredMode)) {
            deliveredMode = mode;
            if (callback != null) callback.onAppearanceChanged(mode);
        }
    }
}
