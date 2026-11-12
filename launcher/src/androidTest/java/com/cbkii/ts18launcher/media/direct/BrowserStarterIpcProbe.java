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
        CountDownLatch loaded = new CountDownLatch(1);
        CountDownLatch playing = new CountDownLatch(1);
        AtomicReference<Boolean> playedEarly = new AtomicReference<>(false);
        instrumentation.runOnMainSync(() -> session.watch(() -> {
            SessionObservation now = session.read();
            if (now.nativeState == PlaybackState.STATE_PLAYING) {
                if (loaded.getCount() > 0) playedEarly.set(true);
                playing.countDown();
            }
            if (DirectSourceEngine.loaded(now)) loaded.countDown();
        }, () -> { }));
        SessionObservation cold = session.read();
        check(cold.nativeState == PlaybackState.STATE_PAUSED, "fixture did not start paused");
        check(!DirectSourceEngine.loaded(cold), "fixture should start with nothing loaded");
        check((cold.actions & PlaybackState.ACTION_PREPARE) != 0L, "PREPARE not advertised over IPC");

        check(cold.deadlinePlay, "bounded HOME Play not advertised over IPC");
        CountDownLatch rejected = new CountDownLatch(1);
        AtomicReference<Integer> afterExpired = new AtomicReference<>(-1);
        instrumentation.runOnMainSync(() -> {
            android.media.session.MediaController controller = new android.media.session.MediaController(app,
                    (android.media.session.MediaSession.Token) session.token());
            android.os.Bundle expired = new android.os.Bundle();
            expired.putInt("protocol", 1);
            expired.putLong("deadline_elapsed_ms", android.os.SystemClock.elapsedRealtime() - 1L);
            controller.getTransportControls().sendCustomAction(SessionObservation.HOME_PLAY_V1, expired);
            controller.sendCommand("inspect-after-expired", null, new android.os.ResultReceiver(main) {
                @Override protected void onReceiveResult(int code, android.os.Bundle data) {
                    afterExpired.set(data.getInt("state", -1)); rejected.countDown();
                }
            });
        });
        check(rejected.await(5, TimeUnit.SECONDS) && afterExpired.get() != PlaybackState.STATE_PLAYING,
                "expired HOME Play started playback");

        // Passive readiness over IPC: PREPARE loads the item and never starts playback.
        AtomicReference<Boolean> prepared = new AtomicReference<>(false);
        instrumentation.runOnMainSync(() -> prepared.set(session.dispatch(DirectOperation.PREPARE)));
        check(prepared.get(), "PREPARE not dispatched");
        check(loaded.await(5, TimeUnit.SECONDS), "PREPARE did not load an item over IPC");
        check(!playedEarly.get() && session.read().nativeState != PlaybackState.STATE_PLAYING,
                "PREPARE must not start playback");
        AtomicReference<Boolean> sent = new AtomicReference<>(false);
        instrumentation.runOnMainSync(() -> sent.set(session.dispatchDeadline(android.os.SystemClock.elapsedRealtime() + 5000L)));
        check(sent.get(), "Play not dispatched");
        check(playing.await(5, TimeUnit.SECONDS), "Play not confirmed by the same session over IPC");
        instrumentation.runOnMainSync(() -> { session.unwatch(); first.connection.disconnect(); });

        // A fresh attempt after teardown is a new, working connection (no stale cached link).
        AttemptResult second = connect(instrumentation, connector, service);
        check(second.session.token() != null, "second connection produced no session");

        // Source process death: the bound connection and the session both report the loss.
        CountDownLatch ended = new CountDownLatch(1);
        instrumentation.runOnMainSync(() -> second.session.watch(() -> { }, ended::countDown));
        instrumentation.runOnMainSync(() -> new android.media.session.MediaController(app,
                (android.media.session.MediaSession.Token) second.session.token())
                .getTransportControls().sendCustomAction(FixtureBrowserService.ACTION_DIE, null));
        boolean lost = ended.await(8, TimeUnit.SECONDS) || second.lost.await(8, TimeUnit.SECONDS);
        check(lost, "source death not reported by session or connection");
        instrumentation.runOnMainSync(() -> { second.session.unwatch(); second.connection.disconnect(); });

        // After death, a deliberate fresh attempt restarts the source and works again.
        AttemptResult third = connect(instrumentation, connector, service);
        check(fixturePackage.equals(third.sessionPackage) && third.session.token() != null,
                "fresh attempt after source death failed");
        check(!third.session.token().equals(second.session.token()), "stale token reused after death");
        instrumentation.runOnMainSync(third.connection::disconnect);
        return "PASS API29 M2 browser starter: resolve, cold bind, owned token, PREPARE loads without Play,"
                + " deadline Play confirmed over IPC, expired Play ignored, fresh reconnect, source death reported, retry after death";
    }

    private static final class AttemptResult {
        BrowserSessionStarter.Connection connection;
        String sessionPackage;
        DirectSessionRegistry.Session session;
        final CountDownLatch lost = new CountDownLatch(1);
    }

    public static String runMedia3(Instrumentation instrumentation) throws Exception {
        Handler main = new Handler(Looper.getMainLooper());
        BrowserSessionStarter.FrameworkConnector connector = new BrowserSessionStarter.FrameworkConnector(
                instrumentation.getTargetContext(), main);
        String pkg = "com.cbkii.ts18mediafixture";
        ComponentName service = connector.resolve(pkg);
        check(service != null && service.getClassName().endsWith("LegacySessionService"),
                "Media3-only manifest not discovered");
        AttemptResult connected = connect(instrumentation, connector, service);
        DirectSessionRegistry.Session session = connected.session;
        CountDownLatch ready = new CountDownLatch(1), playing = new CountDownLatch(1);
        Runnable observe = () -> {
            SessionObservation state = session.read();
            if (state.nativeState == PlaybackState.STATE_PAUSED && !state.itemId.isEmpty()) ready.countDown();
            if (state.nativeState == PlaybackState.STATE_PLAYING) playing.countDown();
        };
        try {
            check(pkg.equals(connected.sessionPackage), "Media3 legacy token has wrong owner");
            check(connector.passiveSafe(pkg), "Media3-only service is excluded from passive readiness");
            instrumentation.runOnMainSync(() -> { session.watch(observe, () -> { }); observe.run(); });
            check(ready.await(8, TimeUnit.SECONDS), "real Media3 decoder did not prepare silently");
            instrumentation.runOnMainSync(() -> check(session.dispatch(DirectOperation.PLAY), "legacy Play refused"));
            check(playing.await(5, TimeUnit.SECONDS), "legacy Play did not reach Media3 playback");
        } finally {
            instrumentation.runOnMainSync(() -> {
                try { session.dispatch(DirectOperation.PAUSE); }
                finally {
                    try { session.unwatch(); }
                    finally { connected.connection.disconnect(); }
                }
            });
        }
        return "PASS API29 real Media3-only service legacy browser / source token / decoder PREPARE / one Play";
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
                    @Override public void onSuspended() {
                        if (result.session != null) { result.lost.countDown(); return; }
                        failure.set("suspended"); answered.countDown();
                    }
                    @Override public void onFailed() {
                        if (result.session != null) { result.lost.countDown(); return; }
                        failure.set("failed"); answered.countDown();
                    }
                }));
        try {
            check(answered.await(10, TimeUnit.SECONDS), "browser connection did not answer");
            check(failure.get().isEmpty() && result.session != null, "browser connection " + failure.get());
            return result;
        } catch (Exception | AssertionError error) {
            instrumentation.runOnMainSync(() -> { if (result.connection != null) result.connection.disconnect(); });
            throw error;
        }
    }

    private static void check(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }
}
