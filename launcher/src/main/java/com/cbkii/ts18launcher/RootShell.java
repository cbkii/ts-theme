package com.cbkii.ts18launcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

final class RootShell {
    private static final int MAX_OUTPUT_CHARS = 8192;

    static final class Result {
        final boolean completed;
        final int exitCode;
        final String output;

        Result(boolean completed, int exitCode, String output) {
            this.completed = completed;
            this.exitCode = exitCode;
            this.output = output;
        }

        boolean success() {
            return completed && exitCode == 0;
        }
    }

    private RootShell() {}

    static Result run(String command, long timeoutSeconds) {
        if (command == null || command.isEmpty() || timeoutSeconds <= 0) {
            return new Result(false, -1, "invalid root command");
        }

        Process process = null;
        StringBuilder output = new StringBuilder();
        Thread drainer = null;
        try {
            // Keep the destructive/long-running boundary inside the root shell as well as
            // around the Java process. Android 10 ships toybox; if this exact unit lacks
            // its timeout applet the command fails closed and Settings opens the public
            // HOME selection path instead.
            String wrapped = "exec /system/bin/toybox timeout -k 1 "
                    + timeoutSeconds
                    + " /system/bin/sh -c "
                    + shellQuote(command);
            process = new ProcessBuilder("su", "-c", wrapped)
                    .redirectErrorStream(true)
                    .start();

            final Process drainingProcess = process;
            drainer = new Thread(() -> drain(drainingProcess, output), "ts18-root-output");
            drainer.setDaemon(true);
            drainer.start();

            boolean completed = process.waitFor(timeoutSeconds + 3, TimeUnit.SECONDS);
            if (!completed) {
                process.destroy();
                if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                }
                joinQuietly(drainer, 1000);
                return new Result(false, -1, appendStatus(output, "root command timed out"));
            }

            joinQuietly(drainer, 1000);
            return new Result(true, process.exitValue(), output.toString());
        } catch (IOException e) {
            return new Result(false, -1, e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(false, -1, "interrupted");
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static void drain(Process process, StringBuilder output) {
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (output) {
                    if (output.length() >= MAX_OUTPUT_CHARS) continue;
                    if (output.length() > 0) output.append('\n');
                    int remaining = MAX_OUTPUT_CHARS - output.length();
                    output.append(line, 0, Math.min(line.length(), remaining));
                }
            }
        } catch (IOException ignored) {
            // Process teardown can close the pipe. The exit result remains authoritative.
        }
    }

    private static void joinQuietly(Thread thread, long millis) throws InterruptedException {
        if (thread != null) thread.join(millis);
    }

    private static String appendStatus(StringBuilder output, String status) {
        synchronized (output) {
            if (output.length() == 0) return status;
            return output + "\n" + status;
        }
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
