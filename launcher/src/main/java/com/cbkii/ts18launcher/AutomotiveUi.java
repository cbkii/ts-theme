package com.cbkii.ts18launcher;

import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.widget.ImageButton;

import java.util.List;

final class AutomotiveUi {
    static final long FEEDBACK_MS = 140L;
    static final long DRAWER_MS = 180L;

    private AutomotiveUi() {}

    static int dimen(Context context, int id) {
        return context.getResources().getDimensionPixelSize(id);
    }

    static int color(Context context, int id) {
        return context.getColor(mappedColor(AppearanceController.resolvedMode(context), id));
    }

    private static int mappedColor(String mode, int id) {
        if (LauncherPrefs.APPEARANCE_HIGH_CONTRAST.equals(mode)) {
            if (id == R.color.ui_black) return R.color.ui_high_black;
            if (id == R.color.ui_surface) return R.color.ui_high_surface;
            if (id == R.color.ui_surface_elevated || id == R.color.ui_surface_pressed
                    || id == R.color.ui_surface_focus) return R.color.ui_high_surface_elevated;
            if (id == R.color.ui_text || id == R.color.ui_icon) return R.color.ui_high_text;
            if (id == R.color.ui_text_secondary) return R.color.ui_high_secondary;
            if (id == R.color.ui_accent || id == R.color.ui_accent_dark) return R.color.ui_high_accent;
        } else if (LauncherPrefs.APPEARANCE_NIGHT.equals(mode)) {
            if (id == R.color.ui_black) return R.color.ui_night_black;
            if (id == R.color.ui_surface) return R.color.ui_night_surface;
            if (id == R.color.ui_surface_elevated || id == R.color.ui_surface_pressed
                    || id == R.color.ui_surface_focus) return R.color.ui_night_surface_elevated;
            if (id == R.color.ui_text || id == R.color.ui_icon) return R.color.ui_night_text;
            if (id == R.color.ui_text_secondary) return R.color.ui_night_secondary;
            if (id == R.color.ui_accent || id == R.color.ui_accent_dark) return R.color.ui_night_accent;
        } else if (LauncherPrefs.APPEARANCE_DIM.equals(mode)) {
            if (id == R.color.ui_black) return R.color.ui_night_black;
            if (id == R.color.ui_surface) return R.color.ui_night_surface;
            if (id == R.color.ui_surface_elevated) return R.color.ui_night_surface_elevated;
        }
        return id;
    }

