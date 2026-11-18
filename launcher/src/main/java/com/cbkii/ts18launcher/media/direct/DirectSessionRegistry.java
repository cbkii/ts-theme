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
        default boolean dispatchDeadline(long deadlineElapsedMs) { return false; }
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

    /** Message of the IllegalStateException a Source throws while its first read is in flight. */
    static final String MEMBERSHIP_READ_PENDING = "MEMBERSHIP_READ_PENDING";

    private final Source source;
    private final Sink sink;
    private final Map<Object, Session> watched = new LinkedHashMap<>();
    /** Sessions published to this launcher directly by a source's own service. */
    private final Map<Object, Session> offered = new LinkedHashMap<>();
    /** Last successful listener reading. A failed read is not an empty reading. */
    private List<Session> listenerSnapshot = Collections.emptyList();
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
        listenerSnapshot = Collections.emptyList();
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
        boolean unknown = false;
        if (granted) {
            try {
                List<Session> reported = source.activeSessions();
                if (reported != null) active = reported;
                listenerSnapshot = new ArrayList<>(active);
            } catch (SecurityException notEffective) {
                // Listed as enabled but not yet effective: report it as missing, never guess.
                granted = false;
                listenerSnapshot = Collections.emptyList();
            } catch (IllegalStateException pending) {
                if (!MEMBERSHIP_READ_PENDING.equals(pending.getMessage())) {
                    active = listenerSnapshot;
                    unknown = true;
                } else {
                    // The first asynchronous read has not completed yet. That is not evidence that
                    // discovery failed; keep the last reading (and any browser offers) and wait for
                    // the read's own change notification instead of reporting "not answering".
                    active = listenerSnapshot;
                }
            } catch (RuntimeException unavailable) {
                // Global discovery cannot veto a token delivered by the source itself.
                // Retain the last successful listener reading, merge current browser offers,
                // then mark only still-unobserved sources unknown.
                active = listenerSnapshot;
                unknown = true;
            }
        }
        if (!granted) listenerSnapshot = Collections.emptyList();
        access = granted;
        // Browser tokens carry their own source-owned authority, independent of listener access.
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
        if (unknown) sink.onMembershipUnknown();
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

    boolean dispatchDeadline(Object token, long deadlineElapsedMs) {
        Session session = watched.get(token);
        if (session == null) return false;
        try { return session.dispatchDeadline(deadlineElapsedMs); }
        catch (RuntimeException gone) { return false; }
    }

    private SessionObservation readOwned(Session session) {
        SessionObservation observation = readSafely(session);
        return observation == null ? null : observation.withSourceOwned(offered.containsKey(session.token()));
    }

    int watchedCount() { return watched.size(); }

    private void changed(Object token) {
        Session session = watched.get(token);
        if (!started || session == null) return;
        SessionObservation observation = readOwned(session);
        if (observation != null) sink.onSessionChanged(observation);
    }

    private void ended(Object token) {
        listenerSnapshot = new ArrayList<>(listenerSnapshot);
        listenerSnapshot.removeIf(session -> token.equals(session.token()));
        offered.remove(token);
        Session session = watched.remove(token);
        if (session == null) return;
        unwatch(session);
        if (started) publish();
    }

    private void publish() {
        List<SessionObservation> readings = new ArrayList<>();
        for (Session session : watched.values()) {
            SessionObservation observation = readOwned(session);
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
