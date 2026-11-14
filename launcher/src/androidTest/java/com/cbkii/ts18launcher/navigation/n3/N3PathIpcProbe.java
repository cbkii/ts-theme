package com.cbkii.ts18launcher.navigation.n3;

import android.app.Instrumentation;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.Looper;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Real API 29 run of the launcher's private-display N3 path: the launcher process owns a private
 * display, launches a separate-UID embeddable fixture onto it once, and must observe real pixels
 * before presenting them. A non-embeddable system Activity must be refused as BLOCKED_NO_AUTHORITY.
 */
public final class N3PathIpcProbe {
    private N3PathIpcProbe() {}

    public static String run(Instrumentation instrumentation, String fixturePackage) throws Exception {
        Handler main = new Handler(Looper.getMainLooper());
        ImageReader presenter = ImageReader.newInstance(640, 360, PixelFormat.RGBA_8888, 2);
        PublicDisplayPath path = new PublicDisplayPath(instrumentation.getTargetContext(), main);
        PublicDisplayPath second = null;
        try {
            CountDownLatch created = new CountDownLatch(1), framed = new CountDownLatch(1), failed = new CountDownLatch(1);
            AtomicReference<String> failure = new AtomicReference<>("");
            // The trampoline hands control back through the relay on the main thread.
            AtomicReference<Integer> display = new AtomicReference<>(0);
            // Only a frame carrying the fixture's known colour proves the fixture rendered; any
            // other content (the trampoline's own frame, a blank composite) is observed but not
            // accepted as the rendering check.
            CountDownLatch fixtureFrame = new CountDownLatch(1);
            presenter.setOnImageAvailableListener(reader -> {
                Image image = reader.acquireLatestImage();
                if (image == null) return;
                try {
                    if (isFixtureFrame(image)) fixtureFrame.countDown();
                } finally {
                    image.close();
                }
            }, main);
            N3DisplayPath.Callback callback = new N3DisplayPath.Callback() {
                @Override public void created(long gen, int id) { display.set(id); created.countDown(); }
                @Override public void launched(long gen) { }
                @Override public void firstFrame(long gen) { framed.countDown(); }
                @Override public void lost(long gen, String reason) { failure.set(reason); failed.countDown(); }
                @Override public void failed(long gen, String reason) { failure.set(reason); failed.countDown(); }
            };
            instrumentation.runOnMainSync(() -> path.create(1L, presenter.getSurface(), 640, 360, 160, callback));
            awaitPhase(created, failed, failure, 5L, "private display not created");
            check(display.get() > 0, "private display identity missing");
            String input = path.input().unavailableReason();

            CountDownLatch launched = new CountDownLatch(1);
            N3DisplayPath.Callback launchCallback = new N3DisplayPath.Callback() {
                @Override public void created(long gen, int id) { }
                @Override public void launched(long gen) { launched.countDown(); }
                @Override public void firstFrame(long gen) { framed.countDown(); }
                @Override public void lost(long gen, String reason) { failure.set(reason); failed.countDown(); }
                @Override public void failed(long gen, String reason) { failure.set(reason); failed.countDown(); }
            };
            instrumentation.runOnMainSync(() -> path.launch(1L,
                    fixturePackage + "/com.cbkii.ts18launcher.navigation.n3.N3FixtureActivity", launchCallback));
            awaitPhase(launched, failed, failure, 10L, "embeddable fixture not launched");
            awaitPhase(framed, failed, failure, 10L, "no pixels from the launched fixture on the N3 display");
            awaitPhase(fixtureFrame, failed, failure, 10L,
                    "frames on the N3 display were not the fixture's known frame");

            // A non-embeddable system Activity is refused for an ordinary caller.
            second = new PublicDisplayPath(instrumentation.getTargetContext(), main);
            CountDownLatch created2 = new CountDownLatch(1);
            AtomicReference<String> refused = new AtomicReference<>("");
            CountDownLatch decided = new CountDownLatch(1);
            N3DisplayPath.Callback callback2 = new N3DisplayPath.Callback() {
                @Override public void created(long gen, int id) { created2.countDown(); }
                @Override public void launched(long gen) { refused.set("LAUNCHED"); decided.countDown(); }
                @Override public void firstFrame(long gen) { }
                @Override public void lost(long gen, String reason) { }
                @Override public void failed(long gen, String reason) { refused.set(reason); decided.countDown(); }
            };
            PublicDisplayPath secondPath = second;
            instrumentation.runOnMainSync(() -> secondPath.create(2L, presenter.getSurface(), 640, 360, 160, callback2));
            check(created2.await(5, TimeUnit.SECONDS), "second private display not created");
            instrumentation.runOnMainSync(() -> secondPath.launch(2L, "com.android.settings/com.android.settings.Settings", callback2));
            check(decided.await(10, TimeUnit.SECONDS), "no decision for the non-embeddable target");
            check(refused.get().startsWith(PublicDisplayPath.BLOCKED), "non-embeddable target not refused: " + refused.get());
            return "PASS API29 N3 private display: owned display, embeddable fixture pixels observed, "
                    + "non-embeddable BLOCKED_NO_AUTHORITY, input " + (input.isEmpty() ? "forwarder granted" : "view-only (" + input + ")");
        } finally {
            instrumentation.runOnMainSync(path::release);
            if (second != null) {
                PublicDisplayPath late = second;
                instrumentation.runOnMainSync(late::release);
            }
            presenter.close();
        }
    }

    /** Stop on the actual failure signal rather than waiting out unrelated phase budgets. */
    static void awaitPhase(CountDownLatch ready, CountDownLatch failed,
            AtomicReference<String> failure, long seconds, String boundary) throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (failed.getCount() != 0L && ready.getCount() != 0L) {
            long remaining = until - System.nanoTime();
            if (remaining <= 0L) break;
            ready.await(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(25L)), TimeUnit.NANOSECONDS);
        }
        check(failed.getCount() != 0L && ready.getCount() == 0L, boundary + ": " + failure.get());
    }

    /** The fixture draws one solid colour (N3FixtureActivity); match several sampled points. */
    private static boolean isFixtureFrame(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();
        int width = image.getWidth(), height = image.getHeight();
        int[][] points = {{width / 2, height / 2}, {width / 4, height / 4}, {3 * width / 4, 3 * height / 4}};
        for (int[] point : points) {
            int offset = point[1] * rowStride + point[0] * pixelStride;
            int r = buffer.get(offset) & 0xFF;
            int g = buffer.get(offset + 1) & 0xFF;
            int b = buffer.get(offset + 2) & 0xFF;
            if (r > 16 || Math.abs(g - 160) > 16 || Math.abs(b - 90) > 16) return false;
        }
        return true;
    }

    private static void check(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }
}
