package com.cbkii.ts18launcher;

/** Pure generation gate for delayed marquee callbacks. */
final class SlowMarqueeSchedule {
    static final long HOLD_MS = 5000L;

    private int generation;
    private boolean armed;

    int arm() {
        armed = true;
        return ++generation;
    }

    void cancel() {
        armed = false;
        generation++;
    }

    boolean accepts(int token) {
        return armed && token == generation;
    }
}
