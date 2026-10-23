package com.cbkii.ts18launcher;

import android.annotation.SuppressLint;
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

    // This method runs only in root app_process, never in the launcher app UID.
    // API-29 hidden-service access is intentional; runtime refusal stays UNKNOWN.
    @SuppressLint({"BlockedPrivateApi", "PrivateApi"})
    public static void main(String[] args) {
        try {
            if (Build.VERSION.SDK_INT != 29 || Process.myUid() != 0)
                throw new IllegalStateException("API29_ROOT_REQUIRED");
            if (args.length == 1 && "serve".equals(args[0])) { serve(); return; }
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
                if (!"mode".equals(action) && !"probe-mode".equals(action)
                        && !"clear-fullscreen-bounds".equals(action))
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
                if ("unknown".equals(observed.component))
                    throw new IllegalStateException("TOP_ACTIVITY_UNOBSERVED");
                if ("clear-fullscreen-bounds".equals(action)) {
                    if (observed.mode != 1 || mode != 1 || toTop)
                        throw new IllegalStateException("FULLSCREEN_AUTHORITY_REQUIRED");
                    // API29 resizeStack(null) removes compact override bounds. Never use a
                    // display-sized non-null rectangle or touch a shared/foreign stack.
                    Method resize = api.getMethod("resizeStack", int.class, Rect.class,
                            boolean.class, boolean.class, boolean.class, int.class);
                    resize.invoke(service, observed.stack, null, false, false, false, -1);
                } else {
                    Method setMode = api.getMethod("setTaskWindowingMode", int.class, int.class, boolean.class);
                    if ("probe-mode".equals(action)) { mode = observed.mode; toTop = false; }
                    if (!"probe-mode".equals(action))
                        setMode.invoke(service, observed.id, observed.mode, false);
                    setMode.invoke(service, observed.id, mode, toTop);
                }
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

    private static final java.util.concurrent.atomic.AtomicLong EPOCH = new java.util.concurrent.atomic.AtomicLong();
    private static final class Request {
        final long id, deadline, epoch;
        final String[] fields;
        final String maskTitle;
        String windowComponent;
        long windowAt;
        Request(String[] line) {
            id = Long.parseLong(line[1]); deadline = Long.parseLong(line[2]); epoch = Long.parseLong(line[3]);
            String tail = line[line.length - 1];
            maskTitle = StartupMaskFocus.validTitle(tail) ? tail : "";
            fields = java.util.Arrays.copyOfRange(line, 4, line.length - (maskTitle.isEmpty() ? 0 : 1));
            long now = android.os.SystemClock.elapsedRealtime();
            if (id <= 0 || epoch < 0 || deadline <= now || deadline - now > 60000L)
                throw new IllegalArgumentException("BAD_DEADLINE");
        }
        Request() { id = deadline = epoch = -1L; fields = new String[0]; maskTitle = ""; }
        void current() {
            if (android.os.SystemClock.elapsedRealtime() >= deadline) throw new IllegalStateException("PHASE_TIMEOUT");
            if (epoch != EPOCH.get()) throw new IllegalStateException("STALE_PRESENTATION");
        }
    }

    /** Private stdin/stdout inherited from the authorised su caller. One worker, bounded queue. */
    private static void serve() throws Exception {
        final java.util.concurrent.ArrayBlockingQueue<Request> requests = new java.util.concurrent.ArrayBlockingQueue<>(2);
        final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
        Thread reader = new Thread(() -> {
            try (java.io.BufferedReader input = new java.io.BufferedReader(new java.io.InputStreamReader(System.in, java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = input.readLine()) != null) {
                    if (line.length() > 2048) break;
                    String[] fields = line.split("\t");
                    if (fields.length == 2 && "EPOCH".equals(fields[0])) EPOCH.set(Long.parseLong(fields[1]));
                    else if (fields.length >= 6 && fields.length <= 16 && "REQUEST".equals(fields[0])) {
                        Request request = new Request(fields);
                        if (!requests.offer(request)) System.out.println("TS18_RESULT " + request.id + " FAIL code=ROOT_BUSY");
                    } else break;
                }
            } catch (java.io.IOException | RuntimeException unavailable) { /* Idle timeout closes root. */ }
            finally { closed.set(true); requests.offer(new Request()); }
        }, "ts18-root-input");
        reader.setDaemon(true); reader.start();
        System.out.println("TS18_READY"); System.out.flush();
        Request request;
        while ((request = requests.poll(60L, java.util.concurrent.TimeUnit.SECONDS)) != null) {
            if (request.id < 0) return;
            String result;
            try { result = transition(request); }
            catch (Exception error) {
                Throwable cause = error instanceof InvocationTargetException ? ((InvocationTargetException) error).getTargetException() : error;
                String code = error instanceof NoSuchMethodException ? "BRIDGE_API_UNSUPPORTED" : String.valueOf(cause.getMessage());
                if (!code.matches("[A-Z_]{1,80}")) code = "ROOT_TRANSACTION_UNCERTAIN";
                result = "FAIL code=" + code;
            }
            System.out.println("TS18_RESULT " + request.id + " " + result); System.out.flush();
            if (closed.get() && requests.isEmpty()) return;
        }
    }

    @SuppressLint({"BlockedPrivateApi", "PrivateApi"})
    private static String transition(Request request) throws Exception {
        request.current();
        String[] a = request.fields;
        if (a.length == 2 && "observe".equals(a[0])) {
            int expectedUser = Integer.parseInt(a[1]);
            Object service = Class.forName("android.app.ActivityTaskManager").getDeclaredMethod("getService").invoke(null);
            Object focused = Class.forName("android.app.IActivityTaskManager").getMethod("getFocusedStackInfo").invoke(service);
            if (focused == null) throw new IllegalStateException("STARTUP_FOCUS_UNKNOWN");
            int[] ids = (int[]) focused.getClass().getField("taskIds").get(focused);
            if (ids == null || ids.length != 1 || ids[0] <= 0) throw new IllegalStateException("STARTUP_FOCUS_UNKNOWN");
            foreground(request, focused, ids[0], ids[0], expectedUser, false);
            ComponentName component = (ComponentName) focused.getClass().getField("topActivity").get(focused);
            return "OK code=FOREGROUND user=" + expectedUser + " task=" + ids[0] + " component=" + component.flattenToString();
        }
        if (a.length < 6) throw new IllegalArgumentException("BAD_ARGUMENT");
        String action = a[0], pkg = a[2];
        int user = Integer.parseInt(a[1]);
        int taskId, homeId;
        Rect target = null;
        boolean sourceReturn = "startup-home".equals(action) && a.length == 7;
        if ("resume-windowed".equals(action) && a.length == 10) {
            taskId = Integer.parseInt(a[7]); homeId = Integer.parseInt(a[9]);
            if (!"com.cbkii.ts18launcher".equals(a[8])) throw new IllegalArgumentException("BAD_HOME_PACKAGE");
            target = new Rect(Integer.parseInt(a[3]), Integer.parseInt(a[4]), Integer.parseInt(a[5]), Integer.parseInt(a[6]));
            if (target.left < 0 || target.top < 0 || target.right <= target.left || target.bottom <= target.top
                    || target.right > 16384 || target.bottom > 16384) throw new IllegalArgumentException("BAD_BOUNDS");
        } else if (sourceReturn) {
            taskId = Integer.parseInt(a[4]); homeId = Integer.parseInt(a[6]);
            if (!"com.tw.media".equals(pkg) || !"com.tw.media/org.oxycblt.auxio.MainActivity".equals(a[3])
                    || !"com.cbkii.ts18launcher".equals(a[5])) throw new IllegalArgumentException("BAD_IDENTITY");
        } else if (("return-home".equals(action) && a.length == 6)
                || ("background-fullscreen".equals(action) && a.length == 7)) {
            taskId = Integer.parseInt(a[3]); homeId = Integer.parseInt(a[5]);
            if (!"com.cbkii.ts18launcher".equals(a[4])) throw new IllegalArgumentException("BAD_HOME_PACKAGE");
        } else throw new IllegalArgumentException("BAD_ACTION");
        if ((!sourceReturn && !"app.organicmaps.incar".equals(pkg)) || user < 0 || taskId <= 0 || homeId <= 0 || homeId == taskId)
            throw new IllegalArgumentException("BAD_IDENTITY");
        Object service = Class.forName("android.app.ActivityTaskManager").getDeclaredMethod("getService").invoke(null);
        Class<?> api = Class.forName("android.app.IActivityTaskManager");
        Method stacks = api.getMethod("getAllStackInfos");
        Method focused = api.getMethod("getFocusedStackInfo");
        Method focus = api.getMethod("setFocusedTask", int.class);
        Method mode = api.getMethod("setTaskWindowingMode", int.class, int.class, boolean.class);
        Method resize = api.getMethod("resizeTask", int.class, Rect.class, int.class);
        Method clear = api.getMethod("resizeStack", int.class, Rect.class, boolean.class, boolean.class, boolean.class, int.class);
        Task task = resolve(stacks.invoke(service), pkg, user, taskId);
        if (task == null) return "FAIL code=TASK_NOT_FOUND";
        Task home = resolve(stacks.invoke(service), "com.cbkii.ts18launcher", user, homeId);
        if (home == null || home.display != 0 || home.mode != 1 || home.taskCount != 1
                || !homeComponent(home.component)) throw new IllegalStateException("HOME_STATE_CHANGED");
        if (sourceReturn) {
            if (task.display != 0 || task.taskCount != 1 || task.activityType != 1 || task.mode != 1 || task.foreignTop
                    || !a[3].equals(task.component)) throw new IllegalStateException("TASK_AUTHORITY_CHANGED");
        } else requireMap(task);
        boolean departure = "background-fullscreen".equals(action);
        foreground(request, focused.invoke(service), taskId, homeId, user, departure);
        if ("return-home".equals(action) || sourceReturn) {
            request.current(); focus.invoke(service, homeId);
            return "OK code=" + (sourceReturn ? "STARTUP_HOME_RESTORED" : "HOME_RETURNED");
        }
        if (departure) {
            request.current();
            if (task.mode != 1) mode.invoke(service, taskId, 1, false);
            task = resolve(stacks.invoke(service), pkg, user, taskId);
            requireMap(task);
            if (task.mode != 1) throw new IllegalStateException("WINDOWING_MODE_MISMATCH");
            if (task.bounds != null && !task.bounds.isEmpty()) {
                foreground(request, focused.invoke(service), taskId, homeId, user, true);
                clear.invoke(service, task.stack, null, false, false, false, -1);
            }
        } else {
            request.current();
            boolean wasFullscreen = task.mode == 1;
            if (task.mode != 5) mode.invoke(service, taskId, 5, false);
            task = resolve(stacks.invoke(service), pkg, user, taskId);
            requireMap(task);
            if (!target.equals(task.bounds)) {
                foreground(request, focused.invoke(service), taskId, homeId, user, false);
                resize.invoke(service, taskId, target, 0);
            }
            if (wasFullscreen) {
                request.windowComponent = null;
                foreground(request, focused.invoke(service), taskId, homeId, user, false);
                focus.invoke(service, homeId);
            }
            request.windowComponent = null;
            foreground(request, focused.invoke(service), taskId, homeId, user, false);
            focus.invoke(service, taskId);
        }
        task = resolve(stacks.invoke(service), pkg, user, taskId);
        requireMap(task);
        if (departure ? task.mode != 1 || task.bounds != null && !task.bounds.isEmpty()
                : task.mode != 5 || !target.equals(task.bounds)) throw new IllegalStateException("GEOMETRY_NOT_VERIFIED");
        String bounds = departure ? "0,0,0,0" : target.left + "," + target.top + "," + target.right + "," + target.bottom;
        return "OK code=" + (departure ? "BACKGROUND_FULLSCREEN" : "RESUMED_NATIVE")
                + " user=" + user + " package=" + pkg + " task=" + taskId + " stack=" + task.stack
                + " display=0 windowingMode=" + task.mode + " bounds=" + bounds
                + " launched=0 method=N2 phase=peer-surface visible=unknown drawn=unknown";
    }

    private static void requireMap(Task task) {
        if (task == null) throw new IllegalStateException("TASK_NOT_FOUND");
        if (task.taskCount != 1 || task.display != 0 || task.activityType != 1 || task.foreignTop
                || !"app.organicmaps.incar/app.organicmaps.MwmActivity".equals(task.component)
                || task.mode != 1 && task.mode != 5) throw new IllegalStateException("TASK_AUTHORITY_CHANGED");
    }
    private static boolean homeComponent(String component) {
        return "com.cbkii.ts18launcher/com.cbkii.ts18launcher.HomeAlias".equals(component)
                || "com.cbkii.ts18launcher/com.cbkii.ts18launcher.LauncherActivity".equals(component);
    }

    private static void foreground(Request request, Object focused, int task, int home, int user, boolean departure) throws Exception {
        request.current();
        if (focused == null || focused.getClass().getField("displayId").getInt(focused) != 0)
            throw new IllegalStateException("FOREGROUND_CHANGED");
        int[] ids = (int[]) focused.getClass().getField("taskIds").get(focused);
        int[] users = (int[]) focused.getClass().getField("taskUserIds").get(focused);
        ComponentName top = (ComponentName) focused.getClass().getField("topActivity").get(focused);
        if (ids == null || users == null || ids.length != 1 || users.length != 1 || users[0] != user || top == null
                || (departure ? ids[0] == task || ids[0] == home : ids[0] != task && ids[0] != home))
            throw new IllegalStateException("FOREGROUND_CHANGED");
        String activity = top.flattenToString();
        if (activity.equals(request.windowComponent) && android.os.SystemClock.elapsedRealtime() - request.windowAt < 150L) {
            request.current(); return;
        }
        // Display focus and window ownership are distinct OEM dump surfaces.
        // Correlate the exact focused token before/after the bounded owner read.
        long focusAt = android.os.SystemClock.elapsedRealtime();
        String displays = windowDump("displays", request);
        String focusedWindow = StartupMaskFocus.focusedWindow(displays);
        if (focusedWindow.isEmpty()) throw focusUnknown("FOCUS_MISSING");
        String owners = !request.maskTitle.isEmpty() && focusedWindow.endsWith(" " + request.maskTitle + "}")
                ? windowDump("windows", request) : "";
        String latest = windowDump("displays", request);
        if (!focusedWindow.equals(StartupMaskFocus.focusedWindow(latest)))
            throw focusUnknown("WINDOW_TOKEN_MISMATCH");
        if (android.os.SystemClock.elapsedRealtime() - focusAt > 2000L)
            throw focusUnknown("FOCUS_STALE");
        request.current();
        if (StartupMaskFocus.ownsFocusedWindow(displays, owners, request.maskTitle, user)) {
            request.windowComponent = activity;
            request.windowAt = android.os.SystemClock.elapsedRealtime();
            return;
        }
        if (!owners.isEmpty()) throw focusUnknown("MASK_OWNER_MISMATCH");
        StringBuilder text = new StringBuilder(displays);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("mCurrentFocus=Window\\{[^\\n]*? u(\\d+) ([A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+)").matcher(text);
        if (!matcher.find() || Integer.parseInt(matcher.group(1)) != user)
            throw new IllegalStateException("STARTUP_FOCUS_UNKNOWN");
        ComponentName window = ComponentName.unflattenFromString(matcher.group(2));
        String component = window == null ? "" : window.flattenToString();
        if (!(component.equals(activity) || homeComponent(component) && homeComponent(activity)) || matcher.find())
            throw new IllegalStateException("FOREGROUND_CHANGED");
        request.windowComponent = activity;
        request.windowAt = android.os.SystemClock.elapsedRealtime();
        request.current();
    }

    private static IllegalStateException focusUnknown(String reason) {
        android.util.Log.i("TS18Nav", "focus unavailable reason=" + reason);
        return new IllegalStateException("STARTUP_FOCUS_UNKNOWN");
    }

    private static String windowDump(String scope, Request request) throws Exception {
        request.current();
        java.lang.Process probe = new ProcessBuilder("/system/bin/toybox", "timeout", "-k", "1", "2",
                "/system/bin/dumpsys", "window", scope).redirectErrorStream(true).start();
        StringBuilder text = new StringBuilder();
        try {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                    probe.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (text.length() + line.length() > 524288) throw focusUnknown("PROBE_CAPPED");
                    if (!"displays".equals(scope) || line.contains("mCurrentFocus=")) text.append(line).append('\n');
                }
            }
            if (!probe.waitFor(100L, java.util.concurrent.TimeUnit.MILLISECONDS) || probe.exitValue() != 0)
                throw focusUnknown("PROBE_TIMEOUT");
            request.current();
            return text.toString();
        } finally { probe.destroy(); }
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
                // The stack top is the task top only for an exclusive stack. Preserve base
                // ownership separately; never mistake the launch alias for bootstrap readiness.
                found.component = ids.length == 1 && top != null ? top.flattenToString() : "unknown";
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
