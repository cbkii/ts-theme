package com.cbkii.ts18launcher;

/** Pure now-playing text policy shared by the runtime view and JVM tests. */
final class MediaTickerPolicy {
    static final class Display {
        final String primary;
        final String secondary;

        Display(String primary, String secondary) {
            this.primary = clean(primary);
            this.secondary = clean(secondary);
        }
    }

    private MediaTickerPolicy() {}

    static Display resolve(String title, String artist, String appLabel, String selectedSource) {
        String primary = clean(title);
        String secondary = clean(artist);
        String label = clean(appLabel);

        if (isSourcePlaceholder(primary, label, selectedSource)) {
            primary = secondary;
            secondary = "";
            if (!label.isEmpty() && label.equalsIgnoreCase(primary)) primary = "";
        } else if (!label.isEmpty() && label.equalsIgnoreCase(secondary)) {
            secondary = "";
        }
        return new Display(primary, secondary);
    }

    static Display resolve(String title, String artist, String appLabel, String selectedSource,
                           String packageName, String radioFrequency) {
        Display normal = resolve(title, artist, appLabel, selectedSource);
        if (MediaSelection.RADIO.equals(selectedSource)) {
            return new Display(join(normal.primary, radioFrequency, normal.secondary), "");
        }
        return normal;
    }

    static String join(String... values) {
        java.util.LinkedHashMap<String, String> distinct = new java.util.LinkedHashMap<>();
        for (String value : values) {
            String text = clean(value);
            if (text.isEmpty() || "-".equals(text) || "--".equals(text)
                    || "unknown".equalsIgnoreCase(text) || "null".equalsIgnoreCase(text)) continue;
            String key = text.toLowerCase(java.util.Locale.ROOT);
            boolean included = false;
            for (String existing : distinct.keySet())
                if ((" " + existing + " ").contains(" " + key + " ")) { included = true; break; }
            if (!included) distinct.putIfAbsent(key, text);
        }
        return String.join(" ", distinct.values());
    }

    private static boolean isSourcePlaceholder(String title, String appLabel, String selectedSource) {
        if (title.isEmpty()) return false;
        if (!appLabel.isEmpty() && title.equalsIgnoreCase(appLabel)) return true;
        if (MediaSelection.RADIO.equals(selectedSource)) {
            return "radio".equalsIgnoreCase(title) || "navradio+".equalsIgnoreCase(title);
        }
        return "music".equalsIgnoreCase(title) || "auxio".equalsIgnoreCase(title);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
