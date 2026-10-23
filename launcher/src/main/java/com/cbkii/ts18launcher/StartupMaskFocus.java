package com.cbkii.ts18launcher;

/** One process-owned mask capability, carried only on the existing private root pipes. */
final class StartupMaskFocus {
    static volatile String activeTitle = "";

    static boolean validTitle(String title) {
        return title != null && title.matches("TS18Ready_[0-9]{5,9}_[a-f0-9]{32}");
    }

    static boolean ownsFocusedWindow(String dump, String title, int user) {
        if (!validTitle(title) || dump == null || dump.length() > 524288) return false;
        int uid;
        try { uid = Integer.parseInt(title.split("_")[1]); }
        catch (RuntimeException invalid) { return false; }
        if (uid / 100000 != user || uid % 100000 < 10000) return false;
        java.util.regex.Matcher focus = java.util.regex.Pattern.compile(
                "mCurrentFocus=Window\\{([a-fA-F0-9]+) u" + user + " "
                        + java.util.regex.Pattern.quote(title) + "\\}").matcher(dump);
        if (!focus.find()) return false;
        String id = focus.group(1);
        if (focus.find()) return false;
        java.util.regex.Matcher all = java.util.regex.Pattern.compile("mCurrentFocus=").matcher(dump);
        int count = 0; while (all.find()) count++;
        if (count != 1) return false;
        java.util.regex.Matcher block = java.util.regex.Pattern.compile(
                "(?ms)^\\s*Window #\\d+ Window\\{" + id + " u" + user + " "
                        + java.util.regex.Pattern.quote(title) + "\\}:?(.*?)(?=^\\s*Window #|\\z)").matcher(dump);
        return block.find() && java.util.regex.Pattern.compile("mOwnerUid=" + uid + "(?:\\s|$)").matcher(block.group(1)).find()
                && java.util.regex.Pattern.compile("ty=(?:2038|APPLICATION_OVERLAY)(?:\\s|[}])").matcher(block.group(1)).find();
    }

    static String focusedWindow(String displays) {
        if (displays == null || displays.length() > 524288) return "";
        java.util.regex.Matcher focus = java.util.regex.Pattern.compile("mCurrentFocus=([^\\r\\n]+)").matcher(displays);
        if (!focus.find()) return "";
        String window = focus.group(1).trim();
        return !focus.find() && window.startsWith("Window{") && window.endsWith("}") ? window : "";
    }

    static boolean ownsFocusedWindow(String displays, String windows, String title, int user) {
        if (windows == null) return false;
        // Only displays selects focus. Some Android builds repeat focus in the window dump.
        String owners = windows.replaceAll("(?m)^.*mCurrentFocus=.*$", "");
        return ownsFocusedWindow(displays + "\n" + owners, title, user);
    }
}
