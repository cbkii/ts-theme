package com.cbkii.ts18launcher;

/** Pure marquee decisions shared by the Android view and JVM tests. */
final class MarqueePolicy {
    static final long HOLD_MS = 5000L;
    static final float SPEED_DP_PER_SECOND = 24f;
    static final long MIN_DURATION_MS = 3500L;
    static final long MAX_DURATION_MS = 30000L;

    private MarqueePolicy() {}

    static boolean primaryChanged(String oldValue, String newValue) {
        return !safe(oldValue).equals(safe(newValue));
    }

    static int overflowPx(int contentPx, int availablePx) {
        return Math.max(0, Math.max(0, contentPx) - Math.max(0, availablePx));
    }

    static boolean shouldAnimate(int contentPx, int availablePx) {
        return overflowPx(contentPx, availablePx) > 0;
    }

    static long durationMs(int overflowPx, float density) {
        if (overflowPx <= 0) return 0L;
        float safeDensity = density > 0f ? density : 1f;
        float pxPerSecond = SPEED_DP_PER_SECOND * safeDensity;
        long raw = (long) (overflowPx / pxPerSecond * 1000f);
        return Math.max(MIN_DURATION_MS, Math.min(MAX_DURATION_MS, raw));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
