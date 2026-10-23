package com.cbkii.ts18launcher;

import static org.junit.Assert.*;
import android.content.Context;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;

/** API29 parcel/value contracts and the real listener lifecycle. Binder scheduling is covered by
 * the controlled full-observer host test; OEM access/reconnection still requires physical testing. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, manifest = Config.NONE, application = android.app.Application.class,
        shadows = MediaObservationApi29Test.ControllerBoundary.class)
public class MediaObservationApi29Test {
    @Implements(MediaController.class)
    public static class ControllerBoundary {
        MediaMetadata metadata;
        PlaybackState state;
        List<MediaSession.QueueItem> queue;
        @Implementation protected String getPackageName() { return "com.tw.media"; }
        @Implementation protected MediaSession.Token getSessionToken() { return null; }
        @Implementation protected MediaMetadata getMetadata() { return metadata; }
        @Implementation protected PlaybackState getPlaybackState() { return state; }
        @Implementation protected List<MediaSession.QueueItem> getQueue() { return queue; }
    }
    private static PlaybackState state(int value, long queueId) {
        return new PlaybackState.Builder().setState(value, 0L, 1f)
                .setActiveQueueItemId(queueId).setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE).build();
    }
    private static MediaMetadata metadata(String id, String title) {
        return new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID, id)
                .putString(MediaMetadata.METADATA_KEY_TITLE, title).build();
    }
    @After public void clean() {
        ProcessMediaSessionMonitor.stop();
        ReflectionHelpers.setStaticField(MediaListenerService.class, "instance", null);
        ReflectionHelpers.setStaticField(MediaListenerService.class, "lastGeneric", new MediaListenerService.Snapshot("", "", "", false));
        ReflectionHelpers.setStaticField(MediaListenerService.class, "lastRadio", new MediaListenerService.Snapshot("", "", "", false));
    }
    @Test public void automaticTransitionRejectsOldArtworkAgainstCurrentQueue() {
        MediaController controller = Shadow.newInstanceOf(MediaController.class);
        ControllerBoundary boundary = Shadow.extract(controller);
        boundary.queue = Arrays.asList(
                new MediaSession.QueueItem(new MediaDescription.Builder().setMediaId("mia").build(), 1),
                new MediaSession.QueueItem(new MediaDescription.Builder().setMediaId("mib").build(), 2));
        boundary.metadata = metadata("a", "A"); boundary.state = state(PlaybackState.STATE_PLAYING, 1);
        assertEquals("A", MediaItemObservation.read(controller, 7, 100).title);
        boundary.metadata = null; boundary.state = state(PlaybackState.STATE_PLAYING, 2);
        assertEquals("", MediaItemObservation.read(controller, 7, 101).title);
        boundary.metadata = metadata("b", "B");
        MediaListenerService.Snapshot current = MediaItemObservation.read(controller, 7, 102);
        assertEquals("B", current.title); assertEquals("b", current.mediaId); assertEquals(2, current.queueItemId);
        boundary.metadata = metadata("a", "A late art");
        assertEquals("", MediaItemObservation.read(controller, 7, 103).title);
    }
    @Test public void disconnectInvalidatesRetainedListenerStateAndNewObserverReplay() {
        MediaListenerService service = Robolectric.buildService(MediaListenerService.class).create().get();
        ReflectionHelpers.setStaticField(MediaListenerService.class, "instance", service);
        ReflectionHelpers.setField(service, "connected", true);
        MediaListenerService.Snapshot stale = new MediaListenerService.Snapshot("com.tw.media", "A", "Artist", true);
        ReflectionHelpers.setStaticField(MediaListenerService.class, "lastGeneric", stale);
        ReflectionHelpers.setStaticField(MediaListenerService.class, "lastRadio", stale);
        service.onListenerDisconnected();
        assertFalse(MediaListenerService.isConnected());
        final MediaListenerService.Snapshot[] replay = { stale, stale };
        MediaListenerService.Observer observer = (music, radio) -> { replay[0] = music; replay[1] = radio; };
        MediaListenerService.addObserver(observer);
        assertTrue(replay[0].isEmpty()); assertTrue(replay[1].isEmpty()); assertFalse(replay[0].playing);
        MediaListenerService.removeObserver(observer);
        service.onDestroy();
    }
    @Test public void destructionOfOldServiceDoesNotEraseNewConnection() {
        MediaListenerService old = Robolectric.buildService(MediaListenerService.class).create().get();
        MediaListenerService current = Robolectric.buildService(MediaListenerService.class).create().get();
        MediaListenerService.Snapshot live = new MediaListenerService.Snapshot("com.tw.media", "B", "", true);
        ReflectionHelpers.setStaticField(MediaListenerService.class, "instance", current);
        ReflectionHelpers.setStaticField(MediaListenerService.class, "lastGeneric", live);
        old.onDestroy();
        assertSame(live, ReflectionHelpers.getStaticField(MediaListenerService.class, "lastGeneric"));
        current.onDestroy();
        assertTrue(((MediaListenerService.Snapshot) ReflectionHelpers.getStaticField(MediaListenerService.class, "lastGeneric")).isEmpty());
    }
    @Test public void nativeLoadingStatesRemainInterruptibleWithoutClaimingPlayback() {
        for (int value : new int[] { PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING }) {
            MediaListenerService.Snapshot snapshot = new MediaListenerService.Snapshot("music", "", "", value, PlaybackState.ACTION_PAUSE, new Object());
            assertFalse(snapshot.playing); assertTrue(snapshot.supports(MediaListenerService.Command.PLAY_PAUSE));
            assertEquals(MediaCommandPolicy.Desired.PAUSE, MediaCommandPolicy.resolve(MediaListenerService.Command.PLAY_PAUSE, MediaCommandPolicy.usesPauseAction(value)));
            assertNotEquals(R.drawable.ic_play, MediaPlaybackPresentation.icon(value));
            assertNotEquals(R.drawable.ic_pause, MediaPlaybackPresentation.icon(value));
        }
    }
}
