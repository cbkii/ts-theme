package com.cbkii.ts18launcher.navigation.n3;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.view.Surface;

/**
 * The public API 29 path: a private, non-default virtual display owned by the launcher.
 *
 * The first frames go to an off-screen reader so that real pixels from the launched app are
 * observed before the display is moved to the presenter surface; allocation or launch acceptance
 * alone is never reported as rendering. Android admits another app's Activity onto this display
 * only if that Activity allows embedding; otherwise this path reports BLOCKED_NO_AUTHORITY and the
 * optional host is the only route. Each launch is one user-initiated start; it is never replayed.
 */
final class PublicDisplayPath implements N3DisplayPath {
    static final String BLOCKED = "BLOCKED_NO_AUTHORITY";

    private final Context app;
    private final Handler main;
    private VirtualDisplay display;
    private ImageReader reader;
    private Surface presenter;
    private boolean framed;
    private N3InputTransport input = N3InputTransport.NONE;

    PublicDisplayPath(Context context, Handler main) {
        this.app = context.getApplicationContext();
        this.main = main;
    }

    @Override public Authority authority() { return Authority.LAUNCHER_PRIVATE_DISPLAY; }

    @android.annotation.SuppressLint("WrongConstant") // RGBA_8888 is a valid ImageReader format.
    @Override public void create(long generation, Surface surface, int width, int height, int dpi,
                                 Callback callback) {
        release();
        presenter = surface;
        DisplayManager manager = (DisplayManager) app.getSystemService(Context.DISPLAY_SERVICE);
        try {
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            reader.setOnImageAvailableListener(r -> {
                Image image = r.acquireLatestImage();
                if (image != null) image.close();
                if (framed || display == null) return;
                framed = true;
                // Real pixels from the display's content arrived; now present them.
                display.setSurface(presenter);
                ImageReader done = reader;
                reader = null;
                main.post(done::close);
                callback.firstFrame(generation);
            }, main);
            // Flags 0: private to this app, not shown elsewhere, never the default display.
            display = manager.createVirtualDisplay("ts18-n3", width, height, dpi, reader.getSurface(), 0,
                    new VirtualDisplay.Callback() {
                        @Override public void onStopped() { callback.lost(generation, "N3 display was removed"); }
                    }, main);
        } catch (RuntimeException refused) {
            release();
            callback.failed(generation, "Display not created: " + refused.getClass().getSimpleName());
            return;
        }
        if (display == null || display.getDisplay() == null) {
            release();
            callback.failed(generation, "Display not created");
            return;
        }
        int id = display.getDisplay().getDisplayId();
        input = ForwarderInputTransport.open(id);
        callback.created(generation, id);
    }

    @Override public void launch(long generation, String component, Callback callback) {
        ComponentName target = ComponentName.unflattenFromString(component == null ? "" : component);
        if (display == null || target == null) { callback.failed(generation, "No display or target"); return; }
        int id = display.getDisplay().getDisplayId();
        Intent intent = new Intent(Intent.ACTION_MAIN).setComponent(target)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
        try {
            if (am == null || !am.isActivityStartAllowedOnDisplay(app, id, intent)) {
                callback.failed(generation, BLOCKED + ": " + target.getPackageName()
                        + " does not allow an ordinary app to host it on a private display");
                return;
            }
            app.startActivity(intent, ActivityOptions.makeBasic().setLaunchDisplayId(id).toBundle());
        } catch (SecurityException refused) {
            callback.failed(generation, BLOCKED + ": start refused on the N3 display");
            return;
        } catch (RuntimeException failed) {
            callback.failed(generation, "Start failed: " + failed.getClass().getSimpleName());
            return;
        }
        callback.launched(generation);
    }

    @Override public void setSurface(Surface surface) {
        presenter = surface;
        if (display != null && framed) display.setSurface(surface);
    }

    @Override public N3InputTransport input() { return input; }

    @Override public void release() {
        input.close();
        input = N3InputTransport.NONE;
        if (display != null) { display.release(); display = null; }
        if (reader != null) { reader.close(); reader = null; }
        framed = false;
        presenter = null;
    }
}
