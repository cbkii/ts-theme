package com.cbkii.ts18launcher;

/** Only an explicit admission rejection permits a different service start. */
final class MediaAdmissionPolicy {
    private MediaAdmissionPolicy() { }
    static boolean accepted(boolean completed, int exit, String output) {
        return completed && exit == 0 && output != null && !output.contains("Error:")
                && !output.contains("Exception") && !output.contains("Security exception:") && !output.contains("Permission Denial:");
    }
    static boolean definitelyRejected(boolean completed, int exit, String output) {
        if (output != null && output.startsWith("process-not-started ")) return true;
        if (!completed || exit == 124 || exit == 137 || output == null) return false;
        return output.contains("Permission Denial:") || output.contains("SecurityException") || output.contains("Security exception:")
                || output.contains("Error: Not found; no service started")
                || output.contains("Error: app is in background")
                || output.contains("su: not found") || output.contains("su: inaccessible");
    }
}
