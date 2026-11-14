package com.cbkii.ts18launcher.navigation.n3;

/** Immutable view of one N3 session for the UI. Never mutated after publication. */
public final class N3Snapshot {
    public final N3State state;
    public final String detail;
    public final long generation;
    public final String component;
    public final String authority;
    public final int displayId;
    public final int displayWidth, displayHeight;
    /** Pixels from the launched content were observed on the display at least once. */
    public final boolean pixelsObserved;
    /** Empty when input can reach the display; otherwise why it cannot (view-only). */
    public final String inputUnavailable;
    public final boolean expanded;

    N3Snapshot(N3State state, String detail, long generation, String component, String authority,
               int displayId, int displayWidth, int displayHeight, boolean pixelsObserved,
               String inputUnavailable, boolean expanded) {
        this.state = state;
        this.detail = detail == null ? "" : detail;
        this.generation = generation;
        this.component = component == null ? "" : component;
        this.authority = authority == null ? "" : authority;
        this.displayId = displayId;
        this.displayWidth = displayWidth;
        this.displayHeight = displayHeight;
        this.pixelsObserved = pixelsObserved;
        this.inputUnavailable = inputUnavailable == null ? "" : inputUnavailable;
        this.expanded = expanded;
    }

    /** STATIC_POC_READY / BLOCKED_NO_AUTHORITY / HOST_UNAVAILABLE / RENDER_OBSERVED / ... never DEVICE_FEASIBILITY_PASS. */
    public String classification() {
        if (detail.startsWith(PublicDisplayPath.BLOCKED)) return "BLOCKED_NO_AUTHORITY";
        if (detail.startsWith("HOST_UNAVAILABLE") || state == N3State.HOST_UNAVAILABLE) return "HOST_UNAVAILABLE";
        if (state == N3State.INTERACTIVE && pixelsObserved) {
            return inputUnavailable.isEmpty() ? "RENDER_AND_INPUT_PATH_OBSERVED" : "RENDER_OBSERVED_VIEW_ONLY";
        }
        if (state == N3State.FAILED) return "FAILED";
        return "STATIC_POC_READY";
    }
}
