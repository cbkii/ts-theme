package com.cbkii.ts18launcher.n3host;

import com.cbkii.ts18launcher.navigation.n3.N3HostPolicy;
import com.cbkii.ts18launcher.navigation.n3.N3HostPolicy.Decision;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Framework-free core of the display host. Every entry point takes the Binder calling UID, never
 * an identity from the request. It resolves the caller through the platform, admits the request
 * through N3HostPolicy, and only then touches a display. Thread-safe: concurrent Binder calls are
 * serialised here. Displays are released when their caller dies.
 */
final class DisplayHostCore {
    /** The privileged edge, implemented with real framework calls in DisplayHostService. */
    interface Platform {
        /** Caller identity derived from PackageManager for this UID; null if it cannot be resolved. */
        N3HostPolicy.Caller resolve(int uid);
        /** A private display owned by the host, or 0 when refused. */
        int createDisplay(int width, int height, int dpi, Object surface, Object client);
        boolean setSurface(int displayId, Object surface);
        boolean launch(int displayId, String component);
        boolean inject(int displayId, Object motionEvent);
        boolean back(int displayId);
        void releaseDisplay(int displayId);
        /** Calls onDeath when the client binder dies; false if it is already dead. */
        boolean linkToDeath(Object client, Runnable onDeath);
    }

    static final class Opened {
        final Decision decision;
        final int displayId;
        final long lease;
        Opened(Decision decision, int displayId, long lease) {
            this.decision = decision; this.displayId = displayId; this.lease = lease;
        }
    }

    private final N3HostPolicy policy;
    private final Platform platform;
    private final Map<Integer, Integer> displaysByUid = new HashMap<>();

    DisplayHostCore(N3HostPolicy policy, Platform platform) {
        this.policy = policy;
        this.platform = platform;
    }

    synchronized Opened open(int uid, Object surface, int width, int height, int dpi, Object client) {
        N3HostPolicy.Caller caller = platform.resolve(uid);
        if (caller == null) return new Opened(Decision.DENY_CALLER_AMBIGUOUS, 0, 0L);
        Decision decision = policy.reserve(caller, width, height, dpi);
        if (decision != Decision.ALLOW) return new Opened(decision, 0, 0L);
        if (surface == null || client == null) {
            policy.cancelReservation(caller);
            return new Opened(Decision.DENY_GEOMETRY, 0, 0L);
        }
        int id = platform.createDisplay(width, height, dpi, surface, client);
        N3HostPolicy.Lease lease = id > 0 ? policy.displayCreated(caller, id, width, height) : null;
        if (lease == null) {
            policy.cancelReservation(caller);
            if (id > 0) platform.releaseDisplay(id);
            return new Opened(Decision.DENY_DISPLAY_NOT_OWNED, 0, 0L);
        }
        displaysByUid.put(uid, id);
        if (!platform.linkToDeath(client, () -> callerDied(uid))) {
            callerDied(uid);
            return new Opened(Decision.DENY_CALLER_AMBIGUOUS, 0, 0L);
        }
        return new Opened(Decision.ALLOW, id, lease.token);
    }

    synchronized Decision launch(int uid, int displayId, long lease, String component) {
        N3HostPolicy.Caller caller = platform.resolve(uid);
        Decision decision = policy.launch(caller, displayId, lease, component);
        if (decision != Decision.ALLOW) return decision;
        return platform.launch(displayId, component) ? Decision.ALLOW : Decision.DENY_COMPONENT;
    }

    synchronized Decision motion(int uid, int displayId, long lease, int maskedAction, float[] xs,
                                 float[] ys, Object event) {
        N3HostPolicy.Caller caller = platform.resolve(uid);
        Decision decision = policy.inject(caller, displayId, lease, maskedAction, xs, ys);
        if (decision != Decision.ALLOW) return decision;
        return platform.inject(displayId, event) ? Decision.ALLOW : Decision.DENY_ACTION;
    }

    synchronized Decision back(int uid, int displayId, long lease) {
        N3HostPolicy.Caller caller = platform.resolve(uid);
        Decision decision = policy.back(caller, displayId, lease);
        if (decision != Decision.ALLOW) return decision;
        return platform.back(displayId) ? Decision.ALLOW : Decision.DENY_ACTION;
    }

    synchronized Decision setSurface(int uid, int displayId, long lease, Object surface) {
        N3HostPolicy.Caller caller = platform.resolve(uid);
        // Same ownership and lease checks as Back, without consuming the input budget.
        N3HostPolicy.Lease recorded = policy.lease(displayId);
        if (caller == null || recorded == null || recorded.token != lease
                || !Integer.valueOf(displayId).equals(displaysByUid.get(uid))) return Decision.DENY_DISPLAY_NOT_OWNED;
        return platform.setSurface(displayId, surface) ? Decision.ALLOW : Decision.DENY_DISPLAY_NOT_OWNED;
    }

    synchronized Decision close(int uid, int displayId, long lease) {
        N3HostPolicy.Caller caller = platform.resolve(uid);
        Decision decision = policy.release(caller, displayId, lease);
        if (decision != Decision.ALLOW) return decision;
        displaysByUid.remove(uid);
        platform.releaseDisplay(displayId);
        return Decision.ALLOW;
    }

    synchronized void callerDied(int uid) {
        List<Integer> released = policy.callerDied(uid);
        for (Integer id : released) platform.releaseDisplay(id);
        displaysByUid.remove(uid);
    }
}
