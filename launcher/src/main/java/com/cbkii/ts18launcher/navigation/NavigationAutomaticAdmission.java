package com.cbkii.ts18launcher.navigation;

/** A single owner for automatic navigation dispatch; task journals remain the OS admission proof. */
public final class NavigationAutomaticAdmission {
    private static Object owner;
    private NavigationAutomaticAdmission() { }

    public static synchronized Object tryAcquire() {
        if (owner != null) return null;
        owner = new Object();
        return owner;
    }

    public static synchronized void release(Object claim) {
        if (claim != null && owner == claim) owner = null;
    }

    public static synchronized boolean isActive() {
        return owner != null;
    }
}
