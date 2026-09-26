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
        if (timeoutSeconds <= 0) return new Result(false, -1, "invalid root command");
        return runInternal(command, timeoutSeconds, TimeUnit.SECONDS.toMillis(timeoutSeconds + 3));
    }

    static Result runMillis(String command, long timeoutMillis) {
        if (timeoutMillis <= 0) return new Result(false, -1, "invalid root command");
        long shellSeconds = Math.max(1L, (timeoutMillis + 999L) / 1000L);
        return runInternal(command, shellSeconds, timeoutMillis + 1500L);
    }

    private static Result runInternal(String command, long shellTimeoutSeconds, long waitMillis) {
        if (command == null || command.isEmpty()) {
            return traced(command, new Result(false, -1, "invalid root command"));
        }

        String commandClass = commandClass(command);
        MediaEventTrace.record("root", "request", commandClass);
        Process process = null;
        StringBuilder output = new StringBuilder();
        Thread drainer = null;
        try {
            String wrapped = "exec /system/bin/toybox timeout -k 1 "
                    + shellTimeoutSeconds
                    + " /system/bin/sh -c "
                    + shellQuote(command);
            process = new ProcessBuilder("su", "-c", wrapped)
                    .redirectErrorStream(true)
                    .start();

            final Process drainingProcess = process;
            drainer = new Thread(() -> drain(drainingProcess, output), "ts18-root-output");
            drainer.setDaemon(true);
            drainer.start();

            boolean completed = process.waitFor(waitMillis, TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroy();
                if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                }
                joinQuietly(drainer, 1000);
                return traced(command,
                        new Result(false, -1, appendStatus(output, "root command timed out")));
            }

            joinQuietly(drainer, 1000);
            return traced(command, new Result(true, process.exitValue(), output.toString()));
        } catch (IOException e) {
            return traced(command,
                    new Result(false, -1, e.getClass().getSimpleName() + ": " + e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return traced(command, new Result(false, -1, "interrupted"));
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static Result traced(String command, Result result) {
        String outcome = result.success() ? "accepted"
                : result.completed ? "rejected" : "unavailable-or-timeout";
        MediaEventTrace.record("root", outcome,
                commandClass(command) + " exit=" + result.exitCode);
        return result;
    }

    private static String commandClass(String command) {
        if (command == null || command.isEmpty()) return "invalid";
        if (command.contains("com.tw.media")) return "media:com.tw.media";
        if (command.contains("com.navimods.radio")) return "media:com.navimods.radio";
        if (command.contains("set-home-activity")) return "home-selection";
        if (command.contains("windowingMode") || command.contains("am stack")) return "navigation-window";
        return "other";
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
