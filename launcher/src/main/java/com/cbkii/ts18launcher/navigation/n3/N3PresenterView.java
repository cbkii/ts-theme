package com.cbkii.ts18launcher.navigation.n3;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * SurfaceView presenter for the N3 display. The buffer is fixed at the display geometry and the
 * view keeps the display's aspect ratio, so view-to-display mapping is an exact scale. Touches go
 * only to the N3 display through the backend; the launcher itself never acts on them.
 */
@SuppressLint("ViewConstructor") // Built in code with its backend; never inflated from XML.
public final class N3PresenterView extends SurfaceView implements SurfaceHolder.Callback {
    private final N3NavigationBackend backend;
    private final int displayWidth, displayHeight;

    public N3PresenterView(Context context, N3NavigationBackend backend, int displayWidth, int displayHeight) {
        super(context);
        this.backend = backend;
        this.displayWidth = displayWidth;
        this.displayHeight = displayHeight;
        getHolder().setFixedSize(displayWidth, displayHeight);
        getHolder().addCallback(this);
        setContentDescription("Navigation shown in the experimental N3 display");
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec), h = MeasureSpec.getSize(heightSpec);
        if (w <= 0 || h <= 0) { super.onMeasure(widthSpec, heightSpec); return; }
        float aspect = displayWidth / (float) displayHeight;
        if (w / (float) h > aspect) w = Math.round(h * aspect); else h = Math.round(w / aspect);
        setMeasuredDimension(w, h);
    }

    @Override public void surfaceCreated(SurfaceHolder holder) { }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        backend.surfaceAvailable(holder.getSurface(), getWidth(), getHeight());
    }

    @Override public void surfaceDestroyed(SurfaceHolder holder) { backend.surfaceGone(); }

    @SuppressLint("ClickableViewAccessibility") // Touch is the map's own input, forwarded verbatim.
    @Override public boolean onTouchEvent(MotionEvent event) {
        backend.touch(event);
        return true;
    }
}
