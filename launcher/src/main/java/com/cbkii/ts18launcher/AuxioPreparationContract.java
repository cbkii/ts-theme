package com.cbkii.ts18launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;

/** Capability of the installed paired Auxio build; never assume an extra makes an old APK silent. */
final class AuxioPreparationContract {
    static final String COMPONENT = "com.tw.media/org.oxycblt.auxio.headunit.LauncherPreparationActivity";
    private static final String CAPABILITY = "org.oxycblt.auxio.PREPARE_HOME_PROTOCOL";

    static boolean supported(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            android.content.pm.ApplicationInfo app = pm.getApplicationInfo("com.tw.media", PackageManager.GET_META_DATA);
            ActivityInfo entry = pm.getActivityInfo(ComponentName.unflattenFromString(COMPONENT), 0);
            return app.enabled && app.uid / AndroidUserId.UID_RANGE_PER_USER == AndroidUserId.current()
                    && app.metaData != null && app.metaData.getInt(CAPABILITY, 0) == 1
                    && entry.enabled && !entry.exported && "com.tw.media".equals(entry.packageName)
                    && entry.applicationInfo != null && entry.applicationInfo.uid == app.uid;
        } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) { return false; }
    }

    static String canonicalEntry(Context context, ComponentName component) {
        try {
            ActivityInfo info = context.getPackageManager().getActivityInfo(component, 0);
            String target = info.targetActivity == null ? info.name : info.targetActivity;
            return new ComponentName(component.getPackageName(), target).flattenToString();
        } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) { return ""; }
    }
}
