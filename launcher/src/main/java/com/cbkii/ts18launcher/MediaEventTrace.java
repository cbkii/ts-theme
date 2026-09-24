package com.cbkii.ts18launcher;

import android.os.SystemClock;
import android.util.Log;

/** Bounded, process-local, non-telemetric fast-media timeline. */
final class MediaEventTrace {
    static final String LOG_TAG = "TS18MediaTrace";
    private static final MediaTraceBuffer BUFFER = new MediaTraceBuffer(96);

    private MediaEventTrace() {}

    static void record(String category, String detail) {
        long now = SystemClock.uptimeMillis();
        BUFFER.add(now, category, detail);
        Log.d(LOG_TAG, now + " " + safe(category) + " " + safe(detail));
    }

    static String dump() {
        return BUFFER.dump(SystemClock.uptimeMillis());
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
