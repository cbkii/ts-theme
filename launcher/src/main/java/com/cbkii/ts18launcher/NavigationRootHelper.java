package com.cbkii.ts18launcher;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Installs and invokes the narrow, systemless Magisk navigation experiment helper. */
final class NavigationRootHelper {
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+");
    private static final Pattern COMPONENT = Pattern.compile(
            "[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+/(?:\\.[A-Za-z0-9_.$]+|[A-Za-z0-9_.$]+(?:\\.[A-Za-z0-9_.$]+)*)");
    private static final Pattern ACTION = Pattern.compile(
            "probe|status|present-native|verify-native|fullscreen|park-windowed");
    private static final String ASSET = "nav/nav-window.sh";
    private static final String ROOT_DIR = "/data/adb/ts18-launcher";
    private static final String ROOT_HELPER = ROOT_DIR + "/nav-window.sh";
    private static final long INSTALL_TIMEOUT_MS = 4000L;
    private static final long COMMAND_TIMEOUT_MS = 12000L;
    private static final long HOME_FOCUS_TIMEOUT_MS = 1800L;

    private final Context context;
    private boolean installedThisProcess;

    NavigationRootHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    synchronized NavigationHelperResult run(String action, String... args) {
        if (!ACTION.matcher(action == null ? "" : action).matches()) {
            return NavigationHelperResult.failure("BAD_ACTION", "");
        }
        for (String arg : args) {
            if (arg == null || arg.isEmpty() || !isSafeArgument(arg)) {
                return NavigationHelperResult.failure("BAD_ARGUMENT", arg == null ? "" : arg);
            }
        }
        NavigationHelperResult install = ensureInstalled();
        if (!install.success) return install;

        StringBuilder command = new StringBuilder(ROOT_HELPER).append(' ').append(action)
                .append(' ').append(AndroidUserId.current());
        for (String arg : args) command.append(' ').append(singleQuote(arg));
        ProcessResult result = executeRoot(command.toString(), COMMAND_TIMEOUT_MS);
        if (result.timedOut) return NavigationHelperResult.failure("TIMEOUT", result.output);
        NavigationHelperResult parsed = NavigationHelperResult.parse(result.output);
        if (result.exitCode != 0 && parsed.success) {
            return NavigationHelperResult.failure("HELPER_EXIT_" + result.exitCode, result.output);
        }
        return parsed;
    }

    /**
     * Parks an already-windowed navigation task behind HOME without re-delivering its Activity.
     * Routine drawer parking deliberately performs only status -> HOME focus -> status. Exact top
     * Activity component equality is not an ownership invariant: one package/task may legitimately
     * transition between startup and map Activities while this handoff is occurring.
     */
    synchronized NavigationHelperResult parkWindowedTask(String packageName, int taskId,
            String homePackage, int homeTaskId) {
        if (!PACKAGE.matcher(packageName == null ? "" : packageName).matches()
                || !PACKAGE.matcher(homePackage == null ? "" : homePackage).matches()
                || taskId <= 0 || homeTaskId <= 0) {
            return NavigationHelperResult.failure("BAD_ARGUMENT", "");
        }

        NavigationHelperResult before = run("status", packageName, Integer.toString(taskId));
        if (!before.success) return before;
        if (before.taskId != taskId || before.displayId != 0 || before.windowingMode != 5) {
            return NavigationHelperResult.failure("SUSPEND_STATE_MISMATCH", before.raw);
        }

        ProcessResult focus = executeRoot(homeFocusCommand(homeTaskId), HOME_FOCUS_TIMEOUT_MS);
        if (focus.timedOut) return NavigationHelperResult.failure("HOME_FOCUS_TIMEOUT", focus.output);
        if (focus.exitCode != 0) return NavigationHelperResult.failure("HOME_FOCUS_FAILED", focus.output);

        NavigationHelperResult after = run("status", packageName, Integer.toString(taskId));
        if (!after.success) return after;
        if (after.taskId != before.taskId || after.userId != before.userId
                || after.displayId != before.displayId || after.windowingMode != 5
                || !safeEquals(after.packageName, before.packageName)
                || !safeEquals(after.bounds, before.bounds)) {
            return NavigationHelperResult.failure("SUSPEND_STATE_CHANGED", after.raw);
        }
        return after;
    }

