package com.cbkii.ts18launcher;

/** One automatic recreation per map lifecycle; only an explicit retry starts a new attempt. */
final class RendererRecovery {
    private boolean automaticUsed;
    private boolean blocked;
    boolean failed() {
        if (blocked || automaticUsed) { blocked = true; return false; }
        automaticUsed = true;
        return true;
    }
    void creationFailed() { automaticUsed = true; blocked = true; }
    boolean blocked() { return blocked; }
    void retry() { blocked = false; automaticUsed = true; }
}
