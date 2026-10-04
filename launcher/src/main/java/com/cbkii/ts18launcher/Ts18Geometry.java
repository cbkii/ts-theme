package com.cbkii.ts18launcher;

/** Full-screen landscape geometry. Only proven physical SystemUI bounds use raw px. */
final class Ts18Geometry {
    static final int PHYSICAL_WIDTH = 1280;
    static final int PHYSICAL_HEIGHT = 720;
    static final int SAFE_RIGHT = 1225;
    static final int SAFE_BOTTOM = 702;
    static final int TOP_SYSTEM_INSET = 55;
    static final int BOTTOM_RESERVE = 18;
    static final int HOTSEAT_WIDTH = 96;
    static final int STRIP_HEIGHT = 88;
    static final int GRID_COLUMNS = 12;
    static final int RADIO_COLUMNS = 4;
    static final int MUSIC_COLUMNS = 6;
    // Retain the current targets until the exact device density has been measured.
    static final int SOURCE_TARGET = 80;
    static final int TRANSPORT_TARGET = 84;
    static final int PRIMARY_TARGET = 88;
    static final int CONTROL_WIDTH = SOURCE_TARGET + 2 * TRANSPORT_TARGET + PRIMARY_TARGET;
    static final int DATE_WIDTH = 128;
    static final int RAIL_ENDPOINT_HEIGHT = 80;
    static final int RAIL_QUICK_HEIGHT = 80;

    static final class Layout {
        final int top, railX, contentLeft, contentRight, safeRight, safeBottom, stripHeight;
        final int radioWidth = CONTROL_WIDTH;
        final int musicWidth = CONTROL_WIDTH;
        final boolean radioRight;
        Layout(int top, int railX, int left, int right, int safeRight, int bottom, boolean radioRight) {
            this.top = top; this.railX = railX; contentLeft = left; contentRight = right;
            this.safeRight = safeRight; safeBottom = bottom; stripHeight = STRIP_HEIGHT;
            this.radioRight = radioRight;
        }
        int railWidth() { return HOTSEAT_WIDTH; }
        int leftGroupX() { return contentLeft; }
        int rightGroupX() { return dateX() - CONTROL_WIDTH; }
        int radioX() { return radioRight ? rightGroupX() : leftGroupX(); }
        int musicX() { return radioRight ? leftGroupX() : rightGroupX(); }
        int metadataX() { return contentLeft + CONTROL_WIDTH; }
        int metadataWidth() { return rightGroupX() - metadataX(); }
        int dateX() { return contentRight - DATE_WIDTH; }
        int dateWidth() { return DATE_WIDTH; }
        int mapX() { return contentLeft; }
        int mapY() { return top + stripHeight; }
        int mapWidth() { return contentRight - contentLeft; }
        int mapHeight() { return safeBottom - mapY(); }
        int railHeight() { return safeBottom - top; }
    }

    static final class Inputs {
        final int width, height, left, top, right, bottom;
        final boolean railRight, radioRight;
        Inputs(int width, int height, int left, int top, int right, int bottom,
               boolean railRight, boolean radioRight) {
            this.width = width; this.height = height;
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
            this.railRight = railRight; this.radioRight = radioRight;
        }
        boolean sameAs(Inputs other) {
            return other != null && width == other.width && height == other.height
                    && left == other.left && top == other.top && right == other.right && bottom == other.bottom
                    && railRight == other.railRight && radioRight == other.radioRight;
        }
    }

    static final class ChangeTracker {
        private Inputs applied;
        boolean shouldApply(Inputs inputs) {
            if (inputs.width <= 0 || inputs.height <= 0 || inputs.sameAs(applied)) return false;
            applied = inputs;
            return true;
        }
        void invalidate() { applied = null; }
    }

    static int residualStartInset(int inset, int rootStart) {
        return Math.max(0, inset - rootStart);
    }

    static int residualEndInset(int inset, int rootStart, int rootSize, int windowSize) {
        return Math.max(0, rootStart + rootSize - Math.max(0, windowSize - inset));
    }

    static Layout resolve(Inputs inputs) {
        Layout base = resolve(inputs.width, inputs.height, inputs.railRight, inputs.radioRight);
        int top = Math.max(base.top, inputs.top);
        int safeRight = Math.min(base.safeRight, inputs.width - inputs.right);
        int bottom = Math.min(base.safeBottom, inputs.height - inputs.bottom);
        int left = inputs.left + (inputs.railRight ? 0 : HOTSEAT_WIDTH);
        int right = safeRight - (inputs.railRight ? HOTSEAT_WIDTH : 0);
        return new Layout(top, inputs.railRight ? right : inputs.left, left, right,
                safeRight, bottom, inputs.radioRight);
    }

    private Ts18Geometry() {}
    static Layout resolve(int width, int height) { return resolve(width, height, false); }
    static Layout resolve(int viewWidth, int viewHeight, boolean railRight) {
        return resolve(viewWidth, viewHeight, railRight, false);
    }
    static Layout resolve(int width, int height, boolean railRight, boolean radioRight) {
        return resolveForSidebar(width, height, railRight, radioRight, true);
    }

    /**
     * Exact-device harness profile for comparing Topway's right sidebar shown/hidden.
     * Runtime HOME uses the proven sidebar-visible boundary by default; physical tests
     * may exercise the hidden profile without introducing a generic compact layout.
     */
    static Layout resolveForSidebar(int width, int height, boolean railRight,
                                    boolean radioRight, boolean sidebarVisible) {
        boolean appearsFullPhysical = width >= PHYSICAL_WIDTH - 10 && height >= PHYSICAL_HEIGHT - 20;
        int top = appearsFullPhysical ? TOP_SYSTEM_INSET : 0;
        int safeRight = appearsFullPhysical && sidebarVisible ? Math.min(SAFE_RIGHT, width) : width;
        int bottom = appearsFullPhysical ? Math.min(SAFE_BOTTOM, height) : height - BOTTOM_RESERVE;
        int left = railRight ? 0 : HOTSEAT_WIDTH;
        int right = railRight ? safeRight - HOTSEAT_WIDTH : safeRight;
        return new Layout(top, railRight ? right : 0, left, right, safeRight, bottom, radioRight);
    }
}
