package com.cbkii.ts18launcher;

import java.util.HashMap;
import java.util.Map;

/** Small parser for the machine-readable root-helper response. */
final class NavigationHelperResult {
    final boolean success;
    final String code;
    final int taskId;
    final int stackId;
    final String packageName;
    final String component;
    final String bounds;
    final String raw;

    private NavigationHelperResult(boolean success, String code, int taskId, int stackId,
            String packageName, String component, String bounds, String raw) {
        this.success = success;
        this.code = code;
        this.taskId = taskId;
        this.stackId = stackId;
        this.packageName = packageName;
        this.component = component;
        this.bounds = bounds;
        this.raw = raw;
    }

    static NavigationHelperResult parse(String output) {
        String raw = output == null ? "" : output.trim();
        String line = lastProtocolLine(raw);
        if (line.isEmpty()) return failure(raw.isEmpty() ? "NO_RESPONSE" : "BAD_RESPONSE", raw);
        String[] pieces = line.split("\\s+");
        boolean ok = "OK".equals(pieces[0]);
        boolean fail = "FAIL".equals(pieces[0]);
        if (!ok && !fail) return failure("BAD_RESPONSE", raw);

        Map<String, String> values = new HashMap<>();
        for (int i = 1; i < pieces.length; i++) {
            int equals = pieces[i].indexOf('=');
            if (equals <= 0) continue;
            values.put(pieces[i].substring(0, equals), pieces[i].substring(equals + 1));
        }
        String code = values.containsKey("code") ? values.get("code") : (ok ? "OK" : "UNKNOWN");
        return new NavigationHelperResult(ok, code,
                parsePositive(values.get("task")), parsePositive(values.get("stack")),
                safe(values.get("package")), safe(values.get("component")), safe(values.get("bounds")), raw);
    }

    static NavigationHelperResult failure(String code, String raw) {
        return new NavigationHelperResult(false, code, -1, -1, "", "", "", raw == null ? "" : raw);
    }

    private static String lastProtocolLine(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String[] lines = raw.split("\\r?\\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.startsWith("OK ") || line.equals("OK") || line.startsWith("FAIL ") || line.equals("FAIL")) return line;
        }
        return "";
    }

    private static int parsePositive(String value) {
        if (value == null) return -1;
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= 0 ? parsed : -1;
        } catch (NumberFormatException ignored) { return -1; }
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
