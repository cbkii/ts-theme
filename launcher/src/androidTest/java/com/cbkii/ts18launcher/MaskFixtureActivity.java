package com.cbkii.ts18launcher;

/** Deliberately bright cross-UID splash. Test APK only. */
public final class MaskFixtureActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        android.view.View view = new android.view.View(this);
        view.setBackgroundColor(android.graphics.Color.MAGENTA);
        setContentView(view);
    }
}
