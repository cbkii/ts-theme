package com.cbkii.ts18launcher;

import android.content.Context;
import android.os.SystemClock;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Private inherited pipes, one root app_process and bounded typed requests. No socket or daemon. */
final class RootTaskSession {
    private static RootTaskSession instance;
    private static final java.util.concurrent.ExecutorService CONTROL =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "ts18-root-control"); t.setDaemon(true); return t;
            });
    private final String apk;
    private final Object writes = new Object();
    private Process process;
    private BufferedWriter input;
    private ArrayBlockingQueue<String> output;
    private long sequence;
    private volatile long epoch;
    private volatile long sentEpoch = -1L;

    private RootTaskSession(Context context) { apk = context.getApplicationInfo().sourceDir; }
    static synchronized RootTaskSession get(Context context) {
        String path = context.getApplicationInfo().sourceDir;
        if (instance == null || !instance.apk.equals(path)) {
            if (instance != null) instance.close();
            instance = new RootTaskSession(context.getApplicationContext());
        }
        return instance;
    }
    static void warm(Context context) {
        CONTROL.execute(() -> {
            RootTaskSession session = get(context);
            synchronized (session) { session.connect(SystemClock.elapsedRealtime() + 4000L); }
        });
    }
    void epoch(long value) {
        epoch = value;
        CONTROL.execute(() -> {
            try { synchronized (writes) { if (input != null && sentEpoch != epoch) sendEpoch(); } }
            catch (java.io.IOException unavailable) { /* The request detects broker death. */ }
        });
    }
    NavigationHelperResult runCurrent(long deadline, String... fields) { return run(epoch, deadline, fields); }
    private void sendEpoch() throws java.io.IOException {
        input.write("EPOCH\t" + epoch + "\n"); input.flush(); sentEpoch = epoch;
    }
    private boolean connect(long deadline) {
        if (process != null && process.isAlive()) return true;
        close();
        if (android.os.Build.VERSION.SDK_INT != 29 || deadline <= SystemClock.elapsedRealtime()) return false;
        try {
            process = new ProcessBuilder("su", "-c", "CLASSPATH='" + apk.replace("'", "'\\''")
                    + "' /system/bin/app_process /system/bin com.cbkii.ts18launcher.NavTaskBridge serve")
                    .redirectErrorStream(true).start();
            input = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));
            output = new ArrayBlockingQueue<>(8);
            final Process child = process;
            final ArrayBlockingQueue<String> lines = output;
            Thread reader = new Thread(() -> {
                try (BufferedReader stream = new BufferedReader(new InputStreamReader(child.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = stream.readLine()) != null) {
                        if (line.length() <= 8192 && line.startsWith("TS18_")) lines.offer(line);
                    }
                } catch (java.io.IOException ignored) { }
                finally { lines.offer("TS18_CLOSED"); }
            }, "ts18-root-session-output");
            reader.setDaemon(true); reader.start();
            String ready = output.poll(Math.max(1L, deadline - SystemClock.elapsedRealtime()), TimeUnit.MILLISECONDS);
            if (!"TS18_READY".equals(ready)) { close(); return false; }
            synchronized (writes) { sendEpoch(); }
            return true;
        } catch (java.io.IOException unavailable) { close(); return false; }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); close(); return false; }
    }

    /** Null means no request was dispatched. An admitted timeout is never replayed via shell. */
    synchronized NavigationHelperResult run(long requestEpoch, long deadline, String... fields) {
        for (String field : fields)
            if (field == null || !field.matches("[A-Za-z0-9_./,-]{1,240}"))
                return NavigationHelperResult.failure("BAD_ARGUMENT", "root session");
        if (!connect(deadline)) return null;
        long id = ++sequence;
        long started = SystemClock.elapsedRealtime();
        MediaEventTrace.record("root-broker", "request", "id=" + id + " action=" + fields[0] + " epoch=" + requestEpoch);
        boolean dispatched = false;
        try {
            synchronized (writes) {
                if (requestEpoch != epoch) return NavigationHelperResult.failure("STALE_PRESENTATION", "before IPC");
                input.write("REQUEST\t" + id + "\t" + deadline + "\t" + requestEpoch);
                for (String field : fields) { input.write('\t'); input.write(field); }
                // A failed flush can follow server admission. Treat it as uncertain.
                dispatched = true;
                input.write('\n'); input.flush();
            }
            String prefix = "TS18_RESULT " + id + " ";
            String result = output.poll(Math.max(1L, deadline - SystemClock.elapsedRealtime()), TimeUnit.MILLISECONDS);
            if (result != null && result.startsWith(prefix)) {
                NavigationHelperResult parsed = NavigationHelperResult.parse(result.substring(prefix.length()));
                MediaEventTrace.record("root-broker", "result", "id=" + id + " action=" + fields[0]
                        + " elapsedMs=" + (SystemClock.elapsedRealtime() - started) + " code=" + parsed.code);
                return parsed;
            }
        } catch (java.io.IOException unavailable) { }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        close();
        MediaEventTrace.record("root-broker", "unconfirmed", "id=" + id + " action=" + fields[0]);
        return dispatched ? NavigationHelperResult.failure("PHASE_TIMEOUT", "root IPC outcome unconfirmed") : null;
    }

    private void close() {
        synchronized (writes) {
            if (process != null) process.destroy();
            process = null; input = null; output = null; sentEpoch = -1L;
        }
    }
}
