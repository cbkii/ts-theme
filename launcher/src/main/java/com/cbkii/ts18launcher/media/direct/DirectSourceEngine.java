package com.cbkii.ts18launcher.media.direct;

import android.media.session.PlaybackState;

import com.cbkii.ts18launcher.media.MediaBackend;
import com.cbkii.ts18launcher.media.MediaCommandResult;
import com.cbkii.ts18launcher.media.MediaEngine;
import com.cbkii.ts18launcher.media.MediaSourceKey;
import com.cbkii.ts18launcher.media.MediaSourceState;
import com.cbkii.ts18launcher.media.MediaUserAction;

import java.util.ArrayList;
import java.util.List;

/**
 * M2 state machine for one source (Music or Radio). Framework-free and single-threaded.
 *
 * Ownership: exactly one configured package and, within it, exactly one pinned session token.
 * Every command is bound to that token and to the current generation; a reading or callback for
 * any other token, package or generation is ignored. The engine never prepares, launches or
 * foregrounds anything: with no live session it reports that truthfully and dispatches nothing.
 *
 * One tap yields at most one transport dispatch. Play and Pause are resolved to a direction at
 * tap time and then confirmed only by the owning session's own state.
 */
public final class DirectSourceEngine {
    /** Sends one operation to the session identified by a token. False when it could not be sent. */
    public interface Transport {
        boolean dispatch(Object token, DirectOperation operation);
        default boolean dispatchDeadline(Object token, long deadlineElapsedMs) { return false; }
    }

    /** Monotonic time and one-shot timers on the engine's own thread. */
    public interface Clock {
        default long nowMs() { return android.os.SystemClock.elapsedRealtime(); }
        Object schedule(long delayMs, Runnable task);
        void cancel(Object handle);
    }

    /** Driver-facing names. Capabilities come only from what the session advertises. */
    public interface Labels {
        String sourceLabel();
        String appLabel(String packageName);
    }

    /**
     * Source-owned cold start: asks a package that is not running to publish its session through
     * its own exported media service. It must never open an Activity or send a transport command.
     */
    public interface Starter {
        boolean canStart(String packageName);
        /**
         * Begin, or join, one start attempt for the package on behalf of owner. A joined attempt
         * that is still connecting is shared; one that already produced a session is replaced.
         */
        void start(Object owner, String packageName);
        /** The owner's attempt timed out: drop its claim on a connection that never completed. */
        void abandon(Object owner, String packageName);
        /** The owner no longer needs the package: drop its claim, including a live connection. */
        void release(Object owner, String packageName);
    }

    public static final long COLD_START_MS = 8000L;
    public static final long PLAY_ACK_MS = 5000L;
    public static final long PAUSE_ACK_MS = 2500L;
    public static final long SKIP_OBSERVE_MS = 2000L;
    /** After PREPARE is sent, how long the source has to show a loaded item. */
    public static final long PREPARE_OBSERVE_MS = 5000L;

    private static final class Pending {
        final long opId;
        final Object token;
        final long generation;
        final DirectOperation operation;
        final String itemAtDispatch;
        final List<MediaBackend.ResultCallback> callbacks = new ArrayList<>();
        Object timer;
        long deadlineElapsedMs;
        Pending(long opId, Object token, long generation, DirectOperation operation, String item) {
            this.opId = opId; this.token = token; this.generation = generation;
            this.operation = operation; this.itemAtDispatch = item;
        }
    }

    private final MediaSourceKey key;
    private final Transport transport;
    private final Clock clock;
    private final Labels labels;
    private final Starter starter;
    private MediaBackend.StateListener listener;

