package com.cbkii.ts18launcher.media;

/** Outcome of exactly one driver action against exactly one source. Immutable. */
public final class MediaCommandResult {
    public enum Outcome {
        /** The owning session reported the requested directional state. */
        ACKNOWLEDGED,
        /** One command was dispatched; no confirming state arrived before the deadline. */
        SENT_UNCONFIRMED,
        /** The source does not advertise the action; nothing was dispatched. */
        UNSUPPORTED,
        /** The pinned session disappeared or changed before or after dispatch. */
        SOURCE_LOST,
        /** Nothing could be dispatched (no session, no access, ambiguous source, ...). */
        FAILED
    }

    public final Outcome outcome;
    /** Short driver-facing explanation; empty when the outcome needs none. */
    public final String message;
    /** True only when the source itself was observed in the native PLAYING state. */
    public final boolean playingObserved;
    /** True when a transport command actually left the launcher. */
    public final boolean dispatched;

    public MediaCommandResult(Outcome outcome, String message, boolean playingObserved,
                              boolean dispatched) {
        this.outcome = outcome;
        this.message = message == null ? "" : message;
        this.playingObserved = playingObserved;
        this.dispatched = dispatched;
    }

    /** The UI may treat the action as settled without showing an error. */
    public boolean settledWithoutError() {
        return outcome == Outcome.ACKNOWLEDGED || outcome == Outcome.SENT_UNCONFIRMED;
    }

    /**
     * A Play was sent but the source never reported playing within its window. Settled without a
     * launcher error, yet the driver must be told rather than left in silence. A Play superseded
     * by the driver's own later intent (direction change, source switch) is not an expiry.
     */
    public boolean silentPlayExpiry() {
        return outcome == Outcome.SENT_UNCONFIRMED && dispatched && !playingObserved
                && (message.isEmpty() || "Play window expired".equals(message));
    }

    @Override public String toString() {
        return outcome + (message.isEmpty() ? "" : "(" + message + ")")
                + " playing=" + playingObserved + " dispatched=" + dispatched;
    }
}
