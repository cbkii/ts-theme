package com.cbkii.ts18launcher;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Optional, default-off mitigation that pre-grants only declared navigation location permissions.
 * It never opens Settings, never grants unrelated permissions, and ordinary app permission UX
 * remains authoritative when root is unavailable or the mitigation is disabled.
 */
final class NavigationPermissionBootstrapper {
    interface Callback { void onResult(Result result); }

    static final class Result {
        final boolean success;
        final boolean attempted;
        final String packageName;
        final List<String> permissions;
        final String detail;

        Result(boolean success, boolean attempted, String packageName,
                List<String> permissions, String detail) {
            this.success = success;
            this.attempted = attempted;
            this.packageName = packageName == null ? "" : packageName;
            this.permissions = java.util.Collections.unmodifiableList(new ArrayList<>(permissions));
            this.detail = detail == null ? "" : detail;
        }
    }

    private static final String TAG = "TS18NavPerm";
    private static final long ROOT_TIMEOUT_MS = 2200L;
    private static final Object ROOT_GRANT_LOCK = new Object();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ts18-nav-permissions");
        thread.setDaemon(true);
        return thread;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private NavigationPermissionBootstrapper() {}

    static void ensureEarly(Context context) {
        ensureEarly(context, null);
    }

    static void ensureEarly(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        if (!UiPersonalizationPrefs.navigationRootPermissionGrant(app)) {
            deliver(callback, new Result(true, false, "",
                    java.util.Collections.emptyList(), "Disabled"));
            return;
        }
        String packageName = LauncherPrefs.packageFor(app, LauncherPrefs.KEY_NAV);
        if (packageName.isEmpty()) {
            deliver(callback, new Result(true, false, "",
                    java.util.Collections.emptyList(), "No assigned navigation app"));
            return;
        }
        EXECUTOR.execute(() -> deliver(callback, ensureNow(app, packageName)));
    }

    /** Runs on a worker thread. Used by the native task backend immediately before presentation. */
    static Result ensureNow(Context context, String packageName) {
        if (!UiPersonalizationPrefs.navigationRootPermissionGrant(context)) {
            return new Result(true, false, packageName,
                    java.util.Collections.emptyList(), "Disabled");
        }
        if (!safePackage(packageName)) {
            return new Result(false, false, packageName,
                    java.util.Collections.emptyList(), "Unsafe package name");
        }

        synchronized (ROOT_GRANT_LOCK) {
            PackageManager packages = context.getPackageManager();
            final PackageInfo info;
            try {
                info = packages.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
            } catch (PackageManager.NameNotFoundException e) {
                return new Result(false, false, packageName,
                        java.util.Collections.emptyList(), "Navigation app not installed");
            }

            Set<String> granted = new HashSet<>();
            for (String permission : new String[] {
                    NavigationPermissionPolicy.COARSE,
                    NavigationPermissionPolicy.FINE,
                    NavigationPermissionPolicy.BACKGROUND}) {
                if (packages.checkPermission(permission, packageName)
                        == PackageManager.PERMISSION_GRANTED) granted.add(permission);
            }
            List<String> missing = NavigationPermissionPolicy.missingDeclared(
                    info.requestedPermissions, granted);
            if (missing.isEmpty()) {
                return new Result(true, false, packageName, missing,
                        "Declared navigation location permissions already granted or not requested");
            }

            int userId = Process.myUserHandle().getIdentifier();
            StringBuilder command = new StringBuilder("failed=0");
            for (String permission : missing) {
                if (!NavigationPermissionPolicy.isAllowed(permission)) continue;
                command.append("; /system/bin/pm grant --user ")
                        .append(userId).append(' ').append(packageName).append(' ')
                        .append(permission).append(" || failed=1");
            }
            command.append("; exit $failed");

            RootShell.Result root = RootShell.runMillis(command.toString(), ROOT_TIMEOUT_MS);
            List<String> remaining = new ArrayList<>();
            for (String permission : missing) {
                if (packages.checkPermission(permission, packageName)
                        != PackageManager.PERMISSION_GRANTED) remaining.add(permission);
            }
            boolean success = remaining.isEmpty();
            String detail;
            if (success) {
                detail = "Granted declared navigation location permissions";
            } else if (!root.completed) {
                detail = "Root permission grant timed out or was unavailable";
            } else {
                detail = "Permission grant incomplete: " + String.join(", ", remaining);
            }
            if (success) Log.i(TAG, "root permission mitigation complete package=" + packageName
                    + " permissions=" + missing);
            else Log.w(TAG, "root permission mitigation incomplete package=" + packageName
                    + " detail=" + detail + " output=" + root.output);
            return new Result(success, true, packageName, missing, detail);
        }
    }

    private static void deliver(Callback callback, Result result) {
        if (callback == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) callback.onResult(result);
        else MAIN.post(() -> callback.onResult(result));
    }

    private static boolean safePackage(String packageName) {
        return packageName != null
                && packageName.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");
    }
}
