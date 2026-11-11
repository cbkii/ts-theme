package com.cbkii.ts18launcher.navigation.n3;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

/** Draws a solid frame so the N3 path has real pixels to observe. Test APK only. */
public final class N3FixtureActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        View view = new View(this);
        view.setBackgroundColor(Color.rgb(0, 160, 90));
        setContentView(view);
    }
}
