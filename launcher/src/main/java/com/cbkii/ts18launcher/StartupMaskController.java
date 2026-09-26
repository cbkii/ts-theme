package com.cbkii.ts18launcher;

import android.app.Activity;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Short-lived cold-start mask. It is never used as a persistent launcher/OEM overlay. */
final class StartupMaskController {
    private final Activity activity;
    private final WindowManager windowManager;
    private View overlay;
    private boolean externalCover;

    StartupMaskController(Activity activity) {
        this.activity = activity;
        windowManager = (WindowManager) activity.getApplicationContext()
                .getSystemService(Context.WINDOW_SERVICE);
    }

    static boolean hasOverlayAccess(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context);
    }

    boolean show() {
        if (overlay != null) return externalCover;

        // Native navigation uses its guarded park/restore handoff for source launches.
        // This mask only covers the visual transition and does not confer task authority.

        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(0xFF050505);
        root.setClickable(true);
        root.setFocusable(true);
        TextView label = new TextView(activity);
        label.setText(R.string.starting_launcher);
        label.setTextColor(0xFFEAEAEA);
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20f);
        label.setGravity(Gravity.CENTER);
        root.addView(label, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasOverlayAccess(activity)
                && windowManager != null) {
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.OPAQUE);
            params.gravity = Gravity.TOP | Gravity.START;
            try {
                windowManager.addView(root, params);
                overlay = root;
                externalCover = true;
                MediaEventTrace.record("startup", "mask-overlay-visible");
                return true;
            } catch (RuntimeException ignored) {
                MediaEventTrace.record("startup", "mask-overlay-rejected");
            }
        }

        activity.addContentView(root, new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        root.bringToFront();
        overlay = root;
        externalCover = false;
        MediaEventTrace.record("startup", "mask-local-visible");
        return false;
    }

    boolean coversExternalActivities() { return externalCover; }

    void dismiss() {
        View current = overlay;
        overlay = null;
        boolean wasExternal = externalCover;
        externalCover = false;
        if (current == null) return;
        try {
            if (wasExternal && windowManager != null) windowManager.removeViewImmediate(current);
            else if (current.getParent() instanceof android.view.ViewGroup)
                ((android.view.ViewGroup) current.getParent()).removeView(current);
        } catch (RuntimeException ignored) {
            // Bounded best-effort cleanup; never keep startup blocked for an overlay error.
        }
        MediaEventTrace.record("startup", "mask-dismissed");
    }
}
