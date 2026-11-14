package com.cbkii.ts18launcher.navigation.n3;

/**
 * Maps touches on the launcher's N3 presenter view to the virtual display's coordinate space.
 * The display is aspect-fitted and centred inside the view's content area (the view minus runtime
 * insets). A gesture belongs to the display only if its first pointer goes down inside the fitted
 * image; later pointers and moves are clamped to the display, and a gesture that started outside
 * is dropped as a whole so the navigator never sees half a gesture. Pure and framework-free.
 */
public final class N3InputMapper {
    public static final int MAX_POINTERS = 10;

    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_MOVE = 2;
    public static final int ACTION_CANCEL = 3;
    public static final int ACTION_POINTER_DOWN = 5;
    public static final int ACTION_POINTER_UP = 6;

    private final float scale;
    private final float originX, originY;
    private final int displayWidth, displayHeight;
    private boolean gestureOwned;

    /**
     * View size and content insets in view pixels; display size in display pixels. Invalid
     * geometry produces a mapper that drops every event.
     */
    public N3InputMapper(int viewWidth, int viewHeight, int insetLeft, int insetTop, int insetRight,
                         int insetBottom, int displayWidth, int displayHeight) {
        int contentW = viewWidth - insetLeft - insetRight;
        int contentH = viewHeight - insetTop - insetBottom;
        this.displayWidth = displayWidth;
        this.displayHeight = displayHeight;
        if (contentW <= 0 || contentH <= 0 || displayWidth <= 0 || displayHeight <= 0) {
            scale = 0f;
            originX = originY = 0f;
            return;
        }
        scale = Math.min(contentW / (float) displayWidth, contentH / (float) displayHeight);
        originX = insetLeft + (contentW - displayWidth * scale) / 2f;
        originY = insetTop + (contentH - displayHeight * scale) / 2f;
    }

    /** One mapped event; null from map() means the event must not reach the display. */
    public static final class Mapped {
        public final int action;
        public final int[] ids;
        public final float[] xs, ys;

        Mapped(int action, int[] ids, float[] xs, float[] ys) {
            this.action = action;
            this.ids = ids;
            this.xs = xs;
            this.ys = ys;
        }
    }

    public boolean valid() { return scale > 0f; }

    public float scale() { return scale; }
    public float originX() { return originX; }
    public float originY() { return originY; }
    public int displayWidth() { return displayWidth; }
    public int displayHeight() { return displayHeight; }

    /** True when a gesture that went down at (x, y) belongs to the display. */
    public boolean owns(float x, float y) { return valid() && inside(x, y); }

    /** Display rectangle inside the view, as left, top, right, bottom. */
    public float[] fittedBounds() {
        return new float[] {originX, originY, originX + displayWidth * scale, originY + displayHeight * scale};
    }

    public Mapped map(int action, int[] ids, float[] xs, float[] ys) {
        if (!valid() || ids == null || xs == null || ys == null) return drop();
        int count = ids.length;
        if (count == 0 || count > MAX_POINTERS || xs.length != count || ys.length != count) return drop();
        if (action == ACTION_DOWN) {
            // A second down means the previous sequence was interrupted: end the owned gesture
            // through the cancel path instead of orphaning its pressed pointer on the display.
            // The replacement sequence is not accepted; the next down after it starts fresh.
            if (gestureOwned) return drop();
            gestureOwned = inside(xs[0], ys[0]);
            if (!gestureOwned) return null;
        } else if (!gestureOwned) {
            return null;
        }
        float[] mx = new float[count];
        float[] my = new float[count];
        for (int i = 0; i < count; i++) {
            if (ids[i] < 0 || Float.isNaN(xs[i]) || Float.isNaN(ys[i])) return drop();
            mx[i] = clamp((xs[i] - originX) / scale, displayWidth - 1);
            my[i] = clamp((ys[i] - originY) / scale, displayHeight - 1);
        }
        Mapped mapped = new Mapped(action, ids.clone(), mx, my);
        if (action == ACTION_UP || action == ACTION_CANCEL) gestureOwned = false;
        return mapped;
    }

    /** A malformed event ends any owned gesture with a cancel so the display is not left pressed. */
    private Mapped drop() {
        if (!gestureOwned) return null;
        gestureOwned = false;
        return new Mapped(ACTION_CANCEL, new int[] {0}, new float[] {0f}, new float[] {0f});
    }

    private boolean inside(float x, float y) {
        float[] b = fittedBounds();
        return x >= b[0] && x < b[2] && y >= b[1] && y < b[3];
    }

    private static float clamp(float value, float max) {
        return value < 0f ? 0f : Math.min(value, max);
    }
}
