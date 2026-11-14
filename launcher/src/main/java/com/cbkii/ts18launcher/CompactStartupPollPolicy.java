package com.cbkii.ts18launcher;

/** Framework-free cadence for CompactStartupClient's bounded Maps evidence reads. */
final class CompactStartupPollPolicy {
    private CompactStartupPollPolicy() {}

    /**
     * Next periodic evidence read, or -1 for none: 500 ms while a serving peer has no renderer
     * yet; bounded backoff while the peer is absent (consent-only answers back off to 1.5 s).
     */
    static long delayMs(boolean compatible, boolean consentOnly, boolean rendererReady, int failures) {
        if (compatible && !consentOnly) return rendererReady ? -1L : 500L;
        if (failures <= 0) return -1L;
        return Math.min(consentOnly ? 1500L : 3000L, 500L * failures);
    }

}