    private boolean attached;
    private String packageName = "";
    private boolean access;
    private long generation;
    private Object pinnedToken;
    private SessionObservation current;
    private boolean liveThisEpoch;
    private MediaSourceState.Availability availability = MediaSourceState.Availability.UNCONFIGURED;
    private Pending pendingToggle;
    /** A Play tap waiting for the source's own service to publish a session. Not yet dispatched. */
    private Pending pendingStart;
    /** Deadline of an unconfirmed handover pause, and the exact session it was sent to. */
    private Object handoverPause;
    private Object handoverPauseToken;
    private long handoverPauseGeneration;
    /** Whether the configured package publishes a startable media service; read on membership. */
    private boolean startCapable;
    private final List<Pending> pendingSkips = new ArrayList<>();
    /** Passive readiness in progress: token null while binding, the pinned token once sent. */
    private Pending pendingPrepare;
    /** Latest epoch asked for, and the latest epoch already decided (attempted or not needed). */
    private long requestedPrepareEpoch = -1L;
    private long decidedPrepareEpoch = -1L;
    private long nextOpId;
    private MediaSourceState published;

    public DirectSourceEngine(MediaSourceKey key, Transport transport, Clock clock, Labels labels) {
        this(key, transport, clock, labels, null);
    }

    /** starter may be null: the engine then controls already-running sources only. */
    public DirectSourceEngine(MediaSourceKey key, Transport transport, Clock clock, Labels labels,
                              Starter starter) {
        this.key = key; this.transport = transport; this.clock = clock; this.labels = labels;
        this.starter = starter;
    }

    public void setListener(MediaBackend.StateListener listener) { this.listener = listener; }

    public boolean attached() { return attached; }

    public String packageName() { return attached ? packageName : ""; }

    /** Select this engine for the source with the exact configured package. */
    public void attach(String configuredPackage) {
        String next = configuredPackage == null ? "" : configuredPackage;
        if (attached && next.equals(packageName)) return;
        failAllPending(MediaCommandResult.Outcome.SOURCE_LOST, "Source changed");
        endStart(MediaCommandResult.Outcome.SOURCE_LOST, "Source changed");
        cancelPrepare();
        releaseStarter();
        attached = true;
        requestedPrepareEpoch = -1L;
        decidedPrepareEpoch = -1L;
        packageName = next;
        generation++;
        clearHandoverPause();
        pinnedToken = null; current = null; liveThisEpoch = false; published = null;
        startCapable = false;
        availability = next.isEmpty() ? MediaSourceState.Availability.UNCONFIGURED
                : MediaSourceState.Availability.NO_SESSION;
    }

    /** Deselect: end pending work without dispatching and publish nothing further. */
    public void detach() {
        if (!attached) return;
        failAllPending(MediaCommandResult.Outcome.FAILED, "Media engine changed");
        endStart(MediaCommandResult.Outcome.FAILED, "Media engine changed");
        cancelPrepare();
        releaseStarter();
        attached = false;
        generation++;
        clearHandoverPause();
        pinnedToken = null; current = null; liveThisEpoch = false; published = null;
        availability = MediaSourceState.Availability.UNCONFIGURED;
    }

    /**
     * The session service failed to answer. That proves nothing about this source: a pinned
     * session keeps working through its own callbacks; otherwise the source is reported unknown.
     */
    public void onMembershipUnknown() {
        if (!attached || packageName.isEmpty() || live()) return;
        settle(MediaSourceState.Availability.SERVICE_UNAVAILABLE, null);
        publish();
    }

    /** Full membership reading: every live session the listener authority can see right now. */
    public void onSessions(boolean accessGranted, List<SessionObservation> sessions) {
        if (!attached) return;
        access = accessGranted;
        if (packageName.isEmpty()) { settle(MediaSourceState.Availability.UNCONFIGURED, null); return; }
        // One package-manager read per membership change, never per state query or tap.
        startCapable = starter != null && starter.canStart(packageName);
        List<SessionObservation> samePackage = new ArrayList<>();
        if (sessions != null) for (SessionObservation session : sessions) {
            if (session != null && session.token != null && packageName.equals(session.packageName)
                    && (accessGranted || session.sourceOwned))
                samePackage.add(session);
        }
        DirectSessionSelector.Selection selection = DirectSessionSelector.select(samePackage, pinnedToken);
        switch (selection.kind) {
            case ONE:
                settle(MediaSourceState.Availability.LIVE, selection.session);
                break;
            case AMBIGUOUS:
                endStart(MediaCommandResult.Outcome.FAILED,
                        labels.appLabel(packageName) + " has several sessions · open it to choose");
                settle(MediaSourceState.Availability.AMBIGUOUS_SOURCE, null);
                break;
            default:
                settle(!accessGranted && !startCapable ? MediaSourceState.Availability.PERMISSION_NEEDED
                        : liveThisEpoch ? MediaSourceState.Availability.SOURCE_LOST
                        : MediaSourceState.Availability.NO_SESSION, null);
        }
        tryPassivePrepare();
    }

