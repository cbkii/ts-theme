package com.cbkii.ts18launcher.navigation.n3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Admission policy enforced by the optional N3 display host (module n3host). Pure and
 * framework-free so every decision is unit-tested without privilege. The default configuration
 * admits nothing.
 *
 * Each request is admitted only if all of the following hold:
 * - the caller is derived by the host, not taken from the request: one package for the UID, the
 *   exact signing certificate and the exact Android user;
 * - the navigator component is on the exact allowlist;
 * - the display is reserved atomically and owned by that caller, with one display per caller;
 * - the lease is current, so replayed or stale leases are rejected;
 * - geometry, pointers and actions are bounded, and coordinates are checked against the geometry
 *   the host recorded, never caller-supplied sizes;
 * - the per-caller input rate limit is not exceeded.
 *
 * There is no shell, intent, global input or wildcard path. Thread-safe: all state transitions are
 * synchronised so concurrent Binder calls cannot over-allocate.
 */
public final class N3HostPolicy {
    public enum Decision {
        ALLOW,
        DENY_HOST_DISABLED,
        DENY_CALLER_AMBIGUOUS,
        DENY_CALLER_PACKAGE,
        DENY_CALLER_CERTIFICATE,
        DENY_CALLER_USER,
        DENY_COMPONENT,
        DENY_DISPLAY_NOT_OWNED,
        DENY_DISPLAY_LIMIT,
        DENY_LEASE,
        DENY_GEOMETRY,
        DENY_POINTERS,
        DENY_ACTION,
        DENY_COORDINATES,
        DENY_RATE
    }

    public static final int MAX_DISPLAYS_PER_CALLER = 1;
    public static final int MIN_SIZE = 64, MAX_SIZE = 4096, MIN_DPI = 72, MAX_DPI = 640;
    public static final int MAX_EVENTS_PER_SECOND = 240;

    /** Fixed at host build/install time; never taken from a request. Immutable after construction. */
    public static final class Config {
        final boolean enabled;
        final String callerPackage;
        final String callerCertSha256;
        final int callerUserId;
        final Set<String> components;

        public Config(boolean enabled, String callerPackage, String callerCertSha256, int callerUserId,
                      Set<String> components) {
            this.enabled = enabled;
            this.callerPackage = callerPackage == null ? "" : callerPackage;
            this.callerCertSha256 = callerCertSha256 == null ? "" : callerCertSha256.toLowerCase(java.util.Locale.ROOT);
            this.callerUserId = callerUserId;
            Set<String> copy = new HashSet<>();
            if (components != null) for (String component : components) {
                if (validComponent(component)) copy.add(component);
            }
            this.components = Collections.unmodifiableSet(copy);
        }

        public static final Config DISABLED = new Config(false, "", "", -1, null);

        public boolean enabled() { return enabled; }

        /** Exact "package/class" with a non-empty, fully qualified or relative class. No wildcard. */
        static boolean validComponent(String component) {
            if (component == null) return false;
            int slash = component.indexOf('/');
            return slash > 0 && slash < component.length() - 1 && component.indexOf('/', slash + 1) < 0
                    && component.indexOf('*') < 0 && component.indexOf(' ') < 0;
        }
    }

    /** Identity the host derives from Binder.getCallingUid() and PackageManager; never from a parcel. */
    public static final class Caller {
        public final int uid;
        final int userId;
        final String[] packages;
        final String certSha256;

