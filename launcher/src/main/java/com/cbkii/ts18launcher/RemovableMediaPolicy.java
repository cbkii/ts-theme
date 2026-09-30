package com.cbkii.ts18launcher;

/** Pure removable-media event policy shared by the Application runtime and JVM tests. */
final class RemovableMediaPolicy {
    enum Event { AVAILABLE, UNAVAILABLE, IGNORE }

    private static final String MEDIA_MOUNTED = "android.intent.action.MEDIA_MOUNTED";
    private static final String MEDIA_UNMOUNTED = "android.intent.action.MEDIA_UNMOUNTED";
    private static final String MEDIA_EJECT = "android.intent.action.MEDIA_EJECT";
    private static final String MEDIA_REMOVED = "android.intent.action.MEDIA_REMOVED";
    private static final String MEDIA_BAD_REMOVAL = "android.intent.action.MEDIA_BAD_REMOVAL";

    private RemovableMediaPolicy() {}

    static Event classify(String action) {
        if (MEDIA_MOUNTED.equals(action)) return Event.AVAILABLE;
        if (MEDIA_UNMOUNTED.equals(action)
                || MEDIA_EJECT.equals(action)
                || MEDIA_REMOVED.equals(action)
                || MEDIA_BAD_REMOVAL.equals(action)) return Event.UNAVAILABLE;
        return Event.IGNORE;
    }

    static boolean shouldWarm(Event event, boolean launcherResumed, boolean warmupEnabled) {
        return event == Event.AVAILABLE && launcherResumed && warmupEnabled;
    }
}
