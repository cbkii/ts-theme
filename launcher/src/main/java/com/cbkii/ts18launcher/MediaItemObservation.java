package com.cbkii.ts18launcher;

import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import java.util.List;
import java.util.Objects;

/** Bounded, off-main read of one controller. Callback payloads are invalidation signals only. */
final class MediaItemObservation {
    private MediaItemObservation() { }

    static MediaListenerService.Snapshot read(MediaController controller, long generation, long now) {
        return read(controller, generation, now, false);
    }

    static MediaListenerService.Snapshot read(MediaController controller, long generation, long now, boolean radio) {
        if (controller == null) return new MediaListenerService.Snapshot("", "", "", false);
        String pkg = "";
        Object token = null;
        try {
            pkg = controller.getPackageName();
            token = controller.getSessionToken();
            PlaybackState before = controller.getPlaybackState();
            MediaMetadata metadata = controller.getMetadata();
            List<MediaSession.QueueItem> queue = controller.getQueue();
            MediaMetadata afterMetadata = controller.getMetadata();
            PlaybackState after = controller.getPlaybackState();
            long queueId = queueId(after);
            String id = text(metadata, MediaMetadata.METADATA_KEY_MEDIA_ID);
            boolean coherent = queueId(before) == queueId
                    && state(before) == state(after)
                    && Objects.equals(id, text(afterMetadata, MediaMetadata.METADATA_KEY_MEDIA_ID))
                    && Objects.equals(text(metadata, MediaMetadata.METADATA_KEY_TITLE),
                            text(afterMetadata, MediaMetadata.METADATA_KEY_TITLE))
                    && Objects.equals(text(metadata, MediaMetadata.METADATA_KEY_ARTIST),
                            text(afterMetadata, MediaMetadata.METADATA_KEY_ARTIST));
            boolean verified = coherent && !id.isEmpty();
            if (verified && queueId != MediaSession.QueueItem.UNKNOWN_ID) {
                MediaDescription current = null;
                if (queue != null) for (MediaSession.QueueItem item : queue) {
                    if (item != null && item.getQueueId() == queueId) { current = item.getDescription(); break; }
                }
                verified = current != null && matches(pkg, id, metadata, current);
            }
            // Radio's station contract is separate from individual music items. Never retain text.
            boolean station = radio || RadioFrequency.NAVRADIO.equals(pkg);
            String title = coherent && (verified || station) ? first(metadata,
                    MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE) : "";
            String artist = coherent && (verified || station) ? first(metadata,
                    MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                    MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) : "";
            if (RadioFrequency.NAVRADIO.equals(pkg) && coherent) artist = MediaListenerService.radioDetails(metadata, artist);
            return new MediaListenerService.Snapshot(pkg, title, artist,
                    coherent ? state(after) : PlaybackState.STATE_NONE,
                    coherent && after != null ? after.getActions() : 0L, token,
                    coherent ? id : "", queueId, generation, now, verified);
        } catch (RuntimeException unavailable) {
            return new MediaListenerService.Snapshot(pkg, "", "", PlaybackState.STATE_NONE, 0L,
                    token, "", -1L, generation, now, false);
        }
    }

    private static boolean matches(String pkg, String id, MediaMetadata metadata, MediaDescription item) {
        String queueMediaId = item.getMediaId();
        if (id.equals(queueMediaId)) return true;
        // Paired Auxio publishes raw song UID in metadata and MediaSessionUID.SingleItem in queue.
        // This is an exact, repository-verified adapter contract, never title/artist matching.
        if ("com.tw.media".equals(pkg) && ("mi" + id).equals(queueMediaId)) return true;
        String uri = text(metadata, MediaMetadata.METADATA_KEY_MEDIA_URI);
        return !uri.isEmpty() && item.getMediaUri() != null && uri.equals(item.getMediaUri().toString());
    }

    private static int state(PlaybackState state) { return state == null ? PlaybackState.STATE_NONE : state.getState(); }
    private static long queueId(PlaybackState state) {
        return state == null ? MediaSession.QueueItem.UNKNOWN_ID : state.getActiveQueueItemId();
    }
    private static String text(MediaMetadata metadata, String key) {
        if (metadata == null) return "";
        CharSequence value = metadata.getText(key);
        return value == null ? "" : value.toString().trim();
    }
    private static String first(MediaMetadata metadata, String... keys) {
        for (String key : keys) { String value = text(metadata, key); if (!value.isEmpty()) return value; }
        return "";
    }
}
