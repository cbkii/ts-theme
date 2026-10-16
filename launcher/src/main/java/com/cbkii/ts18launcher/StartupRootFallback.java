package com.cbkii.ts18launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.SystemClock;

/** One bounded alternative caller; the shared startup coordinator retains every admission claim. */
final class StartupRootFallback {
    private final Context context;
    private final NavigationRootHelper helper;
    StartupRootFallback(Context context) {
        this.context = context.getApplicationContext();
        helper = new NavigationRootHelper(this.context);
    }
    NavigationHelperResult launch(String pkg, String entry, int homeTask) {
        if (!eligible(pkg, entry) || homeTask <= 0) return NavigationHelperResult.failure("STARTUP_INELIGIBLE", "");
        helper.setDeadline(SystemClock.elapsedRealtime() + 6500L);
        return helper.run("startup-source", pkg, entry, context.getPackageName(), Integer.toString(homeTask));
    }
    NavigationHelperResult restoreHome(String pkg, String entry, int sourceTask, int homeTask) {
        if (!eligible(pkg, entry) || sourceTask <= 0 || homeTask <= 0 || sourceTask == homeTask)
            return NavigationHelperResult.failure("STARTUP_INELIGIBLE", "");
        helper.setDeadline(SystemClock.elapsedRealtime() + 6500L);
        return helper.run("startup-home", pkg, entry, Integer.toString(sourceTask),
                context.getPackageName(), Integer.toString(homeTask));
    }
    static boolean definitelyNotStarted(NavigationHelperResult result) {
        return result != null && ("STARTUP_SOURCE_REJECTED".equals(result.code)
                || "ROOT_UNAVAILABLE".equals(result.code) || "ASSET_COPY_FAILED".equals(result.code));
    }
    private boolean eligible(String pkg, String entry) {
        if (!"com.cbkii.ts18launcher".equals(context.getPackageName())
                || !(MediaSourceAdapter.AUXIO_PACKAGE.equals(pkg) || MediaSourceAdapter.NAVRADIO_PACKAGE.equals(pkg))) return false;
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
            ComponentName component = intent == null ? null : intent.getComponent();
            if (component == null || !pkg.equals(component.getPackageName())
                    || !component.flattenToShortString().equals(entry)) return false;
            ActivityInfo info = context.getPackageManager().getActivityInfo(component, 0);
            return info != null && info.enabled && info.exported && info.applicationInfo != null
                    && info.applicationInfo.enabled && pkg.equals(info.packageName)
                    && (info.applicationInfo.flags & (android.content.pm.ApplicationInfo.FLAG_SYSTEM
                            | android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0
                    && info.applicationInfo.uid % AndroidUserId.UID_RANGE_PER_USER >= 10000
                    && AndroidUserId.fromUid(info.applicationInfo.uid) == AndroidUserId.current();
        } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException ignored) { return false; }
    }
}
