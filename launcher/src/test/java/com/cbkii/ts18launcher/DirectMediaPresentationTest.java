package com.cbkii.ts18launcher;

import static org.junit.Assert.*;
import android.media.session.PlaybackState;
import com.cbkii.ts18launcher.media.*;
import org.junit.Test;

public class DirectMediaPresentationTest {
    private MediaSourceState live(int state, long actions) {
        return new MediaSourceState(MediaSourceKey.MUSIC, MediaEngine.DIRECT, "com.tw.media",
                MediaSourceState.Availability.LIVE, state, actions, "token", "track", "Title", "Artist", 1L);
    }
    @Test public void actionAvailabilityMatchesSourceCapabilities() {
        MediaSourceState absent = MediaSourceState.unavailable(MediaSourceKey.MUSIC, MediaEngine.DIRECT,
                "com.tw.media", MediaSourceState.Availability.NO_SESSION, 1L).withStart(true, false);
        assertTrue(DirectMediaPresentation.playPauseEnabled(absent));
        assertFalse(DirectMediaPresentation.skipEnabled(absent, true));
        assertTrue(DirectMediaPresentation.playPauseEnabled(live(PlaybackState.STATE_NONE, 0L)));
        MediaSourceState paused = live(PlaybackState.STATE_PAUSED, PlaybackState.ACTION_PLAY | PlaybackState.ACTION_SKIP_TO_NEXT);
        assertTrue(DirectMediaPresentation.playPauseEnabled(paused));
        assertTrue(DirectMediaPresentation.skipEnabled(paused, true));
        assertFalse(DirectMediaPresentation.skipEnabled(paused, false));
        assertFalse(DirectMediaPresentation.playPauseEnabled(live(PlaybackState.STATE_PLAYING, PlaybackState.ACTION_PLAY)));
        assertTrue(DirectMediaPresentation.playPauseEnabled(live(PlaybackState.STATE_BUFFERING, PlaybackState.ACTION_PAUSE)));
    }
    @Test public void subtitleChangeIsAlreadyAPresentationChange() {
        MediaSourceState before = live(PlaybackState.STATE_PAUSED, PlaybackState.ACTION_PLAY);
        MediaSourceState after = new MediaSourceState(before.key, before.engine, before.packageName,
                before.availability, before.nativeState, before.actions, before.sessionIdentity,
                before.itemId, before.title, "Hydrated artist", before.generation);
        assertFalse(before.samePresentation(after));
        assertTrue(before.samePresentation(live(PlaybackState.STATE_PAUSED, PlaybackState.ACTION_PLAY)));
    }
}
