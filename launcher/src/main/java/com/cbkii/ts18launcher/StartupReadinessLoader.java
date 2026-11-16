package com.cbkii.ts18launcher;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.LinearLayout;

/** Opaque, bounded visual mask. Readiness and launch authority remain in the coordinator. */
final class StartupReadinessLoader {
    private final Activity activity;
    private final FrameLayout homePanel, mask;
    private final WindowManager windows;
    private final Rect bounds = new Rect();
    private final String title;
    private boolean visible, attached, overlayFailed;
    private Runnable drawn;

    StartupReadinessLoader(Activity activity, FrameLayout root, Runnable dismiss) {
        this.activity = activity;
        title = "TS18Ready_" + android.os.Process.myUid() + "_"
                + java.util.UUID.randomUUID().toString().replace("-", "");
        windows = (WindowManager) activity.getApplicationContext().getSystemService(Activity.WINDOW_SERVICE);
        homePanel = content(dismiss);
        homePanel.setVisibility(View.GONE);
        root.addView(homePanel);
        mask = content(dismiss);
        // Cover app-drawn bar backgrounds too, below the higher SystemUI windows. A fixed dark
        // underlay keeps their icons legible; only our controls are inset into the safe area.
        mask.setBackgroundColor(0xff000000);
    }

    private FrameLayout content(Runnable dismiss) {
        FrameLayout panel = new FrameLayout(activity) {
            @Override protected void dispatchDraw(android.graphics.Canvas canvas) {
                super.dispatchDraw(canvas);
                if (this == mask && attached && drawn != null) {
                    Runnable callback = drawn; drawn = null;
                    postOnAnimation(callback);
                }
            }
            @Override public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) dismiss.run();
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }
        };
        panel.setFocusableInTouchMode(true);
        panel.setClickable(true);
        panel.setBackgroundColor(AutomotiveUi.color(activity, R.color.ui_black));
        LinearLayout progress = new LinearLayout(activity);
        progress.setOrientation(LinearLayout.VERTICAL);
        progress.setGravity(Gravity.CENTER);
        ProgressBar spinner = new ProgressBar(activity);
        spinner.setIndeterminate(true);
        int size = AutomotiveUi.dimen(activity, R.dimen.driver_target_min);
        progress.addView(spinner, new LinearLayout.LayoutParams(size, size));
        TextView status = new TextView(activity);
        status.setTag("readiness-status");
        status.setTextColor(0xffffffff);
        status.setTextSize(20);
        status.setGravity(Gravity.CENTER);
        status.setPadding(size / 2, size / 3, size / 2, size / 3);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        progress.addView(status);
        panel.addView(progress, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        android.widget.Button cancel = new android.widget.Button(activity);
        cancel.setText(R.string.startup_loader_dismiss);
        cancel.setAllCaps(false);
        cancel.setTextColor(0xffffffff);
        cancel.setBackground(AutomotiveUi.interactiveBackground(activity, false));
        cancel.setOnClickListener(v -> dismiss.run());
        FrameLayout.LayoutParams button = new FrameLayout.LayoutParams(size * 3, size,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        panel.addView(cancel, button);
        return panel;
    }

    void geometry(int x, int y, int width, int height) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.max(1, width), Math.max(1, height));
        lp.leftMargin = x; lp.topMargin = y;
        homePanel.setLayoutParams(lp);
        int[] screen = new int[2];
        ((View) homePanel.getParent()).getLocationOnScreen(screen);
        Rect next = new Rect(screen[0] + x, screen[1] + y,
                screen[0] + x + Math.max(1, width), screen[1] + y + Math.max(1, height));
        if (bounds.equals(next)) return;
        bounds.set(next);
        if (attached) hideMask();
    }

    void show(String status, boolean homeVisible) {
        ((TextView) homePanel.findViewWithTag("readiness-status")).setText(status);
        ((TextView) mask.findViewWithTag("readiness-status")).setText(status);
        visible = true;
        homePanel.setVisibility(homeVisible ? View.VISIBLE : View.GONE);
        if (homeVisible) { homePanel.bringToFront(); homePanel.requestFocus(); }
    }

    boolean canMask() {
        try { return !overlayFailed && windows != null && !bounds.isEmpty() && Settings.canDrawOverlays(activity); }
        catch (RuntimeException unavailable) { return false; }
    }

    boolean ownsMask() { return attached && title.equals(StartupMaskFocus.activeTitle); }

    void showMask() {
        if (!visible || attached || !canMask()) return;
        try {
            android.util.DisplayMetrics display = new android.util.DisplayMetrics();
            windows.getDefaultDisplay().getRealMetrics(display);
            if (display.widthPixels < bounds.right || display.heightPixels < bounds.bottom)
                throw new IllegalStateException("display bounds unavailable");
            mask.setPadding(bounds.left, bounds.top, display.widthPixels - bounds.right,
                    display.heightPixels - bounds.bottom);
            WindowManager.LayoutParams layout = new WindowManager.LayoutParams(display.widthPixels, display.heightPixels,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.OPAQUE);
            layout.gravity = Gravity.TOP | Gravity.LEFT;
            layout.x = 0; layout.y = 0;
            layout.setTitle(title);
            attached = true;
            StartupMaskFocus.activeTitle = title;
            windows.addView(mask, layout);
            mask.requestFocus();
        } catch (RuntimeException denied) {
            attached = false; overlayFailed = true;
            if (title.equals(StartupMaskFocus.activeTitle)) StartupMaskFocus.activeTitle = "";
            MediaEventTrace.record("startup-loader", "overlay-unavailable", denied.getClass().getSimpleName());
        }
    }

    void whenMaskDrawn(Runnable callback) {
        if (!attached || !canMask()) { callback.run(); return; }
        drawn = callback;
        mask.invalidate();
    }

    void hideMask() {
        Runnable abandoned = drawn;
        drawn = null;
        if (title.equals(StartupMaskFocus.activeTitle)) StartupMaskFocus.activeTitle = "";
        if (attached) {
            attached = false;
            try { windows.removeViewImmediate(mask); }
            catch (RuntimeException gone) { /* Already removed by WMS. */ }
        }
        if (abandoned != null) abandoned.run();
    }
    void hide() { visible = false; homePanel.setVisibility(View.GONE); hideMask(); }
    void destroy() {
        hide();
        if (homePanel.getParent() instanceof FrameLayout)
            ((FrameLayout) homePanel.getParent()).removeView(homePanel);
    }
}
