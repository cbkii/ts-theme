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
        void add(long nanos) {
            if (last >= 0L && nanos > last) {
                long delta = nanos - last;
                intervals++;
                if (delta > 32000000L) over32++;
                if (delta > 100000000L) over100++;
            }
            last = nanos;
        }
    }

    static synchronized boolean start() {
        if (running || Looper.myLooper() != Looper.getMainLooper()) return false;
        running = true;
        Frames frames = new Frames();
        long cpuStart = Process.getElapsedCpuTime();
        Choreographer choreographer = Choreographer.getInstance();
        Choreographer.FrameCallback callback = new Choreographer.FrameCallback() {
            @Override public void doFrame(long nanos) {
                if (!running) return;
                frames.add(nanos);
                choreographer.postFrameCallback(this);
            }
        };
        choreographer.postFrameCallback(callback);
        // The 10-second bound must hold even when the main looper stalls: the deadline runs on
        // its own thread; only the Choreographer cleanup is marshalled back to main.
        Thread stopper = new Thread(() -> {
            try { Thread.sleep(10000L); } catch (InterruptedException stopped) { return; }
            long cpuMs = Process.getElapsedCpuTime() - cpuStart;
            running = false; // Frame callbacks observe this and stop counting immediately.
            new Handler(Looper.getMainLooper()).post(() -> {
                choreographer.removeFrameCallback(callback);
                // PSS can be slow; it is never collected on the HOME thread.
                Thread report = new Thread(() -> Log.i("TS18Perf", "window_ms=10000 intervals=" + frames.intervals
                        + " over32=" + frames.over32 + " over100=" + frames.over100
                        + " cpu_ms=" + cpuMs + " pss_kb=" + Debug.getPss()), "ts18-perf-report");
                report.setDaemon(true); report.start();
            });
        }, "ts18-perf-stop");
        stopper.setDaemon(true);
        stopper.start();
        return true;
    }
}
