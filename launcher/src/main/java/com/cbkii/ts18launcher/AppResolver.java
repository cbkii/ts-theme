package com.cbkii.ts18launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

final class AppResolver {
    private AppResolver() {}

    static boolean launchPackage(Context context, String packageName) {
        boolean launched = launchPackageQuietly(context, packageName);
        if (launched) MediaListenerService.noteExplicitLaunch(context, packageName);
        return launched;
    }

    enum LaunchOutcome { ACCEPTED, REJECTED, UNCERTAIN }

    /** A SecurityException is a definite admission refusal; arbitrary runtime errors are not. */
    static LaunchOutcome launchForStartup(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return LaunchOutcome.REJECTED;
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent == null) return LaunchOutcome.REJECTED;
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return LaunchOutcome.ACCEPTED;
        } catch (SecurityException | android.content.ActivityNotFoundException rejected) {
            return LaunchOutcome.REJECTED;
        } catch (RuntimeException uncertain) {
            return LaunchOutcome.UNCERTAIN;
        }
    }

    /** Startup preparation must not change the user's selected media source merely by launching it. */
    static boolean launchPackageQuietly(Context context, String packageName) {
        return launchForStartup(context, packageName) == LaunchOutcome.ACCEPTED;
    }

    static boolean launchComponent(Context context, String packageName, String activityName) {
        if (packageName == null || packageName.isEmpty()
                || activityName == null || activityName.isEmpty()) return false;
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(packageName, activityName);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            MediaListenerService.noteExplicitLaunch(context, packageName);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static String labelFor(Context context, String packageName, String fallback) {
        if (packageName == null || packageName.isEmpty()) return fallback;
        PackageManager pm = context.getPackageManager();
        try {
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(info);
            return label == null || label.length() == 0 ? fallback : label.toString();
        } catch (PackageManager.NameNotFoundException e) {
            return fallback;
        }
    }
}
