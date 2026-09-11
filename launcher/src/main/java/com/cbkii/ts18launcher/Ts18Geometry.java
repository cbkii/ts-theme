package com.cbkii.ts18launcher;

final class Ts18Geometry {
    static final int PHYSICAL_WIDTH = 1280;
    static final int PHYSICAL_HEIGHT = 720;
    static final int SAFE_RIGHT = 1225;
    static final int SAFE_BOTTOM = 702;
    static final int TOP_SYSTEM_INSET = 55;
    static final int BOTTOM_RESERVE = 18;
    static final int HOTSEAT_WIDTH = 96;
    static final int STRIP_HEIGHT = 88;
    static final int RADIO_WIDTH = 406;
    static final int MUSIC_WIDTH = 560;

    static final class Layout {
        final int top;
        final int railX;
        final int contentLeft;
        final int contentRight;
        final int safeRight;
        final int safeBottom;
        final int stripHeight;
        final int radioWidth;
        final int musicWidth;

        Layout(int top, int railX, int contentLeft, int contentRight, int safeRight,
               int safeBottom, int stripHeight, int radioWidth, int musicWidth) {
            this.top = top;
            this.railX = railX;
            this.contentLeft = contentLeft;
            this.contentRight = contentRight;
            this.safeRight = safeRight;
            this.safeBottom = safeBottom;
            this.stripHeight = stripHeight;
            this.radioWidth = radioWidth;
            this.musicWidth = musicWidth;
        }

        int railWidth() { return HOTSEAT_WIDTH; }
        int radioX() { return contentLeft; }
        int musicX() { return contentLeft + radioWidth; }
        int dateX() { return musicX() + musicWidth; }
        int dateWidth() { return Math.max(1, contentRight - dateX()); }
        int mapX() { return contentLeft; }
        int mapY() { return top + stripHeight; }
        int mapWidth() { return Math.max(1, contentRight - contentLeft); }
        int mapHeight() { return Math.max(1, safeBottom - mapY()); }
        int railHeight() { return Math.max(1, safeBottom - top); }
    }

    private Ts18Geometry() {}

    static Layout resolve(int viewWidth, int viewHeight) {
        return resolve(viewWidth, viewHeight, false);
    }

    static Layout resolve(int viewWidth, int viewHeight, boolean railRight) {
        int width = Math.max(1, viewWidth);
        int height = Math.max(1, viewHeight);

        boolean appearsFullPhysical =
                width >= PHYSICAL_WIDTH - 10 && height >= PHYSICAL_HEIGHT - 20;
        int top = appearsFullPhysical ? TOP_SYSTEM_INSET : 0;
        int safeRight = appearsFullPhysical ? Math.min(SAFE_RIGHT, width) : width;
        int safeBottom = appearsFullPhysical
                ? Math.min(SAFE_BOTTOM, height)
                : Math.max(top + STRIP_HEIGHT + 1, height - BOTTOM_RESERVE);

        float xScale = Math.min(1.0f, safeRight / (float) SAFE_RIGHT);
        int railWidth = Math.max(1, Math.round(HOTSEAT_WIDTH * xScale));
        int contentLeft = railRight ? 0 : railWidth;
        int contentRight = railRight ? Math.max(1, safeRight - railWidth) : safeRight;
        int railX = railRight ? contentRight : 0;
        int available = Math.max(3, contentRight - contentLeft);

        int radioWidth = Math.max(1, Math.round(RADIO_WIDTH * xScale));
        int musicWidth = Math.max(1, Math.round(MUSIC_WIDTH * xScale));
        if (radioWidth + musicWidth >= available) {
            radioWidth = Math.max(1, Math.round(available * (RADIO_WIDTH / 966.0f)));
            musicWidth = Math.max(1, available - radioWidth - 1);
        }

        return new Layout(top, railX, contentLeft, contentRight, safeRight, safeBottom,
                STRIP_HEIGHT, radioWidth, musicWidth);
    }
}
