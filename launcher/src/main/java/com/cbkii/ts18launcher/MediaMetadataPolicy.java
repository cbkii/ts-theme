package com.cbkii.ts18launcher;

/** Pure metadata/token decisions shared by session observation and JVM tests. */
final class MediaMetadataPolicy {
    private MediaMetadataPolicy() {}

    static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            String text = value == null ? "" : value.trim();
            if (!text.isEmpty()) return text;
        }
        return "";
    }

    static boolean sameToken(Object first, Object second) {
        return first != null && first.equals(second);
    }

    static boolean semanticSnapshotChanged(
            String oldPackage, String oldTitle, String oldArtist, int oldState, long oldActions,
            String newPackage, String newTitle, String newArtist, int newState, long newActions) {
        return !safe(oldPackage).equals(safe(newPackage))
                || !safe(oldTitle).equals(safe(newTitle))
                || !safe(oldArtist).equals(safe(newArtist))
                || oldState != newState
                || oldActions != newActions;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
