package com.cbkii.ts18launcher;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Launcher-owned placeholder and geometry authority for the real external navigation task.
 * The map itself is never rendered in this View.
 */
final class NativeNavigationPanel extends FrameLayout {
    interface BoundsListener { void onBoundsChanged(NavigationWindowBounds bounds); }

    private final TextView status;
    private final Button retry;
    private BoundsListener boundsListener;
    private NavigationWindowBounds lastBounds;

    NativeNavigationPanel(Activity activity) {
        super(activity);
        setBackgroundColor(Color.BLACK);
        setFocusable(false);
        setClickable(false);

        LinearLayout message = new LinearLayout(activity);
        message.setOrientation(LinearLayout.VERTICAL);
        message.setGravity(Gravity.CENTER);

        status = new TextView(activity);
        status.setTextColor(AutomotiveUi.color(activity, R.color.ui_text));
        status.setTextSize(18f);
        status.setGravity(Gravity.CENTER);
        status.setText("Starting navigation…");
        int pad = AutomotiveUi.dimen(activity, R.dimen.driver_gap);
        status.setPadding(pad, pad, pad, pad);
        message.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        retry = new Button(activity);
        retry.setText("Retry navigation");
        retry.setVisibility(View.GONE);
        message.addView(retry, new LinearLayout.LayoutParams(280, 88));

        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        addView(message, lp);
    }

    void setBoundsListener(BoundsListener listener) { boundsListener = listener; }

    void setRetryAction(Runnable action) {
        retry.setOnClickListener(v -> { if (action != null) action.run(); });
    }

    void showStarting(String label) {
        status.setText(label == null || label.isEmpty() ? "Starting navigation…" : "Starting " + label + "…");
        retry.setVisibility(View.GONE);
    }

    void showReady() {
        // Normally covered by the external navigation task. Keep a quiet fallback beneath it.
        status.setText("Navigation");
        retry.setVisibility(View.GONE);
    }

    void showUnavailable(String detail) {
        status.setText(detail == null || detail.isEmpty() ? "Navigation unavailable" : detail);
        retry.setVisibility(View.VISIBLE);
    }

    void applyAppearance(Activity activity) {
        setBackgroundColor(AutomotiveUi.color(activity, R.color.ui_black));
        status.setTextColor(AutomotiveUi.color(activity, R.color.ui_text));
    }

    NavigationWindowBounds currentBounds() {
        if (getWidth() <= 0 || getHeight() <= 0) return null;
        try { return NavigationWindowBounds.fromView(this); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        NavigationWindowBounds bounds = currentBounds();
        if (bounds == null || bounds.equals(lastBounds)) return;
        lastBounds = bounds;
        if (boundsListener != null) boundsListener.onBoundsChanged(bounds);
    }
}
