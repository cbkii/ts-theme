package com.cbkii.ts18launcher;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.widget.FrameLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Real API29 WindowManager/draw barrier and cross-UID splash coverage, with frame evidence. */
final class ReadinessOverlayIpcProbe {
    static String run(Instrumentation test) throws Exception {
        Activity home = test.startActivitySync(new Intent(test.getTargetContext(), LauncherActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        StartupReadinessLoader[] loader = new StartupReadinessLoader[1];
        CountDownLatch drawn = new CountDownLatch(1);
        int[] area = new int[4];
        File output = new File(test.getTargetContext().getExternalFilesDir(null), "readiness-overlay");
        check(output.isDirectory() || output.mkdirs(), "frame directory unavailable");
        try {
            test.runOnMainSync(() -> {
                FrameLayout root = home.findViewById(android.R.id.content);
                int[] origin = new int[2]; root.getLocationOnScreen(origin);
                area[0] = origin[0]; area[1] = origin[1]; area[2] = root.getWidth(); area[3] = root.getHeight();
                loader[0] = new StartupReadinessLoader(home, root, () -> { });
                loader[0].geometry(0, 0, area[2], area[3]);
                loader[0].show("Preparing fixture", true);
                loader[0].showMask();
                check(loader[0].ownsMask(), "overlay was not attached");
                check(drawn.getCount() == 1, "draw barrier ran before registration");
                loader[0].whenMaskDrawn(drawn::countDown);
            });
            check(drawn.await(5, TimeUnit.SECONDS), "opaque frame was not drawn");
            sample(test, output, "before-launch.png", area);
            test.getTargetContext().startActivity(new Intent().setClassName(
                    "com.cbkii.ts18launcher.test", MaskFixtureActivity.class.getName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            for (int frame = 0; frame < 12; frame++) {
                SystemClock.sleep(50);
                sample(test, output, frame == 0 ? "launch.png" : frame == 11 ? "background.png" : null, area);
            }
            test.getTargetContext().startActivity(new Intent(test.getTargetContext(), LauncherActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            test.waitForIdleSync();
            sample(test, output, "home-restored.png", area);
            test.runOnMainSync(() -> loader[0].hide());
            check(StartupMaskFocus.activeTitle.isEmpty(), "mask capability leaked");
            return "PASS API29 opaque draw before cross-UID splash, 12 covered frames, HOME and cleanup";
        } finally {
            test.runOnMainSync(() -> { if (loader[0] != null) loader[0].destroy(); });
        }
    }

    private static void sample(Instrumentation test, File output, String name, int[] area) throws Exception {
        Bitmap frame = test.getUiAutomation().takeScreenshot();
        check(frame != null && area[2] > 0 && area[3] > 0, "frame unavailable");
        try {
            // Avoid the spinner and cancel affordance; the rest of the mask must be opaque black.
            for (int x : new int[] {20, 35, 65, 80}) for (int y : new int[] {20, 35, 65}) {
                int pixel = frame.getPixel(area[0] + area[2] * x / 100, area[1] + area[3] * y / 100);
                int mask = AutomotiveUi.color(test.getTargetContext(), R.color.ui_black) & 0x00ffffff;
                check((pixel & 0x00ffffff) == mask, "foreign splash visible under mask: " + Integer.toHexString(pixel));
            }
            if (name != null) try (FileOutputStream stream = new FileOutputStream(new File(output, name))) {
                check(frame.compress(Bitmap.CompressFormat.PNG, 100, stream), "frame export failed");
            }
        } finally { frame.recycle(); }
    }
    private static void check(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }
}
