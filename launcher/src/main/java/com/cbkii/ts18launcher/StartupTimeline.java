package com.cbkii.ts18launcher;

import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Process-local startup phase timeline. Each phase is recorded once per process, on the
 * elapsed-since-boot clock, with its offset from process start, and is emitted under the
 * TS18Startup log tag for bounded collectors. Spans time synchronous main-thread work so a slow
 * step is attributed to its owner rather than guessed. Local only: nothing is persisted or sent.
 */
final class StartupTimeline {
    static final String LOG_TAG = "TS18Startup";
    private static final int MAX_PHASES = 64;
    private static final Map<String, Long> PHASES = new LinkedHashMap<>();
    private static long processStartElapsed = -1L;
    private static String bootId;

    private StartupTimeline() {}

    interface Clock {
        long elapsed();
        long processStart();
    }

    static Clock clock = new Clock() {
        @Override public long elapsed() { return SystemClock.elapsedRealtime(); }
        @Override public long processStart() { return Process.getStartElapsedRealtime(); }
    };

    /** Records the first occurrence of a phase. Returns true when this call recorded it. */
    static boolean mark(String phase) {
        return mark(phase, "");
    }

    static boolean mark(String phase, String detail) {
        long now = clock.elapsed();
        String line;
        synchronized (PHASES) {
            if (PHASES.containsKey(phase) || PHASES.size() >= MAX_PHASES) return false;
            if (processStartElapsed < 0L) {
                processStartElapsed = clock.processStart();
                String id = bootId();
                emit("phase=process.start elapsed=" + processStartElapsed + " process=0"
                        + (id.isEmpty() ? "" : " boot_id=" + id));
            }
            PHASES.put(phase, now);
            line = "phase=" + phase + " elapsed=" + now + " process=" + (now - processStartElapsed)
                    + (detail == null || detail.isEmpty() ? "" : " " + detail);
        }
        emit(line);
        MediaEventTrace.record("startup-phase", phase, "+" + sinceProcessStart(now) + "ms"
                + (detail == null || detail.isEmpty() ? "" : " " + detail));
        return true;
    }

    /** Times one synchronous step and reports its duration. The step's own failures propagate. */
    static void span(String name, Runnable step) {
        long begin = clock.elapsed();
        try {
            step.run();
        } finally {
            long duration = clock.elapsed() - begin;
            emit("span=" + name + " ms=" + duration + " process=" + sinceProcessStart(begin));
            if (duration >= 50L) MediaEventTrace.record("startup-phase", "slow-span", name + " " + duration + "ms");
        }
    }

    /** Offset of a recorded phase from process start, or -1 when not recorded. */
    static long offset(String phase) {
        synchronized (PHASES) {
            Long at = PHASES.get(phase);
            return at == null || processStartElapsed < 0L ? -1L : at - processStartElapsed;
        }
    }

    static String dump() {
        StringBuilder out = new StringBuilder();
        synchronized (PHASES) {
            if (PHASES.isEmpty()) return "No startup phases recorded in this launcher process.";
            for (Map.Entry<String, Long> entry : PHASES.entrySet()) {
                out.append('+').append(entry.getValue() - processStartElapsed).append("ms ")
                        .append(entry.getKey()).append('\n');
            }
        }
        return out.toString().trim();
    }

    private static long sinceProcessStart(long at) {
        synchronized (PHASES) {
            return processStartElapsed < 0L ? -1L : at - processStartElapsed;
        }
    }

    static void resetForTest() {
        synchronized (PHASES) {
            PHASES.clear();
            processStartElapsed = -1L;
        }
    }

    private static String bootId() {
        if (bootId != null) return bootId;
        String value = "";
        try (InputStream in = new FileInputStream("/proc/sys/kernel/random/boot_id")) {
            byte[] buffer = new byte[64];
            int n = in.read(buffer);
            if (n > 0) value = new String(buffer, 0, n, StandardCharsets.US_ASCII).trim();
        } catch (Exception unreadable) {
            value = "";
        }
        bootId = value.matches("[0-9a-f-]{36}") ? value : "";
        return bootId;
    }

    private static void emit(String line) {
        try {
            Log.i(LOG_TAG, line);
        } catch (RuntimeException unavailableInHostTests) {
            // android.util.Log is not implemented on the plain JVM.
        }
    }
}
