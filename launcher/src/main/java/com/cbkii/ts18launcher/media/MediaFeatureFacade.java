package com.cbkii.ts18launcher.media;

import java.util.EnumMap;
import java.util.Map;

/**
 * The single UI-facing entry point for Music and Radio control.
 *
 * It owns exactly two decisions and nothing else:
 *   - which engine acts for each source (the other engine is detached and stays silent);
 *   - source handover when the two sources are not both on the legacy engine: the previously
 *       playing source is paused once, by its own engine, only after the requested source has
 *       been observed playing. A failed replacement leaves the former source untouched.
 * When both sources are on the legacy engine, handover remains entirely inside that engine
 * (see legacyOwnsHandover), so the default configuration behaves exactly as it did
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
    private MediaBackend.StateListener readinessListener;
    /** Monotonic count of user commands, across both sources. */
    private long intentEpoch;
    /** Epoch of the newest user command per source. */
    private final Map<MediaSourceKey, Long> lastIntent = new EnumMap<>(MediaSourceKey.class);
    /**
     * The one handover each source's newest Play may commit. A newer Play on the same source
     * replaces it, so coalesced taps that all receive the same acknowledgement commit once.
     */
    private final Map<MediaSourceKey, Handover> pendingHandover = new EnumMap<>(MediaSourceKey.class);

    /** The exact previous owner captured at tap time; a pause is allowed only to this owner. */
    private static final class Handover {
        final MediaSourceKey target;
        final MediaBackend backend;
        final String packageName;
        final Object sessionIdentity;
        final long generation;
        final boolean compareGeneration;
        final long epoch;

        Handover(MediaSourceKey target, MediaBackend backend, String packageName,
                 MediaSourceState owner, long epoch) {
            this.target = target;
            this.backend = backend;
            this.packageName = packageName;
            // Session identity is compared when the engine exposed one at tap time.
            boolean live = owner != null && owner.live() && packageName.equals(owner.packageName);
            this.sessionIdentity = live ? owner.sessionIdentity : null;
            this.generation = live ? owner.generation : 0L;
            // The direct engine's generation identifies one pinned session; the legacy engine's
            // counts observations, so for legacy only package and session identity are compared.
            this.compareGeneration = live && owner.engine == MediaEngine.DIRECT;
            this.epoch = epoch;
        }
    }

    /** direct: may be null: a requested direct engine then resolves to legacy. */
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
            // A pending handover from or to this source refers to an owner that no longer exists.
            pendingHandover.remove(key);
            pendingHandover.remove(key.opposite());
            effective.put(key, engine);
            packages.put(key, packageName);
            selected.attach(key, packageName);
        }
    }

    /** The engine actually acting for key; legacy until reconcile() has run. */
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
     * True when a switch to key is handled by the legacy engine's own nominate-then-commit
     * handover, i.e. both sources are legacy. The caller then keeps its original legacy
     * nomination; in every other combination this facade commits the handover itself.
     */
    public boolean legacyOwnsHandover(MediaSourceKey key) {
        return backend(key) == legacy && backend(key.opposite()) == legacy;
    }

    public void setStateListener(MediaBackend.StateListener listener) { this.listener = listener; }

    public void setReadinessListener(MediaBackend.StateListener listener) { readinessListener = listener; }

    public void preparePassive(MediaSourceKey key, long epoch) {
        reconcile();
        String pkg = packages.get(key);
        if (pkg != null && !pkg.isEmpty()) backend(key).preparePassive(key, pkg, epoch);
    }

    /** Ask the engines that currently serve a source to re-read live state. */
    public void refresh() {
        reconcile();
        boolean legacyServes = false, directServes = false;
        for (MediaSourceKey key : MediaSourceKey.values()) {
            if (backend(key) == legacy) legacyServes = true; else directServes = true;
        }
        if (legacyServes) legacy.refresh();
        if (directServes && direct != null) direct.refresh();
    }

    /**
     * Passive readiness for one boot, wake or HOME-return epoch. Only sources served by the Direct
     * engine are asked here; the legacy engine keeps its own startup bootstrapper. A deselected
     * engine is never asked.
     */
    public void preparePassive(long epoch) {
        reconcile();
        if (direct == null) return;
        for (MediaSourceKey key : MediaSourceKey.values()) {
            String packageName = packages.get(key);
            if (backend(key) == direct && packageName != null && !packageName.isEmpty()) {
                direct.preparePassive(key, packageName, epoch);
            }
        }
    }

    public void command(MediaSourceKey key, String packageName, MediaUserAction action,
                        MediaBackend.ResultCallback callback) {
        command(key, packageName, action, false, Long.MAX_VALUE, callback);
    }

    /** Continue the original Play after Activity staging; never toggle an auto-resumed source. */
    public void resumePreparedSource(MediaSourceKey key, String packageName,
                                     MediaBackend.ResultCallback callback) {
        resumePreparedSource(key, packageName, Long.MAX_VALUE, callback);
    }

    public void resumePreparedSource(MediaSourceKey key, String packageName, long deadlineElapsedMs,
                                     MediaBackend.ResultCallback callback) {
        command(key, packageName, MediaUserAction.PLAY_PAUSE, true, deadlineElapsedMs, callback);
    }

    private void command(MediaSourceKey key, String packageName, MediaUserAction action,
                         boolean resumeOnly, long deadlineElapsedMs, MediaBackend.ResultCallback callback) {
        reconcile();
        long epoch = ++intentEpoch;
        lastIntent.put(key, epoch);
        MediaBackend requested = backend(key);
        MediaSourceKey oppositeKey = key.opposite();
        MediaBackend opposite = backend(oppositeKey);
        boolean wantsPlay = resumeOnly || action == MediaUserAction.PLAY_PAUSE && !requested.playing(key, packageName);
        boolean legacyOwnsHandover = requested == legacy && opposite == legacy;
        Handover handover = null;
        if (wantsPlay && !legacyOwnsHandover && opposite.attached(oppositeKey)) {
            String oppositePackage = packages.get(oppositeKey);
            if (oppositePackage != null && opposite.playing(oppositeKey, oppositePackage)) {
                handover = new Handover(oppositeKey, opposite, oppositePackage,
                        opposite.state(oppositeKey), epoch);
            }
        }
        if (wantsPlay) {
            // The newest Play on this source owns the handover; anything older is void.
            if (handover == null) pendingHandover.remove(key); else pendingHandover.put(key, handover);
        }
        final Handover captured = handover;
        MediaBackend.ResultCallback completion = result -> {
            if (captured != null) commitHandover(key, captured, result);
            if (callback != null) callback.onResult(result);
        };
        if (resumeOnly && requested.playing(key, packageName)) {
            completion.onResult(new MediaCommandResult(MediaCommandResult.Outcome.ACKNOWLEDGED,
                    "Source is already playing", true, false));
        } else requested.commandUntil(key, packageName, action, deadlineElapsedMs, completion);
    }

    /**
     * Pause the captured previous owner once, only after the replacement was observed playing,
     * and only if that exact owner is still the one playing and nobody has acted on it since.
     */
    private void commitHandover(MediaSourceKey key, Handover handover, MediaCommandResult result) {
        if (pendingHandover.get(key) != handover) return;
        pendingHandover.remove(key);
        if (result == null || !result.playingObserved) return;
        MediaSourceKey target = handover.target;
        Long touched = lastIntent.get(target);
        if (touched != null && touched > handover.epoch) return;
        MediaBackend now = backend(target);
        if (now != handover.backend || !now.attached(target)) return;
        if (!handover.packageName.equals(packages.get(target))
                || !now.playing(target, handover.packageName)) return;
        if (handover.sessionIdentity != null) {
            MediaSourceState owner = now.state(target);
            if (!owner.live() || !handover.packageName.equals(owner.packageName)
                    || !handover.sessionIdentity.equals(owner.sessionIdentity)
                    || (handover.compareGeneration && handover.generation != owner.generation)) return;
        }
        now.pauseForHandover(target);
    }

    private MediaBackend backend(MediaSourceKey key) {
        return engine(key) == MediaEngine.DIRECT && direct != null ? direct : legacy;
    }

    private void forward(MediaBackend source, MediaSourceState state) {
        // A deselected engine can never overwrite the UI state of a source it no longer owns.
        if (state == null || backend(state.key) != source) return;
        if (readinessListener != null) readinessListener.onMediaSourceStateChanged(state);
        if (listener != null) listener.onMediaSourceStateChanged(state);
    }
}
