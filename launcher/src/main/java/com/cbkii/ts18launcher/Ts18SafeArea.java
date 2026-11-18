package com.cbkii.ts18launcher;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.widget.FrameLayout;

/** Applies the exact TS18 top/right/bottom safe area without double-insetting fitted decor. */
final class Ts18SafeArea {
    private Ts18SafeArea() {}

    static void setContent(Activity activity, View content) {
        FrameLayout host = new FrameLayout(activity);
        host.setBackgroundColor(Color.BLACK);
        host.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        activity.setContentView(host);

        host.addOnLayoutChangeListener((view, left, top, right, bottom,
                                        oldLeft, oldTop, oldRight, oldBottom) ->
                apply(content, right - left, bottom - top));
        host.setOnApplyWindowInsetsListener((view, insets) -> {
            host.post(() -> apply(content, host.getWidth(), host.getHeight())); return insets;
        });
        host.post(() -> apply(content, host.getWidth(), host.getHeight()));
    }

    private static void apply(View content, int width, int height) {
        if (width <= 0 || height <= 0) return;
        View host = (View) content.getParent();
        int[] origin = new int[2]; host.getLocationInWindow(origin);
        android.view.WindowInsets insets = host.getRootWindowInsets();
        View decor = host.getRootView();
        int left = insets == null ? 0 : Ts18Geometry.residualStartInset(insets.getSystemWindowInsetLeft(), origin[0]);
        int top = insets == null ? 0 : Ts18Geometry.residualStartInset(insets.getSystemWindowInsetTop(), origin[1]);
        int right = insets == null ? 0 : Ts18Geometry.residualEndInset(insets.getSystemWindowInsetRight(), origin[0], width, decor.getWidth());
        int bottom = insets == null ? 0 : Ts18Geometry.residualEndInset(insets.getSystemWindowInsetBottom(), origin[1], height, decor.getHeight());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                Math.max(1, width - left - right), Math.max(1, height - top - bottom));
        params.leftMargin = left;
        params.topMargin = top;
        content.setLayoutParams(params);
    }
}
