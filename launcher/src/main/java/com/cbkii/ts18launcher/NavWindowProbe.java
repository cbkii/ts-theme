package com.cbkii.ts18launcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Bounded root-side command I/O. No shell/timeout applet, task mutation or unbounded read. */
final class NavWindowProbe {
    static final class Failure extends IOException {
        final String reason;
        Failure(String reason, String detail) { super(detail); this.reason = reason; }
    }

    static String read(ProcessBuilder command, long budgetMs) throws IOException, InterruptedException {
        if (budgetMs < 50L) throw new Failure("PROBE_DEADLINE", "before spawn");
        long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budgetMs);
        // Match the existing root shell helper: app_process loader variables must
        // not leak into an ordinary system executable on OEM Android builds.
        command.environment().remove("LD_PRELOAD");
        command.environment().remove("LD_LIBRARY_PATH");
        final Process process;
        try { process = command.redirectErrorStream(false).start(); }
        catch (IOException unavailable) { throw new Failure("PROBE_SPAWN", unavailable.getClass().getSimpleName()); }
        Drain out = new Drain(process.getInputStream(), 524288);
        Drain err = new Drain(process.getErrorStream(), 2048);
        Thread stdout = start(out), stderr = start(err);
        try {
            if (!process.waitFor(remaining(until), TimeUnit.MILLISECONDS)) {
                if (out.capped || err.capped) throw new Failure("PROBE_CAPPED", "output limit");
                throw new Failure("PROBE_TIMEOUT", "elapsed deadline");
            }
            stdout.join(Math.max(1L, remaining(until)));
            stderr.join(Math.max(1L, remaining(until)));
            if (out.capped || err.capped) throw new Failure("PROBE_CAPPED", "output limit");
            if (process.exitValue() != 0)
                throw new Failure("PROBE_EXIT", "exit=" + process.exitValue() + " stderr=" + err.diagnostic());
            if (stdout.isAlive() || stderr.isAlive() || out.failed || err.failed)
                throw new Failure("PROBE_DRAIN", "incomplete output");
            return out.text();
        } finally {
            process.destroyForcibly();
        }
    }

    private static long remaining(long until) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(until - System.nanoTime()));
    }
    private static Thread start(Drain drain) {
        Thread thread = new Thread(drain, "ts18-window-probe-drain");
        thread.setDaemon(true); thread.start(); return thread;
    }
    private static final class Drain implements Runnable {
        final InputStream stream;
        final byte[] bytes;
        int used;
        volatile boolean capped, failed;
        Drain(InputStream stream, int limit) { this.stream = stream; bytes = new byte[limit]; }
        @Override public void run() {
            try (InputStream input = stream) {
                byte[] buffer = new byte[4096];
                int n;
                while ((n = input.read(buffer)) != -1) {
                    synchronized (this) {
                        int keep = Math.min(n, bytes.length - used);
                        System.arraycopy(buffer, 0, bytes, used, keep); used += keep;
                        if (keep < n) capped = true;
                    }
                    if (capped) return;
                }
            } catch (IOException unavailable) { failed = true; }
        }
        synchronized String text() { return new String(bytes, 0, used, StandardCharsets.UTF_8); }
        String diagnostic() {
            // Only a short error surface, never window/task output or arbitrary paths.
            String value = text().replaceAll("/[^\\s]+", "<path>")
                    .replaceAll("[\\r\\n\\t]+", " ").replaceAll("[^ -~]", "?");
            return value.substring(0, Math.min(200, value.length()));
        }
    }
}
