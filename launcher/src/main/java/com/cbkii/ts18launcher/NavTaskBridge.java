package com.cbkii.ts18launcher;

import android.content.ComponentName;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.os.Process;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/** API-29-only app_process entry point. No Activity launch or vendor-state writes. */
public final class NavTaskBridge {
    private NavTaskBridge() { }

    public static void main(String[] args) {
        try {
            if (Build.VERSION.SDK_INT != 29 || Process.myUid() != 0)
                throw new IllegalStateException("API29_ROOT_REQUIRED");
            if (args.length < 4) throw new IllegalArgumentException("BAD_ARGUMENT");
            String action = args[0], pkg = args[2];
            int user = Integer.parseInt(args[1]), hint = Integer.parseInt(args[3]);
            if (!pkg.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+") || user < 0 || hint < 0)
                throw new IllegalArgumentException("BAD_IDENTITY");
            Class<?> atm = Class.forName("android.app.ActivityTaskManager");
            Object service = atm.getDeclaredMethod("getService").invoke(null);
            Class<?> api = Class.forName("android.app.IActivityTaskManager");
            Method stacks = api.getMethod("getAllStackInfos");
            Task observed = resolve(stacks.invoke(service), pkg, user, hint);
            if (observed == null) { System.out.println("NONE"); return; }
            if (!"status".equals(action)) {
                if (!"mode".equals(action) && !"probe-mode".equals(action))
                    throw new IllegalArgumentException("BAD_ACTION");
                if (hint <= 0 || args.length != 6)
                    throw new IllegalArgumentException("TASK_AUTHORITY_REQUIRED");
                int mode = Integer.parseInt(args[4]);
                boolean toTop = "1".equals(args[5]);
                if ((mode != 1 && mode != 5) || !("0".equals(args[5]) || toTop))
                    throw new IllegalArgumentException("BAD_MODE");
                // Q changes the owning stack's mode. Never change another task with it.
                if (observed.taskCount != 1 || observed.display != 0 || observed.activityType != 1)
                    throw new IllegalStateException("TASK_STACK_NOT_EXCLUSIVE");
                if (observed.foreignTop) throw new IllegalStateException("LEGITIMATE_FOREIGN_ACTIVITY");
                Method setMode = api.getMethod("setTaskWindowingMode", int.class, int.class, boolean.class);
                if ("probe-mode".equals(action)) { mode = observed.mode; toTop = false; }
                // No-op capability proof occurs in this exact root Binder caller first.
                // Permission failure ends here; no transaction-number or policy fallback exists.
                if (!"probe-mode".equals(action))
                    setMode.invoke(service, observed.id, observed.mode, false);
                setMode.invoke(service, observed.id, mode, toTop);
                observed = resolve(stacks.invoke(service), pkg, user, hint);
                if (observed == null || observed.mode != mode)
                    throw new IllegalStateException("MODE_NOT_VERIFIED");
            }
            System.out.println(observed.record());
        } catch (Exception e) {
            Throwable cause = e instanceof InvocationTargetException
                    ? ((InvocationTargetException) e).getTargetException() : e;
            String message = String.valueOf(cause.getMessage()).replaceAll("[\\r\\n\\s]+", "_");
            System.out.println("UNKNOWN " + cause.getClass().getSimpleName() + " "
                    + message.substring(0, Math.min(180, message.length())));
            System.exit(1);
        }
    }

    private static Task resolve(Object raw, String pkg, int user, int hint) throws Exception {
        if (!(raw instanceof List)) throw new IllegalStateException("STACKS_UNREADABLE");
        Task found = null;
        for (Object stack : (List<?>) raw) {
            Class<?> type = stack.getClass();
            int[] ids = (int[]) type.getField("taskIds").get(stack);
            String[] names = (String[]) type.getField("taskNames").get(stack);
            int[] users = (int[]) type.getField("taskUserIds").get(stack);
            Rect[] bounds = (Rect[]) type.getField("taskBounds").get(stack);
            Configuration config = (Configuration) type.getField("configuration").get(stack);
            Object window = Configuration.class.getField("windowConfiguration").get(config);
            int mode = (Integer) window.getClass().getMethod("getWindowingMode").invoke(window);
            int activityType = (Integer) window.getClass().getMethod("getActivityType").invoke(window);
            if (ids == null || names == null || users == null || ids.length != names.length
                    || ids.length != users.length) throw new IllegalStateException("STACKS_INCOMPLETE");
            for (int i = 0; i < ids.length; i++) {
                // Stack taskNames describe the base/original component, not immutable top Activity.
                ComponentName component = ComponentName.unflattenFromString(names[i] == null ? "" : names[i]);
                if (users[i] != user || (hint > 0 && ids[i] != hint)) continue;
                if (component == null) throw new IllegalStateException("TASK_NAME_UNREADABLE");
                if (!pkg.equals(component.getPackageName())) continue;
                if (found != null) throw new IllegalStateException("TASK_AMBIGUOUS");
                found = new Task();
                found.id = ids[i]; found.stack = type.getField("stackId").getInt(stack);
                found.display = type.getField("displayId").getInt(stack);
                found.mode = mode; found.activityType = activityType; found.taskCount = ids.length;
                found.component = component.flattenToString();
                ComponentName top = (ComponentName) type.getField("topActivity").get(stack);
                found.foreignTop = top != null && !pkg.equals(top.getPackageName());
                found.bounds = bounds != null && i < bounds.length ? bounds[i] : null;
            }
        }
        return found;
    }

    private static final class Task {
        int id, stack, display, mode, activityType, taskCount;
        String component;
        boolean foreignTop;
        Rect bounds;
        String record() {
            String rectangle = bounds == null ? "unknown"
                    : bounds.left + "," + bounds.top + "," + bounds.right + "," + bounds.bottom;
            return "FOUND " + id + " " + stack + " " + display + " " + mode + " "
                    + rectangle + " " + component + " unknown";
        }
    }
}
