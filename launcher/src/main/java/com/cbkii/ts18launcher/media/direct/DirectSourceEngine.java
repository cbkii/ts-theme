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
    public interface Transport { boolean dispatch(Object token, DirectOperation operation); }

    /** Monotonic time and one-shot timers on the engine's own thread. */
    public interface Clock {
        Object schedule(long delayMs, Runnable task);
        void cancel(Object handle);
    }

    /** Driver-facing names and exact-device capability additions for a package. */
    public interface Labels {
        String sourceLabel();
        String appLabel(String packageName);
        /** Action bits a qualified source accepts although it does not advertise them. */
        long qualifiedExtraActions(String packageName);
    }

    public static final long PLAY_ACK_MS = 5000L;
    public static final long PAUSE_ACK_MS = 2500L;
    public static final long SKIP_OBSERVE_MS = 2000L;

    private static final class Pending {
        final long opId;
        final Object token;
        final long generation;
        final DirectOperation operation;
        final String itemAtDispatch;
        final List<MediaBackend.ResultCallback> callbacks = new ArrayList<>();
        Object timer;
        Pending(long opId, Object token, long generation, DirectOperation operation, String item) {
            this.opId = opId; this.token = token; this.generation = generation;
            this.operation = operation; this.itemAtDispatch = item;
        }
    }

    private final MediaSourceKey key;
    private final Transport transport;
    private final Clock clock;
    private final Labels labels;
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
    private final List<Pending> pendingSkips = new ArrayList<>();
    private long nextOpId;
    private MediaSourceState published;

    public DirectSourceEngine(MediaSourceKey key, Transport transport, Clock clock, Labels labels) {
        this.key = key; this.transport = transport; this.clock = clock; this.labels = labels;
    }

    public void setListener(MediaBackend.StateListener listener) { this.listener = listener; }

    public boolean attached() { return attached; }

    public String packageName() { return attached ? packageName : ""; }

    /** Select this engine for the source with the exact configured package. */
    public void attach(String configuredPackage) {
        String next = configuredPackage == null ? "" : configuredPackage;
        if (attached && next.equals(packageName)) return;
        failAllPending(MediaCommandResult.Outcome.SOURCE_LOST, "Source changed");
        attached = true;
        packageName = next;
        generation++;
        pinnedToken = null; current = null; liveThisEpoch = false; published = null;
        availability = next.isEmpty() ? MediaSourceState.Availability.UNCONFIGURED
                : MediaSourceState.Availability.NO_SESSION;
    }

    /** Deselect: end pending work without dispatching and publish nothing further. */
    public void detach() {
        if (!attached) return;
        failAllPending(MediaCommandResult.Outcome.FAILED, "Media engine changed");
        attached = false;
        generation++;
        pinnedToken = null; current = null; liveThisEpoch = false; published = null;
        availability = MediaSourceState.Availability.UNCONFIGURED;
    }

    /** Full membership reading: every live session the listener authority can see right now. */
    public void onSessions(boolean accessGranted, List<SessionObservation> sessions) {
        if (!attached) return;
        access = accessGranted;
        if (packageName.isEmpty()) { settle(MediaSourceState.Availability.UNCONFIGURED, null); return; }
        if (!accessGranted) { settle(MediaSourceState.Availability.PERMISSION_NEEDED, null); return; }
        List<SessionObservation> samePackage = new ArrayList<>();
        if (sessions != null) for (SessionObservation session : sessions) {
            if (session != null && session.token != null && packageName.equals(session.packageName))
                samePackage.add(session);
        }
        DirectSessionSelector.Selection selection = DirectSessionSelector.select(samePackage, pinnedToken);
        switch (selection.kind) {
            case ONE:
                settle(MediaSourceState.Availability.LIVE, selection.session);
                break;
            case AMBIGUOUS:
                settle(MediaSourceState.Availability.AMBIGUOUS_SOURCE, null);
                break;
            default:
                settle(liveThisEpoch ? MediaSourceState.Availability.SOURCE_LOST
                        : MediaSourceState.Availability.NO_SESSION, null);
        }
    }

    /** A state or metadata event from one session. Ignored unless it is the pinned session. */
    public void onSessionChanged(SessionObservation observation) {
        if (!attached || observation == null || pinnedToken == null
                || !pinnedToken.equals(observation.token)
                || !packageName.equals(observation.packageName)) return;
        current = observation;
        resolvePending(observation);
        publish();
    }

    public MediaSourceState state() {
        if (!attached) {
            return MediaSourceState.unavailable(key, MediaEngine.DIRECT, "",
                    MediaSourceState.Availability.UNCONFIGURED, generation);
        }
        if (availability != MediaSourceState.Availability.LIVE || current == null) {
            return MediaSourceState.unavailable(key, MediaEngine.DIRECT, packageName, availability, generation);
        }
        return new MediaSourceState(key, MediaEngine.DIRECT, packageName, availability,
                current.nativeState, current.actions | labels.qualifiedExtraActions(packageName),
                current.token, current.itemId, current.title, current.subtitle, generation);
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
        MediaCommandResult unavailable = unavailableResult();
        if (unavailable != null) { deliver(callback, unavailable); return; }

        DirectOperation operation = action == MediaUserAction.NEXT ? DirectOperation.NEXT
                : action == MediaUserAction.PREVIOUS ? DirectOperation.PREVIOUS
                : DirectOperation.resolvePlayPause(current.nativeState);
        if (operation.directional() && pendingToggle != null) {
            // A second tap before the first is confirmed joins it; it is never a second dispatch.
            if (callback != null) pendingToggle.callbacks.add(callback);
            return;
        }
        long effectiveActions = current.actions | labels.qualifiedExtraActions(packageName);
        if ((effectiveActions & operation.supportingActions()) == 0L) {
            deliver(callback, new MediaCommandResult(MediaCommandResult.Outcome.UNSUPPORTED,
                    labels.appLabel(packageName) + " does not offer " + describe(operation) + " right now",
                    current.nativeState == PlaybackState.STATE_PLAYING, false));
            return;
        }

        Object token = pinnedToken;
        Pending pending = new Pending(++nextOpId, token, generation, operation, itemIdentity(current));
        if (callback != null) pending.callbacks.add(callback);
        if (!transport.dispatch(token, operation)) {
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
        long effectiveActions = current.actions | labels.qualifiedExtraActions(packageName);
        if ((effectiveActions & DirectOperation.PAUSE.supportingActions()) == 0L) return;
        if (pendingToggle != null) {
            finish(pendingToggle, new MediaCommandResult(MediaCommandResult.Outcome.SENT_UNCONFIRMED,
                    "Superseded by a source switch", false, true));
        }
        transport.dispatch(pinnedToken, DirectOperation.PAUSE);
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
            failAllPending(MediaCommandResult.Outcome.SOURCE_LOST,
                    labels.appLabel(packageName) + " session ended");
        }
        pinnedToken = nextToken;
        current = session;
        availability = next;
        if (session != null) {
            liveThisEpoch = true;
            resolvePending(session);
        }
        publish();
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
            default: return "Previous";
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
