package com.cbkii.ts18launcher;

import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;

/** Immutable evidence collected off-main; no token-only or placeholder-only readiness. */
final class StartupMediaObservation {
    static final StartupMediaObservation UNKNOWN = new StartupMediaObservation(false, false, false);
    final boolean session, ready, playing;
    StartupMediaObservation(boolean session, boolean ready, boolean playing) {
        this.session = session; this.ready = ready; this.playing = playing;
    }
    static StartupMediaObservation read(String pkg, MediaController controller) {
        try {
            PlaybackState state = controller.getPlaybackState();
            int value = state == null ? PlaybackState.STATE_NONE : state.getState();
            boolean playing = value == PlaybackState.STATE_PLAYING || value == PlaybackState.STATE_BUFFERING
                    || value == PlaybackState.STATE_CONNECTING;
            boolean playable = MediaCommandPolicy.controllerReadyForPlay(value, state == null ? 0L : state.getActions());
            java.util.List<android.media.session.MediaSession.QueueItem> queue = controller.getQueue();
            MediaMetadata metadata = controller.getMetadata();
            boolean content = queue != null && !queue.isEmpty();
            if (metadata != null) content |= meaningful(pkg, metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID))
                    || meaningful(pkg, metadata.getString(MediaMetadata.METADATA_KEY_TITLE))
                    || meaningful(pkg, metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE));
            return new StartupMediaObservation(true, playable && content, playing);
        } catch (RuntimeException unavailable) { return UNKNOWN; }
    }
    static boolean meaningful(String pkg, String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String value = text.trim();
        if (MediaSourceAdapter.NAVRADIO_PACKAGE.equals(pkg))
            return !"radio".equalsIgnoreCase(value) && !"navradio+".equalsIgnoreCase(value);
        if (MediaSourceAdapter.AUXIO_PACKAGE.equals(pkg))
            return !"music".equalsIgnoreCase(value) && !"auxio".equalsIgnoreCase(value);
        return true;
    }
}
