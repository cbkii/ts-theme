package com.cbkii.ts18launcher;

import static org.junit.Assert.*;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, manifest = Config.NONE, application = android.app.Application.class)
public class AppCatalogTest {
    @Test public void retainsMultipleLauncherActivitiesAndAliasesWithDeterministicOrder() {
        Context app = RuntimeEnvironment.getApplication();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (String name : new String[] {"com.example.player.Alias", "com.example.player.Main"}) {
            ResolveInfo info = new ResolveInfo();
            info.activityInfo = new ActivityInfo();
            info.activityInfo.name = name; info.activityInfo.packageName = "com.example.player";
            info.activityInfo.exported = true; info.activityInfo.enabled = true;
            info.activityInfo.applicationInfo = new ApplicationInfo();
            info.activityInfo.applicationInfo.enabled = true;
            info.activityInfo.applicationInfo.packageName = "com.example.player";
            info.nonLocalizedLabel = "Player";
            ((org.robolectric.shadows.ShadowPackageManager) org.robolectric.shadow.api.Shadow.extract(app.getPackageManager()))
                    .addResolveInfoForIntent(query, info);
        }
        java.util.List<AppCatalog.Entry> entries = AppCatalog.discover(app);
        assertEquals(2, entries.size());
        assertTrue(entries.get(0).target().endsWith(".Alias"));
        assertTrue(entries.get(1).target().endsWith(".Main"));
    }

    @Test public void privateDisabledAndPermissionProtectedComponentsAreExcluded() {
        Context app = RuntimeEnvironment.getApplication();
        ActivityInfo info = new ActivityInfo(); info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.enabled = true; info.enabled = true;
        assertFalse(AppCatalog.launchable(app, info));
        info.exported = true;
        assertTrue(AppCatalog.launchable(app, info));
        info.permission = "com.example.signature.only";
        assertFalse(AppCatalog.launchable(app, info));
        info.permission = null; info.enabled = false;
        assertFalse(AppCatalog.launchable(app, info));
    }

    @Test public void componentShortcutConfigurationRoundTripsAlongsideLegacyPackages() throws Exception {
        java.util.Map<String, Object> settings = new java.util.LinkedHashMap<>();
        settings.put(LauncherPrefs.KEY_QUICK_1, "com.example.player/.Alias");
        settings.put(LauncherPrefs.KEY_MUSIC, "com.example.player");
        ConfigurationCodec.Preview preview = ConfigurationCodec.decode(ConfigurationCodec.encode(settings), pkg -> true);
        assertEquals(settings, preview.values);
        assertEquals("com.example.player", AppResolver.packagePart("com.example.player/.Alias"));
        assertFalse(AppResolver.validTarget("com.example.player/../../bad"));
    }
    @Test public void commitAvailabilityUsesThePackageOfAComponentShortcut() {
        Context app = RuntimeEnvironment.getApplication();
        String pkg = "com.example.player";
        ResolveInfo info = new ResolveInfo(); info.activityInfo = new ActivityInfo();
        info.activityInfo.name = pkg + ".Alias"; info.activityInfo.packageName = pkg;
        info.activityInfo.exported = true; info.activityInfo.enabled = true;
        info.activityInfo.applicationInfo = new ApplicationInfo();
        info.activityInfo.applicationInfo.packageName = pkg; info.activityInfo.applicationInfo.enabled = true;
        org.robolectric.shadows.ShadowPackageManager pm = org.robolectric.shadow.api.Shadow.extract(app.getPackageManager());
        pm.addResolveInfoForIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg), info);
        assertTrue(AppResolver.configurationAvailable(app, pkg + "/.Alias"));
        assertFalse(AppResolver.configurationAvailable(app, app.getPackageName() + "/.Alias"));
    }

    @Test public void packageScopedRefreshExcludesOtherPackages() {
        Context app = RuntimeEnvironment.getApplication();
        String pkg = "com.example.player";
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg);
        org.robolectric.shadows.ShadowPackageManager pm = org.robolectric.shadow.api.Shadow.extract(app.getPackageManager());
        for (String name : new String[] {pkg, "com.example.other"}) {
            ResolveInfo info = new ResolveInfo(); info.activityInfo = new ActivityInfo();
            info.activityInfo.name = name + ".Main"; info.activityInfo.packageName = name;
            info.activityInfo.exported = true; info.activityInfo.enabled = true;
            info.activityInfo.applicationInfo = new ApplicationInfo();
            info.activityInfo.applicationInfo.packageName = name; info.activityInfo.applicationInfo.enabled = true;
            pm.addResolveInfoForIntent(query, info);
        }
        java.util.List<AppCatalog.Entry> entries = AppCatalog.discover(app, pkg);
        assertEquals(1, entries.size()); assertEquals(pkg, entries.get(0).packageName);
    }

}
