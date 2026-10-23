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

/** Opaque, bounded visual mask. Readiness and launch authority remain in the coordinator. */
final class StartupReadinessLoader {
    private final Activity activity;
    private final FrameLayout homePanel, mask;
    private final WindowManager windows;
    private final Rect bounds = new Rect();
    private final String title;
    private boolean visible, attached, overlayFailed;

    StartupReadinessLoader(Activity activity, FrameLayout root, Runnable dismiss) {
        this.activity = activity;
        title = "TS18Ready_" + android.os.Process.myUid() + "_"
                + java.util.UUID.randomUUID().toString().replace("-", "");
        windows = (WindowManager) activity.getApplicationContext().getSystemService(Activity.WINDOW_SERVICE);
        homePanel = content(dismiss);
        homePanel.setVisibility(View.GONE);
        root.addView(homePanel);
        mask = content(dismiss);
    }

    private FrameLayout content(Runnable dismiss) {
        FrameLayout panel = new FrameLayout(activity) {
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
        ProgressBar spinner = new ProgressBar(activity);
        spinner.setIndeterminate(true);
        int size = AutomotiveUi.dimen(activity, R.dimen.driver_target_min);
        FrameLayout.LayoutParams spin = new FrameLayout.LayoutParams(size, size, Gravity.CENTER);
        panel.addView(spinner, spin);
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

    void show(String ignoredStatus, boolean homeVisible) {
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
            WindowManager.LayoutParams layout = new WindowManager.LayoutParams(bounds.width(), bounds.height(),
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.OPAQUE);
            layout.gravity = Gravity.TOP | Gravity.LEFT;
            layout.x = bounds.left; layout.y = bounds.top;
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

    void hideMask() {
        if (title.equals(StartupMaskFocus.activeTitle)) StartupMaskFocus.activeTitle = "";
        if (!attached) return;
        attached = false;
        try { windows.removeViewImmediate(mask); }
        catch (RuntimeException gone) { /* Already removed by WMS. */ }
    }
    void hide() { visible = false; homePanel.setVisibility(View.GONE); hideMask(); }
    void destroy() {
        hide();
        if (homePanel.getParent() instanceof FrameLayout)
            ((FrameLayout) homePanel.getParent()).removeView(homePanel);
    }
}
