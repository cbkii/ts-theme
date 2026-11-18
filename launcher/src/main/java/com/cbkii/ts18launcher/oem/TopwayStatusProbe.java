package com.cbkii.ts18launcher.oem;

/**
 * One bounded, single-flight, operator-initiated look at the Topway command service from this
 * app's own identity. Check mode binds and verifies the Binder descriptor only. Observe mode also
 * registers a temporary status callback, sends the source query and waits for the reply, then
 * always unregisters and unbinds. It never registers the music callback and never sends anything
 * that can change Topway state. All steps run on the host's single worker thread.
 */
public final class TopwayStatusProbe {
    static final long BIND_DEADLINE_MS = 4000L;
    static final long REPLY_DEADLINE_MS = 2500L;

    public enum Stage { BIND, DESCRIPTOR, REGISTER, REQUEST, REPLY }

    public static final class Result {
        public final Capability.Status status;
        public final Stage stage;
        public final String detail;
        public final boolean observe;
        /** Topway source value, or null when none was reported. */
        public final Integer source;
        public final Integer reverse;
        public final Integer sleep;

        Result(Capability.Status status, Stage stage, String detail, boolean observe,
               Integer source, Integer reverse, Integer sleep) {
            this.status = status;
            this.stage = stage;
            this.detail = detail;
            this.observe = observe;
            this.source = source;
            this.reverse = reverse;
            this.sleep = sleep;
        }

        @Override public String toString() {
            return status + "@" + stage + " " + detail + (source == null ? "" : " source=" + source)
                    + (reverse == null ? "" : " reverse=" + reverse) + (sleep == null ? "" : " sleep=" + sleep);
        }
    }

    /** Status values pushed by the service; delivered on the worker thread. */
    public interface StatusListener {
        void onSource(int value);
        void onReverse(int value);
        void onSleep(int value);
    }

    /** The bound service, as far as this probe may use it. */
    public interface Remote {
        /** Throws when the service cannot answer. */
        String descriptor() throws Exception;
        boolean registerStatus(StatusListener listener);
        boolean requestSource();
        void unregisterStatus();
    }

    public interface Host {
        /** Starts an exact bind. False means refused immediately (not found, not permitted). */
        boolean bind(java.util.function.Consumer<Remote> onConnected, Runnable onLost);
        void unbind();
        /** Run on the single worker thread after a delay; the token cancels it. */
        Object schedule(Runnable task, long delayMs);
        void cancel(Object token);
    }

    private final Host host;
    private int generation;
    private boolean running;
    private boolean observe;
    private boolean bound;
    private boolean registered;
    private Object deadline;
    private Integer reverse, sleep;
    private java.util.function.Consumer<Result> done;

    public TopwayStatusProbe(Host host) {
        this.host = host;
    }

    public boolean running() { return running; }

    /** Returns false when a probe is already running; the running one is not disturbed. */
    public boolean start(boolean observeSource, java.util.function.Consumer<Result> onDone) {
        if (running) return false;
        running = true;
        observe = observeSource;
        done = onDone;
        registered = false;
        reverse = null;
        sleep = null;
        final int run = ++generation;
        boolean accepted;
        try {
            accepted = host.bind(remote -> connected(run, remote), () -> lost(run));
        } catch (RuntimeException refused) {
            accepted = false;
        }
        if (!accepted) {
            finish(run, Capability.Status.BLOCKED, Stage.BIND, "bind refused for this app", null);
            return true;
        }
        bound = true;
        deadline = host.schedule(() -> finish(run, Capability.Status.UNKNOWN, Stage.BIND,
                "no connection within " + BIND_DEADLINE_MS + " ms (not running or not answering)", null), BIND_DEADLINE_MS);
        return true;
    }

    private void connected(int run, Remote remote) {
        if (!current(run)) return;
        host.cancel(deadline);
        String descriptor;
        try {
            descriptor = remote.descriptor();
        } catch (Exception failed) {
            finish(run, Capability.Status.UNKNOWN, Stage.DESCRIPTOR, "service did not answer", null);
            return;
        }
        if (!TopwayContract.COMMAND_DESCRIPTOR.equals(descriptor)) {
            finish(run, Capability.Status.BLOCKED, Stage.DESCRIPTOR,
                    "unexpected interface " + descriptor, null);
            return;
        }
        if (!observe) {
            finish(run, Capability.Status.AVAILABLE, Stage.DESCRIPTOR, "service answers with the verified interface", null);
            return;
        }
        if (!remote.registerStatus(statusListener(run))) {
            finish(run, Capability.Status.BLOCKED, Stage.REGISTER, "status callback refused", null);
            return;
        }
        registered = true;
        currentRemote = remote;
        if (!remote.requestSource()) {
            finish(run, Capability.Status.BLOCKED, Stage.REQUEST, "source query refused", null);
            return;
        }
        deadline = host.schedule(() -> finish(run, Capability.Status.UNKNOWN, Stage.REPLY,
                "no source reply within " + REPLY_DEADLINE_MS + " ms", null), REPLY_DEADLINE_MS);
    }

    private Remote currentRemote;

    private StatusListener statusListener(int run) {
        return new StatusListener() {
            @Override public void onSource(int value) {
                finish(run, Capability.Status.AVAILABLE, Stage.REPLY, "source reported", value);
            }
            @Override public void onReverse(int value) { if (current(run)) reverse = value; }
            @Override public void onSleep(int value) { if (current(run)) sleep = value; }
        };
    }

    private void lost(int run) {
        finish(run, Capability.Status.UNKNOWN, Stage.BIND, "service disconnected", null);
    }

    private boolean current(int run) {
        return running && run == generation;
    }

    private void finish(int run, Capability.Status status, Stage stage, String detail, Integer source) {
        if (!current(run)) return;
        host.cancel(deadline);
        deadline = null;
        if (registered && currentRemote != null) {
            try { currentRemote.unregisterStatus(); } catch (RuntimeException ignored) { }
        }
        registered = false;
        currentRemote = null;
        if (bound) {
            try { host.unbind(); } catch (RuntimeException ignored) { }
        }
        bound = false;
        running = false;
        generation++;
        java.util.function.Consumer<Result> callback = done;
        done = null;
        if (callback != null) callback.accept(new Result(status, stage, detail, observe, source, reverse, sleep));
    }
}
