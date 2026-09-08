package com.cbkii.ts18launcher;

final class Ts18Geometry {
    static final int PHYSICAL_WIDTH = 1280;
    static final int PHYSICAL_HEIGHT = 720;
    static final int SAFE_RIGHT = 1225;
    static final int SAFE_BOTTOM = 702;
    static final int TOP_SYSTEM_INSET = 55;
    static final int BOTTOM_RESERVE = 18;
    static final int HOTSEAT_WIDTH = 81;
    static final int STRIP_HEIGHT = 64;
    static final int RADIO_WIDTH = 286;
    static final int MUSIC_WIDTH = 680;

    static final class Layout {
        final int top;
        final int left;
        final int safeRight;
        final int safeBottom;
        final int stripHeight;
        final int radioWidth;
        final int musicWidth;

        Layout(int top, int left, int safeRight, int safeBottom, int stripHeight,
               int radioWidth, int musicWidth) {
            this.top = top;
            this.left = left;
            this.safeRight = safeRight;
            this.safeBottom = safeBottom;
            this.stripHeight = stripHeight;
            this.radioWidth = radioWidth;
            this.musicWidth = musicWidth;
        }

        int radioX() { return left; }
        int musicX() { return left + radioWidth; }
        int dateX() { return musicX() + musicWidth; }
        int dateWidth() { return Math.max(1, safeRight - dateX()); }
        int mapX() { return left; }
        int mapY() { return top + stripHeight; }
        int mapWidth() { return Math.max(1, safeRight - left); }
        int mapHeight() { return Math.max(1, safeBottom - mapY()); }
        int railHeight() { return Math.max(1, safeBottom - top); }
    }

    private Ts18Geometry() {}

    static Layout resolve(int viewWidth, int viewHeight) {
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
        int left = Math.max(1, Math.round(HOTSEAT_WIDTH * xScale));
        int radioWidth = Math.max(1, Math.round(RADIO_WIDTH * xScale));
        int musicWidth = Math.max(1, Math.round(MUSIC_WIDTH * xScale));

        int dateX = left + radioWidth + musicWidth;
        if (dateX >= safeRight) {
            int available = Math.max(3, safeRight - left);
            radioWidth = Math.max(1, Math.round(available * (RADIO_WIDTH / 1144.0f)));
            musicWidth = Math.max(1, available - radioWidth - 1);
        }

        return new Layout(top, left, safeRight, safeBottom, STRIP_HEIGHT, radioWidth, musicWidth);
    }
}
