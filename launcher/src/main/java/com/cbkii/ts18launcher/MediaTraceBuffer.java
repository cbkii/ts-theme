package com.cbkii.ts18launcher;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Pure bounded ring used by process-local media diagnostics and JVM tests. */
final class MediaTraceBuffer {
    static final class Entry {
        final long uptimeMs;
        final String category;
        final String detail;

        Entry(long uptimeMs, String category, String detail) {
            this.uptimeMs = uptimeMs;
            this.category = category;
            this.detail = detail;
        }
    }

    private static final int MAX_FIELD_CHARS = 180;
    private final int capacity;
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();

    MediaTraceBuffer(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    synchronized void add(long uptimeMs, String category, String detail) {
        while (entries.size() >= capacity) entries.removeFirst();
        entries.addLast(new Entry(Math.max(0L, uptimeMs), clean(category), clean(detail)));
    }

    synchronized List<Entry> snapshot() {
        return new ArrayList<>(entries);
    }

    synchronized String dump(long nowUptimeMs) {
        if (entries.isEmpty()) return "No media events recorded in this launcher process.";
        StringBuilder text = new StringBuilder();
        long origin = entries.peekFirst().uptimeMs;
        for (Entry entry : entries) {
            if (text.length() > 0) text.append('\n');
            long relative = Math.max(0L, entry.uptimeMs - origin);
            long age = Math.max(0L, nowUptimeMs - entry.uptimeMs);
            text.append('+').append(relative).append("ms")
                    .append(" age=").append(age).append("ms ")
                    .append(entry.category);
            if (!entry.detail.isEmpty()) text.append(" · ").append(entry.detail);
        }
        return text.toString();
    }

    private static String clean(String value) {
        String text = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        if (text.length() <= MAX_FIELD_CHARS) return text;
        return text.substring(0, MAX_FIELD_CHARS - 1) + "…";
    }
}
