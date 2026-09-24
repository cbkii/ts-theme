package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MediaMetadataPolicyTest {
    @Test public void blankTitleFallsThroughToDisplayTitle() {
        assertEquals("Display title",
                MediaMetadataPolicy.firstNonBlank("  ", "Display title"));
    }

    @Test public void blankArtistFallsThroughThroughArtistHierarchy() {
        assertEquals("Album artist",
                MediaMetadataPolicy.firstNonBlank("", "Album artist", "Display subtitle"));
        assertEquals("Display subtitle",
                MediaMetadataPolicy.firstNonBlank("", " ", "Display subtitle"));
    }

    @Test public void valuesAreTrimmedAndAllBlankReturnsEmpty() {
        assertEquals("Track", MediaMetadataPolicy.firstNonBlank("  Track  "));
        assertEquals("", MediaMetadataPolicy.firstNonBlank("", " ", null));
    }
}
