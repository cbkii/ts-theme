package com.cbkii.ts18launcher;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.SystemClock;

/** Cold preference fallback only; authenticated provider remains the live/renderer authority. */
final class CompactStartupConsent {
    static Boolean read(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo before = pm.getApplicationInfo(CompactStartupPeer.PACKAGE, PackageManager.GET_META_DATA);
            if (!before.enabled || !CompactStartupPeer.trusted(context, before.uid) || before.metaData == null
                    || before.metaData.getInt(NavCompactConsent.CAPABILITY, 0) != 1) return null;
            long version = pm.getPackageInfo(CompactStartupPeer.PACKAGE, 0).lastUpdateTime;
            NavigationHelperResult result = RootTaskSession.get(context).runCurrent(
                    SystemClock.elapsedRealtime() + 1200L, "compact-consent", Integer.toString(AndroidUserId.current()));
            ApplicationInfo after = pm.getApplicationInfo(CompactStartupPeer.PACKAGE, PackageManager.GET_META_DATA);
            if (result == null || !result.success || result.userId != AndroidUserId.current()
                    || !CompactStartupPeer.PACKAGE.equals(result.packageName) || !after.enabled || after.uid != before.uid
                    || after.metaData == null || after.metaData.getInt(NavCompactConsent.CAPABILITY, 0) != 1
                    || !before.sourceDir.equals(after.sourceDir) || !CompactStartupPeer.trusted(context, after.uid)
                    || version != pm.getPackageInfo(CompactStartupPeer.PACKAGE, 0).lastUpdateTime) return null;
            if (!("COMPACT_CONSENT_ENABLED".equals(result.code) || "COMPACT_CONSENT_DISABLED".equals(result.code)))
                return null;
            MediaEventTrace.record("compact-startup", "cold-consent", result.code + " user=" + result.userId);
            return "COMPACT_CONSENT_ENABLED".equals(result.code);
        } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) {
            MediaEventTrace.record("compact-startup", "cold-consent-unavailable", unavailable.getClass().getSimpleName());
            return null;
        }
    }
}
