package com.cbkii.ts18launcher;

/** Recent completed request state, independent of historical diagnostic counters. */
final class TileState {
    enum Kind { NETWORK_OK, CACHE_FRESH, CACHE_STALE, FAILED }
    static final long RECENT_MS = 15_000L;
    static final class Snapshot {
        final Kind kind;
        final long atMillis;
        Snapshot(Kind kind, long atMillis) { this.kind = kind; this.atMillis = atMillis; }
        boolean recentFailure(long now) {
            return kind == Kind.FAILED && now >= atMillis && now - atMillis <= RECENT_MS;
        }
    }
    private Snapshot latest;
    synchronized void record(Kind kind, long now) { latest = new Snapshot(kind, now); }
    synchronized Snapshot snapshot() { return latest; }
}
