package com.cbkii.ts18launcher;

import android.media.session.PlaybackState;

/** Presentation is native state, not the pause-side command intention used while connecting. */
final class MediaPlaybackPresentation {
    private MediaPlaybackPresentation() { }
    static int icon(int state) {
        switch (state) {
            case PlaybackState.STATE_PLAYING: return R.drawable.ic_pause;
            case PlaybackState.STATE_PAUSED:
            case PlaybackState.STATE_STOPPED: return R.drawable.ic_play;
            case PlaybackState.STATE_BUFFERING: return R.drawable.ic_media_buffering;
            case PlaybackState.STATE_CONNECTING: return R.drawable.ic_media_connecting;
            case PlaybackState.STATE_ERROR: return R.drawable.ic_media_unavailable;
            default: return R.drawable.ic_media_unknown;
        }
    }
    static int description(int state) {
        switch (state) {
            case PlaybackState.STATE_PLAYING: return R.string.media_playing_pause;
            case PlaybackState.STATE_PAUSED:
            case PlaybackState.STATE_STOPPED: return R.string.media_paused_play;
            case PlaybackState.STATE_BUFFERING: return R.string.media_buffering_pause;
            case PlaybackState.STATE_CONNECTING: return R.string.media_connecting_pause;
            case PlaybackState.STATE_ERROR: return R.string.media_unavailable;
            default: return R.string.media_state_unknown;
        }
    }
}
