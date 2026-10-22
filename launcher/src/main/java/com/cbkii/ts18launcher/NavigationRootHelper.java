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
            "probe|status|present-native|verify-native|resume-windowed|fullscreen|park-windowed|background-fullscreen|probe-task-mode|startup-source|startup-home|return-home");
    private static final String ASSET = "nav/nav-window.sh";
    private static final long INSTALL_TIMEOUT_MS = 4000L;
    private static final long COMMAND_TIMEOUT_MS = 12000L;

    private final Context context;
    private final String rootDir;
    private final String rootHelper;
    private boolean installedThisProcess;
    private long deadline;
    private long presentationEpoch;
    void setPresentationEpoch(long epoch) {
        presentationEpoch = epoch;
        RootTaskSession.get(context).epoch(epoch);
    }
    private volatile String coldPolicy = "manual";
    void setColdLaunchPolicy(String policy) {
        coldPolicy = "automatic".equals(policy) || "manual".equals(policy) ? policy : "deny";
    }
    void setDeadline(long elapsedDeadlineMs) { deadline = elapsedDeadlineMs; }
    private long remaining() {
        return deadline <= 0L ? COMMAND_TIMEOUT_MS
                : Math.max(0L, deadline - android.os.SystemClock.elapsedRealtime());
    }

    NavigationRootHelper(Context context) {
        this.context = context.getApplicationContext();
        rootDir = new File(this.context.getNoBackupFilesDir(), "navigation-root").getAbsolutePath();
        rootHelper = rootDir + "/nav-window.sh";
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
        if (Thread.currentThread().isInterrupted() || remaining() < 1500L)
            return NavigationHelperResult.failure("ACTION_TIMEOUT", "before helper admission");
        boolean mapFastPath = args.length > 0 && "app.organicmaps.incar".equals(args[0])
                && ("resume-windowed".equals(action) || "return-home".equals(action) || "background-fullscreen".equals(action));
        boolean musicFastPath = args.length > 0 && "com.tw.media".equals(args[0]) && "startup-home".equals(action);
        if (mapFastPath || musicFastPath) {
            java.util.ArrayList<String> fields = new java.util.ArrayList<>();
            fields.add(action); fields.add(Integer.toString(AndroidUserId.current()));
            java.util.Collections.addAll(fields, args);
            RootTaskSession session = RootTaskSession.get(context);
            long until = android.os.SystemClock.elapsedRealtime() + remaining();
            NavigationHelperResult fast = "startup-home".equals(action)
                    ? session.runCurrent(until, fields.toArray(new String[0]))
                    : session.run(presentationEpoch, until, fields.toArray(new String[0]));
            if (fast != null && !"BRIDGE_API_UNSUPPORTED".equals(fast.code)) return fast;
        }
        NavigationHelperResult install = ensureInstalled();
        if (!install.success) return install;

        long budget = Math.min(COMMAND_TIMEOUT_MS, remaining() - 500L);
        if (budget < 1000L) return NavigationHelperResult.failure("ACTION_TIMEOUT", "after helper installation");
        long shellDeadline = (android.os.SystemClock.elapsedRealtime() + budget) / 1000L;
        StringBuilder command = new StringBuilder("TS18_DEADLINE_S=").append(shellDeadline)
                .append(" TS18_STATE_DIR=").append(singleQuote(rootDir))
                .append(" TS18_STAGE_UNTIL=").append(deadline)
                .append(" TS18_MASK_TITLE=").append(singleQuote(StartupMaskFocus.activeTitle))
                .append(" TS18_COLD_POLICY=").append(coldPolicy)
                .append(" TS18_METHOD=").append(TestingProfiles.navigation(context))
                .append(" TS18_TRANSITION=").append(TestingProfiles.transition(context))
                .append(" /system/bin/sh ").append(singleQuote(rootHelper)).append(' ').append(action)
                .append(' ').append(AndroidUserId.current());
        for (String arg : args) command.append(' ').append(singleQuote(arg));
        long phaseStart = android.os.SystemClock.elapsedRealtime();
        ProcessResult result = executeRoot(command.toString(), budget);
        android.util.Log.i("TS18Nav", "helper action=" + action + " deadline=" + deadline
                + " method=" + TestingProfiles.summary(context) + " elapsedMs="
                + (android.os.SystemClock.elapsedRealtime() - phaseStart) + " exit=" + result.exitCode);
        NavigationHelperResult parsed = NavigationHelperResult.parse(result.output);
        if (result.timedOut || result.exitCode == 124 || result.exitCode == 137)
            return NavigationHelperResult.withCode(parsed, "PHASE_TIMEOUT", "helper deadline action=" + action);
        if (result.exitCode != 0 && parsed.success) {
            return NavigationHelperResult.failure("HELPER_EXIT_" + result.exitCode, result.output);
        }
        return parsed;
    }

    /**
     * Parks an already-windowed navigation task behind HOME without re-delivering its Activity.
     * The shell helper performs one guarded transaction: it refuses to steal foreground from an
     * unrelated task, focuses the known HOME task, then verifies the same navigation task/package/
     * user/display/mode/bounds. The top Activity component is observation only and may change.
     */
    synchronized NavigationHelperResult parkWindowedTask(String packageName, int taskId,
            String homePackage, int homeTaskId) {
        if (!PACKAGE.matcher(packageName == null ? "" : packageName).matches()
                || !PACKAGE.matcher(homePackage == null ? "" : homePackage).matches()
                || taskId <= 0 || homeTaskId <= 0) {
            return NavigationHelperResult.failure("BAD_ARGUMENT", "");
        }
        return run("park-windowed", packageName, Integer.toString(taskId),
                homePackage, Integer.toString(homeTaskId));
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
        String directory = singleQuote(rootDir);
        String temp = singleQuote(rootHelper + ".tmp");
        String target = singleQuote(rootHelper);
        String command = "umask 077; mkdir -p " + directory + " && cp " + source + " " + temp
                + " && chmod 0700 " + temp + " && mv " + temp + " " + target;
        ProcessResult result = executeRoot(command, Math.min(INSTALL_TIMEOUT_MS, Math.max(1L, remaining() - 500L)));
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
        RootShell.Result result = RootShell.runWithin(command, timeoutMs);
        return new ProcessResult(result.exitCode, result.output, !result.completed);
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
