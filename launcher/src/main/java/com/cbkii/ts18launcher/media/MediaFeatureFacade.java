package com.cbkii.ts18launcher.media;

import java.util.EnumMap;
import java.util.Map;

/**
 * The single UI-facing entry point for Music and Radio control.
 *
 * It owns exactly two decisions and nothing else:
 * <ol>
 *   <li>which engine acts for each source (the other engine is detached and stays silent);</li>
 *   <li>source handover when the two sources are not both on the legacy engine: the previously
 *       playing source is paused once, by its own engine, only after the requested source has
 *       been observed playing. A failed replacement leaves the former source untouched.</li>
 * </ol>
 * When both sources are on the legacy engine, handover remains entirely inside that engine
 * (see {@link #legacyOwnsHandover}), so the default configuration behaves exactly as it did
 * before this type existed.
 */
public final class MediaFeatureFacade {
    /** Supplies the persisted choice and exact configured package for a source. */
    public interface Configuration {
        MediaEngine requestedEngine(MediaSourceKey key);
        String configuredPackage(MediaSourceKey key);
    }

    private final Configuration configuration;
    private final MediaBackend legacy;
    private final MediaBackend direct;
    private final Map<MediaSourceKey, MediaEngine> effective = new EnumMap<>(MediaSourceKey.class);
    private final Map<MediaSourceKey, String> packages = new EnumMap<>(MediaSourceKey.class);
    private MediaBackend.StateListener listener;

    /** @param direct may be null: a requested direct engine then resolves to legacy. */
    public MediaFeatureFacade(Configuration configuration, MediaBackend legacy, MediaBackend direct) {
        if (configuration == null || legacy == null) throw new IllegalArgumentException("legacy engine required");
        this.configuration = configuration;
        this.legacy = legacy;
        this.direct = direct;
        legacy.setStateListener(state -> forward(legacy, state));
        if (direct != null) direct.setStateListener(state -> forward(direct, state));
    }

    /** Re-read configuration; attach the selected engine and detach the other, per source. */
    public void reconcile() {
        for (MediaSourceKey key : MediaSourceKey.values()) {
            MediaEngine requested = configuration.requestedEngine(key);
            MediaEngine engine = requested == MediaEngine.DIRECT && direct != null
                    ? MediaEngine.DIRECT : MediaEngine.LEGACY;
            String configured = configuration.configuredPackage(key);
            String packageName = configured == null ? "" : configured;
            if (engine == effective.get(key) && packageName.equals(packages.get(key))) continue;
            MediaBackend selected = engine == MediaEngine.DIRECT ? direct : legacy;
            MediaBackend other = engine == MediaEngine.DIRECT ? legacy : direct;
            // Silence the deselected engine before the selected one may act.
            if (other != null && other.attached(key)) other.detach(key);
            effective.put(key, engine);
            packages.put(key, packageName);
            selected.attach(key, packageName);
        }
    }

    /** The engine actually acting for {@code key}; legacy until {@link #reconcile()} has run. */
    public MediaEngine engine(MediaSourceKey key) {
        MediaEngine engine = effective.get(key);
        return engine == null ? MediaEngine.LEGACY : engine;
    }

    public boolean directAvailable() { return direct != null; }

    public MediaSourceState state(MediaSourceKey key) { return backend(key).state(key); }

    public boolean playing(MediaSourceKey key, String packageName) {
        return backend(key).playing(key, packageName);
    }

    /**
     * True when a switch to {@code key} is handled by the legacy engine's own nominate-then-commit
     * handover, i.e. both sources are legacy. The caller then keeps its original legacy
     * nomination; in every other combination this facade commits the handover itself.
     */
    public boolean legacyOwnsHandover(MediaSourceKey key) {
        return backend(key) == legacy && backend(key.opposite()) == legacy;
    }

    public void setStateListener(MediaBackend.StateListener listener) { this.listener = listener; }

    public void command(MediaSourceKey key, String packageName, MediaUserAction action,
                        MediaBackend.ResultCallback callback) {
        reconcile();
        MediaBackend requested = backend(key);
        MediaSourceKey oppositeKey = key.opposite();
        MediaBackend opposite = backend(oppositeKey);
        boolean wantsPlay = action == MediaUserAction.PLAY_PAUSE && !requested.playing(key, packageName);
        boolean legacyOwnsHandover = requested == legacy && opposite == legacy;
        requested.command(key, packageName, action, result -> {
            // Commit the handover only on observed playback of the replacement, and only if the
            // opposite source is still served by the engine that was selected at tap time.
            if (wantsPlay && !legacyOwnsHandover && result != null && result.playingObserved
                    && backend(oppositeKey) == opposite && opposite.attached(oppositeKey)) {
                opposite.pauseForHandover(oppositeKey);
            }
            if (callback != null) callback.onResult(result);
        });
    }

    private MediaBackend backend(MediaSourceKey key) {
        return engine(key) == MediaEngine.DIRECT && direct != null ? direct : legacy;
    }

    private void forward(MediaBackend source, MediaSourceState state) {
        // A deselected engine can never overwrite the UI state of a source it no longer owns.
        if (state == null || backend(state.key) != source || listener == null) return;
        listener.onMediaSourceStateChanged(state);
    }
}
