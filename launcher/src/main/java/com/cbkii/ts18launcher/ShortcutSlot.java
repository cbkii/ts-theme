package com.cbkii.ts18launcher;

import android.content.Context;
import android.content.pm.PackageManager;
import android.widget.ImageButton;

/** Existing non-empty assignments migrate to App shortcuts without changing their package. */
final class ShortcutSlot {
    private ShortcutSlot() {}
    static boolean isApp(String assignment) { return assignment != null && !assignment.isEmpty(); }

    static String role(Context context, String packageKey) {
        for (int i = 0; i < LauncherPrefs.QUICK_KEYS.length; i++)
            if (LauncherPrefs.QUICK_KEYS[i].equals(packageKey)) return LauncherPrefs.quickRole(context, i);
        for (int i = 0; i < LauncherPrefs.DRAWER_QUICK_KEYS.length; i++)
            if (LauncherPrefs.DRAWER_QUICK_KEYS[i].equals(packageKey)) return LauncherPrefs.drawerQuickRole(context, i);
        if (LauncherPrefs.KEY_NAV.equals(packageKey)) return RoleIconCatalog.NAVIGATION;
        if (LauncherPrefs.KEY_RADIO.equals(packageKey)) return RoleIconCatalog.RADIO;
        if (LauncherPrefs.KEY_MUSIC.equals(packageKey)) return RoleIconCatalog.MUSIC;
        if (LauncherPrefs.KEY_BLUETOOTH.equals(packageKey)) return RoleIconCatalog.BLUETOOTH;
        return RoleIconCatalog.GENERIC;
    }

    static boolean isSlotKey(String key) {
        for (String candidate : LauncherPrefs.QUICK_KEYS) if (candidate.equals(key)) return true;
        for (String candidate : LauncherPrefs.DRAWER_QUICK_KEYS) if (candidate.equals(key)) return true;
        return false;
    }

    static String label(Context context, String key) {
        String pkg = LauncherPrefs.packageFor(context, key);
        return isApp(pkg) ? AppResolver.labelFor(context, pkg, pkg) : RoleIconCatalog.label(role(context, key));
    }

    static String iconAppearance(Context context, String key) {
        for (int i = 0; i < LauncherPrefs.QUICK_KEYS.length; i++)
            if (LauncherPrefs.QUICK_KEYS[i].equals(key)) return UiPersonalizationPrefs.quickIcon(context, i);
        for (int i = 0; i < LauncherPrefs.DRAWER_QUICK_KEYS.length; i++)
            if (LauncherPrefs.DRAWER_QUICK_KEYS[i].equals(key)) return UiPersonalizationPrefs.drawerQuickIcon(context, i);
        return SlotIconCatalog.AUTO;
    }

    static void bind(Context context, ImageButton button, String key) {
        String override = iconAppearance(context, key);
        if (!SlotIconCatalog.AUTO.equals(override)) {
            button.setImageResource(SlotIconCatalog.icon(override));
            button.setImageTintList(android.content.res.ColorStateList.valueOf(AutomotiveUi.color(context, R.color.ui_icon)));
            button.setContentDescription(label(context, key) + " · " + SlotIconCatalog.label(override) + " icon");
            return;
        }

        String pkg = LauncherPrefs.packageFor(context, key);
        if (isApp(pkg)) {
            button.setImageTintList(null);
            try { button.setImageDrawable(context.getPackageManager().getApplicationIcon(pkg)); }
            catch (PackageManager.NameNotFoundException ignored) { button.setImageResource(R.drawable.ic_shortcut); }
            button.setContentDescription("App: " + label(context, key));
        } else {
            button.setImageResource(RoleIconCatalog.icon(role(context, key)));
            button.setImageTintList(android.content.res.ColorStateList.valueOf(AutomotiveUi.color(context, R.color.ui_icon)));
            button.setContentDescription(RoleIconCatalog.label(role(context, key)));
        }
    }

    static boolean launch(Context context, String key) {
        String pkg = LauncherPrefs.packageFor(context, key);
        if (isApp(pkg)) return AppResolver.launchPackage(context, pkg);
        return RoleIconCatalog.launch(context, role(context, key));
    }

    static void chooseRole(Context context, String roleKey, String packageKey, String role) {
        if (RoleIconCatalog.isKnown(role)) LauncherPrefs.prefs(context).edit()
                .putString(roleKey, role).remove(packageKey).apply();
    }
}
