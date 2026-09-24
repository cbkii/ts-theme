package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MediaMetadataPolicyTest {
    @Test public void blankPrimaryFallsThroughToDisplayTitle() {
        assertEquals("Display title",
                MediaMetadataPolicy.firstNonBlank("   ", "Display title"));
    }

    @Test public void blankArtistFallsThroughToAlbumArtistThenSubtitle() {
        assertEquals("Album artist",
                MediaMetadataPolicy.firstNonBlank("", "Album artist", "Subtitle"));
        assertEquals("Subtitle",
                MediaMetadataPolicy.firstNonBlank(" ", "  ", "Subtitle"));
    }

    @Test public void tokenDedupUsesRealTokenEquality() {
        Object token = new Object();
        assertTrue(MediaMetadataPolicy.sameToken(token, token));
        assertFalse(MediaMetadataPolicy.sameToken(token, new Object()));
        assertFalse(MediaMetadataPolicy.sameToken(null, null));
    }

    @Test public void playbackPositionOutsideSnapshotDoesNotCauseSemanticChange() {
        assertFalse(MediaMetadataPolicy.semanticSnapshotChanged(
                "pkg", "Title", "Artist", 3, 7L,
                "pkg", "Title", "Artist", 3, 7L));
        assertTrue(MediaMetadataPolicy.semanticSnapshotChanged(
                "pkg", "Title", "Artist", 3, 7L,
                "pkg", "Other", "Artist", 3, 7L));
    }
}
