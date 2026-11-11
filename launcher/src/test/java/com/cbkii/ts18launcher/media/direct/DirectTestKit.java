package com.cbkii.ts18launcher.media.direct;

import android.media.session.PlaybackState;

import java.util.ArrayList;
import java.util.List;

/** Hand-driven doubles for the direct engine: a scripted session source and a manual clock. */
final class DirectTestKit {
    static final long PLAYABLE = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE;
    static final long FULL = PLAYABLE | PlaybackState.ACTION_SKIP_TO_NEXT
            | PlaybackState.ACTION_SKIP_TO_PREVIOUS;

    private DirectTestKit() {}

    static final class ManualClock implements DirectSourceEngine.Clock {
        long now = 1000L;
        @Override public long nowMs() { return now; }
        final List<Runnable> scheduled = new ArrayList<>();
        @Override public Object schedule(long delayMs, Runnable task) { scheduled.add(task); return task; }
        @Override public void cancel(Object handle) { scheduled.remove(handle); }
        /** Fire every deadline that is still armed. */
        void expireAll() {
            List<Runnable> due = new ArrayList<>(scheduled);
            scheduled.clear();
            for (Runnable task : due) task.run();
        }
    }

    /** A scripted platform session. Mutate the fields, then call emit(). */
    static final class FakeSession implements DirectSessionRegistry.Session {
        final Object token;
        final String packageName;
        int state = PlaybackState.STATE_PAUSED;
        long actions = FULL;
        String itemId = "";
        String title = "";
        String subtitle = "";
        boolean deadlinePlay = true;
        long dispatchedDeadline;
        boolean acceptDispatch = true;
        boolean throwOnDispatch;
        int watches;
        int unwatches;
        final List<DirectOperation> dispatched = new ArrayList<>();
        private Runnable onChanged;
        private Runnable onEnded;

        FakeSession(String tokenName, String packageName) {
            this.token = tokenName;
            this.packageName = packageName;
        }

        FakeSession state(int next) { state = next; return this; }
        FakeSession item(String id, String nextTitle, String nextSubtitle) {
            itemId = id; title = nextTitle; subtitle = nextSubtitle; return this;
        }
        FakeSession actions(long next) { actions = next; return this; }

        @Override public Object token() { return token; }
        @Override public SessionObservation read() {
            return new SessionObservation(token, packageName, state, actions, itemId, title, subtitle, deadlinePlay, false);
        }
        @Override public void watch(Runnable changed, Runnable ended) {
            watches++; onChanged = changed; onEnded = ended;
        }
        @Override public void unwatch() { unwatches++; onChanged = null; onEnded = null; }
        @Override public boolean dispatch(DirectOperation operation) {
            if (throwOnDispatch) throw new IllegalStateException("dead session");
            if (!acceptDispatch) return false;
            dispatched.add(operation);
            return true;
        }
        @Override public boolean dispatchDeadline(long deadlineElapsedMs) {
            dispatchedDeadline = deadlineElapsedMs;
            return dispatch(DirectOperation.PLAY);
        }
        boolean watched() { return onChanged != null; }
        void emit() { if (onChanged != null) onChanged.run(); }
        void end() { if (onEnded != null) onEnded.run(); }
    }

    /** A scripted listener authority. */
    static final class FakeSource implements DirectSessionRegistry.Source {
        boolean access = true;
        boolean throwSecurity;
        boolean throwRuntime;
        int reads;
        final List<DirectSessionRegistry.Session> sessions = new ArrayList<>();
        Runnable listener;

        @Override public boolean accessGranted() { return access; }
        @Override public List<DirectSessionRegistry.Session> activeSessions() {
            reads++;
            if (throwSecurity) throw new SecurityException("listener not enabled");
            if (throwRuntime) throw new IllegalStateException("session service unavailable");
            return new ArrayList<>(sessions);
        }
        @Override public void setChangeListener(Runnable onChanged) { listener = onChanged; }
        void changed() { if (listener != null) listener.run(); }
    }

    static int totalDispatches(FakeSession... sessions) {
        int total = 0;
        for (FakeSession session : sessions) total += session.dispatched.size();
        return total;
    }
}
