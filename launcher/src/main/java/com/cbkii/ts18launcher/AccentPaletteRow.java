package com.cbkii.ts18launcher;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Compact colour-circle selector for the semantic accent role. */
final class AccentPaletteRow extends LinearLayout {
    AccentPaletteRow(Context context, Runnable onChanged) {
        super(context);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackground(AutomotiveUi.cardBackground(context));
        int gap = AutomotiveUi.dimen(context, R.dimen.driver_gap);
        setPadding(gap, 0, gap, 0);

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.addView(text(context, "Accent hue", R.dimen.ui_settings_label, R.color.ui_text));
        labels.addView(text(context, AccentPalette.label(UiPersonalizationPrefs.accentHue(context)),
                R.dimen.ui_settings_value, R.color.ui_text_secondary));
        addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        LinearLayout strip = new LinearLayout(context);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        String selected = UiPersonalizationPrefs.accentHue(context);
        int target = gap * 6; // 48dp touch target.
        int dot = gap * 4;    // 32dp visible circle.
        int stroke = AutomotiveUi.dimen(context, R.dimen.ui_focus_stroke);
        for (int i = 0; i < AccentPalette.VALUES.length; i++) {
            final String value = AccentPalette.VALUES[i];
            boolean checked = value.equals(selected);
            FrameLayout touch = new FrameLayout(context);
            touch.setFocusable(true);
            touch.setClickable(true);
            touch.setContentDescription("Accent " + AccentPalette.LABELS[i] + (checked ? " selected" : ""));

            View swatch = new View(context);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(AccentPalette.baseColor(value));
            if (checked) circle.setStroke(stroke, AutomotiveUi.color(context, R.color.ui_text));
            swatch.setBackground(circle);
            touch.addView(swatch, new FrameLayout.LayoutParams(dot, dot, Gravity.CENTER));
            touch.setOnClickListener(v -> {
                UiPersonalizationPrefs.setAccentHue(context, value);
                if (onChanged != null) onChanged.run();
            });
            AutomotiveUi.attachFeedback(touch);
            strip.addView(touch, new LinearLayout.LayoutParams(target, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        addView(strip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private static TextView text(Context context, String value, int dimenId, int colorId) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextColor(AutomotiveUi.color(context, colorId));
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(dimenId));
        view.setPadding(0, 4, 0, 4);
        return view;
    }
}
