package com.cbkii.ts18launcher;

/** Pure marquee decisions so content/reset behaviour can be regression-tested without Android UI. */
final class SlowMarqueePolicy {
    private SlowMarqueePolicy() {}

    static boolean contentChanged(CharSequence current, CharSequence next) {
        String currentText = current == null ? "" : current.toString();
        String nextText = next == null ? "" : next.toString();
        return !currentText.equals(nextText);
    }

    static int overflowPx(int contentWidthPx, int availableWidthPx) {
        return Math.max(0, contentWidthPx - Math.max(0, availableWidthPx));
    }

    static boolean shouldAnimate(int contentWidthPx, int availableWidthPx, boolean shown) {
        return shown && overflowPx(contentWidthPx, availableWidthPx) > 0;
    }
}
