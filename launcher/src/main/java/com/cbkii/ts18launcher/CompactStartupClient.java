package com.cbkii.ts18launcher;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Preference/evidence observation only. NavigationWindowController retains task authority. */
final class CompactStartupClient {
    private static final Uri URI = Uri.parse("content://app.organicmaps.incar.compact.startup/status");
    static final class Snapshot {
        final boolean compatible, enabled, rendererReady;
        final int task;
        final int width, height;
        Snapshot(boolean compatible, boolean enabled, boolean ready, int task) {
            this(compatible, enabled, ready, task, -1, -1);
        }
        Snapshot(boolean compatible, boolean enabled, boolean ready, int task, int width, int height) {
            this.compatible = compatible; this.enabled = enabled; rendererReady = ready; this.task = task;
            this.width = width; this.height = height;
        }
        boolean surfaceMatches(int width, int height) {
            // Maps reports its content area, excluding any task caption/insets. A stale
            // fullscreen surface must not qualify a newly compacted task.
            return rendererReady && this.width > 0 && this.height > 0
                    && this.width <= width && this.height <= height;
        }
        static Snapshot unknown() { return new Snapshot(false, false, false, -1); }
    }
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = new java.util.concurrent.ThreadPoolExecutor(
            0, 2, 15L, TimeUnit.SECONDS, new java.util.concurrent.SynchronousQueue<>(), r -> {
                Thread t = new Thread(r, "compact-startup-query"); t.setDaemon(true); return t;
            });
    private final java.util.function.Consumer<Snapshot> callback;
    private final android.os.Messenger messenger;
    private java.util.function.IntConsumer homeReturn = task -> { };
    void setHomeReturnCallback(java.util.function.IntConsumer callback) { homeReturn = callback; }
    private boolean busy, closed, dirty;
    private int failures;
    private String lastQueryFailure = "";
    private int request;
    private long until;
    private CancellationSignal cancel;
    private final Runnable poll = () -> {
        if (SystemClock.elapsedRealtime() < until) refresh();
    };

    CompactStartupClient(Context context, java.util.function.Consumer<Snapshot> callback) {
        this.context = context.getApplicationContext(); this.callback = callback;
        messenger = new android.os.Messenger(new PeerHandler(this));
    }
    private static final class PeerHandler extends Handler {
        private final java.lang.ref.WeakReference<CompactStartupClient> owner;
        PeerHandler(CompactStartupClient client) { super(Looper.getMainLooper()); owner = new java.lang.ref.WeakReference<>(client); }
        @Override public void handleMessage(android.os.Message message) {
            CompactStartupClient client = owner.get();
            if (client != null && !client.closed && message.what == 1)
                client.refreshFromPeer(message.sendingUid);
            else if (client != null && !client.closed && message.what == 2)
                client.returnHomeFromPeer(message.sendingUid, message.arg1, message.arg2);
        }
    }

    private void returnHomeFromPeer(int uid, int task, int boot) {
        if (closed || task <= 0 || boot < 0) return;
        try {
            worker.execute(() -> {
                boolean trusted = CompactStartupPeer.trusted(context, uid)
                        && boot == Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
                if (trusted) main.post(() -> { if (!closed) homeReturn.accept(task); });
            });
        } catch (java.util.concurrent.RejectedExecutionException unavailable) {
            MediaEventTrace.record("compact-startup", "return-auth-unavailable");
        }
    }

    private void refreshFromPeer(int uid) {
        if (closed) return;
        try {
            worker.execute(() -> {
                boolean trusted = CompactStartupPeer.trusted(context, uid);
                if (trusted) main.post(() -> { if (!closed) refresh(); });
            });
        } catch (java.util.concurrent.RejectedExecutionException full) {
            MediaEventTrace.record("compact-startup", "peer-auth-saturated");
        }
    }

