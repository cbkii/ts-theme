package com.cbkii.ts18launcher;

import android.content.Context;
import android.graphics.Color;

/** Small Material-derived accent palette. Only the launcher's accent/orange roles use this hue. */
final class AccentPalette {
    static final String ORANGE = "orange";
    static final String AMBER = "amber";
    static final String LIME = "lime";
    static final String GREEN = "green";
    static final String TEAL = "teal";
    static final String CYAN = "cyan";
    static final String BLUE = "blue";
    static final String PURPLE = "purple";
    static final String PINK = "pink";
    static final String RED = "red";

    static final String[] VALUES = {ORANGE, AMBER, LIME, GREEN, TEAL, CYAN, BLUE, PURPLE, PINK, RED};
    static final String[] LABELS = {"Orange", "Amber", "Lime", "Green", "Teal", "Cyan", "Blue", "Purple", "Pink", "Red"};

    private static final int[] BASE = {
            0xFFFF7043, 0xFFFFC107, 0xFFCDDC39, 0xFF66BB6A, 0xFF4DB6AC,
            0xFF26C6DA, 0xFF42A5F5, 0xFFBA68C8, 0xFFF06292, 0xFFEF5350
    };
    private static final int[] DARK = {
            0xFFF4511E, 0xFFFFB300, 0xFFAFB42B, 0xFF43A047, 0xFF00897B,
            0xFF00ACC1, 0xFF1E88E5,
            // Purple/Pink 600 fall below 4.5:1 against the black primary glyph; keep their
            // Material base tones for pressed/focused fills rather than sacrificing contrast.
            0xFFBA68C8, 0xFFF06292, 0xFFE53935
    };

    private AccentPalette() {}

    static boolean isKnown(String value) {
        if (value == null) return false;
        for (String candidate : VALUES) if (candidate.equals(value)) return true;
        return false;
    }

    static String safe(String value) { return isKnown(value) ? value : ORANGE; }

    static String label(String value) {
        String safe = safe(value);
        for (int i = 0; i < VALUES.length; i++) if (VALUES[i].equals(safe)) return LABELS[i];
        return "Orange";
    }

    static int color(Context context, boolean dark) {
        String selected = safe(UiPersonalizationPrefs.accentHue(context));
        for (int i = 0; i < VALUES.length; i++) if (VALUES[i].equals(selected)) return dark ? DARK[i] : BASE[i];
        return dark ? DARK[0] : BASE[0];
    }

    static String css(Context context) {
        return String.format(java.util.Locale.US, "#%06X", color(context, false) & 0xFFFFFF);
    }

    static double blackContrast(Context context) {
        int value = color(context, false);
        double r = linear(Color.red(value) / 255.0);
        double g = linear(Color.green(value) / 255.0);
        double b = linear(Color.blue(value) / 255.0);
        double luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        return (luminance + 0.05) / 0.05;
    }

    private static double linear(double channel) {
        return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }
}
