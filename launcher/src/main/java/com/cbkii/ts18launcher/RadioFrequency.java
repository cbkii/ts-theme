package com.cbkii.ts18launcher;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Explicit, unit-bearing radio text only. Never interprets an undocumented vendor integer. */
final class RadioFrequency {
    static final String NAVRADIO = "com.navimods.radio";
    private static final Pattern VALUE = Pattern.compile(
            "(?i)(?<![\\w.])(?:(FM|AM)\\s*(\\d{2,4}(?:\\.\\d{1,3})?)|(\\d{2,4}(?:\\.\\d{1,3})?)\\s*(MHz|kHz|FM|AM))(?![\\w.])");
    final int khz;
    final String band;

    RadioFrequency(int khz, String band) { this.khz = khz; this.band = band; }

    String display() {
        return ("FM".equals(band) ? BigDecimal.valueOf(khz, 3).stripTrailingZeros().toPlainString()
                : Integer.toString(khz)) + " " + band;
    }

    static boolean needsDirectory(String pkg, String title, String subtitle) {
        return NAVRADIO.equals(pkg) && !containsExplicitFrequency(pkg, title)
                && !containsExplicitFrequency(pkg, subtitle);
    }

    static boolean containsExplicitFrequency(String pkg, String value) {
        if (!NAVRADIO.equals(pkg) || value == null || value.length() > 2048) return false;
        Matcher matcher = VALUE.matcher(value);
        while (matcher.find()) if (observed(pkg, matcher.group()) != null) return true;
        return false;
    }

    static RadioFrequency observed(String pkg, String... values) {
        if (!NAVRADIO.equals(pkg)) return null;
        RadioFrequency result = null;
        for (String value : values) {
            if (value == null || value.length() > 2048) continue;
            Matcher m = VALUE.matcher(value);
            while (m.find()) {
                String unit = (m.group(1) == null ? m.group(4) : m.group(1)).toUpperCase(Locale.ROOT);
                String number = m.group(2) == null ? m.group(3) : m.group(2);
                boolean fm = "FM".equals(unit) || "MHZ".equals(unit);
                int khz;
                try { khz = new BigDecimal(number).multiply(BigDecimal.valueOf(fm ? 1000 : 1)).intValueExact(); }
                catch (ArithmeticException | NumberFormatException ignored) { continue; }
                if (fm ? khz < 65000 || khz > 108000 : khz < 500 || khz > 1710) continue;
                RadioFrequency next = new RadioFrequency(khz, fm ? "FM" : "AM");
                if (result != null && !result.display().equals(next.display())) return null;
                result = next;
            }
        }
        return result;
    }
}
