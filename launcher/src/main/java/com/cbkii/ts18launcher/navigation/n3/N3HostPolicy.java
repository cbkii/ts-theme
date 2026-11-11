package com.cbkii.ts18launcher.navigation.n3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Admission policy for an optional privileged N3 display host. No such host is built or deployed
 * by this repository; this is the contract any future host must enforce, written as a pure class
 * so it can be tested without privilege. The default configuration admits nothing.
 *
 * Every request is checked for an exact caller (one package for the UID, exact signing
 * certificate, exact Android user), an exact allowlisted navigator component, display ownership
 * by that caller, bounded geometry and pointer counts, in-bounds coordinates and an input rate
 * limit. There is no shell, arbitrary intent, global input or wildcard path.
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
        DENY_GEOMETRY,
        DENY_POINTERS,
        DENY_COORDINATES,
        DENY_RATE
    }

    public static final int MAX_DISPLAYS_PER_CALLER = 1;
    public static final int MIN_SIZE = 64, MAX_SIZE = 4096, MIN_DPI = 72, MAX_DPI = 640;
    public static final int MAX_EVENTS_PER_SECOND = 240;

    /** Fixed at host install time; never taken from a request. */
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
            this.components = components == null ? Collections.<String>emptySet() : Collections.unmodifiableSet(components);
        }

        public static final Config DISABLED = new Config(false, "", "", -1, null);
    }

    /** Identity the host derives itself from Binder.getCallingUid() and PackageManager; never from the parcel. */
    public static final class Caller {
        final int uid;
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

    public interface Clock { long elapsedMs(); }

    private final Config config;
    private final Clock clock;
    private final Map<Integer, Integer> displayOwner = new HashMap<>();
    private final Map<Integer, long[]> rate = new HashMap<>();

    public N3HostPolicy(Config config, Clock clock) {
        this.config = config == null ? Config.DISABLED : config;
        this.clock = clock;
    }

    private Decision caller(Caller c) {
        if (!config.enabled) return Decision.DENY_HOST_DISABLED;
        if (c == null || c.packages.length != 1) return Decision.DENY_CALLER_AMBIGUOUS;
        if (config.callerPackage.isEmpty() || !config.callerPackage.equals(c.packages[0])) return Decision.DENY_CALLER_PACKAGE;
        if (config.callerCertSha256.isEmpty() || !config.callerCertSha256.equals(c.certSha256)) return Decision.DENY_CALLER_CERTIFICATE;
        if (c.userId != config.callerUserId) return Decision.DENY_CALLER_USER;
        return Decision.ALLOW;
    }

    public Decision createDisplay(Caller c, int width, int height, int dpi) {
        Decision d = caller(c);
        if (d != Decision.ALLOW) return d;
        if (width < MIN_SIZE || width > MAX_SIZE || height < MIN_SIZE || height > MAX_SIZE
                || dpi < MIN_DPI || dpi > MAX_DPI) return Decision.DENY_GEOMETRY;
        if (owned(c.uid).size() >= MAX_DISPLAYS_PER_CALLER) return Decision.DENY_DISPLAY_LIMIT;
        return Decision.ALLOW;
    }

    /** Called by the host after it actually created the display for this caller. */
    public void displayCreated(Caller c, int displayId) {
        if (caller(c) == Decision.ALLOW) displayOwner.put(displayId, c.uid);
    }

    public Decision launch(Caller c, int displayId, String flattenedComponent) {
        Decision d = caller(c);
        if (d != Decision.ALLOW) return d;
        if (!ownedBy(displayId, c)) return Decision.DENY_DISPLAY_NOT_OWNED;
        if (flattenedComponent == null || !config.components.contains(flattenedComponent)) return Decision.DENY_COMPONENT;
        return Decision.ALLOW;
    }

    public Decision inject(Caller c, int displayId, int width, int height, float[] xs, float[] ys) {
        Decision d = caller(c);
        if (d != Decision.ALLOW) return d;
        if (!ownedBy(displayId, c)) return Decision.DENY_DISPLAY_NOT_OWNED;
        if (xs == null || ys == null || xs.length == 0 || xs.length != ys.length
                || xs.length > N3InputMapper.MAX_POINTERS) return Decision.DENY_POINTERS;
        for (int i = 0; i < xs.length; i++) {
            if (!(xs[i] >= 0f && xs[i] < width && ys[i] >= 0f && ys[i] < height)) return Decision.DENY_COORDINATES;
        }
        long now = clock.elapsedMs();
        long[] bucket = rate.get(c.uid);
        if (bucket == null || now - bucket[0] >= 1000L) bucket = new long[] {now, 0};
        if (bucket[1] >= MAX_EVENTS_PER_SECOND) return Decision.DENY_RATE;
        bucket[1]++;
        rate.put(c.uid, bucket);
        return Decision.ALLOW;
    }

    public Decision release(Caller c, int displayId) {
        Decision d = caller(c);
        if (d != Decision.ALLOW) return d;
        if (!ownedBy(displayId, c)) return Decision.DENY_DISPLAY_NOT_OWNED;
        displayOwner.remove(displayId);
        return Decision.ALLOW;
    }

    /** Binder death of the caller: every display it owned must be released by the host. */
    public List<Integer> callerDied(int uid) {
        List<Integer> released = owned(uid);
        for (Integer id : released) displayOwner.remove(id);
        rate.remove(uid);
        return released;
    }

    private boolean ownedBy(int displayId, Caller c) {
        Integer owner = displayOwner.get(displayId);
        return owner != null && owner == c.uid;
    }

    private List<Integer> owned(int uid) {
        List<Integer> out = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : displayOwner.entrySet()) if (e.getValue() == uid) out.add(e.getKey());
        Collections.sort(out);
        return out;
    }
}
