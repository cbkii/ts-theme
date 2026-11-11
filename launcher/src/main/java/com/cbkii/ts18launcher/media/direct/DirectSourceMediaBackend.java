package com.cbkii.ts18launcher.media.direct;

import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.cbkii.ts18launcher.media.MediaBackend;
import com.cbkii.ts18launcher.media.MediaEngine;
import com.cbkii.ts18launcher.media.MediaSourceKey;
import com.cbkii.ts18launcher.media.MediaSourceState;
import com.cbkii.ts18launcher.media.MediaUserAction;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * M2 Direct Source Media: exact-package, exact-token control of sessions that already exist.
 *
 * Independent of the legacy engine: it shares only the launcher's notification-listener
 * component as its authority to see sessions. It holds one session registry for the process and
 * one small state machine per source. While no source is attached it holds no subscription,
 * callback or timer.
 *
 * A running source is controlled through its live session. A source that is not running is
 * started only on a Play tap and only through its own exported media browser service; a package
 * without one is reported as not running. No Activity is opened and no root is used.
 */
public final class DirectSourceMediaBackend implements MediaBackend {
    /** Launcher-side services the engine needs but does not own. */
    public interface Host {
        String appLabel(String packageName);
        void trace(String event, String detail);
    }

    private final Host host;
    private final DirectSessionRegistry registry;
    private final Map<MediaSourceKey, DirectSourceEngine> engines = new EnumMap<>(MediaSourceKey.class);
    private StateListener listener;

    /** Builds the optional cold-start path once the backend can receive its events. */
    interface StarterFactory { DirectSourceEngine.Starter create(BrowserSessionStarter.Events events); }

    public DirectSourceMediaBackend(Context context, ComponentName listenerComponent, Host host) {
        this(new FrameworkSessions(context, listenerComponent, new Handler(Looper.getMainLooper())),
                new HandlerClock(new Handler(Looper.getMainLooper())), host,
                events -> new BrowserSessionStarter(context, new Handler(Looper.getMainLooper()), events));
    }

    DirectSourceMediaBackend(DirectSessionRegistry.Source source, DirectSourceEngine.Clock clock,
                             Host host) {
        this(source, clock, host, null);
    }

    DirectSourceMediaBackend(DirectSessionRegistry.Source source, DirectSourceEngine.Clock clock,
                             Host host, StarterFactory starterFactory) {
        this.host = host;
        DirectSourceEngine.Starter starter = starterFactory == null ? null
                : starterFactory.create(new BrowserSessionStarter.Events() {
                    @Override public void onSessionOffered(String packageName,
                                                           DirectSessionRegistry.Session session) {
                        host.trace("start-offered", packageName);
                        registry().offer(session);
                    }
                    @Override public void onSessionWithdrawn(String packageName, Object token) {
                        host.trace("start-withdrawn", packageName);
                        registry().withdraw(token);
                    }
                    @Override public void onStartFailed(String packageName, String reason) {
                        host.trace("start-failed", packageName + " " + reason);
                        for (DirectSourceEngine engine : engines.values()) engine.onStartFailed(packageName, reason);
                    }
                });
        this.registry = new DirectSessionRegistry(source, new DirectSessionRegistry.Sink() {
            @Override public void onMembership(boolean accessGranted, List<SessionObservation> sessions) {
                for (DirectSourceEngine engine : engines.values()) engine.onSessions(accessGranted, sessions);
            }
            @Override public void onMembershipUnknown() {
                host.trace("membership-unknown", "");
                for (DirectSourceEngine engine : engines.values()) engine.onMembershipUnknown();
            }
            @Override public void onSessionChanged(SessionObservation observation) {
                for (DirectSourceEngine engine : engines.values()) engine.onSessionChanged(observation);
            }
        });
        for (MediaSourceKey key : MediaSourceKey.values()) {
            String sourceLabel = key == MediaSourceKey.RADIO ? "Radio" : "Music";
            DirectSourceEngine engine = new DirectSourceEngine(key, (token, operation) -> {
                boolean sent = registry.dispatch(token, operation);
                host.trace(sent ? "dispatch" : "dispatch-refused", key + " " + operation);
                return sent;
            }, clock, labelsFor(sourceLabel), starter);
            engine.setListener(state -> {
                StateListener current = listener;
                if (current != null) current.onMediaSourceStateChanged(state);
            });
            engines.put(key, engine);
        }
    }

    private DirectSessionRegistry registry() { return registry; }

    private DirectSourceEngine.Labels labelsFor(String sourceLabel) {
        return new DirectSourceEngine.Labels() {
                @Override public String sourceLabel() { return sourceLabel; }
                @Override public String appLabel(String packageName) {
                    String label = host.appLabel(packageName);
                    return label == null || label.isEmpty() ? sourceLabel : label;
                }
        };
    }

    @Override public MediaEngine engine() { return MediaEngine.DIRECT; }

    @Override public void attach(MediaSourceKey key, String packageName) {
        engines.get(key).attach(packageName);
        host.trace("attached", key + " " + (packageName == null ? "" : packageName));
        if (registry.started()) registry.refresh(); else registry.start();
    }

    @Override public void detach(MediaSourceKey key) {
        engines.get(key).detach();
        host.trace("detached", key.name());
        for (DirectSourceEngine engine : engines.values()) if (engine.attached()) return;
        // Nothing is served by this engine any more: drop every subscription and callback.
        registry.stop();
    }

    @Override public boolean attached(MediaSourceKey key) { return engines.get(key).attached(); }

    @Override public MediaSourceState state(MediaSourceKey key) { return engines.get(key).state(); }

    @Override public boolean playing(MediaSourceKey key, String packageName) {
        return engines.get(key).playing(packageName);
    }

    @Override public void command(MediaSourceKey key, String packageName, MediaUserAction action,
                                  ResultCallback callback) {
        DirectSourceEngine engine = engines.get(key);
        engine.command(packageName, action, result -> {
            host.trace("result", key + " " + action + " " + result);
            if (callback != null) callback.onResult(result);
        });
    }

    @Override public void pauseForHandover(MediaSourceKey key) { engines.get(key).pauseForHandover(); }

    @Override public void preparePassive(MediaSourceKey key, String packageName, long epoch) {
        DirectSourceEngine engine = engines.get(key);
        if (engine.attached() && engine.packageName().equals(packageName)) engine.preparePassive(epoch);
    }

    @Override public void setStateListener(StateListener listener) { this.listener = listener; }

    /** Re-read membership now, for example when HOME returns. No effect while nothing is attached. */
    public void refresh() { registry.refresh(); }

    private static final class HandlerClock implements DirectSourceEngine.Clock {
        private final Handler handler;
        HandlerClock(Handler handler) { this.handler = handler; }
        @Override public Object schedule(long delayMs, Runnable task) {
            handler.postDelayed(task, delayMs);
            return task;
        }
        @Override public void cancel(Object handle) {
            if (handle instanceof Runnable) handler.removeCallbacks((Runnable) handle);
        }
    }
}
