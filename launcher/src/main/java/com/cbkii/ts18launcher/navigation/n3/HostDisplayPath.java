package com.cbkii.ts18launcher.navigation.n3;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.MotionEvent;
import android.view.Surface;

/**
 * Client of the optional N3 display host (separate n3host APK). Used only when the driver chose
 * it and the host is installed; the host decides every request from its own view of this caller.
 * A disabled or absent host is reported as HOST_UNAVAILABLE, never worked around.
 */
final class HostDisplayPath implements N3DisplayPath {
    static final String HOST_PACKAGE = "com.cbkii.ts18launcher.n3host";
    static final String ACTION = "com.cbkii.ts18launcher.n3host.BIND_DISPLAY_HOST";
    static final int PROTOCOL = 1;

    private final Context app;
    private final Handler main;
    private IN3DisplayHost host;
    private ServiceConnection connection;
    private int displayId;
    private long lease;
    private long generation;
    private Callback callback;

    HostDisplayPath(Context context, Handler main) {
        this.app = context.getApplicationContext();
        this.main = main;
    }

    /** The host service component, only if exactly one exported service answers the action. */
    static ComponentName resolve(Context context) {
        Intent intent = new Intent(ACTION).setPackage(HOST_PACKAGE);
        java.util.List<ResolveInfo> found = context.getPackageManager().queryIntentServices(intent, 0);
        if (found == null || found.size() != 1 || found.get(0).serviceInfo == null
                || !found.get(0).serviceInfo.exported) return null;
        return new ComponentName(HOST_PACKAGE, found.get(0).serviceInfo.name);
    }

    @Override public Authority authority() { return Authority.OPTIONAL_HOST; }

    private final IN3DisplayHostClient.Stub client = new IN3DisplayHostClient.Stub() {
        @Override public void onFirstFrame(int id) {
            main.post(() -> { if (id == displayId && callback != null) callback.firstFrame(generation); });
        }
        @Override public void onDisplayLost(int id, String reason) {
            main.post(() -> { if (id == displayId && callback != null) callback.lost(generation, reason); });
        }
    };

    @Override public void create(long gen, Surface surface, int width, int height, int dpi, Callback cb) {
        release();
        generation = gen;
        callback = cb;
        ComponentName component = resolve(app);
        if (component == null) { cb.failed(gen, "HOST_UNAVAILABLE: display host not installed"); return; }
        connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) {
                if (gen != generation) return;
                host = IN3DisplayHost.Stub.asInterface(service);
                try {
                    if (host.protocolVersion() != PROTOCOL) { cb.failed(gen, "HOST_UNAVAILABLE: protocol mismatch"); return; }
                    Bundle result = host.open(surface, width, height, dpi, client);
                    String decision = result == null ? "NO_REPLY" : result.getString("decision", "NO_REPLY");
                    if (!"ALLOW".equals(decision)) { cb.failed(gen, "HOST_UNAVAILABLE: " + decision); return; }
                    displayId = result.getInt("displayId");
                    lease = result.getLong("lease");
                    cb.created(gen, displayId);
                } catch (RemoteException | RuntimeException failed) {
                    cb.failed(gen, "HOST_UNAVAILABLE: host did not answer");
                }
            }
            @Override public void onServiceDisconnected(ComponentName name) { cb.lost(gen, "Display host stopped"); }
            @Override public void onBindingDied(ComponentName name) { cb.lost(gen, "Display host stopped"); }
            @Override public void onNullBinding(ComponentName name) { cb.failed(gen, "HOST_UNAVAILABLE: host refused binding"); }
        };
        boolean bound;
        try {
            bound = app.bindService(new Intent(ACTION).setComponent(component), connection, Context.BIND_AUTO_CREATE);
        } catch (SecurityException refused) {
            bound = false;
        }
        if (!bound) { connection = null; cb.failed(gen, "HOST_UNAVAILABLE: bind refused"); }
    }

    @Override public void launch(long gen, String component, Callback cb) {
        int decision = call(() -> host.launch(displayId, lease, component));
        if (decision == 0) cb.launched(gen); else cb.failed(gen, "Host refused launch: " + name(decision));
    }

    @Override public void setSurface(Surface surface) {
        call(() -> host.setSurface(displayId, lease, surface));
    }

    @Override public N3InputTransport input() {
        if (host == null) return N3InputTransport.NONE;
        return new N3InputTransport() {
            @Override public String unavailableReason() { return host == null ? "Display host not connected" : ""; }
            @Override public boolean motion(MotionEvent e) { return call(() -> host.motion(displayId, lease, e)) == 0; }
            @Override public boolean back() { return call(() -> host.back(displayId, lease)) == 0; }
            @Override public void close() { }
        };
    }

    @Override public void release() {
        if (host != null && displayId > 0) call(() -> host.close(displayId, lease));
        if (connection != null) {
            try { app.unbindService(connection); } catch (RuntimeException ignored) { }
        }
        connection = null;
        host = null;
        displayId = 0;
        lease = 0L;
        callback = null;
    }

    private interface Call { int run() throws RemoteException; }

    private int call(Call call) {
        if (host == null) return -1;
        try { return call.run(); } catch (RemoteException | RuntimeException failed) { return -1; }
    }

    private static String name(int ordinal) {
        N3HostPolicy.Decision[] all = N3HostPolicy.Decision.values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal].name() : "NO_REPLY";
    }

    static boolean installed(Context context) {
        try {
            context.getPackageManager().getPackageInfo(HOST_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException absent) {
            return false;
        }
    }
}