    /** A state or metadata event from one session. Ignored unless it is the pinned session. */
    public void onSessionChanged(SessionObservation observation) {
        if (!attached || observation == null || pinnedToken == null
                || !pinnedToken.equals(observation.token)
                || !packageName.equals(observation.packageName)) return;
        current = observation;
        if (handoverPause != null && observation.nativeState != PlaybackState.STATE_PLAYING) clearHandoverPause();
        resolvePending(observation);
        observePrepare(observation);
        publish();
    }

    public MediaSourceState state() {
        if (!attached) {
            return MediaSourceState.unavailable(key, MediaEngine.DIRECT, "",
                    MediaSourceState.Availability.UNCONFIGURED, generation);
        }
        if (availability != MediaSourceState.Availability.LIVE || current == null) {
            return MediaSourceState.unavailable(key, MediaEngine.DIRECT, packageName, availability, generation)
                    .withStart(startable(), pendingStart != null, pendingPrepare != null);
        }
        MediaSourceState live = new MediaSourceState(key, MediaEngine.DIRECT, packageName, availability,
                current.nativeState, current.actions,
                current.token, current.itemId, current.title, current.subtitle, generation);
        return pendingPrepare == null ? live : live.withStart(false, false, true);
    }

    public boolean playing(String expectedPackage) {
        return attached && packageName.equals(expectedPackage) && live()
                && current.nativeState == PlaybackState.STATE_PLAYING;
    }

    public void command(String expectedPackage, MediaUserAction action, MediaBackend.ResultCallback callback) {
        if (!attached) { deliver(callback, failed("Direct engine is not selected")); return; }
        if (!packageName.equals(expectedPackage)) {
            // The caller is addressing a package this engine does not own. Never redirect.
            deliver(callback, failed(labels.sourceLabel() + " source changed"));
            return;
        }
        // A deliberate tap supersedes passive readiness; nothing further is sent for it.
        cancelPrepare();
        MediaCommandResult unavailable = unavailableResult();
        if (unavailable != null) {
            if (action == MediaUserAction.PLAY_PAUSE && startable()) { requestStart(callback); return; }
            deliver(callback, unavailable);
            return;
        }

        DirectOperation operation = action == MediaUserAction.NEXT ? DirectOperation.NEXT
                : action == MediaUserAction.PREVIOUS ? DirectOperation.PREVIOUS
                : DirectOperation.resolvePlayPause(current.nativeState);
        if (operation.directional() && pendingToggle != null) {
            // A second tap before the first is confirmed joins it; it is never a second dispatch.
            if (callback != null) pendingToggle.callbacks.add(callback);
            return;
        }
        long effectiveActions = current.actions;
        if ((effectiveActions & operation.supportingActions()) == 0L) {
            deliver(callback, new MediaCommandResult(MediaCommandResult.Outcome.UNSUPPORTED,
                    labels.appLabel(packageName) + " does not offer " + describe(operation) + " right now",
                    current.nativeState == PlaybackState.STATE_PLAYING, false));
            return;
        }

        Object token = pinnedToken;
        Pending pending = new Pending(++nextOpId, token, generation, operation, itemIdentity(current));
        if (callback != null) pending.callbacks.add(callback);
        long deadline = clock.nowMs() + PLAY_ACK_MS;
        if (operation == DirectOperation.PLAY && !loaded(current) && !current.deadlinePlay) {
            deliver(callback, coldUnsupported());
            return;
        }
        if (!dispatch(token, operation, deadline)) {
            // The session vanished between observation and dispatch. Report it; do not retry.
            deliverAll(pending, new MediaCommandResult(MediaCommandResult.Outcome.SOURCE_LOST,
                    labels.appLabel(packageName) + " stopped responding", false, false));
            return;
        }
        if (operation.directional()) pendingToggle = pending; else pendingSkips.add(pending);
        long window = operation == DirectOperation.PLAY ? PLAY_ACK_MS
                : operation == DirectOperation.PAUSE ? PAUSE_ACK_MS : SKIP_OBSERVE_MS;
        pending.timer = clock.schedule(window, () -> onDeadline(pending));
    }

