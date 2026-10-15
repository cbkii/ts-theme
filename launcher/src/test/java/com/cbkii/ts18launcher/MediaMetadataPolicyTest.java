package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class MediaMetadataPolicyTest {
    @Test public void identicalOneSecondSnapshotsDoNotResetPrimary() {
        MediaMetadataPolicy.Change change = MediaMetadataPolicy.update(
                "Long primary title", "Artist", "music:com.tw.media", "music:com.tw.media",
                "Long primary title", "Artist");
        assertFalse(change.primaryChanged);
        assertFalse(change.secondaryChanged);
    }

    @Test public void playbackOnlyOrArtistOnlyChangesDoNotResetPrimary() {
        MediaMetadataPolicy.Change artistChange = MediaMetadataPolicy.update(
                "Track", "Artist A", "music:com.tw.media", "music:com.tw.media",
                "Track", "Artist B");
        assertFalse(artistChange.primaryChanged);
        assertTrue(artistChange.secondaryChanged);
    }

    @Test public void primaryTitleChangeResetsPrimary() {
        MediaMetadataPolicy.Change change = MediaMetadataPolicy.update(
                "Track A", "Artist", "music:com.tw.media", "music:com.tw.media",
                "Track B", "Artist");
        assertTrue(change.primaryChanged);
        assertFalse(change.secondaryChanged);
    }

    @Test public void sourceChangeWithSameTitleResetsPrimary() {
        MediaMetadataPolicy.Change change = MediaMetadataPolicy.update(
                "Shared title", "Context", "music:com.tw.media", "radio:com.navimods.radio",
                "Shared title", "Context");
        assertTrue(change.primaryChanged);
        assertFalse(change.secondaryChanged);
    }

    @Test public void blankTitleFallsThroughToDisplayTitle() {
        assertEquals("Display title",
                MediaMetadataPolicy.firstNonBlank("   ", "Display title"));
    }

    @Test public void blankArtistFallsThroughExistingSubtitleHierarchy() {
        assertEquals("Album artist",
                MediaMetadataPolicy.firstNonBlank("", "Album artist", "Display subtitle"));
        assertEquals("Display subtitle",
                MediaMetadataPolicy.firstNonBlank(" ", " ", "Display subtitle"));
    }

    @Test public void firstEmptyRenderStillInitialisesSecondaryVisibilityState() {
        MediaMetadataPolicy.Change change = MediaMetadataPolicy.update(null, null, "", "");
        assertTrue(change.primaryChanged);
        assertTrue(change.secondaryChanged);
    }
}
