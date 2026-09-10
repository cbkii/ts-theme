package com.cbkii.ts18launcher;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;

final class HomeMode {
    private static final ComponentName HOME_ALIAS =
            new ComponentName("com.cbkii.ts18launcher", "com.cbkii.ts18launcher.HomeAlias");

    private HomeMode() {}

    static void setHomeAliasEnabled(Context context, boolean enabled) {
        context.getPackageManager().setComponentEnabledSetting(
                HOME_ALIAS,
                enabled
                        ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    static boolean isDefaultHome(Context context) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        android.content.pm.ResolveInfo info =
                context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return info != null
                && info.activityInfo != null
                && context.getPackageName().equals(info.activityInfo.packageName);
    }

    static void requestHomeRole(Activity activity) {
        setHomeAliasEnabled(activity, true);
        try {
            RoleManager roleManager = activity.getSystemService(RoleManager.class);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                activity.startActivityForResult(
                        roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME), 7001);
                return;
            }
        } catch (RuntimeException ignored) {
            // Some aftermarket API-29 builds expose RoleManager incompletely.
        }
        try {
            activity.startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (RuntimeException e) {
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
        }
    }

    static RootShell.Result setHomeWithRoot(Context context) {
        setHomeAliasEnabled(context, true);
        return RootShell.run(
                "cmd package set-home-activity --user 0 com.cbkii.ts18launcher/.HomeAlias",
                8);
    }
}