    static boolean automaticAllowed(Context context) {
        try {
            android.os.PowerManager power = (android.os.PowerManager) context.getSystemService(Context.POWER_SERVICE);
            android.app.KeyguardManager keyguard = (android.app.KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            android.media.AudioManager audio = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            android.os.UserManager users = (android.os.UserManager) context.getSystemService(Context.USER_SERVICE);
            return android.os.Build.VERSION.SDK_INT == 29 && HomeMode.isDefaultHome(context)
                    && power != null && power.isInteractive() && keyguard != null && !keyguard.isKeyguardLocked()
                    && users != null && users.isUserUnlocked() && audio != null && audio.getMode() == android.media.AudioManager.MODE_NORMAL;
        } catch (RuntimeException unavailable) { return false; }
    }

    void watch() {
        if (closed) return;
        until = SystemClock.elapsedRealtime() + 45000L;
        failures = 0;
        refresh();
    }
    void refresh() {
        if (closed) return;
        if (busy) { dirty = true; return; }
        dirty = false;
        busy = true;
        int token = ++request;
        CancellationSignal signal = new CancellationSignal(); cancel = signal;
        Runnable timeout = () -> {
            if (!closed && busy && request == token) {
                // Invalidate late evidence and release the request slot. At most two Binder
                // workers can remain stuck; saturation fails closed without an unbounded queue.
                request++; busy = false;
                MediaEventTrace.record("compact-startup", "query-unavailable", "phase=binder reason=PROBE_TIMEOUT");
                callback.accept(Snapshot.unknown());
                workerCancel(signal);
                scheduleNext(Snapshot.unknown());
            }
        };
        main.postDelayed(timeout, 2000L);
        try { worker.execute(() -> {
            Snapshot result = Snapshot.unknown();
            String phase = "package";
            try {
                int uid = context.getPackageManager().getApplicationInfo(CompactStartupPeer.PACKAGE, 0).uid;
                int boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
                if (CompactStartupPeer.trusted(context, uid) && boot >= 0
                        && CompactStartupPeer.ownsProvider(context, "app.organicmaps.incar.compact.startup")) {
                    Uri uri = URI.buildUpon().appendQueryParameter("v", "1")
                            .appendQueryParameter("boot", Integer.toString(boot)).build();
                    android.os.Bundle observation = new android.os.Bundle();
                    observation.putInt("boot", boot);
                    observation.putBinder("observer", messenger.getBinder());
                    phase = "observe";
                    try {
                        context.getContentResolver().call(URI, "observe", "1", observation);
                    } catch (IllegalArgumentException observerUnavailable) {
                        // Subscription failure is not a consent answer. A separately
                        // authenticated query can still establish the read-only preference.
                        traceFailure(phase, observerUnavailable);
                    }
                    phase = "query";
                    try (Cursor c = context.getContentResolver().query(uri, null, null, null, null, signal)) {
                        if (c != null && c.moveToFirst() && number(c, "protocol") == 1
                                && CompactStartupPeer.PACKAGE.equals(c.getString(c.getColumnIndexOrThrow("package")))
                                && number(c, "boot") == boot && number(c, "user") == AndroidUserId.current()
                                && (boot + ":" + AndroidUserId.current() + ":1").equals(c.getString(c.getColumnIndexOrThrow("generation")))) {
                            MediaEventTrace.record("compact-startup", "evidence", "boot=" + boot
                                    + " compatible=1 enabled=" + number(c, "enabled")
                                    + " task=" + number(c, "task") + " stage=" + c.getString(c.getColumnIndexOrThrow("stage"))
                                    + " native=" + number(c, "native") + " attached=" + number(c, "attached")
                                    + " size=" + number(c, "width") + "x" + number(c, "height")
                                    + " resumed=" + number(c, "resumed") + " renderer=" + number(c, "renderer_ready")
                                    + " at=" + SystemClock.elapsedRealtime());
                            result = new Snapshot(true, number(c, "enabled") == 1,
                                    number(c, "renderer_ready") == 1, number(c, "task"), number(c, "width"), number(c, "height"));
                        }
                    }
                }
            } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException unavailable) {
                traceFailure(phase, unavailable);
            }
            if (result.compatible) lastQueryFailure = "";
            final Snapshot snapshot = signal.isCanceled() ? Snapshot.unknown() : result;
            main.post(() -> {
                main.removeCallbacks(timeout);
                if (closed || request != token) return;
                busy = false;
                callback.accept(snapshot);
                scheduleNext(snapshot);
            });
        }); } catch (java.util.concurrent.RejectedExecutionException full) {
            main.removeCallbacks(timeout); busy = false;
            callback.accept(Snapshot.unknown());
            scheduleNext(Snapshot.unknown());
        }
    }
    private void scheduleNext(Snapshot snapshot) {
        main.removeCallbacks(poll);
        failures = snapshot.compatible ? 0 : failures + 1;
        // The time limit bounds automatic polling. Authenticated lifecycle/preference
        // invalidations remain live after it so disabling startup or losing a surface is seen.
        if (dirty) main.post(this::refresh);
        else if (SystemClock.elapsedRealtime() < until
                && (snapshot.compatible && !snapshot.rendererReady || failures > 0))
            main.postDelayed(poll, snapshot.compatible ? 500L : Math.min(3000L, 500L * failures));
    }
    // Cancellation may itself cross Binder; do not block HOME's main thread.
    private void workerCancel(CancellationSignal signal) {
        Thread thread = new Thread(signal::cancel, "compact-query-cancel"); thread.setDaemon(true); thread.start();
    }
    private static int number(Cursor c, String key) { return c.getInt(c.getColumnIndexOrThrow(key)); }
    private void traceFailure(String phase, Exception error) {
        String reason = error.getMessage();
        if (reason == null || !reason.matches("AUTH_MISMATCH|PROTOCOL_MISMATCH|BOOT_UNAVAILABLE|BOOT_MISMATCH|USER_LOCKED|OBSERVER_REQUIRED"))
            reason = error instanceof SecurityException ? "AUTH_MISMATCH" : "PROVIDER_UNAVAILABLE";
        String failure = "phase=" + phase + " reason=" + reason + " type=" + error.getClass().getSimpleName();
        if (!failure.equals(lastQueryFailure)) {
            lastQueryFailure = failure;
            MediaEventTrace.record("compact-startup", "query-unavailable", failure);
        }
    }
    void close() {
        if (closed) return;
        closed = true; request++; main.removeCallbacksAndMessages(null);
        if (cancel != null) workerCancel(cancel);
        try { worker.execute(() -> {
            try {
                if (CompactStartupPeer.ownsProvider(context, "app.organicmaps.incar.compact.startup")) {
                    android.os.Bundle observation = new android.os.Bundle();
                    observation.putInt("boot", Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1));
                    observation.putBinder("observer", messenger.getBinder());
                    context.getContentResolver().call(URI, "unobserve", "1", observation);
                }
            } catch (RuntimeException unavailable) { MediaEventTrace.record("compact-startup", "unsubscribe-unavailable"); }
        }); } catch (java.util.concurrent.RejectedExecutionException full) {
            // The peer holds only a Binder to a weak handler. Cleanup must not block HOME.
            MediaEventTrace.record("compact-startup", "unsubscribe-saturated");
        }
        worker.shutdown();
    }
}
