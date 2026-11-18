package com.cbkii.ts18launcher;

/** Deliberately bright cross-UID splash. Test APK only. */
public final class MaskFixtureActivity extends android.app.Activity {
    public static final String FINISH = "com.cbkii.ts18launcher.test.FINISH_MASK_FIXTURE";
    /** Shell-start cleanup contract: build no UI and remove the whole fixture task immediately.
     * Works from a cold process, unlike the in-process broadcast fast path. */
    public static final String EXTRA_FINISH_NOW = "com.cbkii.ts18launcher.test.extra.FINISH_NOW";
    private static java.lang.ref.WeakReference<MaskFixtureActivity> active = new java.lang.ref.WeakReference<>(null);
    /** Manifest-exported to the shell's DUMP permission; no API33 registration flag on API29. */
    public static final class Cleanup extends android.content.BroadcastReceiver {
        @Override public void onReceive(android.content.Context context, android.content.Intent intent) {
            MaskFixtureActivity activity = active.get();
            if (FINISH.equals(intent.getAction()) && activity != null && !activity.isFinishing())
                activity.finishAndRemoveTask();
        }
    }
    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_FINISH_NOW, false)) {
            active.clear();
            finishAndRemoveTask();
            return;
        }
        active = new java.lang.ref.WeakReference<>(this);
        android.view.View view = new android.view.View(this);
        view.setBackgroundColor(android.graphics.Color.MAGENTA);
        setContentView(view);
    }
    @Override public void onDestroy() { if (active.get() == this) active.clear(); super.onDestroy(); }
}
