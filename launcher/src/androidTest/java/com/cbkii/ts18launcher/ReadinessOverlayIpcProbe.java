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
        boolean[] fixtureStarted = { false };
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
            android.content.ComponentName fixture = new android.content.ComponentName(
                    "com.cbkii.ts18launcher.test", MaskFixtureActivity.class.getName());
            check(test.getTargetContext().getPackageManager().getApplicationInfo(fixture.getPackageName(), 0).uid
                    != android.os.Process.myUid(), "fixture does not have a separate UID");
            fixtureStarted[0] = true;
            check(startAndVerify(test, fixture) != home.getTaskId(), "fixture reused the HOME task");
            for (int frame = 0; frame < 12; frame++) {
                SystemClock.sleep(50);
                sample(test, output, frame == 0 ? "launch.png" : frame == 11 ? "background.png" : null, area);
            }
            check(startAndVerify(test, new android.content.ComponentName(test.getTargetContext(), LauncherActivity.class))
                    == home.getTaskId(), "HOME task was replaced");
            test.waitForIdleSync();
            sample(test, output, "home-restored.png", area);
            test.runOnMainSync(() -> loader[0].hide());
            check(StartupMaskFocus.activeTitle.isEmpty(), "mask capability leaked");
            return "PASS API29 opaque draw before cross-UID splash, 12 covered frames, HOME and cleanup";
        } finally {
            try {
                if (fixtureStarted[0]) {
                    // Test APK only: remove the fixture even after a failed assertion, then verify HOME.
                    shell(test, "am broadcast -a " + MaskFixtureActivity.FINISH + " -p com.cbkii.ts18launcher.test");
                    startAndVerify(test, new android.content.ComponentName(test.getTargetContext(), LauncherActivity.class));
                }
            } finally { test.runOnMainSync(() -> { if (loader[0] != null) loader[0].destroy(); }); }
        }
    }

    private static int startAndVerify(Instrumentation test, android.content.ComponentName target) throws Exception {
        String result = shell(test, "am start -W -f 0x10020000 -n " + target.flattenToString());
        check(result.contains("Status: ok") && (result.contains("Activity: " + target.flattenToString())
                || result.contains("Activity: " + target.flattenToShortString())), "Activity not confirmed: " + result);
        String taskState = shell(test, "dumpsys activity activities");
        java.util.regex.Matcher resumed = java.util.regex.Pattern.compile(
                "(?m)^.*mResumedActivity[:=]\\s*ActivityRecord\\{[^\\r\\n]* (?:"
                + java.util.regex.Pattern.quote(target.flattenToString()) + "|"
                + java.util.regex.Pattern.quote(target.flattenToShortString()) + ") t([0-9]+)\\}")
                .matcher(taskState);
        check(resumed.find(), "Expected Activity is not resumed: " + target.flattenToString());
        return Integer.parseInt(resumed.group(1));
    }

    private static String shell(Instrumentation test, String command) throws Exception {
        try (java.io.InputStream input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(
                test.getUiAutomation().executeShellCommand("/system/bin/toybox timeout 10 " + command));
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] bytes = new byte[1024]; int n;
            while ((n = input.read(bytes)) > 0) {
                check(output.size() + n <= 524288, "fixture command output exceeded bound");
                output.write(bytes, 0, n);
            }
            return output.toString("UTF-8");
        }
    }

    private static void sample(Instrumentation test, File output, String name, int[] area) throws Exception {
        Bitmap frame = test.getUiAutomation().takeScreenshot();
        check(frame != null && area[2] > 0 && area[3] > 0, "frame unavailable");
        try {
            // Avoid the spinner and cancel affordance; the rest of the mask must be opaque black.
            for (int x : new int[] {20, 35, 65, 80}) for (int y : new int[] {20, 35, 65}) {
                int pixel = frame.getPixel(area[0] + area[2] * x / 100, area[1] + area[3] * y / 100);
                check((pixel & 0x00ffffff) == 0, "foreign splash visible under mask: " + Integer.toHexString(pixel));
            }
            // The bright fixture must not tint the app-drawn strip behind SystemUI's status icons.
            if (area[1] > 2) check((frame.getPixel(frame.getWidth() / 2, area[1] / 2) & 0x00ffffff) == 0,
                    "foreign status-bar background escaped the underlay");
            if (name != null) try (FileOutputStream stream = new FileOutputStream(new File(output, name))) {
                check(frame.compress(Bitmap.CompressFormat.PNG, 100, stream), "frame export failed");
            }
        } finally { frame.recycle(); }
    }
    private static void check(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }
}
