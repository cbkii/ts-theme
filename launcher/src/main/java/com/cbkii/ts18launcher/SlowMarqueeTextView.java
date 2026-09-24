package com.cbkii.ts18launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

/** Endless low-speed marquee with five-second readable holds at both ends. */
final class SlowMarqueeTextView extends TextView {
    static final long HOLD_MS = MediaTickerPolicy.HOLD_MS;
    private static final float SPEED_DP_PER_SECOND = 24f;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ValueAnimator animator;
    private final Runnable restart = this::startMarquee;

    SlowMarqueeTextView(Context context) { super(context); init(); }
    SlowMarqueeTextView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        setSingleLine(true);
        setHorizontalFadingEdgeEnabled(true);
        setFadingEdgeLength(18);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        scheduleFromStart();
    }

    @Override protected void onDetachedFromWindow() {
        cancelMarquee();
        super.onDetachedFromWindow();
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (handler == null || !isAttachedToWindow()) return;
        if (MediaTickerPolicy.shouldSchedule(true, visibility == View.VISIBLE,
                getWindowVisibility() == View.VISIBLE)) scheduleFromStart();
        else cancelMarquee();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (handler == null || !isAttachedToWindow()) return;
        if (MediaTickerPolicy.shouldSchedule(true, getVisibility() == View.VISIBLE,
                visibility == View.VISIBLE)) scheduleFromStart();
        else cancelMarquee();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (MediaTickerPolicy.widthChangeRestarts(oldw, w)) scheduleFromStart();
    }

    @Override public void setText(CharSequence text, BufferType type) {
        CharSequence safe = text == null ? "" : text;
        if (TextUtils.equals(getText(), safe)) return;
        super.setText(safe, type);
        if (handler != null) scheduleFromStart();
    }

    private void scheduleFromStart() {
        cancelMarquee();
        scrollTo(0, 0);
        if (!MediaTickerPolicy.shouldSchedule(isAttachedToWindow(),
                getVisibility() == View.VISIBLE, getWindowVisibility() == View.VISIBLE)) return;
        handler.postDelayed(restart, HOLD_MS);
    }

    private void startMarquee() {
        int available = Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight());
        int content = (int) Math.ceil(getPaint().measureText(
                getText() == null ? "" : getText().toString()));
        int overflow = Math.max(0, content - available);
        if (!MediaTickerPolicy.shouldScroll(content, available, isShown())) return;
        float pxPerSecond = SPEED_DP_PER_SECOND * getResources().getDisplayMetrics().density;
        long duration = Math.max(3500L,
                Math.min(30000L, (long) (overflow / pxPerSecond * 1000f)));
        ValueAnimator next = ValueAnimator.ofInt(0, overflow);
        animator = next;
        next.setInterpolator(new LinearInterpolator());
        next.setDuration(duration);
        next.addUpdateListener(value -> scrollTo((Integer) value.getAnimatedValue(), 0));
        next.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (animator != next) return;
                handler.postDelayed(() -> {
                    if (animator != next) return;
                    scrollTo(0, 0);
                    animator = null;
                    if (isShown()) handler.postDelayed(restart, HOLD_MS);
                }, HOLD_MS);
            }
        });
        next.start();
    }

    private void cancelMarquee() {
        handler.removeCallbacksAndMessages(null);
        ValueAnimator old = animator;
        animator = null;
        if (old != null) old.cancel();
    }
}
