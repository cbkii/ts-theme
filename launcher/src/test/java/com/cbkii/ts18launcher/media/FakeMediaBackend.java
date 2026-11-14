package com.cbkii.ts18launcher.media;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Scriptable engine double that records every externally visible action in order. */
final class FakeMediaBackend implements MediaBackend {
    final MediaEngine engine;
    final List<String> log;
    final Map<MediaSourceKey, String> attached = new EnumMap<>(MediaSourceKey.class);
    final Map<MediaSourceKey, Boolean> playing = new EnumMap<>(MediaSourceKey.class);
    final List<ResultCallback> pendingCallbacks = new ArrayList<>();
    StateListener listener;

    FakeMediaBackend(MediaEngine engine, List<String> log) { this.engine = engine; this.log = log; }

    @Override public MediaEngine engine() { return engine; }
    @Override public void attach(MediaSourceKey key, String packageName) {
        attached.put(key, packageName); log.add(engine + ".attach " + key + " " + packageName);
    }
    @Override public void detach(MediaSourceKey key) {
        attached.remove(key); log.add(engine + ".detach " + key);
    }
    @Override public boolean attached(MediaSourceKey key) { return attached.containsKey(key); }
    @Override public MediaSourceState state(MediaSourceKey key) {
        return MediaSourceState.unavailable(key, engine, attached.get(key),
                MediaSourceState.Availability.NO_SESSION, 0L);
    }
    @Override public boolean playing(MediaSourceKey key, String packageName) {
        return attached.containsKey(key) && Boolean.TRUE.equals(playing.get(key));
    }
    @Override public void command(MediaSourceKey key, String packageName, MediaUserAction action,
                                  ResultCallback callback) {
        log.add(engine + ".command " + key + " " + packageName + " " + action);
        pendingCallbacks.add(callback);
    }
    @Override public void pauseForHandover(MediaSourceKey key) {
        log.add(engine + ".pauseForHandover " + key);
        playing.put(key, false);
    }
    @Override public void setStateListener(StateListener listener) { this.listener = listener; }

    void complete(MediaCommandResult result) { pendingCallbacks.remove(0).onResult(result); }

    void publish(MediaSourceKey key) {
        listener.onMediaSourceStateChanged(MediaSourceState.unavailable(key, engine, "pkg",
                MediaSourceState.Availability.NO_SESSION, 1L));
    }
}
