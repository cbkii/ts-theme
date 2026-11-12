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
 * Minimal external player for IPC tests: one MediaSession that starts with nothing loaded,
 * loads an item on PREPARE and changes state only when told to. No audio, no focus. Lives in the test APK's own package and process.
 */
public final class FixtureBrowserService extends MediaBrowserService {
    /** Discovery decoy: an unrelated Media3 candidate must not hide the standard browser. */
    public static final class Media3Candidate extends android.app.Service {
        @Override public android.os.IBinder onBind(android.content.Intent intent) { return null; }
    }
    static final String ACTION_DIE = "com.cbkii.ts18launcher.test.FIXTURE_DIE";
    private MediaSession session;

    @Override public void onCreate() {
        super.onCreate();
        session = new MediaSession(this, "ts18-m2-fixture");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { state(PlaybackState.STATE_PLAYING); }
            @Override public void onPause() { state(PlaybackState.STATE_PAUSED); }
            // Like Auxio-TS onPrepare(): load the restored item, stay paused, take no focus.
            @Override public void onPrepare() {
                session.setMetadata(new MediaMetadata.Builder()
                        .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "fixture-1")
                        .putString(MediaMetadata.METADATA_KEY_TITLE, "Fixture track").build());
                state(PlaybackState.STATE_PAUSED);
            }
            @Override public void onCommand(String command, Bundle args, android.os.ResultReceiver receiver) {
                Bundle result = new Bundle();
                result.putInt("state", session.getController().getPlaybackState().getState());
                if (receiver != null) receiver.send(0, result);
            }
            // Test-only: simulate the source process dying.
            @Override public void onCustomAction(String action, Bundle extras) {
                if (SessionObservation.HOME_PLAY_V1.equals(action) && extras != null
                        && extras.getInt("protocol", 0) == 1
                        && extras.getLong("deadline_elapsed_ms", -1L) > android.os.SystemClock.elapsedRealtime())
                    state(PlaybackState.STATE_PLAYING);
                if (ACTION_DIE.equals(action)) android.os.Process.killProcess(android.os.Process.myPid());
            }
        });
        state(PlaybackState.STATE_PAUSED);
        session.setActive(true);
        setSessionToken(session.getSessionToken());
    }

    private void state(int value) {
        session.setPlaybackState(new PlaybackState.Builder().setState(value, 0L, 1f)
                .addCustomAction(new PlaybackState.CustomAction.Builder(SessionObservation.HOME_PLAY_V1,
                        "Bounded Play", android.R.drawable.ic_media_play).build())
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_PREPARE).build());
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
