package com.cbkii.ts18launcher.navigation.n3;

/**
 * Classification of what N3 can do on this unit, from observed capability only.
 *
 * Creating a display is not evidence of external pixels or input. DEVICE_FEASIBILITY_PASS is
 * never produced here: it needs observed map pixels plus working touch and Back on the device.
 */
public final class N3Feasibility {
    public enum Result {
        /** The build contains N3 and nothing has been probed on a device. */
        STATIC_POC_READY,
        /** The platform lacks secondary-display Activity support. */
        INFEASIBLE_ON_THIS_ROM,
        /** No navigation app is selected or it has no launchable Activity. */
        NO_TARGET,
        /** The app-owned display exists, but the platform refuses the external Activity on it. */
        BLOCKED_NO_AUTHORITY,
        /** The platform admits the Activity on the display; pixels and input are not yet proven. */
        LAUNCH_ADMITTED_UNVERIFIED,
        /** The probe itself failed; nothing is known. */
        PROBE_FAILED
    }

    /** Facts gathered by a probe; every field is an observation, never an assumption. */
    public static final class Observation {
        public final boolean probed;
        public final boolean secondaryDisplayActivities;
        public final boolean targetResolved;
        public final boolean displayCreated;
        /** Boolean.TRUE/FALSE when asked of the platform; null when the question could not be asked. */
        public final Boolean startAllowed;
        public final String failure;

        public Observation(boolean probed, boolean secondaryDisplayActivities, boolean targetResolved,
                           boolean displayCreated, Boolean startAllowed, String failure) {
            this.probed = probed;
            this.secondaryDisplayActivities = secondaryDisplayActivities;
            this.targetResolved = targetResolved;
            this.displayCreated = displayCreated;
            this.startAllowed = startAllowed;
            this.failure = failure == null ? "" : failure;
        }

        public static Observation notProbed() { return new Observation(false, false, false, false, null, ""); }
    }

    private N3Feasibility() {}

    public static Result classify(Observation o) {
        if (o == null || !o.probed) return Result.STATIC_POC_READY;
        if (!o.failure.isEmpty()) return Result.PROBE_FAILED;
        if (!o.secondaryDisplayActivities) return Result.INFEASIBLE_ON_THIS_ROM;
        if (!o.targetResolved) return Result.NO_TARGET;
        if (!o.displayCreated || o.startAllowed == null) return Result.PROBE_FAILED;
        return o.startAllowed ? Result.LAUNCH_ADMITTED_UNVERIFIED : Result.BLOCKED_NO_AUTHORITY;
    }

    public static String describe(Result result) {
        switch (result) {
            case INFEASIBLE_ON_THIS_ROM: return "Not possible on this system: no secondary-display Activity support";
            case NO_TARGET: return "Choose a navigation app first";
            case BLOCKED_NO_AUTHORITY: return "Blocked: Android refuses this app on a launcher-owned display";
            case LAUNCH_ADMITTED_UNVERIFIED: return "Admitted by Android · map pixels and touch not yet verified";
            case PROBE_FAILED: return "Test could not complete";
            default: return "Not tested on this unit";
        }
    }
}
