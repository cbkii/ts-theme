package com.cbkii.ts18launcher.navigation.n3;

/** States of one N3 virtual-display navigation session, in roadmap order. */
public enum N3State {
    DISABLED,
    HOST_UNAVAILABLE,
    HOST_CONNECTING,
    DISPLAY_CREATING,
    APP_LAUNCHING,
    RENDER_AWAITING,
    INTERACTIVE,
    SURFACE_DETACHED,
    REATTACHING,
    RECOVERING,
    FAILED,
    SHUTDOWN;

    /** True while the session holds or is acquiring a display. */
    public boolean holdsDisplay() {
        switch (this) {
            case DISPLAY_CREATING: case APP_LAUNCHING: case RENDER_AWAITING: case INTERACTIVE:
            case SURFACE_DETACHED: case REATTACHING: case RECOVERING:
                return true;
            default:
                return false;
        }
    }
}
