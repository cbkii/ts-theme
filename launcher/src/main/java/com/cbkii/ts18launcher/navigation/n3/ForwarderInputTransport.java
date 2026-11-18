package com.cbkii.ts18launcher.navigation.n3;

import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.lang.reflect.Method;

/**
 * Optional legacy display-owner forwarder. Some platform builds expose an activity-manager
 * contract handing a virtual-display owner a forwarder that injects only into that
 * display on platform builds that expose this legacy contract. It is not a public SDK or a
 * guarantee of authority: missing APIs, hidden-API policy and permission refusals leave N3
 * view-only. Nothing falls back to global injection or changes device permissions.
 */
final class ForwarderInputTransport implements N3InputTransport {
    private final Object forwarder;
    private final Method forward;
    private String reason;

    private ForwarderInputTransport(Object forwarder, Method forward, String reason) {
        this.forwarder = forwarder;
        this.forward = forward;
        this.reason = reason;
    }

    // Isolated, default-off legacy experiment. API/permission refusal returns view-only.
    @android.annotation.SuppressLint("PrivateApi")
    static N3InputTransport open(int displayId) {
        if (displayId <= 0) return new ForwarderInputTransport(null, null, "No owned display");
        try {
            Object am = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
            Object forwarder = Class.forName("android.app.IActivityManager")
                    .getMethod("createInputForwarder", int.class).invoke(am, displayId);
            if (forwarder == null) return new ForwarderInputTransport(null, null, "Forwarder refused");
            Method forward = Class.forName("android.app.IInputForwarder").getMethod("forwardEvent", InputEvent.class);
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
        if (forwarder == null || event == null || !reason.isEmpty()) return false;
        try {
            Object ok = forward.invoke(forwarder, event);
            if (ok instanceof Boolean && (Boolean) ok) return true;
            reason = "Input event refused";
        } catch (Throwable failed) {
            Throwable cause = failed.getCause() != null ? failed.getCause() : failed;
            reason = "Input path failed (" + cause.getClass().getSimpleName() + ")";
        }
        return false;
    }

    @Override public void close() { reason = "Input path closed"; }
}
