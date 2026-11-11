package com.cbkii.ts18launcher.media.direct;

import static org.junit.Assert.assertEquals;

import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;

/** API 29 value mapping from one real controller reading to one observation. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, manifest = Config.NONE, application = android.app.Application.class,
        shadows = FrameworkSessionsReadTest.ControllerBoundary.class)
public class FrameworkSessionsReadTest {
    @Implements(MediaController.class)
    public static class ControllerBoundary {
        String packageName = "com.tw.media";
        MediaMetadata metadata;
        PlaybackState state;
        boolean dead;
        @Implementation protected String getPackageName() { return packageName; }
        @Implementation protected MediaMetadata getMetadata() {
            if (dead) throw new IllegalStateException("session destroyed");
            return metadata;
        }
        @Implementation protected PlaybackState getPlaybackState() { return state; }
    }

    private final MediaController controller = Shadow.newInstanceOf(MediaController.class);
    private final ControllerBoundary boundary = Shadow.extract(controller);

    private static PlaybackState playback(int state, long actions, long queueId) {
        return new PlaybackState.Builder().setState(state, 0L, 1f).setActions(actions)
                .setActiveQueueItemId(queueId).build();
    }

    @Test public void readsStateActionsAndItemFromTheControllerOnly() {
        boundary.state = playback(PlaybackState.STATE_BUFFERING,
                PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE, 7L);
        boundary.metadata = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "track-9")
                .putString(MediaMetadata.METADATA_KEY_TITLE, "  Song  ")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Artist").build();
        SessionObservation observation = FrameworkSessions.read("token", controller);
        assertEquals("token", observation.token);
        assertEquals("com.tw.media", observation.packageName);
        assertEquals(PlaybackState.STATE_BUFFERING, observation.nativeState);
        assertEquals(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE, observation.actions);
        assertEquals("track-9", observation.itemId);
        assertEquals("Song", observation.title);
        assertEquals("Artist", observation.subtitle);
    }

    @Test public void displayFieldsAndQueueIdAreFallbacksNotInventions() {
        boundary.state = playback(PlaybackState.STATE_PLAYING, 0L, 42L);
        boundary.metadata = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, "Station")
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, "105.7 FM").build();
        SessionObservation observation = FrameworkSessions.read("token", controller);
        assertEquals("queue:42", observation.itemId);
        assertEquals("Station", observation.title);
        assertEquals("105.7 FM", observation.subtitle);
    }

    @Test public void emptySessionReadsAsIdleWithNoText() {
        SessionObservation observation = FrameworkSessions.read("token", controller);
        assertEquals(PlaybackState.STATE_NONE, observation.nativeState);
        assertEquals(0L, observation.actions);
        assertEquals("", observation.itemId);
        assertEquals("", observation.title);
    }

    @Test public void sessionDyingMidReadNeverLeaksPartialText() {
        boundary.state = playback(PlaybackState.STATE_PLAYING, PlaybackState.ACTION_PAUSE, 1L);
        boundary.dead = true;
        SessionObservation observation = FrameworkSessions.read("token", controller);
        assertEquals(PlaybackState.STATE_NONE, observation.nativeState);
        assertEquals(0L, observation.actions);
        assertEquals("", observation.title);
    }
    @Test public void nullCustomActionDoesNotEraseTheOwningSessionsPlaybackState() {
        boundary.state = playback(PlaybackState.STATE_PLAYING, PlaybackState.ACTION_PAUSE, 1L);
        org.robolectric.util.ReflectionHelpers.setField(boundary.state, "mCustomActions",
                java.util.Arrays.asList((PlaybackState.CustomAction) null));
        SessionObservation observation = FrameworkSessions.read("token", controller);
        assertEquals(PlaybackState.STATE_PLAYING, observation.nativeState);
        assertEquals(PlaybackState.ACTION_PAUSE, observation.actions);
        assertEquals(false, observation.deadlinePlay);
    }

}