    static String homeFocusCommand(int homeTaskId) {
        return "PATH=/system/bin:/system/xbin:/vendor/bin; export PATH; "
                + "exec /system/bin/am task focus " + homeTaskId;
    }

    private static boolean safeEquals(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    private NavigationHelperResult ensureInstalled() {
        if (installedThisProcess) return NavigationHelperResult.parse("OK code=INSTALLED");
        File staged = new File(context.getNoBackupFilesDir(), "nav-window.sh");
        try {
            copyAsset(staged);
        } catch (IOException e) {
            return NavigationHelperResult.failure("ASSET_COPY_FAILED", e.getClass().getSimpleName());
        }
        String source = singleQuote(staged.getAbsolutePath());
        String rootDir = singleQuote(ROOT_DIR);
        String temp = singleQuote(ROOT_HELPER + ".tmp");
        String target = singleQuote(ROOT_HELPER);
        String command = "umask 077; mkdir -p " + rootDir + " && cp " + source + " " + temp
                + " && chmod 0700 " + temp + " && mv " + temp + " " + target;
        ProcessResult result = executeRoot(command, INSTALL_TIMEOUT_MS);
        if (result.timedOut) return NavigationHelperResult.failure("INSTALL_TIMEOUT", result.output);
        if (result.exitCode != 0) return NavigationHelperResult.failure("ROOT_UNAVAILABLE", result.output);
        installedThisProcess = true;
        return NavigationHelperResult.parse("OK code=INSTALLED");
    }

    private void copyAsset(File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("cannot create helper staging directory");
        }
        try (InputStream in = context.getAssets().open(ASSET);
             FileOutputStream out = new FileOutputStream(target, false)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read > 0) out.write(buffer, 0, read);
            }
            out.getFD().sync();
        }
        if (!target.setReadable(true, true) || !target.setExecutable(true, true)) {
            throw new IOException("cannot secure helper staging file");
        }
    }

    private static boolean isSafeArgument(String arg) {
        if (PACKAGE.matcher(arg).matches() || COMPONENT.matcher(arg).matches()) return true;
        if (arg.length() > 6) return false;
        for (int i = 0; i < arg.length(); i++) {
            if (!Character.isDigit(arg.charAt(i))) return false;
        }
        return true;
    }

    private static String singleQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static ProcessResult executeRoot(String command, long timeoutMs) {
        Process process = null;
        try {
            long shellSeconds = Math.max(1L, (timeoutMs + 999L) / 1000L);
            String wrapped = "exec /system/bin/toybox timeout -k 1 " + shellSeconds
                    + " /system/bin/sh -c " + singleQuote(command);
            process = new ProcessBuilder("su", "-c", wrapped).redirectErrorStream(true).start();
            boolean finished = process.waitFor(timeoutMs + 1500L, TimeUnit.MILLISECONDS);
            if (!finished) {
                terminate(process);
                return new ProcessResult(-1, "", true);
            }
            return new ProcessResult(process.exitValue(), readAll(process), false);
        } catch (IOException e) {
            return new ProcessResult(-1, e.getClass().getSimpleName(), false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) terminate(process);
            return new ProcessResult(-1, "Interrupted", true);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }

    private static void terminate(Process process) {
        if (process == null) return;
        process.destroy();
        try {
            if (!process.waitFor(300L, TimeUnit.MILLISECONDS)) process.destroyForcibly();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static String readAll(Process process) {
        if (process == null) return "";
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null && lines.size() < 48) lines.add(line);
        } catch (IOException ignored) {
            // Parser will classify an empty/partial response.
        }
        StringBuilder output = new StringBuilder();
        for (String line : lines) {
            if (output.length() > 0) output.append('\n');
            output.append(line);
        }
        return output.toString();
    }

    private static final class ProcessResult {
        final int exitCode;
        final String output;
        final boolean timedOut;

        ProcessResult(int exitCode, String output, boolean timedOut) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
            this.timedOut = timedOut;
        }
    }
}
