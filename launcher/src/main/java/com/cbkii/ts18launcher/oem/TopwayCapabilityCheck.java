package com.cbkii.ts18launcher.oem;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import java.util.List;
import java.util.function.Consumer;

/**
 * Operator-initiated capability check. Reads facts and runs one probe on a private worker thread,
 * reports on the main thread, then stops the worker. Single-flight per process.
 */
public final class TopwayCapabilityCheck {
    private static boolean running;

    private TopwayCapabilityCheck() {}

    /** Returns false when a check is already running. */
    public static synchronized boolean run(Context context, boolean observeSource, Consumer<List<Capability>> onMain) {
        if (running) return false;
        running = true;
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        HandlerThread thread = new HandlerThread("ts18-topway-check");
        thread.start();
        Handler worker = new Handler(thread.getLooper());
        worker.post(() -> {
            DeviceCapabilities.Facts facts = DeviceCapabilities.read(app);
            Consumer<TopwayStatusProbe.Result> report = result -> {
                List<Capability> capabilities = DeviceCapabilities.classify(facts, result);
                thread.quitSafely();
                main.post(() -> {
                    synchronized (TopwayCapabilityCheck.class) { running = false; }
                    onMain.accept(capabilities);
                });
            };
            boolean probeable = facts.xtInstalled && facts.xtServiceDeclared && facts.xtServiceEnabled
                    && facts.xtServiceExported && facts.xtServicePermission.isEmpty();
            if (!probeable) { report.accept(null); return; }
            boolean observe = observeSource && DeviceCapabilities.mayObserve(facts);
            facts.observeWithheld = observeSource && !observe;
            new TopwayStatusProbe(new FrameworkTopwayHost(app, worker)).start(observe, report);
        });
        return true;
    }
}