    /** Source switch: pause once, only when this source is observed playing. */
    public void pauseForHandover() {
        if (!attached || !live() || current.nativeState != PlaybackState.STATE_PLAYING) return;
        if (handoverPause != null && handoverPauseToken == pinnedToken && handoverPauseGeneration == generation) {
            // A handover pause to this exact session is already in flight; never send another.
            return;
        }
        long effectiveActions = current.actions;
        if ((effectiveActions & DirectOperation.PAUSE.supportingActions()) == 0L) return;
        if (pendingToggle != null) {
            finish(pendingToggle, new MediaCommandResult(MediaCommandResult.Outcome.SENT_UNCONFIRMED,
                    "Superseded by a source switch", false, true));
        }
        if (!transport.dispatch(pinnedToken, DirectOperation.PAUSE)) return;
        handoverPauseToken = pinnedToken;
        handoverPauseGeneration = generation;
        Object[] holder = new Object[1];
        holder[0] = clock.schedule(PAUSE_ACK_MS, () -> {
            if (handoverPause == holder[0]) clearHandoverPause();
        });
        handoverPause = holder[0];
    }

    private boolean dispatch(Object token, DirectOperation operation, long deadline) {
        if (operation == DirectOperation.PLAY && !loaded(current)) {
            return current.deadlinePlay && deadline > clock.nowMs()
                    && transport.dispatchDeadline(token, deadline);
        }
        return transport.dispatch(token, operation);
    }

    private MediaCommandResult coldUnsupported() {
        return new MediaCommandResult(MediaCommandResult.Outcome.UNSUPPORTED,
                labels.appLabel(packageName) + " needs a loaded item · open it once", false, false);
    }

    private void clearHandoverPause() {
        if (handoverPause != null) clock.cancel(handoverPause);
        handoverPause = null;
        handoverPauseToken = null;
    }

    private boolean live() {
        return availability == MediaSourceState.Availability.LIVE && current != null && pinnedToken != null;
    }

    private void settle(MediaSourceState.Availability next, SessionObservation session) {
        Object nextToken = session == null ? null : session.token;
        boolean tokenChanged = pinnedToken == null ? nextToken != null : !pinnedToken.equals(nextToken);
        if (tokenChanged) {
            // A different (or no) session: nothing in flight may be confirmed by it.
            generation++;
            clearHandoverPause();
            failAllPending(MediaCommandResult.Outcome.SOURCE_LOST,
                    labels.appLabel(packageName) + " session ended");
            if (pendingPrepare != null && pendingPrepare.token != null) cancelPrepare();
        }
        pinnedToken = nextToken;
        current = session;
        availability = next;
        if (session != null) {
            liveThisEpoch = true;
            resolvePending(session);
            continueStart();
            continuePrepare();
        }
        publish();
    }

    /** The source's own service could not publish a session. Ends the waiting Play truthfully. */
    public void onStartFailed(String failedPackage, String reason) {
        if (!attached || !packageName.equals(failedPackage)) return;
        endStart(MediaCommandResult.Outcome.FAILED, labels.appLabel(packageName) + " " + reason);
    }

    /** True while a Play tap is waiting for the source to publish a session. */
    public boolean starting() { return pendingStart != null; }

