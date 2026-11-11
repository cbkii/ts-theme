package com.cbkii.ts18launcher.media.direct;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Drives the real BrowserSessionStarter.FrameworkConnector against FixtureBrowserService in the
 * test package: discovery, cold bind, package-owned token, one Play over the offered session,
 * confirmation through the same session, teardown, and a fresh connection after teardown.
 */
public final class BrowserStarterIpcProbe {
    private BrowserStarterIpcProbe() {}

    public static String run(Instrumentation instrumentation, String fixturePackage) throws Exception {
        Context app = instrumentation.getTargetContext();
        Handler main = new Handler(Looper.getMainLooper());
        BrowserSessionStarter.FrameworkConnector connector = new BrowserSessionStarter.FrameworkConnector(app, main);

        ComponentName service = connector.resolve(fixturePackage);
        check(service != null && service.getClassName().endsWith("FixtureBrowserService"),
                "exported browser service not resolved: " + service);
        check(connector.resolve("com.cbkii.ts18launcher.absent") == null, "absent package resolved");

        // First attempt: connect, receive the source's own session, send one Play, observe it.
        AttemptResult first = connect(instrumentation, connector, service);
        check(fixturePackage.equals(first.sessionPackage), "session owner " + first.sessionPackage);
        DirectSessionRegistry.Session session = first.session;
        CountDownLatch playing = new CountDownLatch(1);
        instrumentation.runOnMainSync(() -> session.watch(() -> {
            if (session.read().nativeState == PlaybackState.STATE_PLAYING) playing.countDown();
        }, () -> { }));
        check(session.read().nativeState == PlaybackState.STATE_PAUSED, "fixture did not start paused");
        AtomicReference<Boolean> sent = new AtomicReference<>(false);
        instrumentation.runOnMainSync(() -> sent.set(session.dispatch(DirectOperation.PLAY)));
        check(sent.get(), "Play not dispatched");
        check(playing.await(5, TimeUnit.SECONDS), "Play not confirmed by the same session over IPC");
        instrumentation.runOnMainSync(() -> { session.unwatch(); first.connection.disconnect(); });

        // A fresh attempt after teardown is a new, working connection (no stale cached link).
        AttemptResult second = connect(instrumentation, connector, service);
        check(second.session.token() != null, "second connection produced no session");
        instrumentation.runOnMainSync(second.connection::disconnect);
        return "PASS API29 M2 browser starter: resolve, cold bind, owned token, Play confirmed over IPC, fresh reconnect";
    }

    private static final class AttemptResult {
        BrowserSessionStarter.Connection connection;
        String sessionPackage;
        DirectSessionRegistry.Session session;
    }

    private static AttemptResult connect(Instrumentation instrumentation,
                                         BrowserSessionStarter.FrameworkConnector connector,
                                         ComponentName service) throws Exception {
        AttemptResult result = new AttemptResult();
        CountDownLatch answered = new CountDownLatch(1);
        AtomicReference<String> failure = new AtomicReference<>("");
        instrumentation.runOnMainSync(() -> result.connection = connector.connect(service,
                new BrowserSessionStarter.ConnectionCallbacks() {
                    @Override public void onConnected(String sessionPackage, DirectSessionRegistry.Session session) {
                        result.sessionPackage = sessionPackage;
                        result.session = session;
                        answered.countDown();
                    }
                    @Override public void onSuspended() { failure.set("suspended"); answered.countDown(); }
                    @Override public void onFailed() { failure.set("failed"); answered.countDown(); }
                }));
        check(answered.await(10, TimeUnit.SECONDS), "browser connection did not answer");
        check(failure.get().isEmpty() && result.session != null, "browser connection " + failure.get());
        return result;
    }

    private static void check(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }
}
