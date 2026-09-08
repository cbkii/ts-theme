package com.cbkii.ts18launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

final class AppResolver {
    private AppResolver() {}

    static boolean launchPackage(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) return false;
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
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
