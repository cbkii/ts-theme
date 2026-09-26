package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;

import android.media.session.PlaybackState;

import org.junit.Test;

public final class MediaMetadataStabilityTest {
    @Test public void transientEmptyMetadataKeepsLastValidTextForSameSession() {
        Object token = new Object();
        MediaListenerService.Snapshot previous = new MediaListenerService.Snapshot(
                "com.tw.media", "Track", "Artist", PlaybackState.STATE_PLAYING, 0L, token);
        MediaListenerService.Snapshot next = new MediaListenerService.Snapshot(
                "com.tw.media", "", "", PlaybackState.STATE_PLAYING, 0L, token);
        MediaListenerService.Snapshot stable = MediaListenerService.stabiliseSnapshot(previous, next);
        assertEquals("Track", stable.title);
        assertEquals("Artist", stable.artist);
    }

    @Test public void replacementSessionInSamePackageDoesNotInheritStaleMetadata() {
        MediaListenerService.Snapshot previous = new MediaListenerService.Snapshot(
                "com.tw.media", "Old track", "Old artist", PlaybackState.STATE_PLAYING, 0L,
                new Object());
        MediaListenerService.Snapshot next = new MediaListenerService.Snapshot(
                "com.tw.media", "", "", PlaybackState.STATE_PAUSED, 0L, new Object());
        MediaListenerService.Snapshot stable = MediaListenerService.stabiliseSnapshot(previous, next);
        assertEquals("", stable.title);
        assertEquals("", stable.artist);
    }

    @Test public void genuineSessionRemovalStillClearsMetadata() {
        Object token = new Object();
        MediaListenerService.Snapshot previous = new MediaListenerService.Snapshot(
                "com.tw.media", "Track", "Artist", PlaybackState.STATE_PLAYING, 0L, token);
        MediaListenerService.Snapshot stable = MediaListenerService.stabiliseSnapshot(
                previous, new MediaListenerService.Snapshot("", "", "", false));
        assertEquals("", stable.packageName);
        assertEquals("", stable.title);
    }

    @Test public void radioNotificationProvidesTruthfulFallbackWhenSessionMetadataIsEmpty() {
        Object token = new Object();
        MediaListenerService.Snapshot empty = new MediaListenerService.Snapshot(
                "com.navimods.radio", "", "", PlaybackState.STATE_PLAYING,
                PlaybackState.ACTION_PAUSE, token);
        MediaListenerService.Snapshot enriched =
                MediaListenerService.applyRadioFallback(empty, "ABC Classic", "105.9 FM");
        assertEquals("ABC Classic", enriched.title);
        assertEquals("105.9 FM", enriched.artist);
    }

    @Test public void sessionMetadataWinsOverNotificationFallback() {
        Object token = new Object();
        MediaListenerService.Snapshot session = new MediaListenerService.Snapshot(
                "com.navimods.radio", "Session station", "Session detail",
                PlaybackState.STATE_PLAYING, PlaybackState.ACTION_PAUSE, token);
        MediaListenerService.Snapshot enriched =
                MediaListenerService.applyRadioFallback(session, "Notification", "Fallback");
        assertEquals("Session station", enriched.title);
        assertEquals("Session detail", enriched.artist);
    }

    @Test public void stationSessionGainsMissingFrequencyFromNotification() {
        MediaListenerService.Snapshot session = new MediaListenerService.Snapshot(
                "com.navimods.radio", "ABC Classic", "", PlaybackState.STATE_PLAYING,
                PlaybackState.ACTION_PAUSE, new Object());
        MediaListenerService.Snapshot merged = MediaListenerService.applyRadioFallback(
                session, "ABC Classic", "105.9 FM");
        assertEquals("ABC Classic", merged.title);
        assertEquals("105.9 FM", merged.artist);
    }

    @Test public void sourceLabelIsReplacedWithStationAndChannel() {
        MediaListenerService.Snapshot session = new MediaListenerService.Snapshot(
                "com.navimods.radio", "NavRadio+", "", PlaybackState.STATE_PLAYING,
                PlaybackState.ACTION_PAUSE, new Object());
        MediaListenerService.Snapshot merged = MediaListenerService.applyRadioFallback(
                session, "2CA", "1053 AM");
        assertEquals("2CA", merged.title);
        assertEquals("1053 AM", merged.artist);
    }

    @Test public void duplicateNotificationTextDoesNotCreateDuplicateSecondary() {
        MediaListenerService.Snapshot session = new MediaListenerService.Snapshot(
                "com.navimods.radio", "ABC Classic", "", PlaybackState.STATE_PLAYING,
                PlaybackState.ACTION_PAUSE, new Object());
        MediaListenerService.Snapshot merged = MediaListenerService.applyRadioFallback(
                session, "ABC Classic", "ABC Classic");
        assertEquals("ABC Classic", merged.title);
        assertEquals("", merged.artist);
    }
}
