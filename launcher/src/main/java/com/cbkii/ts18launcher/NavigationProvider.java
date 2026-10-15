package com.cbkii.ts18launcher;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;

import java.util.Locale;

/** Lightweight hand-off from the dashboard map to the configured navigation authority. */
final class NavigationProvider {
    static final String GOOGLE_MAPS = "com.google.android.apps.maps";
    static final String WAZE = "com.waze";
    static final String ORGANIC_MAPS = "app.organicmaps";
    static final String ORGANIC_MAPS_INCAR = "app.organicmaps.incar";
    static final String OSMAND = "net.osmand";
    static final String OSMAND_PLUS = "net.osmand.plus";

    private NavigationProvider() {}

    static boolean open(Context context, String packageName, Location location) {
        if (packageName == null || packageName.isEmpty()) return false;

        Intent mapIntent = location == null ? null : locationIntent(packageName, location);
        if (mapIntent != null && context.getPackageManager().resolveActivity(mapIntent, 0) != null) {
            try {
                context.startActivity(mapIntent);
                return true;
            } catch (RuntimeException ignored) {
                // Fall through to the app's normal launcher Activity.
            }
        }
        return AppResolver.launchPackage(context, packageName);
    }

    static boolean hasLauncherActivity(Context context, String packageName) {
        return packageName != null && !packageName.isEmpty()
                && context.getPackageManager().getLaunchIntentForPackage(packageName) != null;
    }

    private static Intent locationIntent(String packageName, Location location) {
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        final Uri uri;
        if (WAZE.equals(packageName)) {
            uri = Uri.parse(String.format(Locale.US, "waze://?ll=%.7f,%.7f", lat, lon));
        } else {
            uri = Uri.parse(String.format(Locale.US, "geo:%.7f,%.7f?z=15", lat, lon));
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage(packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }
}
