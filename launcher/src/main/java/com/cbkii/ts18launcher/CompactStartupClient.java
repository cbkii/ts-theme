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
import java.util.concurrent.Executors;

/** Preference/evidence observation only. NavigationWindowController retains task authority. */
final class CompactStartupClient {
    private static final Uri URI = Uri.parse("content://app.organicmaps.incar.compact.startup/status");
    static final class Snapshot {
        final boolean compatible, enabled, rendererReady;
        final int task;
        Snapshot(boolean compatible, boolean enabled, boolean ready, int task) {
            this.compatible = compatible; this.enabled = enabled; rendererReady = ready; this.task = task;
        }
        static Snapshot unknown() { return new Snapshot(false, false, false, -1); }
    }
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "compact-startup-query"); t.setDaemon(true); return t;
    });
    private final java.util.function.Consumer<Snapshot> callback;
    private final android.os.Messenger messenger;
    private boolean busy, closed, dirty;
    private int failures;
    private int request;
    private long until;
    private CancellationSignal cancel;
    private final Runnable poll = this::refresh;

    CompactStartupClient(Context context, java.util.function.Consumer<Snapshot> callback) {
        this.context = context.getApplicationContext(); this.callback = callback;
        messenger = new android.os.Messenger(new PeerHandler(this));
    }
    private static final class PeerHandler extends Handler {
        private final java.lang.ref.WeakReference<CompactStartupClient> owner;
        PeerHandler(CompactStartupClient client) { super(Looper.getMainLooper()); owner = new java.lang.ref.WeakReference<>(client); }
        @Override public void handleMessage(android.os.Message message) {
            CompactStartupClient client = owner.get();
            if (client != null && !client.closed && message.what == 1
                    && CompactStartupPeer.trusted(client.context, message.sendingUid)) client.refresh();
        }
    }

    static void suppressForBoot(Context context) {
        try {
            int boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
            if (boot >= 0) context.getSharedPreferences("startup_readiness", Context.MODE_PRIVATE).edit()
                    .putInt("compact_user_override_v1", boot).apply();
        } catch (RuntimeException unavailable) {
            MediaEventTrace.record("compact-startup", "override-journal-unavailable");
        }
    }
    static boolean automaticAllowed(Context context) {
        try {
            android.os.PowerManager power = (android.os.PowerManager) context.getSystemService(Context.POWER_SERVICE);
            android.app.KeyguardManager keyguard = (android.app.KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            android.media.AudioManager audio = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            android.os.UserManager users = (android.os.UserManager) context.getSystemService(Context.USER_SERVICE);
            int boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
            boolean suppressed = boot < 0 || context.getSharedPreferences("startup_readiness", Context.MODE_PRIVATE)
                    .getInt("compact_user_override_v1", -1) == boot;
            return !suppressed && android.os.Build.VERSION.SDK_INT == 29 && HomeMode.isDefaultHome(context)
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
                callback.accept(Snapshot.unknown());
                // Remains busy until Binder returns; never accumulate parallel stuck queries.
                workerCancel(signal);
            }
        };
        main.postDelayed(timeout, 2000L);
        worker.execute(() -> {
            Snapshot result = Snapshot.unknown();
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
                    context.getContentResolver().call(URI, "observe", "1", observation);
                    try (Cursor c = context.getContentResolver().query(uri, null, null, null, null, signal)) {
                        if (c != null && c.moveToFirst() && number(c, "protocol") == 1
                                && CompactStartupPeer.PACKAGE.equals(c.getString(c.getColumnIndexOrThrow("package")))
                                && number(c, "boot") == boot && number(c, "user") == AndroidUserId.current()
                                && (boot + ":" + AndroidUserId.current() + ":1").equals(c.getString(c.getColumnIndexOrThrow("generation")))) {
                            MediaEventTrace.record("compact-startup", "evidence", "boot=" + boot
                                    + " task=" + number(c, "task") + " stage=" + c.getString(c.getColumnIndexOrThrow("stage"))
                                    + " native=" + number(c, "native") + " attached=" + number(c, "attached")
                                    + " size=" + number(c, "width") + "x" + number(c, "height")
                                    + " resumed=" + number(c, "resumed") + " renderer=" + number(c, "renderer_ready")
                                    + " at=" + SystemClock.elapsedRealtime());
                            result = new Snapshot(true, number(c, "enabled") == 1,
                                    number(c, "renderer_ready") == 1, number(c, "task"));
                        }
                    }
                }
            } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException unavailable) {
                MediaEventTrace.record("compact-startup", "query-unavailable", unavailable.getClass().getSimpleName());
            }
            final Snapshot snapshot = signal.isCanceled() ? Snapshot.unknown() : result;
            main.post(() -> {
                main.removeCallbacks(timeout);
                busy = false;
                if (closed || request != token) return;
                callback.accept(snapshot);
                main.removeCallbacks(poll);
                failures = snapshot.compatible ? 0 : failures + 1;
                if (dirty) main.post(poll);
                else if (SystemClock.elapsedRealtime() < until
                        && (snapshot.compatible && !snapshot.rendererReady || failures > 0 && failures < 3))
                    main.postDelayed(poll, 500L);
            });
        });
    }
    // Cancellation may itself cross Binder; do not block HOME's main thread.
    private void workerCancel(CancellationSignal signal) {
        Thread thread = new Thread(signal::cancel, "compact-query-cancel"); thread.setDaemon(true); thread.start();
    }
    private static int number(Cursor c, String key) { return c.getInt(c.getColumnIndexOrThrow(key)); }
    void close() {
        closed = true; request++; main.removeCallbacksAndMessages(null);
        if (cancel != null) workerCancel(cancel);
        worker.execute(() -> {
            try {
                if (CompactStartupPeer.ownsProvider(context, "app.organicmaps.incar.compact.startup")) {
                    android.os.Bundle observation = new android.os.Bundle();
                    observation.putInt("boot", Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1));
                    observation.putBinder("observer", messenger.getBinder());
                    context.getContentResolver().call(URI, "unobserve", "1", observation);
                }
            } catch (RuntimeException unavailable) { MediaEventTrace.record("compact-startup", "unsubscribe-unavailable"); }
        });
        worker.shutdown();
    }
}
