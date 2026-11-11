package com.cbkii.ts18launcher.n3host;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;

import com.cbkii.ts18launcher.navigation.n3.IN3DisplayHost;
import com.cbkii.ts18launcher.navigation.n3.IN3DisplayHostClient;
import com.cbkii.ts18launcher.navigation.n3.N3HostPolicy;

import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Binder edge of the optional display host. Identity comes only from Binder.getCallingUid() and
 * PackageManager. Admission is DisplayHostCore + N3HostPolicy. Displays are private to this host;
 * input reaches only an owned display through the platform's display-owner forwarder.
 */
public final class DisplayHostService extends Service {
    private static final int PROTOCOL = 1;
    private final Handler main = new Handler(Looper.getMainLooper());
    private DisplayHostCore core;

    @Override public void onCreate() {
        super.onCreate();
        N3HostPolicy.Config config = new N3HostPolicy.Config(BuildConfig.HOST_ENABLED,
                BuildConfig.CALLER_PACKAGE, BuildConfig.CALLER_CERT_SHA256, 0,
                new HashSet<>(Arrays.asList(BuildConfig.COMPONENTS.split(","))));
        core = new DisplayHostCore(new N3HostPolicy(config, SystemClock::elapsedRealtime,
                new java.security.SecureRandom().nextLong()), new FrameworkPlatform(this, main));
    }

    @Override public IBinder onBind(Intent intent) { return binder; }

    private final IN3DisplayHost.Stub binder = new IN3DisplayHost.Stub() {
        @Override public int protocolVersion() { return PROTOCOL; }

        @Override public Bundle open(Surface surface, int width, int height, int dpi, IN3DisplayHostClient client) {
            int uid = Binder.getCallingUid();
            DisplayHostCore.Opened opened = onMain(() -> core.open(uid, surface, width, height, dpi, client));
            Bundle result = new Bundle();
            result.putString("decision", opened == null ? "NO_REPLY" : opened.decision.name());
            if (opened != null && opened.displayId > 0) {
                result.putInt("displayId", opened.displayId);
                result.putLong("lease", opened.lease);
            }
            return result;
        }

        @Override public int launch(int displayId, long lease, String component) {
            int uid = Binder.getCallingUid();
            return ordinal(onMain(() -> core.launch(uid, displayId, lease, component)));
        }

        @Override public int motion(int displayId, long lease, MotionEvent event) {
            int uid = Binder.getCallingUid();
            if (event == null) return N3HostPolicy.Decision.DENY_POINTERS.ordinal();
            int count = event.getPointerCount();
            if (count > 10) return N3HostPolicy.Decision.DENY_POINTERS.ordinal();
            float[] xs = new float[count], ys = new float[count];
            for (int i = 0; i < count; i++) { xs[i] = event.getX(i); ys[i] = event.getY(i); }
            return ordinal(onMain(() -> core.motion(uid, displayId, lease, event.getActionMasked(), xs, ys, event)));
        }

        @Override public int back(int displayId, long lease) {
            int uid = Binder.getCallingUid();
            return ordinal(onMain(() -> core.back(uid, displayId, lease)));
        }

        @Override public int setSurface(int displayId, long lease, Surface surface) {
            int uid = Binder.getCallingUid();
            return ordinal(onMain(() -> core.setSurface(uid, displayId, lease, surface)));
        }

        @Override public int close(int displayId, long lease) {
            int uid = Binder.getCallingUid();
            return ordinal(onMain(() -> core.close(uid, displayId, lease)));
        }
    };

    private static int ordinal(N3HostPolicy.Decision decision) {
        return decision == null ? -1 : decision.ordinal();
    }

