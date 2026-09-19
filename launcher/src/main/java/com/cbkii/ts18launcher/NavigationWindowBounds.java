package com.cbkii.ts18launcher;

import android.graphics.Rect;

/** Immutable physical-screen bounds for the external navigation task. */
final class NavigationWindowBounds {
    final int left;
    final int top;
    final int right;
    final int bottom;

    NavigationWindowBounds(int left, int top, int right, int bottom) {
        if (left < 0 || top < 0 || right <= left || bottom <= top) {
            throw new IllegalArgumentException("invalid navigation bounds");
        }
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    static NavigationWindowBounds fromView(android.view.View view) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return new NavigationWindowBounds(location[0], location[1],
                location[0] + view.getWidth(), location[1] + view.getHeight());
    }

    Rect asRect() { return new Rect(left, top, right, bottom); }
    int width() { return right - left; }
    int height() { return bottom - top; }

    @Override public boolean equals(Object other) {
        if (!(other instanceof NavigationWindowBounds)) return false;
        NavigationWindowBounds b = (NavigationWindowBounds) other;
        return left == b.left && top == b.top && right == b.right && bottom == b.bottom;
    }

    @Override public int hashCode() {
        int result = left;
        result = 31 * result + top;
        result = 31 * result + right;
        result = 31 * result + bottom;
        return result;
    }

    @Override public String toString() {
        return left + "," + top + "," + right + "," + bottom;
    }
}
