package com.cbkii.ts18launcher;

import android.app.Activity;
import android.app.Instrumentation;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Standalone, dependency-free API29 framework IPC qualification. Test APK only; no audio player.
 * Run: adb shell am instrument -w com.cbkii.ts18launcher.test/com.cbkii.ts18launcher.MediaSessionApi29Instrumentation
 */
public final class MediaSessionApi29Instrumentation extends Instrumentation {
    private boolean observeLive;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        observeLive = arguments != null && "1".equals(arguments.getString("observe_live"));
        start();
    }
    @Override public void onStart() {
        if (observeLive) { observeSelectedAuxio(); return; }
        Bundle result = new Bundle();
        MediaSession session = new MediaSession(getTargetContext(), "TS18-media-contract-test");
        MediaController controller = session.getController();
        CountDownLatch metadataCallback = new CountDownLatch(1);
        MediaController.Callback callback = new MediaController.Callback() {
            @Override public void onMetadataChanged(MediaMetadata metadata) { metadataCallback.countDown(); }
        };
        try {
            controller.registerCallback(callback, new Handler(Looper.getMainLooper()));
            session.setQueue(Arrays.asList(
                    new MediaSession.QueueItem(new MediaDescription.Builder().setMediaId("a").build(), 1L),
                    new MediaSession.QueueItem(new MediaDescription.Builder().setMediaId("b").build(), 2L)));
            session.setMetadata(metadata("a", "Track A"));
            session.setPlaybackState(state(PlaybackState.STATE_PLAYING, 1L));
            check(metadataCallback.await(5, TimeUnit.SECONDS), "framework metadata callback absent");
            check("Track A".equals(read(controller).title), "initial item");
            session.setQueue(java.util.Collections.singletonList(new MediaSession.QueueItem(
                    new MediaDescription.Builder().setMediaId("file:///raw-a").build(), 6L)));
            session.setMetadata(metadata("file:///raw-a", "Raw A"));
            session.setPlaybackState(state(PlaybackState.STATE_PLAYING, 6L));
            check("Raw A".equals(read(controller).title), "raw restored item");
            session.setQueue(java.util.Collections.singletonList(new MediaSession.QueueItem(
                    new MediaDescription.Builder().setMediaId("file:///raw-b").build(), 7L)));
            session.setMetadata(metadata("file:///raw-b", "Raw B"));
            session.setPlaybackState(state(PlaybackState.STATE_PLAYING, 7L));
            check("Raw B".equals(read(controller).title), "raw automatic next");
            session.setQueue(Arrays.asList(
                    new MediaSession.QueueItem(new MediaDescription.Builder().setMediaId("a").build(), 1L),
                    new MediaSession.QueueItem(new MediaDescription.Builder().setMediaId("b").build(), 2L)));
            session.setMetadata(null);
            session.setPlaybackState(state(PlaybackState.STATE_PLAYING, 2L));
            check(read(controller).title.isEmpty(), "old A survived blank B");
            session.setMetadata(metadata("b", "Track B"));
            check("Track B".equals(read(controller).title), "B did not replace A");
            session.setMetadata(metadata("a", "Late A artwork"));
            check(read(controller).title.isEmpty(), "old artwork crossed current queue item");
            session.setMetadata(metadata("b", "Track B"));
            for (int value : new int[] { PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING,
                    PlaybackState.STATE_CONNECTING, PlaybackState.STATE_PAUSED, PlaybackState.STATE_STOPPED }) {
                session.setPlaybackState(state(value, 2L));
                MediaListenerService.Snapshot snapshot = read(controller);
                check(snapshot.state == value && snapshot.playing == (value == PlaybackState.STATE_PLAYING),
                        "native playback state " + value);
            }
            result.putString("stream", "PASS API29 MediaSession/controller metadata, queue, callback and native-state IPC\n");
            finish(Activity.RESULT_OK, result);
        } catch (Exception | AssertionError error) {
            result.putString("stream", "FAIL " + error + "\n");
            finish(Activity.RESULT_CANCELED, result);
        } finally {
            controller.unregisterCallback(callback); session.release();
        }
    }
    /** Optional read-only physical probe. No synthetic session or transport is created here.
     * Add -e observe_live 1 to the instrumentation command while the matched Auxio is installed.
     */
    private void observeSelectedAuxio() {
        Bundle result = new Bundle();
        CountDownLatch selected = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<MediaController> current = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicLong generation = new java.util.concurrent.atomic.AtomicLong();
        ProcessMediaSessionMonitor.Observer observer = (music, radio, authoritative) -> {
            if (authoritative && "com.tw.media".equals(music.packageName)) {
                current.set(ProcessMediaSessionMonitor.selectedController(music.packageName));
                generation.set(music.observationGeneration); selected.countDown();
            }
        };
        try {
            check(MediaListenerService.hasNotificationAccess(getTargetContext()), "BLOCKED notification access");
            ProcessMediaSessionMonitor.addObserver(getTargetContext(), observer);
            check(selected.await(3, TimeUnit.SECONDS) && current.get() != null, "BLOCKED no selected Auxio authority");
            MediaListenerService.Snapshot sample = MediaItemObservation.read(current.get(), generation.get(), android.os.SystemClock.uptimeMillis());
            String evidence = "OBSERVED uid=" + android.os.Process.myUid() + " " + sample.observationEvidence();
            android.util.Log.i(MediaEventTrace.LOG_TAG, evidence);
            result.putString("stream", evidence + "\nNo transport or audible qualification performed.\n");
            finish(Activity.RESULT_OK, result);
        } catch (Exception | AssertionError unavailable) {
            result.putString("stream", "BLOCKED " + unavailable + "\n"); finish(Activity.RESULT_CANCELED, result);
        } finally { ProcessMediaSessionMonitor.removeObserver(observer); }
    }
    private static MediaListenerService.Snapshot read(MediaController controller) {
        return MediaItemObservation.read(controller, 1L, android.os.SystemClock.uptimeMillis());
    }
    private static void check(boolean pass, String message) { if (!pass) throw new AssertionError(message); }
    private static MediaMetadata metadata(String id, String title) {
        return new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID, id)
                .putString(MediaMetadata.METADATA_KEY_TITLE, title).build();
    }
    private static PlaybackState state(int value, long item) {
        return new PlaybackState.Builder().setState(value, 0L, 1f).setActiveQueueItemId(item)
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE).build();
    }
}
