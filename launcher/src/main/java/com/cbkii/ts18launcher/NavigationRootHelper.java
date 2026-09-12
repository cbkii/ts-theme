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

/** Installs and invokes the narrow, systemless Magisk task-window helper. */
final class NavigationRootHelper {
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+");
    private static final Pattern ACTION = Pattern.compile("probe|status|window|verify|focus|fullscreen");
    private static final String ASSET = "nav/nav-window.sh";
    private static final String ROOT_DIR = "/data/adb/ts18-launcher";
    private static final String ROOT_HELPER = ROOT_DIR + "/nav-window.sh";
    private static final long INSTALL_TIMEOUT_MS = 4000L;
    private static final long COMMAND_TIMEOUT_MS = 7000L;

    private final Context context;
    private boolean installedThisProcess;

    NavigationRootHelper(Context context) { this.context = context.getApplicationContext(); }

    synchronized NavigationHelperResult run(String action, String... args) {
        if (!ACTION.matcher(action == null ? "" : action).matches())
            return NavigationHelperResult.failure("BAD_ACTION", "");
        for (String arg : args) {
            if (arg == null || arg.isEmpty()) return NavigationHelperResult.failure("BAD_ARGUMENT", "");
            if (!isSafeArgument(arg)) return NavigationHelperResult.failure("BAD_ARGUMENT", arg);
        }
        NavigationHelperResult install = ensureInstalled();
        if (!install.success) return install;

        StringBuilder command = new StringBuilder(ROOT_HELPER).append(' ').append(action);
        for (String arg : args) command.append(' ').append(arg);
        ProcessResult result = executeRoot(command.toString(), COMMAND_TIMEOUT_MS);
        if (result.timedOut) return NavigationHelperResult.failure("TIMEOUT", result.output);
        NavigationHelperResult parsed = NavigationHelperResult.parse(result.output);
        if (result.exitCode != 0 && parsed.success)
            return NavigationHelperResult.failure("HELPER_EXIT_" + result.exitCode, result.output);
        return parsed;
    }

    private NavigationHelperResult ensureInstalled() {
        if (installedThisProcess) return NavigationHelperResult.parse("OK code=INSTALLED");
        File staged = new File(context.getNoBackupFilesDir(), "nav-window.sh");
        try { copyAsset(staged); }
        catch (IOException e) { return NavigationHelperResult.failure("ASSET_COPY_FAILED", e.getClass().getSimpleName()); }

        String source = singleQuote(staged.getAbsolutePath());
        String rootDir = singleQuote(ROOT_DIR);
        String temp = singleQuote(ROOT_HELPER + ".tmp");
        String target = singleQuote(ROOT_HELPER);
        String command = "umask 077; mkdir -p " + rootDir
                + " && cp " + source + " " + temp
                + " && chmod 0700 " + temp
                + " && mv " + temp + " " + target;
        ProcessResult result = executeRoot(command, INSTALL_TIMEOUT_MS);
        if (result.timedOut) return NavigationHelperResult.failure("INSTALL_TIMEOUT", result.output);
        if (result.exitCode != 0) return NavigationHelperResult.failure("ROOT_UNAVAILABLE", result.output);
        installedThisProcess = true;
        return NavigationHelperResult.parse("OK code=INSTALLED");
    }

    private void copyAsset(File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory())
            throw new IOException("cannot create helper staging directory");
        try (InputStream in = context.getAssets().open(ASSET);
             FileOutputStream out = new FileOutputStream(target, false)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read > 0) out.write(buffer, 0, read);
            }
            out.getFD().sync();
        }
        if (!target.setReadable(true, true) || !target.setExecutable(true, true))
            throw new IOException("cannot secure helper staging file");
    }

    private static boolean isSafeArgument(String arg) {
        if (PACKAGE.matcher(arg).matches()) return true;
        if (arg.length() > 6) return false;
        for (int i = 0; i < arg.length(); i++) if (!Character.isDigit(arg.charAt(i))) return false;
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
                return new ProcessResult(-1, readAll(process), true);
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
            while ((line = reader.readLine()) != null) {
                if (lines.size() < 32) lines.add(line);
            }
        } catch (IOException ignored) { }
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
