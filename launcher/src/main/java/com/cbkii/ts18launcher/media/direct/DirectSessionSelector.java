package com.cbkii.ts18launcher.media.direct;

import android.media.session.PlaybackState;

import java.util.ArrayList;
import java.util.List;

/**
 * Chooses the one session that represents a configured package, or refuses to choose.
 *
 * Input is already restricted to the exact configured package; sessions of any other app are
 * never candidates. Order of evidence: real playback activity, then the previously pinned token,
 * then advertised transport capability. If that still leaves more than one, the answer is
 * AMBIGUOUS rather than a guess.
 */
final class DirectSessionSelector {
    enum Kind { NONE, ONE, AMBIGUOUS }

    static final class Selection {
        final Kind kind;
        final SessionObservation session;
        private Selection(Kind kind, SessionObservation session) { this.kind = kind; this.session = session; }
    }

    private static final Selection NONE = new Selection(Kind.NONE, null);
    private static final Selection AMBIGUOUS = new Selection(Kind.AMBIGUOUS, null);
    private static final long TRANSPORT_ACTIONS = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
            | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
            | PlaybackState.ACTION_SKIP_TO_PREVIOUS;

    private DirectSessionSelector() {}

    static Selection select(List<SessionObservation> samePackage, Object pinnedToken) {
        if (samePackage == null || samePackage.isEmpty()) return NONE;
        if (samePackage.size() == 1) return one(samePackage.get(0));

        List<SessionObservation> active = new ArrayList<>();
        List<SessionObservation> controllable = new ArrayList<>();
        SessionObservation pinned = null;
        for (SessionObservation session : samePackage) {
            if (active(session.nativeState)) active.add(session);
            if ((session.actions & TRANSPORT_ACTIONS) != 0L) controllable.add(session);
            if (pinnedToken != null && pinnedToken.equals(session.token)) pinned = session;
        }
        if (active.size() == 1) return one(active.get(0));
        if (active.size() > 1) return pinned != null && active.contains(pinned) ? one(pinned) : AMBIGUOUS;
        if (pinned != null) return one(pinned);
        if (controllable.size() == 1) return one(controllable.get(0));
        return AMBIGUOUS;
    }

    private static Selection one(SessionObservation session) { return new Selection(Kind.ONE, session); }

    private static boolean active(int state) {
        return state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
                || state == PlaybackState.STATE_CONNECTING || state == PlaybackState.STATE_FAST_FORWARDING
                || state == PlaybackState.STATE_REWINDING || state == PlaybackState.STATE_SKIPPING_TO_NEXT
                || state == PlaybackState.STATE_SKIPPING_TO_PREVIOUS
                || state == PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM;
    }
}