    private boolean startable() {
        return startCapable
                && (availability == MediaSourceState.Availability.NO_SESSION
                        || availability == MediaSourceState.Availability.SOURCE_LOST);
    }

    private void requestStart(MediaBackend.ResultCallback callback) {
        if (pendingStart != null) {
            // Further taps join the one attempt; the source is asked once.
            if (callback != null) pendingStart.callbacks.add(callback);
            return;
        }
        Pending start = new Pending(++nextOpId, null, generation, DirectOperation.PLAY, "");
        if (callback != null) start.callbacks.add(callback);
        start.deadlineElapsedMs = clock.nowMs() + COLD_START_MS;
        pendingStart = start;
        start.timer = clock.schedule(COLD_START_MS, () -> {
            if (pendingStart != start) return;
            start.timer = null;
            // Tear down this owner's claim on the stalled connection so a later tap is a fresh,
            // bounded attempt rather than a wait on the same one.
            if (starter != null) starter.abandon(key, packageName);
            endStart(MediaCommandResult.Outcome.FAILED,
                    labels.appLabel(packageName) + " did not start · open it once");
        });
        publish();
        starter.start(key, packageName);
    }

    /** The awaited session is now pinned: send the one Play the driver asked for. */
    private void continueStart() {
        Pending start = pendingStart;
        if (start == null || !live()) return;
        pendingStart = null;
        if (start.timer != null) { clock.cancel(start.timer); start.timer = null; }
        if (DirectOperation.PLAY.acknowledgedBy(current.nativeState)) {
            // The source resumed by itself; there is nothing left to send.
            deliverAll(start, new MediaCommandResult(MediaCommandResult.Outcome.ACKNOWLEDGED, "", true, false));
            return;
        }
        long effectiveActions = current.actions;
        if ((effectiveActions & DirectOperation.PLAY.supportingActions()) == 0L) {
            deliverAll(start, new MediaCommandResult(MediaCommandResult.Outcome.UNSUPPORTED,
                    labels.appLabel(packageName) + " started but does not offer Play yet", false, false));
            return;
        }
        long remaining = Math.min(PLAY_ACK_MS, start.deadlineElapsedMs - clock.nowMs());
        if (remaining <= 0L) {
            deliverAll(start, failed("Play window expired · tap again"));
            return;
        }
        if (!loaded(current) && !current.deadlinePlay) {
            deliverAll(start, coldUnsupported());
            return;
        }
        Pending play = new Pending(++nextOpId, pinnedToken, generation, DirectOperation.PLAY,
                itemIdentity(current));
        play.callbacks.addAll(start.callbacks);
        if (!dispatch(pinnedToken, DirectOperation.PLAY, clock.nowMs() + remaining)) {
            deliverAll(play, new MediaCommandResult(MediaCommandResult.Outcome.SOURCE_LOST,
                    labels.appLabel(packageName) + " stopped responding", false, false));
            return;
        }
        pendingToggle = play;
        play.timer = clock.schedule(remaining, () -> onDeadline(play));
    }

    /**
     * Passive readiness for one epoch. Uses only the source's own contract: bind its exported
     * media browser if it is not running, then send PREPARE once if it advertises it and has no
     * loaded item. Never Play, never an Activity, never audio focus. One attempt per epoch.
     */
    public void preparePassive(long epoch) {
        if (!attached || epoch <= decidedPrepareEpoch) return;
        requestedPrepareEpoch = Math.max(requestedPrepareEpoch, epoch);
        tryPassivePrepare();
    }

