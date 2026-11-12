package com.cbkii.ts18launcher.media;

/**
 * One control engine. A backend acts only for sources that are currently attached to it.
 *
 * Contract for every implementation: after detach, the backend must not command,
 * prepare, or publish state for that source until it is attached again.
 */
public interface MediaBackend {
    interface ResultCallback { void onResult(MediaCommandResult result); }

    interface StateListener { void onMediaSourceStateChanged(MediaSourceState state); }

    MediaEngine engine();

    /** Select this backend for key with the exact configured package (may be empty). */
    void attach(MediaSourceKey key, String packageName);

    /** Deselect this backend for key; it must go quiet for that source. */
    void detach(MediaSourceKey key);

    boolean attached(MediaSourceKey key);

    MediaSourceState state(MediaSourceKey key);

    /**
     * True only when packageName, addressed as the attached source, is observed in the
     * native PLAYING state. A backend that pins one package returns false for any other.
     */
    boolean playing(MediaSourceKey key, String packageName);

    /**
     * Perform one driver action. packageName is the package the caller believes it is
     * addressing; a backend that pins a configured package must refuse a mismatch.
     */
    void command(MediaSourceKey key, String packageName, MediaUserAction action,
                 ResultCallback callback);

    /** Continuation of one admitted action; a supporting backend retains its original deadline. */
    default void commandUntil(MediaSourceKey key, String packageName, MediaUserAction action,
                              long deadlineElapsedMs, ResultCallback callback) {
        if (deadlineElapsedMs != Long.MAX_VALUE) {
            if (callback != null) callback.onResult(new MediaCommandResult(
                    MediaCommandResult.Outcome.FAILED, "Backend does not support bounded commands", false, false));
            return;
        }
        command(key, packageName, action, callback);
    }

    /** Pause this backend's attached source once, only if it is observed playing. */
    void pauseForHandover(MediaSourceKey key);

    /**
     * Passive readiness for one epoch (boot, wake or HOME return): bring the source to a loaded,
     * paused queue through its own contract without playing, focusing or opening it. At most one
     * attempt per source per epoch. Engines without such a path ignore it.
     */
    default void preparePassive(MediaSourceKey key, String packageName, long epoch) { }

    void setStateListener(StateListener listener);

    /** Re-read live state now (for example when HOME returns). Engines without a pull path ignore it. */
    default void refresh() { }
}
