package com.cbkii.ts18launcher;

import android.content.Context;
import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Gradient is painted in row coordinates, independent of the child's marquee scroll. */
// Programmatic wrapper around an existing TextView; never inflated from XML.
@SuppressLint("ViewConstructor")
final class MetadataFadeRow extends FrameLayout {
    private final TextView text;
    private final Paint fade = new Paint();
    private final int fadeWidth;
    private int shaderWidth = -1;
    private int shaderColor;

    MetadataFadeRow(Context context, TextView text) {
        super(context);
        this.text = text;
        fadeWidth = AutomotiveUi.dimen(context, R.dimen.ui_metadata_fade_width);
        setClipChildren(true);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(text, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        text.setHorizontalFadingEdgeEnabled(false);
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        int width = getWidth();
        int available = text.getWidth() - text.getPaddingLeft() - text.getPaddingRight();
        if (width <= 0 || text.getVisibility() != View.VISIBLE || available <= 0
                || text.getPaint().measureText(text.getText().toString()) <= available) return;
        int black = AutomotiveUi.color(getContext(), R.color.ui_black);
        int start = Math.max(0, width - fadeWidth);
        if (shaderWidth != width || shaderColor != black) {
            shaderWidth = width; shaderColor = black;
            fade.setShader(new LinearGradient(start, 0, width, 0,
                    black & 0x00ffffff, black, Shader.TileMode.CLAMP));
        }
        canvas.drawRect(start, 0, width, getHeight(), fade);
    }
}
