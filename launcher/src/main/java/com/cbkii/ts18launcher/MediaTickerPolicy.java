package com.cbkii.ts18launcher;

/** Pure semantic/scheduling decisions for the shared media ticker. */
final class MediaTickerPolicy {
    static final long HOLD_MS = 5000L;

    private MediaTickerPolicy() {}

    static String normalise(String value) {
        return value == null ? "" : value.trim();
    }

    static boolean primaryChanged(String previous, String next) {
        return !normalise(previous).equals(normalise(next));
    }

    static boolean secondaryChanged(String previous, String next) {
        return !normalise(previous).equals(normalise(next));
    }

    static boolean shouldSchedule(boolean attached, boolean viewVisible,
                                  boolean windowVisible) {
        return attached && viewVisible && windowVisible;
    }

    static boolean shouldScroll(int contentWidthPx, int availableWidthPx,
                                boolean attachedAndVisible) {
        return attachedAndVisible && contentWidthPx > Math.max(0, availableWidthPx);
    }

    static boolean widthChangeRestarts(int oldWidthPx, int newWidthPx) {
        return oldWidthPx != newWidthPx;
    }
}