    private void tryPassivePrepare() {
        long epoch = requestedPrepareEpoch;
        if (!attached || epoch <= decidedPrepareEpoch || packageName.isEmpty()) return;
        // Membership and access must be known before anything is decided for this epoch.
        if (!access && !startCapable) return;
        if (pendingStart != null || pendingPrepare != null || pendingToggle != null) return;
        if (availability == MediaSourceState.Availability.AMBIGUOUS_SOURCE
                || availability == MediaSourceState.Availability.SERVICE_UNAVAILABLE) return;
        decidedPrepareEpoch = epoch;
        if (live()) {
            if (loaded(current) || !advertisesPrepare(current)) return;
            pendingPrepare = new Pending(++nextOpId, null, generation, DirectOperation.PREPARE, "");
            continuePrepare();
            publish();
            return;
        }
        if (!startable()) return;
        Pending prepare = new Pending(++nextOpId, null, generation, DirectOperation.PREPARE, "");
        pendingPrepare = prepare;
        prepare.timer = clock.schedule(COLD_START_MS, () -> {
            if (pendingPrepare != prepare) return;
            prepare.timer = null;
            if (pendingStart == null && starter != null) starter.abandon(key, packageName);
            endPrepare();
        });
        publish();
        starter.start(key, packageName);
    }

    /** The session is pinned: finish if it is already loaded, else send PREPARE once. */
    private void continuePrepare() {
        Pending prepare = pendingPrepare;
        if (prepare == null || !live() || prepare.token != null) return;
        if (prepare.timer != null) { clock.cancel(prepare.timer); prepare.timer = null; }
        if (loaded(current) || !advertisesPrepare(current)) { endPrepare(); return; }
        Pending sent = new Pending(++nextOpId, pinnedToken, generation, DirectOperation.PREPARE, "");
        pendingPrepare = sent;
        if (!transport.dispatch(pinnedToken, DirectOperation.PREPARE)) { endPrepare(); return; }
        sent.timer = clock.schedule(PREPARE_OBSERVE_MS, () -> {
            if (pendingPrepare != sent) return;
            sent.timer = null;
            endPrepare();
        });
    }

    private void observePrepare(SessionObservation observation) {
        Pending prepare = pendingPrepare;
        if (prepare == null || prepare.token == null || prepare.generation != generation
                || !prepare.token.equals(observation.token)) return;
        if (loaded(observation) || observation.nativeState == PlaybackState.STATE_ERROR) endPrepare();
    }

    private void endPrepare() {
        Pending prepare = pendingPrepare;
        if (prepare == null) return;
        pendingPrepare = null;
        if (prepare.timer != null) { clock.cancel(prepare.timer); prepare.timer = null; }
        if (attached) publish();
    }

    /** Stop waiting without sending anything further. The browser link stays with its owners. */
    private void cancelPrepare() {
        Pending prepare = pendingPrepare;
        if (prepare == null) return;
        pendingPrepare = null;
        if (prepare.timer != null) { clock.cancel(prepare.timer); prepare.timer = null; }
    }

    /** True while passive readiness is in progress. */
    public boolean preparing() { return pendingPrepare != null; }

    /** A playable item is loaded: the source's own current item, not just a process or token. */
    static boolean loaded(SessionObservation observation) {
        return observation != null && (observation.nativeState == PlaybackState.STATE_PAUSED
                    || observation.nativeState == PlaybackState.STATE_PLAYING
                    || observation.nativeState == PlaybackState.STATE_STOPPED)
                && (!observation.itemId.isEmpty() || !observation.title.isEmpty());
    }

    private static boolean advertisesPrepare(SessionObservation observation) {
        return observation != null && (observation.actions & PlaybackState.ACTION_PREPARE) != 0L;
    }

    private void endStart(MediaCommandResult.Outcome outcome, String message) {
        Pending start = pendingStart;
        if (start == null) return;
        pendingStart = null;
        if (start.timer != null) { clock.cancel(start.timer); start.timer = null; }
        deliverAll(start, new MediaCommandResult(outcome, message, false, false));
        if (attached) publish();
    }

    private void releaseStarter() {
        if (starter != null && attached && !packageName.isEmpty()) starter.release(key, packageName);
    }