        public Caller(int uid, int userId, String[] packages, String certSha256) {
            this.uid = uid;
            this.userId = userId;
            this.packages = packages == null ? new String[0] : packages.clone();
            this.certSha256 = certSha256 == null ? "" : certSha256.toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Geometry and lease the host recorded for a display it created. */
    public static final class Lease {
        public final int displayId;
        public final long token;
        public final int width, height;
        final int ownerUid;

        Lease(int displayId, long token, int width, int height, int ownerUid) {
            this.displayId = displayId;
            this.token = token;
            this.width = width;
            this.height = height;
            this.ownerUid = ownerUid;
        }
    }

    public interface Clock { long elapsedMs(); }

    /** Accepted MotionEvent masked actions. Anything else (hover, scroll, outside, buttons) is refused. */
    public static boolean allowedAction(int maskedAction) {
        return maskedAction == N3InputMapper.ACTION_DOWN || maskedAction == N3InputMapper.ACTION_UP
                || maskedAction == N3InputMapper.ACTION_MOVE || maskedAction == N3InputMapper.ACTION_CANCEL
                || maskedAction == N3InputMapper.ACTION_POINTER_DOWN
                || maskedAction == N3InputMapper.ACTION_POINTER_UP;
    }

    private final Config config;
    private final Clock clock;
    private final Map<Integer, Lease> leases = new HashMap<>();
    private final Map<Integer, Integer> reservations = new HashMap<>();
    private final Map<Integer, long[]> rate = new HashMap<>();
    private long nextToken;

    public N3HostPolicy(Config config, Clock clock, long tokenSeed) {
        this.config = config == null ? Config.DISABLED : config;
        this.clock = clock;
        this.nextToken = tokenSeed == 0L ? 1L : tokenSeed;
    }

    public N3HostPolicy(Config config, Clock clock) {
        this(config, clock, 1L);
    }

    public boolean enabled() { return config.enabled; }

    private Decision caller(Caller c) {
        if (!config.enabled) return Decision.DENY_HOST_DISABLED;
        if (c == null || c.packages.length != 1) return Decision.DENY_CALLER_AMBIGUOUS;
        if (config.callerPackage.isEmpty() || !config.callerPackage.equals(c.packages[0])) return Decision.DENY_CALLER_PACKAGE;
        if (config.callerCertSha256.isEmpty() || !config.callerCertSha256.equals(c.certSha256)) return Decision.DENY_CALLER_CERTIFICATE;
        if (c.userId != config.callerUserId) return Decision.DENY_CALLER_USER;
        return Decision.ALLOW;
    }

    /**
     * Atomically checks the caller and geometry and reserves the caller's single display slot.
     * The host must follow with displayCreated() or cancelReservation().
     */
    public synchronized Decision reserve(Caller c, int width, int height, int dpi) {
        Decision d = caller(c);
        if (d != Decision.ALLOW) return d;
        if (width < MIN_SIZE || width > MAX_SIZE || height < MIN_SIZE || height > MAX_SIZE
                || dpi < MIN_DPI || dpi > MAX_DPI) return Decision.DENY_GEOMETRY;
        int held = reservations.containsKey(c.uid) ? 1 : 0;
        for (Lease lease : leases.values()) if (lease.ownerUid == c.uid) held++;
        if (held >= MAX_DISPLAYS_PER_CALLER) return Decision.DENY_DISPLAY_LIMIT;
        reservations.put(c.uid, width);
        return Decision.ALLOW;
    }

    public synchronized void cancelReservation(Caller c) {
        if (c != null) reservations.remove(c.uid);
    }

    /** Records the display the host actually created, with host-recorded geometry; returns its lease. */
    public synchronized Lease displayCreated(Caller c, int displayId, int width, int height) {
        if (c == null || !reservations.containsKey(c.uid) || displayId <= 0 || leases.containsKey(displayId)) {
            if (c != null) reservations.remove(c.uid);
            return null;
        }
        reservations.remove(c.uid);
        Lease lease = new Lease(displayId, nextToken++, width, height, c.uid);
        leases.put(displayId, lease);
        return lease;
    }

    private Decision owned(Caller c, int displayId, long token) {
        Decision d = caller(c);
        if (d != Decision.ALLOW) return d;
        Lease lease = leases.get(displayId);
        if (lease == null || lease.ownerUid != c.uid) return Decision.DENY_DISPLAY_NOT_OWNED;
        if (lease.token != token) return Decision.DENY_LEASE;
        return Decision.ALLOW;
    }

    public synchronized Decision launch(Caller c, int displayId, long token, String flattenedComponent) {
        Decision d = owned(c, displayId, token);
        if (d != Decision.ALLOW) return d;
        if (!config.components.contains(flattenedComponent)) return Decision.DENY_COMPONENT;
        return Decision.ALLOW;
    }

    /** Coordinates are checked against the host-recorded display size, never the caller's. */
    public synchronized Decision inject(Caller c, int displayId, long token, int maskedAction,
                                        float[] xs, float[] ys) {
        Decision d = owned(c, displayId, token);
        if (d != Decision.ALLOW) return d;
        if (!allowedAction(maskedAction)) return Decision.DENY_ACTION;
        if (xs == null || ys == null || xs.length == 0 || xs.length != ys.length
                || xs.length > N3InputMapper.MAX_POINTERS) return Decision.DENY_POINTERS;
        Lease lease = leases.get(displayId);
        for (int i = 0; i < xs.length; i++) {
            if (!(xs[i] >= 0f && xs[i] < lease.width && ys[i] >= 0f && ys[i] < lease.height)) return Decision.DENY_COORDINATES;
        }
        return admitRate(c.uid);
    }

    /** Back is a key event scoped to the owned display; it shares the input rate budget. */
    public synchronized Decision back(Caller c, int displayId, long token) {
        Decision d = owned(c, displayId, token);
        return d != Decision.ALLOW ? d : admitRate(c.uid);
    }

    private Decision admitRate(int uid) {
        long now = clock.elapsedMs();
        long[] bucket = rate.get(uid);
        if (bucket == null || now - bucket[0] >= 1000L) bucket = new long[] {now, 0};
        if (bucket[1] >= MAX_EVENTS_PER_SECOND) return Decision.DENY_RATE;
        bucket[1]++;
        rate.put(uid, bucket);
        return Decision.ALLOW;
    }

    /** Releases one display; the lease must match so a stale or replayed release cannot free another. */
    public synchronized Decision release(Caller c, int displayId, long token) {
        Decision d = owned(c, displayId, token);
        if (d != Decision.ALLOW) return d;
        leases.remove(displayId);
        return Decision.ALLOW;
    }

    /** Binder death of the caller: every display it owned must be released by the host. */
    public synchronized List<Integer> callerDied(int uid) {
        List<Integer> released = new ArrayList<>();
        for (Lease lease : new ArrayList<>(leases.values())) {
            if (lease.ownerUid == uid) { released.add(lease.displayId); leases.remove(lease.displayId); }
        }
        reservations.remove(uid);
        rate.remove(uid);
        Collections.sort(released);
        return released;
    }

    public synchronized Lease lease(int displayId) { return leases.get(displayId); }
}
