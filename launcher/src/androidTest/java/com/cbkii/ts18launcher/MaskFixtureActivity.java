package com.cbkii.ts18launcher;

/** Deliberately bright cross-UID splash. Test APK only. */
public final class MaskFixtureActivity extends android.app.Activity {
    public static final String FINISH = "com.cbkii.ts18launcher.test.FINISH_MASK_FIXTURE";
    private final android.content.BroadcastReceiver cleanup = new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context context, android.content.Intent intent) {
            finishAndRemoveTask();
        }
    };
    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        registerReceiver(cleanup, new android.content.IntentFilter(FINISH));
        android.view.View view = new android.view.View(this);
        view.setBackgroundColor(android.graphics.Color.MAGENTA);
        setContentView(view);
    }
    @Override public void onDestroy() { unregisterReceiver(cleanup); super.onDestroy(); }
}
