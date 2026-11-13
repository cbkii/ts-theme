package com.cbkii.ts18launcher;

import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.view.Choreographer;

/** Explicitly requested, local 10-second sample. No background loop, persistence or upload. */
final class PerfSampler {
    // Written by the independent deadline thread, read by main-looper frame callbacks.
    private static volatile boolean running;
    private PerfSampler() { }

    static final class Frames {
        long last = -1L;
        int intervals, over32, over100;
        synchronized void add(long nanos) {
            if (last >= 0L && nanos > last) {
                long delta = nanos - last;
                intervals++;
                if (delta > 32000000L) over32++;
                if (delta > 100000000L) over100++;
            }
            last = nanos;
        }
        synchronized int[] snapshot() { return new int[] {intervals, over32, over100}; }
    }

    static synchronized boolean start() {
        if (running || Looper.myLooper() != Looper.getMainLooper()) return false;
        running = true;
        Frames frames = new Frames();
        long cpuStart = Process.getElapsedCpuTime();
        long startedAt = System.nanoTime();
        java.util.concurrent.atomic.AtomicBoolean collecting = new java.util.concurrent.atomic.AtomicBoolean(true);
        Choreographer choreographer = Choreographer.getInstance();
        Choreographer.FrameCallback callback = new Choreographer.FrameCallback() {
            @Override public void doFrame(long nanos) {
                if (!collecting.get()) return;
                frames.add(nanos);
                choreographer.postFrameCallback(this);
            }
        };
        choreographer.postFrameCallback(callback);
        // The 10-second bound must hold even when the main looper stalls: the deadline runs on
        // its own thread; only the Choreographer cleanup is marshalled back to main.
        Thread stopper = new Thread(() -> {
            try { Thread.sleep(10000L); } catch (InterruptedException stopped) { return; }
            collecting.set(false); // An older callback can never join a subsequent sample.
            long cpuMs = Process.getElapsedCpuTime() - cpuStart;
            long windowMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            int[] counts = frames.snapshot();
            running = false;
            new Handler(Looper.getMainLooper()).post(() -> choreographer.removeFrameCallback(callback));
            // Report the frozen measurement here, even if HOME's main looper remains stalled.
            Log.i("TS18Perf", "window_ms=" + windowMs + " intervals=" + counts[0]
                    + " over32=" + counts[1] + " over100=" + counts[2] + " cpu_ms=" + cpuMs);
            // PSS is a separate off-main sample and cannot delay the frame/CPU report.
            Log.i("TS18Perf", "pss_kb=" + Debug.getPss());
        }, "ts18-perf-stop");
        stopper.setDaemon(true);
        stopper.start();
        return true;
    }
}
