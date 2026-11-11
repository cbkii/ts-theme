package com.cbkii.ts18launcher.navigation.n3;

import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.lang.reflect.Method;

/**
 * Android 10's display-owner input path (the one the platform's own ActivityView uses): the
 * activity manager hands the owner of a virtual display a forwarder that injects only into that
 * display. The platform itself checks that the caller owns the display; no injection permission
 * is involved and nothing can reach any other display. It is not public SDK, so it is reached by
 * one reflective lookup that fails closed: any refusal (hidden-API policy, security, missing
 * method) leaves N3 view-only and says so.
 */
final class ForwarderInputTransport implements N3InputTransport {
    private final Object forwarder;
    private final Method forward;
    private final String reason;

    private ForwarderInputTransport(Object forwarder, Method forward, String reason) {
        this.forwarder = forwarder;
        this.forward = forward;
        this.reason = reason;
    }

    static N3InputTransport open(int displayId) {
        if (displayId <= 0) return new ForwarderInputTransport(null, null, "No owned display");
        try {
            Object am = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
            Object forwarder = am.getClass().getMethod("createInputForwarder", int.class).invoke(am, displayId);
            if (forwarder == null) return new ForwarderInputTransport(null, null, "Forwarder refused");
            Method forward = forwarder.getClass().getMethod("forwardEvent", InputEvent.class);
            return new ForwarderInputTransport(forwarder, forward, "");
        } catch (Throwable refused) {
            Throwable cause = refused.getCause() != null ? refused.getCause() : refused;
            return new ForwarderInputTransport(null, null,
                    "Input path refused (" + cause.getClass().getSimpleName() + ")");
        }
    }

    @Override public String unavailableReason() { return reason; }

    @Override public boolean motion(MotionEvent displayEvent) {
        return send(displayEvent);
    }

    @Override public boolean back() {
        KeyEvent[] keys = N3InputTransport.backKeys();
        return send(keys[0]) && send(keys[1]);
    }

    private boolean send(InputEvent event) {
        if (forwarder == null || event == null) return false;
        try {
            Object ok = forward.invoke(forwarder, event);
            return !(ok instanceof Boolean) || (Boolean) ok;
        } catch (Throwable failed) {
            return false;
        }
    }

    @Override public void close() { }
}
