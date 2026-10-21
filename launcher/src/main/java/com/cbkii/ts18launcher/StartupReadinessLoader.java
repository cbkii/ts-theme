package com.cbkii.ts18launcher;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Optional visual mask. It cannot change task focus or prove readiness. Owned by HOME's lifecycle. */
final class StartupReadinessLoader {
    private final Activity activity;
    private final FrameLayout homePanel;
    private final LinearLayout mask;
    private final TextView homeStatus, maskStatus;
    private final WindowManager windows;
    private final Rect bounds = new Rect();
    private boolean visible, attached, overlayFailed;

    StartupReadinessLoader(Activity activity, FrameLayout root, Runnable dismiss) {
        this.activity = activity;
        windows = (WindowManager) activity.getApplicationContext().getSystemService(Activity.WINDOW_SERVICE);
        homePanel = new FrameLayout(activity);
        LinearLayout content = content(dismiss);
        homeStatus = (TextView) content.getChildAt(2);
        homePanel.setBackgroundColor(AutomotiveUi.color(activity, R.color.ui_black));
        homePanel.addView(content, new FrameLayout.LayoutParams(-1, -1));
        homePanel.setVisibility(View.GONE);
        root.addView(homePanel);
        mask = content(dismiss);
        maskStatus = (TextView) mask.getChildAt(2);
    }

    private LinearLayout content(Runnable dismiss) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setBackgroundColor(AutomotiveUi.color(activity, R.color.ui_black));
        int gap = AutomotiveUi.dimen(activity, R.dimen.driver_gap);
        panel.setPadding(gap, gap, gap, gap);
        TextView title = new TextView(activity);
        title.setText(R.string.startup_loader_title);
        title.setTextSize(22);
        title.setTextColor(AutomotiveUi.color(activity, R.color.ui_text));
        panel.addView(title);
        ProgressBar spinner = new ProgressBar(activity);
        spinner.setIndeterminate(true);
        LinearLayout.LayoutParams spin = new LinearLayout.LayoutParams(gap * 4, gap * 4);
        spin.topMargin = gap; spin.bottomMargin = gap;
        panel.addView(spinner, spin);
        TextView status = new TextView(activity);
        status.setGravity(Gravity.CENTER);
        status.setTextColor(AutomotiveUi.color(activity, R.color.ui_text));
        panel.addView(status);
        Button close = new Button(activity);
        close.setText(R.string.startup_loader_dismiss);
        close.setOnClickListener(v -> dismiss.run());
        panel.addView(close);
        return panel;
    }

    void geometry(int x, int y, int width, int height) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.max(1, width), Math.max(1, height));
        lp.leftMargin = x; lp.topMargin = y;
        homePanel.setLayoutParams(lp);
        int[] screen = new int[2];
        // Geometry is supplied by the same residual-inset owner as the real map panel.
        // Use the root's screen origin, since this panel may not have laid out yet.
        View parent = (View) homePanel.getParent();
        parent.getLocationOnScreen(screen);
        Rect next = new Rect(screen[0] + x, screen[1] + y,
                screen[0] + x + Math.max(1, width), screen[1] + y + Math.max(1, height));
        if (bounds.equals(next)) return;
        bounds.set(next);
        if (attached) { hideMask(); }
    }

    void show(String status, boolean homeVisible) {
        visible = true;
        if (!status.contentEquals(homeStatus.getText())) {
            homeStatus.setText(status); maskStatus.setText(status);
        }
        homePanel.setVisibility(homeVisible ? View.VISIBLE : View.GONE);
        if (homeVisible) homePanel.bringToFront();
    }

    /** Called only after a read-only foreground check identifies the exact staged/Maps entry. */
    boolean canMask() {
        try { return !overlayFailed && windows != null && !bounds.isEmpty() && Settings.canDrawOverlays(activity); }
        catch (RuntimeException unavailable) { return false; }
    }

    void showMask() {
        if (!visible || attached || !canMask()) return;
        try {
            if (!Settings.canDrawOverlays(activity)) return;
            WindowManager.LayoutParams maskLayout = new WindowManager.LayoutParams(bounds.width(), bounds.height(),
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            maskLayout.gravity = Gravity.TOP | Gravity.LEFT;
            maskLayout.x = bounds.left; maskLayout.y = bounds.top;
            maskLayout.setTitle("TS18 startup readiness");
            windows.addView(mask, maskLayout);
            attached = true;
        } catch (RuntimeException denied) {
            // Revoked app-op, unsupported OEM window or dead token: retain ordinary HOME UI.
            attached = false; overlayFailed = true;
            MediaEventTrace.record("startup-loader", "overlay-unavailable", denied.getClass().getSimpleName());
        }
    }

    void hideMask() {
        if (!attached) return;
        try { windows.removeViewImmediate(mask); }
        catch (RuntimeException gone) { /* Already removed by WMS. */ }
        attached = false;
    }
    void hide() { visible = false; homePanel.setVisibility(View.GONE); hideMask(); }
    void destroy() {
        hide();
        if (homePanel.getParent() instanceof FrameLayout)
            ((FrameLayout) homePanel.getParent()).removeView(homePanel);
    }
}
