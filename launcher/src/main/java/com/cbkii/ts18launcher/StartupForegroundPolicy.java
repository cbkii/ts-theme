package com.cbkii.ts18launcher;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Read-only API29 foreground evidence. Missing, contradictory or multi-display evidence yields. */
final class StartupForegroundPolicy {
    private static final Pattern RESUMED = Pattern.compile(
            "mResumedActivity: ActivityRecord\\{[^\\n]*? u(\\d+) ([A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+) t(\\d+)[^\\n]*");
    private static final Pattern FOCUS = Pattern.compile(
            "mCurrentFocus=Window\\{[^\\n]*? u(\\d+) ([A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+)[^\\n]*");
    final int user;
    final int task;
    final String component;
    private StartupForegroundPolicy(int user, int task, String component) {
        this.user = user; this.task = task; this.component = normalise(component);
    }
    static StartupForegroundPolicy parse(String output) {
        Matcher r = RESUMED.matcher(output == null ? "" : output);
        Matcher f = FOCUS.matcher(output == null ? "" : output);
        if (!r.find() || !f.find()) return null;
        try {
            int user = Integer.parseInt(r.group(1)), task = Integer.parseInt(r.group(3));
            String component = normalise(r.group(2));
            if (user != Integer.parseInt(f.group(1)) || !component.equals(normalise(f.group(2)))
                    || task <= 0 || r.find() || f.find()) return null;
            return new StartupForegroundPolicy(user, task, component);
        } catch (NumberFormatException ignored) { return null; }
    }
    boolean matches(int expectedUser, int expectedTask, String expectedComponent) {
        return user == expectedUser && task == expectedTask && component.equals(normalise(expectedComponent));
    }
    boolean isEntry(int expectedUser, String entry) {
        return user == expectedUser && component.equals(normalise(entry));
    }
    private static String normalise(String value) {
        if (value == null) return "";
        int slash = value.indexOf('/');
        value = value.replace("com.cbkii.ts18launcher/.HomeAlias", "com.cbkii.ts18launcher/.LauncherActivity")
                .replace("com.cbkii.ts18launcher/com.cbkii.ts18launcher.HomeAlias", "com.cbkii.ts18launcher/.LauncherActivity");
        slash = value.indexOf('/');
        return slash >= 0 && slash + 1 < value.length() && value.charAt(slash + 1) == '.'
                ? value.substring(0, slash + 1) + value.substring(0, slash) + value.substring(slash + 1)
                : value;
    }
}
