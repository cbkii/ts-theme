package com.cbkii.ts18launcher;

import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Bounded process-local media lifecycle trace.
 *
 * This is deliberately local-only: no persistence, telemetry or remote upload. The ring buffer is
 * suitable for Settings diagnostics while the same events are emitted under the TS18Media log tag
 * so bounded Termux collectors can retain a physical qualification timeline.
 */
final class MediaEventTrace {
    static final String LOG_TAG = "TS18Media";
    private static final int MAX_EVENTS = 192;
    private static final int MAX_DETAIL_CHARS = 240;
    private static final ArrayDeque<Entry> EVENTS = new ArrayDeque<>();

    private MediaEventTrace() {}

    static void record(String surface, String event) {
        record(surface, event, "");
    }

    static void record(String surface, String event, String detail) {
        long elapsed = elapsedRealtime();
        String safeSurface = safe(surface);
        String safeEvent = safe(event);
        String safeDetail = safe(detail);
        synchronized (EVENTS) {
            while (EVENTS.size() >= MAX_EVENTS) EVENTS.removeFirst();
            EVENTS.addLast(new Entry(elapsed, safeSurface, safeEvent, safeDetail));
        }
        StringBuilder line = new StringBuilder()
                .append(elapsed).append("ms ")
                .append(safeSurface).append('/').append(safeEvent);
        if (!safeDetail.isEmpty()) line.append(" · ").append(safeDetail);
        logInfo(line.toString());
    }

    static String dump() {
        return dump(48);
    }

    static String dump(int maxEvents) {
        int limit = Math.max(1, Math.min(MAX_EVENTS, maxEvents));
        List<Entry> snapshot;
        synchronized (EVENTS) {
            snapshot = new ArrayList<>(EVENTS);
        }
        if (snapshot.isEmpty()) return "No media events recorded in this launcher process.";
        int first = Math.max(0, snapshot.size() - limit);
        long base = snapshot.get(first).elapsedMs;
        StringBuilder out = new StringBuilder();
        for (int i = first; i < snapshot.size(); i++) {
            Entry entry = snapshot.get(i);
            if (out.length() > 0) out.append('\n');
            out.append('+').append(entry.elapsedMs - base).append(" ms ")
                    .append(entry.surface).append('/').append(entry.event);
            if (!entry.detail.isEmpty()) out.append(" · ").append(entry.detail);
        }
        return out.toString();
    }

    private static long elapsedRealtime() {
        try {
            return SystemClock.elapsedRealtime();
        } catch (RuntimeException | LinkageError unavailableAndroidRuntime) {
            // Plain JVM tests use android.jar stubs. Keep the trace monotonic there without
            // changing the Android-device authority, which always uses SystemClock.
            return System.nanoTime() / 1_000_000L;
        }
    }

    private static void logInfo(String line) {
        try {
            Log.i(LOG_TAG, line);
        } catch (RuntimeException | LinkageError unavailableAndroidRuntime) {
            // Ring-buffer evidence remains testable when android.util.Log is only a JVM stub.
        }
    }

    private static String safe(String value) {
        if (value == null) return "";
        String cleaned = value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
        if (cleaned.length() <= MAX_DETAIL_CHARS) return cleaned;
        return cleaned.substring(0, MAX_DETAIL_CHARS - 1) + "…";
    }

    private static final class Entry {
        final long elapsedMs;
        final String surface;
        final String event;
        final String detail;

        Entry(long elapsedMs, String surface, String event, String detail) {
            this.elapsedMs = elapsedMs;
            this.surface = surface;
            this.event = event;
            this.detail = detail;
        }
    }
}
