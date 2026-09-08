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
        host.post(() -> apply(content, host.getWidth(), host.getHeight()));
    }

    private static void apply(View content, int width, int height) {
        if (width <= 0 || height <= 0) return;
        Ts18Geometry.Layout geometry = Ts18Geometry.resolve(width, height);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                Math.max(1, geometry.safeRight),
                Math.max(1, geometry.safeBottom - geometry.top));
        params.leftMargin = 0;
        params.topMargin = geometry.top;
        content.setLayoutParams(params);
    }
}
