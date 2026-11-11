package com.cbkii.ts18launcher.media.direct;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The direct engine's one view of live media sessions. Framework-free and single-threaded.
 *
 * One registry serves both sources, so there is exactly one membership subscription and exactly
 * one callback per session token however many sources are attached. It never selects a session
 * and never sends a command on its own: it reports what exists and forwards one operation to the
 * token it is given.
 */
final class DirectSessionRegistry {
    /** One live session as the platform exposes it. */
    interface Session {
        /** Stable identity; two handles for the same platform session have equal tokens. */
        Object token();
        SessionObservation read();
        /** Start delivering change and end events. Called once per watched session. */
        void watch(Runnable onChanged, Runnable onEnded);
        void unwatch();
        boolean dispatch(DirectOperation operation);
    }

    /** Where sessions come from: the notification-listener authority on device. */
    interface Source {
        boolean accessGranted();
        /** Current live sessions. Throws SecurityException when access is not effective. */
        List<Session> activeSessions();
        /** Subscribe to membership and access changes; null unsubscribes. */
        void setChangeListener(Runnable onChanged);
    }

    interface Sink {
        void onMembership(boolean accessGranted, List<SessionObservation> sessions);
        /** The platform could not be read; watched sessions keep reporting through callbacks. */
        void onMembershipUnknown();
        void onSessionChanged(SessionObservation observation);
    }

    private final Source source;
    private final Sink sink;
    private final Map<Object, Session> watched = new LinkedHashMap<>();
    /** Sessions published to this launcher directly by a source's own service. */
    private final Map<Object, Session> offered = new LinkedHashMap<>();
    private boolean started;
    private boolean access;

    DirectSessionRegistry(Source source, Sink sink) { this.source = source; this.sink = sink; }

    boolean started() { return started; }

    void start() {
        if (started) return;
        started = true;
        source.setChangeListener(this::refresh);
        refresh();
    }

    void stop() {
        if (!started) return;
        started = false;
        source.setChangeListener(null);
        offered.clear();
        release();
    }

    /**
     * Add a session a source handed to this launcher itself. It joins the same membership as the
     * listed sessions; the same platform session listed both ways is one entry.
     */
    void offer(Session session) {
        Object token = session == null ? null : session.token();
        if (!started || token == null) return;
        offered.put(token, session);
        refresh();
    }

    void withdraw(Object token) {
        if (token != null && offered.remove(token) != null && started) refresh();
    }

    /** Re-read membership from the platform and publish it. Safe to call at any time. */
    void refresh() {
        if (!started) return;
        List<Session> active = Collections.emptyList();
        boolean granted = source.accessGranted();
        if (granted) {
            try {
                List<Session> reported = source.activeSessions();
                if (reported != null) active = reported;
            } catch (SecurityException notEffective) {
                // Listed as enabled but not yet effective: report it as missing, never guess.
                granted = false;
            } catch (RuntimeException unavailable) {
                // A failed read is not an empty one. Keep what is watched and say it is unknown.
                sink.onMembershipUnknown();
                return;
            }
        }
        access = granted;
        // Sessions handed over by a source are held only under the listener authority.
        if (!granted) offered.clear();
        Map<Object, Session> next = new LinkedHashMap<>();
        List<Session> candidates = new ArrayList<>(active);
        candidates.addAll(offered.values());
        for (Session candidate : candidates) {
            Object token = candidate == null ? null : candidate.token();
            if (token == null || next.containsKey(token)) continue;
            Session owner = watched.get(token);
            if (owner == null) {
                owner = candidate;
                try {
                    owner.watch(() -> changed(token), () -> ended(token));
                } catch (RuntimeException gone) {
                    continue;
                }
            }
            next.put(token, owner);
        }
        for (Map.Entry<Object, Session> entry : watched.entrySet()) {
            if (!next.containsKey(entry.getKey())) unwatch(entry.getValue());
        }
        watched.clear();
        watched.putAll(next);
        publish();
    }

    /** Forward one operation to one session. False when that session is no longer watched. */
    boolean dispatch(Object token, DirectOperation operation) {
        Session session = token == null ? null : watched.get(token);
        if (session == null || operation == null) return false;
        try {
            return session.dispatch(operation);
        } catch (RuntimeException gone) {
            return false;
        }
    }

    int watchedCount() { return watched.size(); }

    private void changed(Object token) {
        Session session = watched.get(token);
        if (!started || session == null) return;
        SessionObservation observation = readSafely(session);
        if (observation != null) sink.onSessionChanged(observation);
    }

    private void ended(Object token) {
        offered.remove(token);
        Session session = watched.remove(token);
        if (session == null) return;
        unwatch(session);
        if (started) publish();
    }

    private void publish() {
        List<SessionObservation> readings = new ArrayList<>();
        for (Session session : watched.values()) {
            SessionObservation observation = readSafely(session);
            if (observation != null) readings.add(observation);
        }
        sink.onMembership(access, readings);
    }

    private void release() {
        for (Session session : watched.values()) unwatch(session);
        watched.clear();
    }

    private static SessionObservation readSafely(Session session) {
        try {
            return session.read();
        } catch (RuntimeException gone) {
            return null;
        }
    }

    private static void unwatch(Session session) {
        try {
            session.unwatch();
        } catch (RuntimeException gone) {
            // Already destroyed by the platform.
        }
    }
}
