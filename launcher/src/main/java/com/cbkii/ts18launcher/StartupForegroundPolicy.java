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
    static StartupForegroundPolicy fromHelper(NavigationHelperResult result) {
        return result != null && result.success && "FOREGROUND".equals(result.code)
                && result.userId >= 0 && result.taskId > 0 && result.component.contains("/")
                ? new StartupForegroundPolicy(result.userId, result.taskId, result.component) : null;
    }
    static StartupForegroundPolicy parse(String output) {
        Matcher r = RESUMED.matcher(output == null ? "" : output);
        Matcher f = FOCUS.matcher(output == null ? "" : output);
        if (!f.find()) return null;
        try {
            int user = Integer.parseInt(f.group(1));
            String component = normalise(f.group(2));
            if (f.find()) return null;
            // Android Q can resume HOME and the freeform map simultaneously. Window focus
            // chooses the candidate; a unique matching resumed task corroborates it.
            int task = -1;
            while (r.find()) {
                if (user != Integer.parseInt(r.group(1)) || !component.equals(normalise(r.group(2)))) continue;
                int candidate = Integer.parseInt(r.group(3));
                if (candidate <= 0 || task > 0 && task != candidate) return null;
                task = candidate;
            }
            return task > 0 ? new StartupForegroundPolicy(user, task, component) : null;
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
        value = value.replace("com.tw.media/com.tw.music.MusicActivity", "com.tw.media/org.oxycblt.auxio.MainActivity");
        value = value.replace("com.cbkii.ts18launcher/.HomeAlias", "com.cbkii.ts18launcher/.LauncherActivity")
                .replace("com.cbkii.ts18launcher/com.cbkii.ts18launcher.HomeAlias", "com.cbkii.ts18launcher/.LauncherActivity");
        slash = value.indexOf('/');
        return slash >= 0 && slash + 1 < value.length() && value.charAt(slash + 1) == '.'
                ? value.substring(0, slash + 1) + value.substring(0, slash) + value.substring(slash + 1)
                : value;
    }
}
