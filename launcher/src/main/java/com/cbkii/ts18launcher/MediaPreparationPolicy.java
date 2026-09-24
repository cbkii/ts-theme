package com.cbkii.ts18launcher;

/** Pure preparation/race rules used by the Android coordinator and JVM tests. */
final class MediaPreparationPolicy {
    enum RootOutcome { SUCCESS, DENIED_OR_FAILED, TIMEOUT, UNAVAILABLE }
    enum NextRoute { ROOT_ACCEPTED, NORMAL_FALLBACK, FAIL }

    private MediaPreparationPolicy() {}

    static NextRoute afterRoot(RootOutcome outcome, boolean normalFallbackAvailable) {
        if (outcome == RootOutcome.SUCCESS) return NextRoute.ROOT_ACCEPTED;
        return normalFallbackAvailable ? NextRoute.NORMAL_FALLBACK : NextRoute.FAIL;
    }

    static boolean generationActive(boolean destroyed, int expectedGeneration,
                                    int actualGeneration, long nowMs, long deadlineMs) {
        return !destroyed && expectedGeneration == actualGeneration && nowMs < deadlineMs;
    }

    static boolean shouldDispatch(boolean settled, boolean alreadyDispatched,
                                  boolean generationActive, boolean controllerCapable) {
        return !settled && !alreadyDispatched && generationActive && controllerCapable;
    }

    static boolean shouldCommitOppositePause(boolean playAcknowledged,
                                             boolean oppositeStillPlaying,
                                             boolean samePackage) {
        return playAcknowledged && oppositeStillPlaying && !samePackage;
    }

    static boolean shouldCoalesce(MediaCommandPolicy.Desired existing,
                                  MediaCommandPolicy.Desired incoming,
                                  boolean settled, boolean dispatched) {
        return !settled && !dispatched && existing != null && existing == incoming
                && (incoming == MediaCommandPolicy.Desired.PLAY
                || incoming == MediaCommandPolicy.Desired.PAUSE);
    }
}
