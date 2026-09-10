package com.cbkii.ts18launcher.platform;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Narrow exact-device adapter. It deliberately exposes only evidence-backed,
 * ordinary PackageManager information. Hardware/MCU/radio authority remains
 * with Topway until an exact TS18 contract is recovered and validated.
 */
public final class TopwayAdapter {
    public static final String DOFUN_PACKAGE = "com.dofun.variety";
    public static final String STOCK_MUSIC_PACKAGE = "com.tw.music";

    private TopwayAdapter() {}

    public static boolean isTopwayEnvironment(Context context) {
        return isInstalled(context, DOFUN_PACKAGE);
    }

    public static String defaultMusicPackage(Context context) {
        return isInstalled(context, STOCK_MUSIC_PACKAGE) ? STOCK_MUSIC_PACKAGE : "";
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
