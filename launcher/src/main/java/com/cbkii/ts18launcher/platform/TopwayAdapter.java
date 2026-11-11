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
    public static final String AUXIO_PACKAGE = "com.tw.media";
    public static final String STOCK_MUSIC_PACKAGE = "com.tw.music";

    private static final java.util.Map<String, Boolean> INSTALLED = new java.util.concurrent.ConcurrentHashMap<>();
    public static void invalidatePackages() { INSTALLED.clear(); }
    private TopwayAdapter() {}

    public static boolean isTopwayEnvironment(Context context) {
        return isInstalled(context, DOFUN_PACKAGE);
    }

    public static String defaultMusicPackage(Context context) {
        // Package identity, not Auxio's retained com.tw.music component namespace.
        // This only resolves an unset preference; every caller preserves an explicit choice.
        if (isInstalled(context, AUXIO_PACKAGE)) return AUXIO_PACKAGE;
        return isInstalled(context, STOCK_MUSIC_PACKAGE) ? STOCK_MUSIC_PACKAGE : "";
    }

    private static boolean isInstalled(Context context, String packageName) {
        Boolean cached = INSTALLED.get(packageName);
        if (cached != null) return cached;
        try {
            context.getPackageManager().getApplicationInfo(packageName, 0);
            INSTALLED.put(packageName, true);
            return true;
        } catch (PackageManager.NameNotFoundException missing) {
            INSTALLED.put(packageName, false);
            return false;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }
}
