package com.cbkii.ts18launcher;

/** Pure metadata fallback rules shared by session observation and JVM tests. */
final class MediaMetadataPolicy {
    private MediaMetadataPolicy() {}

    static String normalise(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    static String firstNonBlank(CharSequence... values) {
        if (values == null) return "";
        for (CharSequence value : values) {
            String text = normalise(value);
            if (!text.isEmpty()) return text;
        }
        return "";
    }
}
