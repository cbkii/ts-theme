package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.media.session.PlaybackState;

import com.cbkii.ts18launcher.media.MediaEngine;
import com.cbkii.ts18launcher.media.MediaSourceKey;
import com.cbkii.ts18launcher.media.MediaSourceState;
import com.cbkii.ts18launcher.media.MediaSourceState.Availability;

import org.junit.Test;

/** Integration review F10: bounded same-package metadata continuity across a reconnect gap. */
public class DirectMetadataRetentionTest {
    private static MediaSourceState live(String pkg, String title) {
        return new MediaSourceState(MediaSourceKey.MUSIC, MediaEngine.DIRECT, pkg, Availability.LIVE,
                PlaybackState.STATE_PLAYING, PlaybackState.ACTION_PAUSE, "token", "id", title, "Artist", 3L);
    }
    private static MediaSourceState absent(String pkg, Availability availability) {
        return MediaSourceState.unavailable(MediaSourceKey.MUSIC, MediaEngine.DIRECT, pkg, availability, 4L);
    }

    @Test public void reconnectGapKeepsTextButNeverClaimsPlaybackOrControls() {
        DirectMetadataRetention retention = new DirectMetadataRetention();
        retention.present(live("com.tw.media", "Song"), 0L);
        MediaSourceState shown = retention.present(absent("com.tw.media", Availability.SOURCE_LOST), 100L);
        assertEquals("Song", shown.title);
        assertFalse(shown.playing());
        assertEquals(0L, shown.actions);
        assertEquals(DirectMetadataRetention.RETAIN_MS - 400L, retention.remainingMs(500L));
    }

    @Test public void genuineLossIsShownAfterTheBoundedGap() {
        DirectMetadataRetention retention = new DirectMetadataRetention();
        retention.present(live("com.tw.media", "Song"), 0L);
        retention.present(absent("com.tw.media", Availability.SOURCE_LOST), 100L);
        MediaSourceState after = absent("com.tw.media", Availability.SOURCE_LOST);
        assertSame(after, retention.present(after, 100L + DirectMetadataRetention.RETAIN_MS));
        assertEquals(-1L, retention.remainingMs(100L + DirectMetadataRetention.RETAIN_MS));
    }

    @Test public void packageChangeOrUnconfiguredClearsImmediately() {
        DirectMetadataRetention retention = new DirectMetadataRetention();
        retention.present(live("com.tw.media", "Song"), 0L);
        MediaSourceState other = absent("other.player", Availability.NO_SESSION);
        assertSame(other, retention.present(other, 10L));
        retention.present(live("com.tw.media", "Song"), 20L);
        MediaSourceState unconfigured = absent("com.tw.media", Availability.UNCONFIGURED);
        assertSame(unconfigured, retention.present(unconfigured, 30L));
    }

    @Test public void newLiveItemReplacesRetainedText() {
        DirectMetadataRetention retention = new DirectMetadataRetention();
        retention.present(live("com.tw.media", "Old"), 0L);
        retention.present(absent("com.tw.media", Availability.NO_SESSION), 10L);
        assertEquals("New", retention.present(live("com.tw.media", "New"), 20L).title);
        assertTrue(retention.remainingMs(30L) < 0L);
    }
}
