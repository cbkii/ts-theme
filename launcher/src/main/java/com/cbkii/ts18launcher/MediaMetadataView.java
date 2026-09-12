package com.cbkii.ts18launcher;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Two-level glanceable media metadata: moving primary title, static secondary context. */
final class MediaMetadataView extends LinearLayout {
    private final SlowMarqueeTextView primary;
    private final TextView secondary;

    MediaMetadataView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int gap = AutomotiveUi.dimen(context, R.dimen.driver_gap);
        setPadding(gap, 0, gap, 0);

        primary = new SlowMarqueeTextView(context);
        primary.setTextColor(AutomotiveUi.color(context, R.color.ui_text));
        primary.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(R.dimen.ui_metadata_text));
        primary.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        primary.setGravity(Gravity.BOTTOM);
        addView(primary, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.15f));

        secondary = new TextView(context);
        secondary.setTextColor(AutomotiveUi.color(context, R.color.ui_text_secondary));
        secondary.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(R.dimen.ui_metadata_secondary_text));
        secondary.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        secondary.setSingleLine(true);
        secondary.setEllipsize(TextUtils.TruncateAt.END);
        secondary.setGravity(Gravity.TOP);
        addView(secondary, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 0.85f));
    }

    void setMetadata(String title, String context) {
        String safeTitle = title == null ? "" : title.trim();
        String safeContext = context == null ? "" : context.trim();
        primary.setText(safeTitle);
        secondary.setText(safeContext);
        secondary.setVisibility(safeContext.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
