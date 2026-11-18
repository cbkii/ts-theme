package com.cbkii.ts18launcher.navigation.n3;

import android.graphics.Matrix;
import android.view.MotionEvent;

/**
 * Converts presenter-view MotionEvents into display-space MotionEvents. A copy of the original is
 * transformed, so pointer IDs, action and action index, down/event time and historical samples
 * are preserved exactly. A gesture belongs to the display only if its first pointer went down on
 * the fitted image. Coordinates outside the display (a finger dragged off the image) are clamped
 * in a rebuilt event without history. A malformed sequence ends with one cancel. The caller
 * recycles the returned event.
 */
public final class N3MotionConverter {
    private final N3InputMapper mapper;
    private final Matrix toDisplay = new Matrix();
    private boolean owned;

    public N3MotionConverter(N3InputMapper mapper) {
        this.mapper = mapper;
        if (mapper.valid()) {
            toDisplay.setTranslate(-mapper.originX(), -mapper.originY());
            toDisplay.postScale(1f / mapper.scale(), 1f / mapper.scale());
        }
    }

    public boolean owned() { return owned; }

    /** Returns a new display-space event, or null when the event must not reach the display. */
    public MotionEvent convert(MotionEvent view) {
        if (view == null || !mapper.valid()) return null;
        int action = view.getActionMasked();
        int count = view.getPointerCount();
        if (count < 1 || count > N3InputMapper.MAX_POINTERS || !(action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_CANCEL
                || action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP)) {
            return cancelIfOwned(view);
        }
        if (action == MotionEvent.ACTION_DOWN) {
            // A second down means the live gesture was interrupted: cancel it through the usual
            // path and drop the replacement sequence rather than orphan a pressed pointer.
            if (owned) return cancelIfOwned(view);
            owned = mapper.owns(view.getX(0), view.getY(0));
            if (!owned) return null;
        } else if (!owned) {
            return null;
        }
        MotionEvent copy = MotionEvent.obtain(view);
        copy.transform(toDisplay);
        if (!finite(copy)) {
            // A corrupt (NaN/Infinite) coordinate cannot be clamped truthfully: end the owned
            // gesture with one cancel and stop forwarding this sequence.
            copy.recycle();
            return cancelIfOwned(view);
        }
        if (!inBounds(copy)) {
            MotionEvent clamped = clamp(copy);
            copy.recycle();
            copy = clamped;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) owned = false;
        return copy;
    }

    private MotionEvent cancelIfOwned(MotionEvent view) {
        if (!owned) return null;
        owned = false;
        MotionEvent cancel = MotionEvent.obtain(view.getDownTime(), view.getEventTime(),
                MotionEvent.ACTION_CANCEL, 0f, 0f, 0);
        cancel.setSource(view.getSource());
        return cancel;
    }

    private boolean inBounds(MotionEvent e) {
        float w = mapper.displayWidth(), h = mapper.displayHeight();
        for (int p = 0; p < e.getPointerCount(); p++) {
            if (!within(e.getX(p), e.getY(p), w, h)) return false;
            for (int s = 0; s < e.getHistorySize(); s++) {
                if (!within(e.getHistoricalX(p, s), e.getHistoricalY(p, s), w, h)) return false;
            }
        }
        return true;
    }

    private static boolean finite(MotionEvent e) {
        for (int p = 0; p < e.getPointerCount(); p++) {
            if (!finite(e.getX(p)) || !finite(e.getY(p))) return false;
            for (int s = 0; s < e.getHistorySize(); s++) {
                if (!finite(e.getHistoricalX(p, s)) || !finite(e.getHistoricalY(p, s))) return false;
            }
        }
        return true;
    }

    private static boolean finite(float v) {
        return !Float.isNaN(v) && !Float.isInfinite(v);
    }

    private static boolean within(float x, float y, float w, float h) {
        return x >= 0f && y >= 0f && x < w && y < h;
    }

    private MotionEvent clamp(MotionEvent e) {
        int count = e.getPointerCount();
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[count];
        float maxX = mapper.displayWidth() - 1, maxY = mapper.displayHeight() - 1;
        for (int p = 0; p < count; p++) {
            props[p] = new MotionEvent.PointerProperties();
            e.getPointerProperties(p, props[p]);
            coords[p] = new MotionEvent.PointerCoords();
            e.getPointerCoords(p, coords[p]);
            coords[p].x = Math.max(0f, Math.min(coords[p].x, maxX));
            coords[p].y = Math.max(0f, Math.min(coords[p].y, maxY));
        }
        MotionEvent out = MotionEvent.obtain(e.getDownTime(), e.getEventTime(), e.getAction(), count,
                props, coords, e.getMetaState(), e.getButtonState(), 1f, 1f, e.getDeviceId(),
                e.getEdgeFlags(), e.getSource(), e.getFlags());
        return out;
    }
}