    static RippleDrawable interactiveBackground(Context context, boolean accent) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused},
                rounded(context, accent ? R.color.ui_accent_dark : R.color.ui_surface_focus,
                        R.color.ui_accent, 3));
        states.addState(new int[] {android.R.attr.state_pressed},
                rounded(context, accent ? R.color.ui_accent_dark : R.color.ui_surface_pressed,
                        R.color.ui_surface_pressed, 0));
        states.addState(new int[] {}, rounded(context,
                accent ? R.color.ui_accent : R.color.ui_surface,
                accent ? R.color.ui_accent : R.color.ui_surface, 0));
        return ripple(context, states);
    }

    static RippleDrawable transparentActionBackground(Context context) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused},
                rounded(context, R.color.ui_surface_focus, R.color.ui_accent, 3));
        states.addState(new int[] {android.R.attr.state_pressed},
                rounded(context, R.color.ui_surface_pressed, R.color.ui_surface_pressed, 0));
        states.addState(new int[] {}, new ColorDrawable(Color.TRANSPARENT));
        return ripple(context, states);
    }

    static RippleDrawable primaryTransportBackground(Context context) {
        int target = dimen(context, R.dimen.driver_target_primary);
        int visual = dimen(context, R.dimen.ui_play_visual);
        int inset = Math.max(0, (target - visual) / 2);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused},
                new InsetDrawable(circle(context, R.color.ui_accent_dark, R.color.ui_accent, 3), inset));
        states.addState(new int[] {android.R.attr.state_pressed},
                new InsetDrawable(circle(context, R.color.ui_accent_dark, R.color.ui_accent_dark, 0), inset));
        states.addState(new int[] {}, new InsetDrawable(
                circle(context, R.color.ui_accent, R.color.ui_accent, 0), inset));
        return ripple(context, states);
    }

    static GradientDrawable cardBackground(Context context) {
        return rounded(context, R.color.ui_surface, R.color.ui_surface, 0);
    }

    static GradientDrawable chipBackground(Context context) {
        return rounded(context, R.color.ui_surface_elevated, R.color.ui_surface_elevated, 0);
    }

    static void styleIconButton(Context context, ImageButton button, boolean accent) {
        button.setBackground(accent ? primaryTransportBackground(context) : interactiveBackground(context, false));
        button.setImageTintList(ColorStateList.valueOf(accent ? Color.BLACK : color(context, R.color.ui_icon)));
        int pad = dimen(context, R.dimen.ui_icon_button_padding);
        button.setPadding(pad, pad, pad, pad);
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        button.setFocusable(true);
        attachFeedback(button);
    }

    static void styleTransportButton(Context context, ImageButton button, boolean primary) {
        button.setBackground(primary ? primaryTransportBackground(context) : transparentActionBackground(context));
        button.setImageTintList(ColorStateList.valueOf(primary ? Color.BLACK : color(context, R.color.ui_icon)));
        int pad = dimen(context, R.dimen.ui_icon_button_padding);
        button.setPadding(pad, pad, pad, pad);
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        button.setFocusable(true);
        attachFeedback(button);
    }

    static void styleRailButton(Context context, ImageButton button) {
        button.setBackground(transparentActionBackground(context));
        button.setImageTintList(ColorStateList.valueOf(color(context, R.color.ui_icon)));
        int pad = dimen(context, R.dimen.driver_gap_large);
        button.setPadding(pad, pad, pad, pad);
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        button.setFocusable(true);
        attachFeedback(button);
    }

    static void styleMapButton(Context context, ImageButton button, boolean primary) {
        button.setBackground(interactiveBackground(context, primary));
        button.setImageTintList(ColorStateList.valueOf(primary ? Color.BLACK : color(context, R.color.ui_icon)));
        int pad = dimen(context, R.dimen.ui_icon_button_padding);
        button.setPadding(pad, pad, pad, pad);
        button.setFocusable(true);
        attachFeedback(button);
    }

    static void attachFeedback(View view) {
        StateListAnimator animator = new StateListAnimator();
        ObjectAnimator disabled = ObjectAnimator.ofFloat(view, View.ALPHA, 0.35f);
        disabled.setDuration(FEEDBACK_MS);
        animator.addState(new int[] {-android.R.attr.state_enabled}, disabled);
        ObjectAnimator pressed = ObjectAnimator.ofFloat(view, View.ALPHA, 0.78f);
        pressed.setDuration(FEEDBACK_MS);
        animator.addState(new int[] {android.R.attr.state_pressed, android.R.attr.state_enabled}, pressed);
        ObjectAnimator focused = ObjectAnimator.ofFloat(view, View.ALPHA, 1f);
        focused.setDuration(FEEDBACK_MS);
        animator.addState(new int[] {android.R.attr.state_focused, android.R.attr.state_enabled}, focused);
        ObjectAnimator normal = ObjectAnimator.ofFloat(view, View.ALPHA, 1f);
        normal.setDuration(FEEDBACK_MS);
        animator.addState(new int[] {}, normal);
        view.setStateListAnimator(animator);
    }

    static ColorStateList followTint(Context context) {
        return new ColorStateList(new int[][] {
                new int[] {android.R.attr.state_selected}, new int[] {}
        }, new int[] {color(context, R.color.ui_accent), color(context, R.color.ui_icon)});
    }

    static void linkVertical(List<? extends View> views) { linkFocus(views, true); }
    static void linkHorizontal(List<? extends View> views) { linkFocus(views, false); }

    private static void linkFocus(List<? extends View> views, boolean vertical) {
        for (View view : views) if (view.getId() == View.NO_ID) view.setId(View.generateViewId());
        for (int i = 0; i < views.size(); i++) {
            View current = views.get(i);
            if (i > 0) {
                if (vertical) current.setNextFocusUpId(views.get(i - 1).getId());
                else current.setNextFocusLeftId(views.get(i - 1).getId());
            }
            if (i + 1 < views.size()) {
                if (vertical) current.setNextFocusDownId(views.get(i + 1).getId());
                else current.setNextFocusRightId(views.get(i + 1).getId());
            }
        }
    }

    private static RippleDrawable ripple(Context context, android.graphics.drawable.Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(color(context, R.color.ui_ripple)),
                content, rounded(context, android.R.color.white, android.R.color.white, 0));
    }

    private static GradientDrawable circle(Context context, int fillId, int strokeId, int strokeDp) {
        GradientDrawable drawable = rounded(context, fillId, strokeId, strokeDp);
        drawable.setShape(GradientDrawable.OVAL);
        return drawable;
    }

    private static GradientDrawable rounded(Context context, int fillId, int strokeId, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color(context, fillId));
        drawable.setCornerRadius(dimen(context, R.dimen.ui_corner_radius));
        if (strokeDp > 0) drawable.setStroke(Math.max(1, dimen(context, R.dimen.ui_focus_stroke)),
                color(context, strokeId));
        return drawable;
    }
}
