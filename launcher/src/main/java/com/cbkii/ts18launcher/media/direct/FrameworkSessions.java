package com.cbkii.ts18launcher.media.direct;

import android.content.ComponentName;
import android.content.Context;
import android.database.ContentObserver;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Android binding for the direct engine: live MediaSessions seen through the launcher's existing
 * notification-listener component. It observes and forwards; it creates no session, service,
 * queue or audio focus request, and it starts nothing.
 */
final class FrameworkSessions implements DirectSessionRegistry.Source {
    private static final String ENABLED_LISTENERS = "enabled_notification_listeners";

    private final Context app;
    private final ComponentName listenerComponent;
    private final Handler handler;
    private final MediaSessionManager manager;
    private final MediaSessionManager.OnActiveSessionsChangedListener membership =
            controllers -> notifyChanged();
    private final ContentObserver accessObserver;
    private Runnable changeListener;
    private boolean membershipRegistered;
    private boolean accessRegistered;

    FrameworkSessions(Context context, ComponentName listenerComponent, Handler handler) {
        this.app = context.getApplicationContext();
        this.listenerComponent = listenerComponent;
        this.handler = handler;
        this.manager = (MediaSessionManager) app.getSystemService(Context.MEDIA_SESSION_SERVICE);
        this.accessObserver = new ContentObserver(handler) {
            @Override public void onChange(boolean selfChange) { notifyChanged(); }
        };
    }

    @Override public boolean accessGranted() {
        String flat = Settings.Secure.getString(app.getContentResolver(), ENABLED_LISTENERS);
        if (flat == null || flat.isEmpty()) return false;
        for (String item : flat.split(":")) {
            ComponentName component = ComponentName.unflattenFromString(item);
            if (listenerComponent.equals(component)) return true;
        }
        return false;
    }

    @Override public List<DirectSessionRegistry.Session> activeSessions() {
        List<DirectSessionRegistry.Session> sessions = new ArrayList<>();
        if (manager == null) return sessions;
        registerMembership();
        List<MediaController> controllers = manager.getActiveSessions(listenerComponent);
        if (controllers != null) for (MediaController controller : controllers) {
            if (controller != null) sessions.add(new ControllerSession(controller, handler));
        }
        return sessions;
    }

    @Override public void setChangeListener(Runnable onChanged) {
        changeListener = onChanged;
        if (onChanged != null) {
            if (!accessRegistered) {
                app.getContentResolver().registerContentObserver(
                        Settings.Secure.getUriFor(ENABLED_LISTENERS), false, accessObserver);
                accessRegistered = true;
            }
            return;
        }
        if (accessRegistered) {
            app.getContentResolver().unregisterContentObserver(accessObserver);
            accessRegistered = false;
        }
        if (membershipRegistered && manager != null) {
            try {
                manager.removeOnActiveSessionsChangedListener(membership);
            } catch (RuntimeException gone) {
                // The service already dropped it.
            }
        }
        membershipRegistered = false;
    }

    /** Registered lazily, only once access is effective; a refusal is retried on the next read. */
    private void registerMembership() {
        if (membershipRegistered || changeListener == null) return;
        manager.addOnActiveSessionsChangedListener(membership, listenerComponent, handler);
        membershipRegistered = true;
    }

    private void notifyChanged() {
        Runnable listener = changeListener;
        if (listener != null) listener.run();
    }

    /** One reading of one controller. Every field comes from that controller, right now. */
    static SessionObservation read(Object token, MediaController controller) {
        String packageName = "";
        int state = PlaybackState.STATE_NONE;
        long actions = 0L;
        String itemId = "";
        String title = "";
        String subtitle = "";
        try {
            packageName = controller.getPackageName();
            PlaybackState playback = controller.getPlaybackState();
            MediaMetadata metadata = controller.getMetadata();
            if (playback != null) {
                state = playback.getState();
                actions = playback.getActions();
            }
            if (metadata != null) {
                itemId = text(metadata, MediaMetadata.METADATA_KEY_MEDIA_ID);
                title = text(metadata, MediaMetadata.METADATA_KEY_TITLE,
                        MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
                subtitle = text(metadata, MediaMetadata.METADATA_KEY_ARTIST,
                        MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
                        MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            }
            if (itemId.isEmpty() && playback != null
                    && playback.getActiveQueueItemId() != MediaSession.QueueItem.UNKNOWN_ID) {
                itemId = "queue:" + playback.getActiveQueueItemId();
            }
        } catch (RuntimeException gone) {
            // A session that died mid-read is reported as idle, never with stale text.
            state = PlaybackState.STATE_NONE;
            actions = 0L;
            itemId = "";
            title = "";
            subtitle = "";
        }
        return new SessionObservation(token, packageName, state, actions, itemId, title, subtitle);
    }

    private static String text(MediaMetadata metadata, String... keys) {
        for (String key : keys) {
            CharSequence value = metadata.getText(key);
            if (value != null) {
                String trimmed = value.toString().trim();
                if (!trimmed.isEmpty()) return trimmed;
            }
        }
        return "";
    }

    private static final class ControllerSession extends MediaController.Callback
            implements DirectSessionRegistry.Session {
        private final MediaController controller;
        private final Handler handler;
        private final MediaSession.Token token;
        private Runnable onChanged;
        private Runnable onEnded;

        ControllerSession(MediaController controller, Handler handler) {
            this.controller = controller;
            this.handler = handler;
            MediaSession.Token resolved = null;
            try {
                resolved = controller.getSessionToken();
            } catch (RuntimeException gone) {
                // No token: the registry skips this handle.
            }
            this.token = resolved;
        }

        @Override public Object token() { return token; }

        @Override public SessionObservation read() { return FrameworkSessions.read(token, controller); }

        @Override public void watch(Runnable onChanged, Runnable onEnded) {
            this.onChanged = onChanged;
            this.onEnded = onEnded;
            controller.registerCallback(this, handler);
        }

        @Override public void unwatch() {
            onChanged = null;
            onEnded = null;
            controller.unregisterCallback(this);
        }

        @Override public boolean dispatch(DirectOperation operation) {
            MediaController.TransportControls controls = controller.getTransportControls();
            if (controls == null) return false;
            switch (operation) {
                case PLAY: controls.play(); break;
                case PAUSE: controls.pause(); break;
                case NEXT: controls.skipToNext(); break;
                default: controls.skipToPrevious(); break;
            }
            return true;
        }

        @Override public void onPlaybackStateChanged(PlaybackState state) { changed(); }

        @Override public void onMetadataChanged(MediaMetadata metadata) { changed(); }

        @Override public void onSessionDestroyed() {
            Runnable ended = onEnded;
            if (ended != null) ended.run();
        }

        private void changed() {
            Runnable listener = onChanged;
            if (listener != null) listener.run();
        }
    }
}
