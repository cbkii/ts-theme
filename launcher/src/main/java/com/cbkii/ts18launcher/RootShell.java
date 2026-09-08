package com.cbkii.ts18launcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

final class RootShell {
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
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                process.destroy();
                if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                }
                return new Result(false, -1, "root command timed out");
            }
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null && output.length() < 8192) {
                    if (output.length() > 0) output.append('\n');
                    output.append(line);
                }
            }
            return new Result(true, process.exitValue(), output.toString());
        } catch (IOException e) {
            return new Result(true, -1, e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(false, -1, "interrupted");
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
