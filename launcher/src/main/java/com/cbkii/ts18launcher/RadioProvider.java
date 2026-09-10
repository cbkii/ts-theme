package com.cbkii.ts18launcher;

import android.content.Context;
import android.content.pm.PackageManager;

/** Public-package radio resolver. It never synthesises radio commands or private Topway calls. */
final class RadioProvider {
    static final String NAVRADIO_PLUS_PACKAGE = "com.navimods.radio";

    private RadioProvider() {}

    static String resolvePackage(Context context) {
        String configured = LauncherPrefs.packageFor(context, LauncherPrefs.KEY_RADIO);
        if (!configured.isEmpty()) return configured;
        return isInstalled(context, NAVRADIO_PLUS_PACKAGE) ? NAVRADIO_PLUS_PACKAGE : "";
    }

    static boolean isAutoDetectedNavRadio(Context context) {
        return LauncherPrefs.packageFor(context, LauncherPrefs.KEY_RADIO).isEmpty()
                && NAVRADIO_PLUS_PACKAGE.equals(resolvePackage(context));
    }

    private static boolean isInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getApplicationInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
