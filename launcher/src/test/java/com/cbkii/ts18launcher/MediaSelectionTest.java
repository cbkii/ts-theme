package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

public class MediaSelectionTest {
    @Test public void explicitSourceWinsWhenBothSessionsPlay() {
        MediaSelection selection = new MediaSelection(MediaSelection.MUSIC);
        assertEquals(MediaSelection.MUSIC, selection.reconcile(true, true));
        selection.select(MediaSelection.RADIO);
        assertEquals(MediaSelection.RADIO, selection.reconcile(true, true));
    }

    @Test public void solePlayingAuthorityMayBecomeVisibleButInactiveStateDoesNotFlicker() {
        MediaSelection selection = new MediaSelection(MediaSelection.RADIO);
        assertEquals(MediaSelection.MUSIC, selection.reconcile(false, true));
        assertEquals(MediaSelection.RADIO, selection.reconcile(false, false));
    }

    @Test public void rememberedUsablePackagePrecedesOtherActiveFallback() {
        assertEquals(1, MediaSelection.pick(Arrays.asList(
                new MediaSelection.Candidate("other", true, true, true, false),
                new MediaSelection.Candidate("remembered", true, true, false, true)),
                "configured", false, "remembered"));
    }

    @Test public void configuredPreferencePrecedesRememberedPackageWhenActive() {
        assertEquals(0, MediaSelection.pick(Arrays.asList(
                new MediaSelection.Candidate("configured", true, true, true, false),
                new MediaSelection.Candidate("remembered", true, true, true, false)),
                "configured", true, "remembered"));
    }
}
