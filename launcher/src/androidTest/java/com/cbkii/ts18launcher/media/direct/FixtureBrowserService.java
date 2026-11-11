package com.cbkii.ts18launcher.media.direct;

import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.service.media.MediaBrowserService;

import java.util.Collections;
import java.util.List;

/**
 * Minimal external player for IPC tests: one MediaSession that changes state only when told to.
 * It produces no audio and requests no focus. Lives in the test APK's own package and process.
 */
public final class FixtureBrowserService extends MediaBrowserService {
    private MediaSession session;

    @Override public void onCreate() {
        super.onCreate();
        session = new MediaSession(this, "ts18-m2-fixture");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { state(PlaybackState.STATE_PLAYING); }
            @Override public void onPause() { state(PlaybackState.STATE_PAUSED); }
        });
        session.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "fixture-1")
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Fixture track").build());
        state(PlaybackState.STATE_PAUSED);
        session.setActive(true);
        setSessionToken(session.getSessionToken());
    }

    private void state(int value) {
        session.setPlaybackState(new PlaybackState.Builder().setState(value, 0L, 1f)
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE).build());
    }

    @Override public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        return new BrowserRoot("root", null);
    }

    @Override public void onLoadChildren(String parentId, Result<List<MediaBrowser.MediaItem>> result) {
        result.sendResult(Collections.emptyList());
    }

    @Override public void onDestroy() {
        session.release();
        super.onDestroy();
    }
}
