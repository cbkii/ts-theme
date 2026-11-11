package com.cbkii.ts18launcher.navigation.n3;

import android.app.Instrumentation;
import android.graphics.PixelFormat;
import android.media.ImageReader;
import android.os.Handler;
import android.os.Looper;

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
        CountDownLatch created = new CountDownLatch(1), framed = new CountDownLatch(1), failed = new CountDownLatch(1);
        AtomicReference<String> failure = new AtomicReference<>("");
        AtomicReference<Integer> display = new AtomicReference<>(0);
        N3DisplayPath.Callback callback = new N3DisplayPath.Callback() {
            @Override public void created(long gen, int id) { display.set(id); created.countDown(); }
            @Override public void launched(long gen) { }
            @Override public void firstFrame(long gen) { framed.countDown(); }
            @Override public void lost(long gen, String reason) { failure.set(reason); failed.countDown(); }
            @Override public void failed(long gen, String reason) { failure.set(reason); failed.countDown(); }
        };
        instrumentation.runOnMainSync(() -> path.create(1L, presenter.getSurface(), 640, 360, 160, callback));
        check(created.await(5, TimeUnit.SECONDS) && display.get() > 0, "private display not created: " + failure.get());
        String input = path.input().unavailableReason();

        instrumentation.runOnMainSync(() -> path.launch(1L,
                fixturePackage + "/com.cbkii.ts18launcher.navigation.n3.N3FixtureActivity", callback));
        check(failed.getCount() == 1, "embeddable fixture refused: " + failure.get());
        check(framed.await(10, TimeUnit.SECONDS), "no pixels from the launched fixture on the N3 display");
        instrumentation.runOnMainSync(path::release);

        // A non-embeddable system Activity is refused for an ordinary caller.
        PublicDisplayPath second = new PublicDisplayPath(instrumentation.getTargetContext(), main);
        CountDownLatch created2 = new CountDownLatch(1);
        AtomicReference<String> refused = new AtomicReference<>("");
        N3DisplayPath.Callback callback2 = new N3DisplayPath.Callback() {
            @Override public void created(long gen, int id) { created2.countDown(); }
            @Override public void launched(long gen) { refused.set("LAUNCHED"); }
            @Override public void firstFrame(long gen) { }
            @Override public void lost(long gen, String reason) { }
            @Override public void failed(long gen, String reason) { refused.set(reason); }
        };
        instrumentation.runOnMainSync(() -> second.create(2L, presenter.getSurface(), 640, 360, 160, callback2));
        check(created2.await(5, TimeUnit.SECONDS), "second private display not created");
        instrumentation.runOnMainSync(() -> second.launch(2L, "com.android.settings/com.android.settings.Settings", callback2));
        check(refused.get().startsWith(PublicDisplayPath.BLOCKED), "non-embeddable target not refused: " + refused.get());
        instrumentation.runOnMainSync(second::release);
        presenter.close();
        return "PASS API29 N3 private display: owned display, embeddable fixture pixels observed, "
                + "non-embeddable BLOCKED_NO_AUTHORITY, input " + (input.isEmpty() ? "forwarder granted" : "view-only (" + input + ")");
    }

    private static void check(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }
}
