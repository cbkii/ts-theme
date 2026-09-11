package com.cbkii.ts18launcher;

import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.widget.ImageButton;

final class AutomotiveUi {
    static final long FEEDBACK_MS = 140L;

    private AutomotiveUi() {}

    static int dimen(Context context, int id) {
        return context.getResources().getDimensionPixelSize(id);
    }

    static int color(Context context, int id) {
        return context.getColor(id);
    }

    static RippleDrawable interactiveBackground(Context context, boolean accent) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused},
                rounded(context, accent ? R.color.ui_accent_dark : R.color.ui_surface_focus,
                        R.color.ui_accent, 2));
        states.addState(new int[] {android.R.attr.state_pressed},
                rounded(context, accent ? R.color.ui_accent_dark : R.color.ui_surface_pressed,
                        R.color.ui_surface_pressed, 0));
        states.addState(new int[] {}, rounded(context,
                accent ? R.color.ui_accent : R.color.ui_surface,
                accent ? R.color.ui_accent : R.color.ui_surface, 0));
        return new RippleDrawable(
                ColorStateList.valueOf(color(context, R.color.ui_ripple)),
                states,
                rounded(context, android.R.color.white, android.R.color.white, 0));
    }

    static GradientDrawable cardBackground(Context context) {
        return rounded(context, R.color.ui_surface, R.color.ui_surface, 0);
    }

    static GradientDrawable chipBackground(Context context) {
        return rounded(context, R.color.ui_surface_elevated, R.color.ui_surface_elevated, 0);
    }

    static void styleIconButton(Context context, ImageButton button, boolean accent) {
        button.setBackground(interactiveBackground(context, accent));
        button.setImageTintList(ColorStateList.valueOf(
                accent ? Color.BLACK : color(context, R.color.ui_icon)));
        button.setPadding(
                dimen(context, R.dimen.ui_icon_button_padding),
                dimen(context, R.dimen.ui_icon_button_padding),
                dimen(context, R.dimen.ui_icon_button_padding),
                dimen(context, R.dimen.ui_icon_button_padding));
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
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
        return new ColorStateList(
                new int[][] {
                        new int[] {android.R.attr.state_selected},
                        new int[] {}
                },
                new int[] {
                        color(context, R.color.ui_accent),
                        color(context, R.color.ui_icon)
                });
    }

    static int quickRoleIcon(int index) {
        switch (index) {
            case 0: return R.drawable.ic_navigation;
            case 1: return R.drawable.ic_radio;
            case 2: return R.drawable.ic_music;
            case 3: return R.drawable.ic_bluetooth;
            case 4: return R.drawable.ic_star;
            default: return R.drawable.ic_shortcut;
        }
    }

    static int drawerQuickRoleIcon(int index) {
        switch (index) {
            case 0: return R.drawable.ic_navigation;
            case 1: return R.drawable.ic_radio;
            case 2: return R.drawable.ic_music;
            case 3: return R.drawable.ic_bluetooth;
            default: return R.drawable.ic_star;
        }
    }

    private static GradientDrawable rounded(
            Context context, int fillId, int strokeId, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color(context, fillId));
        drawable.setCornerRadius(dimen(context, R.dimen.ui_corner_radius));
        if (strokeDp > 0) {
            drawable.setStroke(Math.max(1, dimen(context, R.dimen.ui_focus_stroke)),
                    color(context, strokeId));
        }
        return drawable;
    }
}
