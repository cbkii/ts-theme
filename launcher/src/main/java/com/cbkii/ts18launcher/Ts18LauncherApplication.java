package com.cbkii.ts18launcher;

import android.app.Application;

/** Process-level startup hooks kept narrow and optional. */
public final class Ts18LauncherApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        NavigationPermissionBootstrapper.ensureEarly(this);
    }
}
