package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MediaTickerPolicyTest {
    @Test public void appLabelIsNeverPromotedAsNowPlayingMetadata() {
        MediaTickerPolicy.Display display = MediaTickerPolicy.resolve(
                "NavRadio+", "2CA 1053 AM", "NavRadio+", MediaSelection.RADIO);
        assertEquals("2CA 1053 AM", display.primary);
        assertEquals("", display.secondary);
    }

    @Test public void realTrackAndArtistRemainTwoLevelMetadata() {
        MediaTickerPolicy.Display display = MediaTickerPolicy.resolve(
                "Cannonball", "The Breeders", "Auxio", MediaSelection.MUSIC);
        assertEquals("Cannonball", display.primary);
        assertEquals("The Breeders", display.secondary);
    }

    @Test public void emptySnapshotStaysEmptyInsteadOfShowingAppName() {
        MediaTickerPolicy.Display display = MediaTickerPolicy.resolve(
                "", "", "Auxio", MediaSelection.MUSIC);
        assertEquals("", display.primary);
        assertEquals("", display.secondary);
    }

    @Test public void duplicateAppLabelSecondaryIsDropped() {
        MediaTickerPolicy.Display display = MediaTickerPolicy.resolve(
                "Cannonball", "Auxio", "Auxio", MediaSelection.MUSIC);
        assertEquals("Cannonball", display.primary);
        assertEquals("", display.secondary);
    }
}
