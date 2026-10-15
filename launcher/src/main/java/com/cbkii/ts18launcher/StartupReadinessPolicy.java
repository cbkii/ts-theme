package com.cbkii.ts18launcher;

import java.util.EnumMap;
import java.util.Map;

/**
 * Pure per-source startup ledger shared by {@link StartupReadinessCoordinator} and JVM tests.
 *
 * It deduplicates overlapping triggers, enforces one in-flight admission per source, never
 * disturbs a healthy source and never repeats an accepted/uncertain Activity launch within one
 * startup generation. Service/session readiness, task/window readiness and physical audibility
 * stay distinct: this ledger only records what was observed, never infers audibility.
 */
final class StartupReadinessPolicy {
    enum Source { MUSIC, RADIO, NAVIGATION }
    enum State { UNAVAILABLE, STARTING, READY, ACTIVE, FAILED, BLOCKED, UNCERTAIN }
    enum Trigger { BOOT, SCREEN_ON, ACC_ON, HOME_RESUME, STORAGE_MOUNT, SESSION_LOST, RESTART }
    enum Kind { FULL_BOOT, SAME_BOOT_WAKE, HOME_RETURN }
    enum Mode { BACKGROUND_ONLY, AUTOMATIC_FALLBACK, AGGRESSIVE }
    enum Method { NONE, SESSION, BROWSER, SERVICE, ACTIVITY_BOOTSTRAP, TASK_ADOPTION }

    static final long TRIGGER_DEBOUNCE_MS = 1500L;
    static final long FAILED_RETRY_SPACING_MS = 15000L;
    static final int MAX_ATTEMPTS_PER_BOOT = 3;

    static final class Record {
        State state = State.UNAVAILABLE;
        Method method = Method.NONE;
        int generation;
        int attempts;
        long startedMs = -1L;
        long finishedMs = -1L;
        String firstFailingBoundary = "";
        boolean activityLaunchAccepted;
        Trigger trigger;

        long elapsedMs(long now) {
            if (startedMs < 0L) return 0L;
            return (finishedMs >= 0L ? finishedMs : now) - startedMs;
        }
    }

    private final Map<Source, Record> records = new EnumMap<>(Source.class);
    private final Map<Trigger, Long> lastTrigger = new EnumMap<>(Trigger.class);

    StartupReadinessPolicy() {
        for (Source source : Source.values()) records.put(source, new Record());
    }

    Record record(Source source) { return records.get(source); }

    /** The boot counter is the only reliable full-boot discriminator; screen-on is not ACC-on. */
    static Kind classify(Trigger trigger, long bootCount, long lastSeenBootCount) {
        if (trigger == Trigger.BOOT || bootCount != lastSeenBootCount) return Kind.FULL_BOOT;
        if (trigger == Trigger.SCREEN_ON || trigger == Trigger.ACC_ON) return Kind.SAME_BOOT_WAKE;
        return Kind.HOME_RETURN;
    }

    /** Returns false when an equivalent trigger already arrived within the debounce window. */
    boolean acceptTrigger(Trigger trigger, long now) {
        Long last = lastTrigger.get(trigger);
        if (last != null && now - last < TRIGGER_DEBOUNCE_MS) return false;
        lastTrigger.put(trigger, now);
        return true;
    }

    /** A new full boot resets every source for a fresh startup session. */
    void resetForBoot() {
        for (Source source : Source.values()) records.put(source, new Record());
        lastTrigger.clear();
    }

    /** Whether this trigger may begin a new admission for the source. */
    boolean admit(Source source, Trigger trigger, long now) {
        Record r = records.get(source);
        switch (r.state) {
            case STARTING:
                return false;
            case READY:
            case ACTIVE:
                return trigger == Trigger.SESSION_LOST
                        || (source == Source.MUSIC && trigger == Trigger.STORAGE_MOUNT);
            case FAILED:
            case UNAVAILABLE:
            case BLOCKED:
                if (r.attempts >= MAX_ATTEMPTS_PER_BOOT
                        && !(source == Source.MUSIC && trigger == Trigger.STORAGE_MOUNT)) return false;
                boolean lateUsb = source == Source.MUSIC && trigger == Trigger.STORAGE_MOUNT;
                if (r.state == State.FAILED && r.finishedMs >= 0L && !lateUsb
                        && now - r.finishedMs < FAILED_RETRY_SPACING_MS) return false;
                return true;
            case UNCERTAIN:
                // Observe only; the caller must not relaunch while an accepted launch is unresolved.
                return trigger == Trigger.SESSION_LOST || trigger == Trigger.STORAGE_MOUNT;
            default:
                return false;
        }
    }

