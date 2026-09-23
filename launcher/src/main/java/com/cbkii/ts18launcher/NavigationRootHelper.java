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
            "probe|status|present-native|verify-native|fullscreen|suspend");
    private static final String ASSET = "nav/nav-window.sh";
    private static final String ROOT_DIR = "/data/adb/ts18-launcher";
    private static final String ROOT_HELPER = ROOT_DIR + "/nav-window.sh";
    private static final long INSTALL_TIMEOUT_MS = 4000L;
    private static final long COMMAND_TIMEOUT_MS = 12000L;

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

        StringBuilder command = new StringBuilder(ROOT_HELPER).append(' ').append(action);
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
     * Parks an already-windowed navigation task behind HOME without starting an Activity.
     *
     * A warm suspend is deliberately a task-focus transaction only. Reissuing `am start` for
     * the navigation Activity can deliver a new Intent to a SINGLE_TOP Activity and therefore
     * re-enter application startup/permission code even though the task already exists.
     */
    synchronized NavigationHelperResult parkWindowedTask(String packageName, int taskId,
            String homePackage, int homeTaskId) {
        if (!PACKAGE.matcher(packageName == null ? "" : packageName).matches()
                || !PACKAGE.matcher(homePackage == null ? "" : homePackage).matches()
                || taskId <= 0 || homeTaskId <= 0) {
            return NavigationHelperResult.failure("BAD_ARGUMENT", "");
        }

        NavigationHelperResult navigationBefore = run("status", packageName,
                Integer.toString(taskId));
        if (!isExactTask(navigationBefore, packageName, taskId)) return navigationBefore;
        if (navigationBefore.displayId != 0 || navigationBefore.windowingMode != 5) {
            return NavigationHelperResult.failure("SUSPEND_STATE_MISMATCH", navigationBefore.raw);
        }
        NavigationHelperResult homeBefore = run("status", homePackage,
                Integer.toString(homeTaskId));
        if (!isExactTask(homeBefore, homePackage, homeTaskId)) return homeBefore;

        String command = "PATH=/system/bin:/system/xbin:/vendor/bin:/product/bin; export PATH; "
                + "unset LD_PRELOAD LD_LIBRARY_PATH; am task focus " + homeTaskId;
        ProcessResult focus = executeRoot(command, COMMAND_TIMEOUT_MS);
        if (focus.timedOut) return NavigationHelperResult.failure("HOME_FOCUS_TIMEOUT", focus.output);
        if (focus.exitCode != 0) return NavigationHelperResult.failure("HOME_FOCUS_FAILED", focus.output);

        NavigationHelperResult navigationAfter = run("status", packageName,
                Integer.toString(taskId));
        if (!isExactTask(navigationAfter, packageName, taskId)) return navigationAfter;
        if (navigationAfter.displayId != 0 || navigationAfter.windowingMode != 5
                || !navigationBefore.bounds.equals(navigationAfter.bounds)) {
            return NavigationHelperResult.failure("SUSPEND_STATE_CHANGED", navigationAfter.raw);
        }
        return withCode(navigationAfter, "SUSPENDED");
    }

    private static boolean isExactTask(NavigationHelperResult result, String packageName, int taskId) {
        if (!result.success || result.taskId != taskId || !packageName.equals(result.packageName)) {
            return false;
        }
        return result.component == null || result.component.isEmpty()
                || "unknown".equals(result.component)
                || result.component.startsWith(packageName + "/");
    }

    private static NavigationHelperResult withCode(NavigationHelperResult result, String code) {
        StringBuilder line = new StringBuilder("OK code=").append(code)
                .append(" task=").append(result.taskId)
                .append(" stack=").append(result.stackId >= 0 ? result.stackId : "unknown")
                .append(" package=").append(result.packageName)
                .append(" component=").append(result.component == null || result.component.isEmpty()
                        ? "unknown" : result.component)
                .append(" display=").append(result.displayId >= 0 ? result.displayId : "unknown")
                .append(" windowingMode=").append(result.windowingMode >= 0
                        ? result.windowingMode : "unknown")
                .append(" bounds=").append(result.bounds == null || result.bounds.isEmpty()
                        ? "unknown" : result.bounds)
                .append(" supportsPip=").append(result.supportsPip >= 0
                        ? result.supportsPip : "unknown")
                .append(" launched=0 transaction=0 helpExit=not-run helpWindowingMode=0")
                .append(" helpDisplay=0 launchExit=not-run");
        return NavigationHelperResult.parse(line.toString());
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
            process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroy();
                if (!process.waitFor(250L, TimeUnit.MILLISECONDS)) process.destroyForcibly();
                return new ProcessResult(-1, "", true);
            }
            return new ProcessResult(process.exitValue(), readAll(process), false);
        } catch (IOException e) {
            return new ProcessResult(-1, e.getClass().getSimpleName(), false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroy();
            return new ProcessResult(-1, "Interrupted", true);
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