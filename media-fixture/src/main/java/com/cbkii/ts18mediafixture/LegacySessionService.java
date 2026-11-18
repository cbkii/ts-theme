package com.cbkii.ts18mediafixture;

import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Real Media3 legacy bridge and decoder fixture. No Activity, tuner or audible output. */
public final class LegacySessionService extends MediaSessionService {
    private ExoPlayer player;
    private MediaSession session;

    @Override public void onCreate() {
        super.onCreate();
        player = new ExoPlayer.Builder(this).build();
        player.setVolume(0f);
        session = new MediaSession.Builder(this, player).build();
        try {
            File wav = new File(getCacheDir(), "resume.wav");
            int samples = 16000 * 8;
            ByteBuffer data = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN);
            data.put(new byte[] {'R','I','F','F'}).putInt(36 + samples * 2);
            data.put(new byte[] {'W','A','V','E','f','m','t',' '}).putInt(16).putShort((short) 1);
            data.putShort((short) 1).putInt(16000).putInt(32000).putShort((short) 2).putShort((short) 16);
            data.put(new byte[] {'d','a','t','a'}).putInt(samples * 2);
            while (data.hasRemaining()) data.putShort((short) 0);
            try (FileOutputStream output = new FileOutputStream(wav)) { output.write(data.array()); }
            player.setMediaItem(new MediaItem.Builder().setMediaId("saved-fixture")
                    .setUri(Uri.fromFile(wav)).setMediaMetadata(new MediaMetadata.Builder()
                            .setTitle("Saved fixture item").build()).build());
            player.prepare();
        } catch (java.io.IOException error) {
            throw new IllegalStateException("fixture input unavailable", error);
        }
    }

    @Override public MediaSession onGetSession(MediaSession.ControllerInfo controller) { return session; }

    @Override public void onDestroy() {
        if (session != null) session.release();
        if (player != null) player.release();
        super.onDestroy();
    }
}
