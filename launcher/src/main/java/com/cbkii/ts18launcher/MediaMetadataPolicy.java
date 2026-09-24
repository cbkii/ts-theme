package com.cbkii.ts18launcher;

/** Pure metadata normalisation/change policy shared by runtime rendering and JVM tests. */
final class MediaMetadataPolicy {
    static final class Change {
        final String primary;
        final String secondary;
        final boolean primaryChanged;
        final boolean secondaryChanged;

        Change(String primary, String secondary, boolean primaryChanged, boolean secondaryChanged) {
            this.primary = primary;
            this.secondary = secondary;
            this.primaryChanged = primaryChanged;
            this.secondaryChanged = secondaryChanged;
        }
    }

    private MediaMetadataPolicy() {}

    static Change update(String previousPrimary, String previousSecondary,
                         String nextPrimary, String nextSecondary) {
        String primary = normalise(nextPrimary);
        String secondary = normalise(nextSecondary);
        return new Change(primary, secondary,
                previousPrimary == null || !normalise(previousPrimary).equals(primary),
                previousSecondary == null || !normalise(previousSecondary).equals(secondary));
    }

    static String firstNonBlank(CharSequence... values) {
        if (values == null) return "";
        for (CharSequence value : values) {
            String text = value == null ? "" : value.toString().trim();
            if (!text.isEmpty()) return text;
        }
        return "";
    }

    private static String normalise(String value) {
        return value == null ? "" : value.trim();
    }
}
