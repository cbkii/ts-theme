package com.cbkii.ts18launcher;

/** Pure storage-event classification used by the launcher receiver and JVM tests. */
final class MediaStorageEventPolicy {
    static final String MEDIA_MOUNTED = "android.intent.action.MEDIA_MOUNTED";
    static final String MEDIA_UNMOUNTED = "android.intent.action.MEDIA_UNMOUNTED";
    static final String MEDIA_EJECT = "android.intent.action.MEDIA_EJECT";
    static final String MEDIA_REMOVED = "android.intent.action.MEDIA_REMOVED";

    private MediaStorageEventPolicy() {}

    static boolean isMounted(String action) {
        return MEDIA_MOUNTED.equals(action);
    }

    static boolean isRemoval(String action) {
        return MEDIA_UNMOUNTED.equals(action)
                || MEDIA_EJECT.equals(action)
                || MEDIA_REMOVED.equals(action);
    }

    static boolean relevant(String action) {
        return isMounted(action) || isRemoval(action);
    }
}