    int begin(Source source, Trigger trigger, Method method, long now) {
        Record r = records.get(source);
        r.state = State.STARTING;
        r.method = method;
        r.trigger = trigger;
        r.generation++;
        r.attempts++;
        r.startedMs = now;
        r.finishedMs = -1L;
        r.firstFailingBoundary = "";
        if (trigger == Trigger.SESSION_LOST) r.activityLaunchAccepted = false;
        return r.generation;
    }

    void changeMethod(Source source, Method method) { records.get(source).method = method; }

    /** Stale generation results are ignored, so a superseded transaction cannot flip state. */
    boolean ready(Source source, int generation, long now) {
        return finish(source, generation, State.READY, "", now);
    }

    boolean fail(Source source, int generation, String boundary, long now) {
        return finish(source, generation, State.FAILED, boundary, now);
    }

    boolean block(Source source, int generation, String boundary, long now) {
        return finish(source, generation, State.BLOCKED, boundary, now);
    }

    boolean uncertain(Source source, int generation, String boundary, long now) {
        return finish(source, generation, State.UNCERTAIN, boundary, now);
    }

    void active(Source source, long now) {
        Record r = records.get(source);
        if (r.state == State.READY || r.state == State.STARTING || r.state == State.UNCERTAIN) {
            r.state = State.ACTIVE;
            if (r.finishedMs < 0L) r.finishedMs = now;
            r.firstFailingBoundary = "";
        }
    }

    /** Observed readiness arriving after an uncertain/failed launch (task adopted, late session). */
    boolean observedReady(Source source, long now) {
        Record r = records.get(source);
        if (r.state == State.READY || r.state == State.ACTIVE) return false;
        r.state = State.READY;
        r.finishedMs = now;
        return true;
    }

    void sessionLost(Source source) {
        Record r = records.get(source);
        if (r.state == State.READY || r.state == State.ACTIVE) {
            r.state = State.UNAVAILABLE;
            r.attempts = 0;
        }
    }

    boolean markActivityLaunch(Source source) {
        Record r = records.get(source);
        if (r.activityLaunchAccepted) return false;
        r.activityLaunchAccepted = true;
        return true;
    }

    private boolean finish(Source source, int generation, State state, String boundary, long now) {
        Record r = records.get(source);
        if (r.generation != generation || r.state != State.STARTING) return false;
        r.state = state;
        r.finishedMs = now;
        if (r.firstFailingBoundary.isEmpty()) r.firstFailingBoundary = boundary == null ? "" : boundary;
        return true;
    }

    /** Foreground Activity bootstrap is allowed only by the Automatic/Aggressive choices. */
    static boolean foregroundAllowed(Mode mode) { return mode != Mode.BACKGROUND_ONLY; }

    static Mode parseMode(String value) {
        if ("background".equals(value)) return Mode.BACKGROUND_ONLY;
        if ("aggressive".equals(value)) return Mode.AGGRESSIVE;
        return Mode.AUTOMATIC_FALLBACK;
    }

    String summary(long now) {
        StringBuilder out = new StringBuilder();
        for (Source source : Source.values()) {
            Record r = records.get(source);
            if (out.length() > 0) out.append(' ');
            out.append(source.name().toLowerCase(java.util.Locale.ROOT)).append('=').append(r.state)
                    .append('/').append(r.method).append("/gen").append(r.generation)
                    .append('/').append(r.elapsedMs(now)).append("ms");
            if (!r.firstFailingBoundary.isEmpty()) out.append("/fail:").append(r.firstFailingBoundary);
        }
        return out.toString();
    }
}