    /** Runs on the main thread with a bounded wait so a Binder thread never blocks indefinitely. */
    private <T> T onMain(java.util.concurrent.Callable<T> task) {
        java.util.concurrent.FutureTask<T> future = new java.util.concurrent.FutureTask<>(task);
        main.post(future);
        try {
            return future.get(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception failed) {
            future.cancel(false);
            return null;
        }
    }

    /** Real framework edge. Main thread only. */
    static final class FrameworkPlatform implements DisplayHostCore.Platform {
        private final Context context;
        private final Handler main;
        private final Map<Integer, VirtualDisplay> displays = new HashMap<>();
        private final Map<Integer, Surface> presenters = new HashMap<>();
        private final Map<Integer, ImageReader> readers = new HashMap<>();
        private final Map<Integer, Object> forwarders = new HashMap<>();

        FrameworkPlatform(Context context, Handler main) {
            this.context = context;
            this.main = main;
        }

        @Override public N3HostPolicy.Caller resolve(int uid) {
            PackageManager pm = context.getPackageManager();
            String[] packages = pm.getPackagesForUid(uid);
            if (packages == null || packages.length != 1) return new N3HostPolicy.Caller(uid, uid / 100000, packages, "");
            try {
                @SuppressWarnings("deprecation")
                PackageInfo info = pm.getPackageInfo(packages[0], PackageManager.GET_SIGNATURES);
                @SuppressWarnings("deprecation")
                Signature[] signatures = info.signatures;
                String cert = signatures != null && signatures.length == 1 ? sha256(signatures[0].toByteArray()) : "";
                return new N3HostPolicy.Caller(uid, uid / 100000, packages, cert);
            } catch (PackageManager.NameNotFoundException gone) {
                return null;
            }
        }

        @android.annotation.SuppressLint("WrongConstant") // RGBA_8888 is a valid ImageReader format.
        @Override public int createDisplay(int width, int height, int dpi, Object surface, Object client) {
            DisplayManager manager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            ImageReader reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            VirtualDisplay display;
            try {
                display = manager.createVirtualDisplay("ts18-n3-host", width, height, dpi, reader.getSurface(), 0,
                        new VirtualDisplay.Callback() {
                            @Override public void onStopped() { lost(client, "Display removed"); }
                        }, main);
            } catch (RuntimeException refused) {
                reader.close();
                return 0;
            }
            if (display == null || display.getDisplay() == null) { reader.close(); return 0; }
            int id = display.getDisplay().getDisplayId();
            displays.put(id, display);
            presenters.put(id, (Surface) surface);
            readers.put(id, reader);
            reader.setOnImageAvailableListener(r -> {
                Image image = r.acquireLatestImage();
                if (image != null) image.close();
                if (readers.remove(id) == null) return;
                VirtualDisplay current = displays.get(id);
                if (current != null) current.setSurface(presenters.get(id));
                main.post(r::close);
                try { ((IN3DisplayHostClient) client).onFirstFrame(id); } catch (RemoteException gone) { }
            }, main);
            forwarders.put(id, forwarder(id));
            return id;
        }

        private void lost(Object client, String reason) {
            try { ((IN3DisplayHostClient) client).onDisplayLost(0, reason); } catch (RemoteException gone) { }
        }

        @Override public boolean setSurface(int displayId, Object surface) {
            VirtualDisplay display = displays.get(displayId);
            if (display == null) return false;
            presenters.put(displayId, (Surface) surface);
            if (!readers.containsKey(displayId)) display.setSurface((Surface) surface);
            return true;
        }

        @Override public boolean launch(int displayId, String component) {
            ComponentName target = ComponentName.unflattenFromString(component);
            if (target == null || !displays.containsKey(displayId)) return false;
            Intent intent = new Intent(Intent.ACTION_MAIN).setComponent(target)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            try {
                if (am == null || !am.isActivityStartAllowedOnDisplay(context, displayId, intent)) return false;
                context.startActivity(intent, ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle());
                return true;
            } catch (RuntimeException refused) {
                return false;
            }
        }

        @Override public boolean inject(int displayId, Object motionEvent) {
            return forward(displayId, (InputEvent) motionEvent);
        }

        @Override public boolean back(int displayId) {
            long now = SystemClock.uptimeMillis();
            KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0);
            return forward(displayId, down) && forward(displayId, KeyEvent.changeAction(down, KeyEvent.ACTION_UP));
        }

        private boolean forward(int displayId, InputEvent event) {
            Object forwarder = forwarders.get(displayId);
            if (forwarder == null) return false;
            try {
                Method method = forwarder.getClass().getMethod("forwardEvent", InputEvent.class);
                Object ok = method.invoke(forwarder, event);
                return !(ok instanceof Boolean) || (Boolean) ok;
            } catch (Throwable failed) {
                return false;
            }
        }

        /** Display-owner input forwarder; the platform verifies this host owns the display. */
        private static Object forwarder(int displayId) {
            try {
                Object am = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
                return am.getClass().getMethod("createInputForwarder", int.class).invoke(am, displayId);
            } catch (Throwable refused) {
                return null;
            }
        }

        @Override public void releaseDisplay(int displayId) {
            VirtualDisplay display = displays.remove(displayId);
            if (display != null) display.release();
            ImageReader reader = readers.remove(displayId);
            if (reader != null) reader.close();
            presenters.remove(displayId);
            forwarders.remove(displayId);
        }

        @Override public boolean linkToDeath(Object client, Runnable onDeath) {
            try {
                ((IN3DisplayHostClient) client).asBinder().linkToDeath(() -> main.post(onDeath), 0);
                return true;
            } catch (RemoteException dead) {
                return false;
            }
        }

        private static String sha256(byte[] bytes) {
            try {
                StringBuilder out = new StringBuilder();
                for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) out.append(String.format("%02x", b & 0xff));
                return out.toString();
            } catch (Exception impossible) {
                return "";
            }
        }
    }
}
