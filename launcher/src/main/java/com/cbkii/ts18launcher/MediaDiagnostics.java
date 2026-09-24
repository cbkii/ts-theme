package com.cbkii.ts18launcher;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import com.cbkii.ts18launcher.platform.TopwayAdapter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Process-local, bounded, non-telemetric media event trace. */
final class MediaDiagnostics {
    static final String LOG_TAG = "TS18MediaTrace";
    private static final int MAX_EVENTS = 96;
    private static final int MAX_DETAIL_CHARS = 240;
    private static final Object LOCK = new Object();
    private static final ArrayDeque<Event> EVENTS = new ArrayDeque<>();
    private static final LinkedHashMap<String, Readiness> READINESS = new LinkedHashMap<>();

    static final class Event {
        final long uptimeMs;
        final String category;
        final String detail;

        Event(long uptimeMs, String category, String detail) {
            this.uptimeMs = uptimeMs;
            this.category = category;
            this.detail = detail;
        }
    }

    static final class Readiness {
        final MediaCommandPolicy.Phase phase;
        final String route;
        final String detail;
        final long uptimeMs;

        Readiness(MediaCommandPolicy.Phase phase, String route, String detail, long uptimeMs) {
            this.phase = phase;
            this.route = route;
            this.detail = detail;
            this.uptimeMs = uptimeMs;
        }
    }

    private MediaDiagnostics() {}

    static void record(String category, String detail) {
        long now = SystemClock.uptimeMillis();
        String safeCategory = clean(category, 40);
        String safeDetail = clean(detail, MAX_DETAIL_CHARS);
        synchronized (LOCK) {
            EVENTS.addLast(new Event(now, safeCategory, safeDetail));
            while (EVENTS.size() > MAX_EVENTS) EVENTS.removeFirst();
        }
        Log.i(LOG_TAG, now + " " + safeCategory + " " + safeDetail);
    }

    static void readiness(String packageName, MediaCommandPolicy.Phase phase,
                          String route, String detail) {
        if (packageName == null || packageName.isEmpty() || phase == null) return;
        long now = SystemClock.uptimeMillis();
        String safeRoute = clean(route, 64);
        String safeDetail = clean(detail, MAX_DETAIL_CHARS);
        synchronized (LOCK) {
            READINESS.put(packageName,
                    new Readiness(phase, safeRoute, safeDetail, now));
            while (READINESS.size() > 8) {
                String first = READINESS.keySet().iterator().next();
                READINESS.remove(first);
            }
        }
        record("readiness", packageName + " phase=" + phase
                + (safeRoute.isEmpty() ? "" : " route=" + safeRoute)
                + (safeDetail.isEmpty() ? "" : " detail=" + safeDetail));
    }

    static String report(Context context) {
        String radio = RadioProvider.resolvePackage(context);
        String music = LauncherPrefs.packageFor(context, LauncherPrefs.KEY_MUSIC);
        if (music.isEmpty()) music = TopwayAdapter.defaultMusicPackage(context);
        StringBuilder out = new StringBuilder();
        out.append("Configured music: ").append(emptyAsNone(music)).append('\n');
        out.append("Configured radio: ").append(emptyAsNone(radio)).append('\n');
        out.append("Audible output: NOT inferred from playback state\n");
        synchronized (LOCK) {
            if (!READINESS.isEmpty()) {
                out.append("\nReadiness\n");
                for (Map.Entry<String, Readiness> entry : READINESS.entrySet()) {
                    Readiness value = entry.getValue();
                    out.append(entry.getKey()).append(": ").append(value.phase);
                    if (!value.route.isEmpty()) out.append(" via ").append(value.route);
                    if (!value.detail.isEmpty()) out.append(" · ").append(value.detail);
                    out.append('\n');
                }
            }
        }
        out.append("\nRecent fast-media timeline\n").append(timeline(40));
        return out.toString();
    }

    static String timeline(int limit) {
        List<Event> copy;
        synchronized (LOCK) {
            copy = new ArrayList<>(EVENTS);
        }
        if (copy.isEmpty()) return "No fast-media events in this launcher process.";
        int start = Math.max(0, copy.size() - Math.max(1, limit));
        long origin = copy.get(start).uptimeMs;
        StringBuilder out = new StringBuilder();
        for (int i = start; i < copy.size(); i++) {
            Event event = copy.get(i);
            out.append(String.format(Locale.US, "+%06dms %-12s %s",
                    event.uptimeMs - origin, event.category, event.detail));
            if (i + 1 < copy.size()) out.append('\n');
        }
        return out.toString();
    }

    static String rootOutcome(RootShell.Result result) {
        if (result == null) return "unavailable";
        if (result.success()) return "success";
        if (!result.completed && result.output != null
                && result.output.toLowerCase(Locale.ROOT).contains("timed out")) return "timeout";
        if (!result.completed) return "unavailable";
        return "denied_or_failed_exit_" + result.exitCode;
    }

    static void clearForTests() {
        synchronized (LOCK) {
            EVENTS.clear();
            READINESS.clear();
        }
    }

    private static String clean(String value, int maxChars) {
        String safe = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        return safe.length() <= maxChars ? safe : safe.substring(0, maxChars);
    }

    private static String emptyAsNone(String value) {
        return value == null || value.isEmpty() ? "(none)" : value;
    }
}
