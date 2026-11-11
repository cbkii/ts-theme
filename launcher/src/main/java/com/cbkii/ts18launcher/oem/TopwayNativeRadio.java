package com.cbkii.ts18launcher.oem;

/**
 * Boundary for native Topway tuner commands. Every operation is refused: no caller authority,
 * source arbitration, reversible exercise or owner approval has been established, so there is
 * deliberately no transport behind this class. Radio control goes through the selected radio
 * app's own MediaSession (NavRadio+ or the stock radio session) instead.
 */
public final class TopwayNativeRadio {
    private TopwayNativeRadio() {}

    public enum Operation { NEXT_STATION, PREVIOUS_STATION, SEEK_UP, SEEK_DOWN, TUNE }

    static final String REASON = "native tuner commands not qualified: caller authority, source arbitration, "
            + "reversible test and owner approval are not established";

    public static Capability capability() {
        return new Capability("radio.topway.native", Capability.Status.BLOCKED, "policy", REASON);
    }

    /** Always BLOCKED; nothing is sent. */
    public static Capability command(Operation operation) {
        return new Capability("radio.topway.native." + operation.name().toLowerCase(java.util.Locale.ROOT),
                Capability.Status.BLOCKED, "policy", REASON);
    }
}
