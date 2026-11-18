package com.cbkii.ts18launcher.navigation.n3;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;

/**
 * Read-only N3 feasibility probe for the API 29 public path.
 *
 * It creates one private, launcher-owned virtual display backed by an off-screen ImageReader,
 * asks the platform the same admission question the real path asks — may this launcher's
 * trampoline Activity start on the display to reach the selected navigator — and releases the
 * display. It launches nothing, injects no input, uses no root and needs no permission. The
 * answer is the discriminating fact for N3 on this unit; it is not a claim of pixels or touch.
 */
public final class N3DisplayProbe {
    private N3DisplayProbe() {}

    // PixelFormat.RGBA_8888 is the documented format for a virtual-display ImageReader; the
    // ImageFormat-only lint annotation predates that use.
    @android.annotation.SuppressLint("WrongConstant")
    public static N3Feasibility.Observation run(Context context, String navigationPackage) {
        Context app = context.getApplicationContext();
        PackageManager pm = app.getPackageManager();
        boolean secondary;
        try {
            secondary = pm.hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS);
        } catch (RuntimeException unavailable) {
            return new N3Feasibility.Observation(true, false, false, false, null, "package manager");
        }
        if (!secondary) return new N3Feasibility.Observation(true, false, false, false, null, "");
        Intent launch = navigationPackage == null || navigationPackage.isEmpty()
                ? null : pm.getLaunchIntentForPackage(navigationPackage);
        ComponentName target = launch == null ? null : launch.getComponent();
        if (target == null) return new N3Feasibility.Observation(true, true, false, false, null, "");
        DisplayManager displays = (DisplayManager) app.getSystemService(Context.DISPLAY_SERVICE);
        if (displays == null) return new N3Feasibility.Observation(true, true, true, false, null, "display service");
        ImageReader reader = null;
        VirtualDisplay display = null;
        try {
            reader = ImageReader.newInstance(320, 180, PixelFormat.RGBA_8888, 2);
            // Flags 0: a private display only this app owns. Public displays need privileges.
            display = displays.createVirtualDisplay("ts18-n3-probe", 320, 180, 160, reader.getSurface(), 0);
            if (display == null) return new N3Feasibility.Observation(true, true, true, false, null, "");
            // The real path admits through this launcher's trampoline: the launcher may always
            // start its own Activity on its own display, and the trampoline, once present there,
            // asks about the target. Ask the same first question; nothing is launched.
            Intent intent = new Intent(app, N3TrampolineActivity.class)
                    .putExtra(N3TrampolineActivity.EXTRA_TARGET, target.flattenToString())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            ActivityManager activities = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
            if (activities == null) return new N3Feasibility.Observation(true, true, true, true, null, "activity service");
            boolean allowed = activities.isActivityStartAllowedOnDisplay(
                    app, display.getDisplay().getDisplayId(), intent);
            return new N3Feasibility.Observation(true, true, true, true, allowed, "");
        } catch (RuntimeException failed) {
            return new N3Feasibility.Observation(true, true, true, display != null, null,
                    failed.getClass().getSimpleName());
        } finally {
            if (display != null) display.release();
            if (reader != null) reader.close();
        }
    }
}