    private void resolvePending(SessionObservation observation) {
        Pending toggle = pendingToggle;
        if (toggle != null && toggle.generation == generation && toggle.token.equals(observation.token)) {
            if (toggle.operation.acknowledgedBy(observation.nativeState)) {
                finish(toggle, new MediaCommandResult(MediaCommandResult.Outcome.ACKNOWLEDGED, "",
                        observation.nativeState == PlaybackState.STATE_PLAYING, true));
            } else if (toggle.operation == DirectOperation.PLAY
                    && observation.nativeState == PlaybackState.STATE_ERROR) {
                finish(toggle, new MediaCommandResult(MediaCommandResult.Outcome.FAILED,
                        labels.appLabel(packageName) + " reported a playback error", false, true));
            }
        }
        if (pendingSkips.isEmpty()) return;
        String item = itemIdentity(observation);
        for (Pending skip : new ArrayList<>(pendingSkips)) {
            if (skip.generation == generation && skip.token.equals(observation.token)
                    && !skip.itemAtDispatch.equals(item)) {
                finish(skip, new MediaCommandResult(MediaCommandResult.Outcome.ACKNOWLEDGED, "",
                        observation.nativeState == PlaybackState.STATE_PLAYING, true));
            }
        }
    }

    private void onDeadline(Pending pending) {
        pending.timer = null;
        if (pending != pendingToggle && !pendingSkips.contains(pending)) return;
        // Sent once and not confirmed in time. Truthful, and never a reason to send again.
        finish(pending, new MediaCommandResult(MediaCommandResult.Outcome.SENT_UNCONFIRMED, "",
                false, true));
    }

    private void finish(Pending pending, MediaCommandResult result) {
        if (pending == pendingToggle) pendingToggle = null;
        pendingSkips.remove(pending);
        if (pending.timer != null) { clock.cancel(pending.timer); pending.timer = null; }
        deliverAll(pending, result);
    }

    private void failAllPending(MediaCommandResult.Outcome outcome, String message) {
        List<Pending> all = new ArrayList<>(pendingSkips);
        if (pendingToggle != null) all.add(0, pendingToggle);
        for (Pending pending : all) finish(pending, new MediaCommandResult(outcome, message, false, true));
    }

    private MediaCommandResult unavailableResult() {
        String app = labels.appLabel(packageName);
        switch (availability) {
            case LIVE:
                return current == null ? failed(app + " has no active session") : null;
            case UNCONFIGURED:
                return failed("No " + labels.sourceLabel().toLowerCase(java.util.Locale.ROOT) + " app configured");
            case PERMISSION_NEEDED:
                return failed("Notification access needed");
            case AMBIGUOUS_SOURCE:
                return failed(app + " has several sessions · open it to choose");
            case SOURCE_LOST:
                return new MediaCommandResult(MediaCommandResult.Outcome.SOURCE_LOST,
                        app + " stopped · open it again", false, false);
            case SERVICE_UNAVAILABLE:
                return failed("Media service not answering · try again");
            default:
                return failed(app + " is not running · open it first");
        }
    }

    private void publish() {
        MediaSourceState next = state();
        if (published != null && next.samePresentation(published)) return;
        published = next;
        if (listener != null) listener.onMediaSourceStateChanged(next);
    }

    private static String itemIdentity(SessionObservation observation) {
        return observation.itemId.isEmpty()
                ? "t:" + observation.title + "\u0000" + observation.subtitle : "i:" + observation.itemId;
    }

    private static String describe(DirectOperation operation) {
        switch (operation) {
            case PLAY: return "Play";
            case PAUSE: return "Pause";
            case NEXT: return "Next";
            case PREVIOUS: return "Previous";
            default: return "Prepare";
        }
    }

    private static MediaCommandResult failed(String message) {
        return new MediaCommandResult(MediaCommandResult.Outcome.FAILED, message, false, false);
    }

    private static void deliver(MediaBackend.ResultCallback callback, MediaCommandResult result) {
        if (callback != null) callback.onResult(result);
    }

    private static void deliverAll(Pending pending, MediaCommandResult result) {
        for (MediaBackend.ResultCallback callback : new ArrayList<>(pending.callbacks)) callback.onResult(result);
    }
}
