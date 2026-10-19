package com.cbkii.ts18launcher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;

/** Launcher-owned fallback only; a foreign map never renders inside this View. */
@SuppressLint("ViewConstructor")
final class NativeNavigationPanel extends FrameLayout {
    interface BoundsListener { void onBoundsChanged(NavigationWindowBounds bounds); }
    private final ImageButton openNavigation;
    private BoundsListener boundsListener;
    private NavigationWindowBounds lastBounds;
    private Runnable openAction;
    private Runnable fullscreenAction;

    NativeNavigationPanel(Activity activity) {
        super(activity);
        setBackgroundColor(Color.BLACK);
        setFocusable(false);
        setClickable(false);
        openNavigation = new ImageButton(activity);
        openNavigation.setImageResource(R.drawable.ic_navigation_filled);
        openNavigation.setImageTintList(ColorStateList.valueOf(Color.BLACK));
        openNavigation.setScaleType(ImageView.ScaleType.FIT_CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.WHITE);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        openNavigation.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x55000000), circle, mask));
        openNavigation.setFocusable(true);
        AutomotiveUi.attachFeedback(openNavigation);
        openNavigation.setContentDescription("Open navigation");
        openNavigation.setOnClickListener(v -> { if (openAction != null) openAction.run(); });
        openNavigation.setVisibility(View.GONE);
        addView(openNavigation, new LayoutParams(0, 0, Gravity.CENTER));
    }

    void setBoundsListener(BoundsListener listener) { boundsListener = listener; }
    void setAction(String label, Runnable action) { showAction(label, action); }
    // Recovery remains controller-owned. The fallback offers only an explicit open action.
    void setRecoveryActions(Runnable retry, Runnable fullscreen) { fullscreenAction = fullscreen; }
    private void showAction(String detail, Runnable action) {
        openAction = action;
        openNavigation.setContentDescription(detail == null || detail.isEmpty()
                ? "Open navigation" : "Open navigation · " + detail);
        openNavigation.setVisibility(action == null ? View.GONE : View.VISIBLE);
    }
    void showStarting(String label, String backend) { showAction("Starting navigation", fullscreenAction); }
    void showConfigured(String detail, Runnable openFullscreen) { showAction(detail, openFullscreen); }
    void showFailure(String detail, Runnable retry, Runnable openFullscreen) { showAction(detail, openFullscreen); }
    void showUnavailable(String detail, Runnable openFullscreen) { showAction(detail, openFullscreen); }
    void showFullscreenOnly(String label, Runnable openFullscreen) { showAction(label, openFullscreen); }
    void applyAppearance(Activity activity) { setBackgroundColor(Color.BLACK); }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int availableWidth = Math.max(0, getMeasuredWidth() - getPaddingLeft() - getPaddingRight());
        int availableHeight = Math.max(0, getMeasuredHeight() - getPaddingTop() - getPaddingBottom());
        int diameter = Math.min(availableWidth, Math.round(availableHeight * 0.75f));
        LayoutParams params = (LayoutParams) openNavigation.getLayoutParams();
        params.width = diameter;
        params.height = diameter;
        int inset = Math.round(diameter * 0.19f);
        openNavigation.setPadding(inset, inset, inset, inset);
        int exact = View.MeasureSpec.makeMeasureSpec(diameter, View.MeasureSpec.EXACTLY);
        openNavigation.measure(exact, exact);
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
