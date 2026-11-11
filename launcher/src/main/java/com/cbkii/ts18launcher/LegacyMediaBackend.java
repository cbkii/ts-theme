package com.cbkii.ts18launcher;

import android.content.Context;

import com.cbkii.ts18launcher.media.MediaBackend;
import com.cbkii.ts18launcher.media.MediaCommandResult;
import com.cbkii.ts18launcher.media.MediaEngine;
import com.cbkii.ts18launcher.media.MediaSourceKey;
import com.cbkii.ts18launcher.media.MediaSourceState;
import com.cbkii.ts18launcher.media.MediaUserAction;

import java.util.EnumMap;
import java.util.Map;

/**
 * M1: the original media machinery behind the engine contract.
 *
 * This is a thin adapter. Every command still goes through the one shared
 * MediaSourceBootstrapper with the same arguments as before; no legacy decision is
 * re-implemented here. Its only new duty is to stay silent for a source that is not attached.
 */
final class LegacyMediaBackend implements MediaBackend {
    private final Context app;
    private final Map<MediaSourceKey, String> attached = new EnumMap<>(MediaSourceKey.class);

    LegacyMediaBackend(Context context) { app = context.getApplicationContext(); }

    @Override public MediaEngine engine() { return MediaEngine.LEGACY; }

    @Override public void attach(MediaSourceKey key, String packageName) {
        attached.put(key, packageName == null ? "" : packageName);
    }

    @Override public void detach(MediaSourceKey key) {
        String packageName = attached.remove(key);
        if (packageName != null && !packageName.isEmpty()) {
            sources().cancelPendingFor(packageName, "Source moved to another media engine");
        }
        MediaEventTrace.record("engine", "legacy-detached", key.name());
    }

    @Override public boolean attached(MediaSourceKey key) { return attached.containsKey(key); }

    @Override public MediaSourceState state(MediaSourceKey key) {
        String configured = attached.get(key);
        if (configured == null || configured.isEmpty()) {
            return MediaSourceState.unavailable(key, MediaEngine.LEGACY, "",
                    MediaSourceState.Availability.UNCONFIGURED, 0L);
        }
        if (!MediaListenerService.hasNotificationAccess(app)) {
            return MediaSourceState.unavailable(key, MediaEngine.LEGACY, configured,
                    MediaSourceState.Availability.PERMISSION_NEEDED, 0L);
        }
        MediaListenerService.Snapshot snapshot = ProcessMediaSessionMonitor.selectedSnapshot(configured);
        if (snapshot.packageName.isEmpty()) {
            return MediaSourceState.unavailable(key, MediaEngine.LEGACY, configured,
                    MediaSourceState.Availability.NO_SESSION, 0L);
        }
        return new MediaSourceState(key, MediaEngine.LEGACY, snapshot.packageName,
                MediaSourceState.Availability.LIVE, snapshot.state, snapshot.actions,
                snapshot.sessionIdentity, snapshot.mediaId, snapshot.title, snapshot.artist,
                snapshot.observationGeneration);
    }

    @Override public boolean playing(MediaSourceKey key, String packageName) {
        return attached.containsKey(key) && packageName != null && !packageName.isEmpty()
                && sources().isPlaying(packageName);
    }

    @Override public void command(MediaSourceKey key, String packageName, MediaUserAction action,
                                  ResultCallback callback) {
        if (!attached.containsKey(key)) {
            // A deselected engine never commands; report instead of acting.
            if (callback != null) callback.onResult(new MediaCommandResult(
                    MediaCommandResult.Outcome.FAILED, "Legacy engine is not selected", false, false));
            return;
        }
        MediaSourceBootstrapper owner = sources();
        owner.command(label(key), packageName, legacyCommand(action), (success, message) -> {
            if (callback == null) return;
            boolean playing = success && packageName != null && !packageName.isEmpty()
                    && sources().isPlaying(packageName);
            callback.onResult(new MediaCommandResult(
                    success ? MediaCommandResult.Outcome.ACKNOWLEDGED : MediaCommandResult.Outcome.FAILED,
                    message, playing, success));
        });
    }

    @Override public void pauseForHandover(MediaSourceKey key) {
        if (!attached.containsKey(key)) return;
        String packageName = controlPackage(key);
        if (packageName.isEmpty()) return;
        MediaSourceBootstrapper owner = sources();
        if (!owner.isPlaying(packageName)) return;
        MediaEventTrace.record("switch", "facade-handover-pause", packageName);
        // Observed playing, so the legacy engine resolves this single action to PAUSE.
        owner.command(label(key), packageName, MediaListenerService.Command.PLAY_PAUSE,
                (success, message) -> { });
    }

    @Override public void setStateListener(StateListener listener) {
        // Legacy state reaches the UI through its original observers, unchanged.
    }

    /**
     * The package the legacy engine is controlling for a source. Legacy generic-music "Auto" mode
     * follows its own selected session; that selection rule is legacy behaviour and stays here.
     */
    private String controlPackage(MediaSourceKey key) {
        if (key == MediaSourceKey.MUSIC
                && LauncherPrefs.MEDIA_MODE_AUTO.equals(LauncherPrefs.mediaMode(app))) {
            String selected = ProcessMediaSessionMonitor.selectedMusicPackage();
            if (!selected.isEmpty()) return selected;
        }
        String configured = attached.get(key);
        return configured == null ? "" : configured;
    }

    private MediaSourceBootstrapper sources() { return Ts18LauncherApplication.mediaSources(app); }

    private static String label(MediaSourceKey key) { return key == MediaSourceKey.RADIO ? "Radio" : "Music"; }

    static MediaListenerService.Command legacyCommand(MediaUserAction action) {
        if (action == MediaUserAction.PREVIOUS) return MediaListenerService.Command.PREVIOUS;
        if (action == MediaUserAction.NEXT) return MediaListenerService.Command.NEXT;
        return MediaListenerService.Command.PLAY_PAUSE;
    }

    static MediaUserAction userAction(MediaListenerService.Command command) {
        if (command == MediaListenerService.Command.PREVIOUS) return MediaUserAction.PREVIOUS;
        if (command == MediaListenerService.Command.NEXT) return MediaUserAction.NEXT;
        return MediaUserAction.PLAY_PAUSE;
    }
}
