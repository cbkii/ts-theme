package com.cbkii.ts18launcher;

/** Small, JVM-testable authority for whether the native task may cover the HOME map panel. */
final class NavigationWindowUiState {
    private boolean homeVisible;
    private boolean launcherOverlayOpen;

    boolean onHomeVisible() {
        boolean changed = !homeVisible || launcherOverlayOpen;
        homeVisible = true;
        launcherOverlayOpen = false;
        return changed;
    }

    void onHomeStopped() {
        homeVisible = false;
    }

    boolean onLauncherOverlayOpened() {
        if (launcherOverlayOpen) return false;
        launcherOverlayOpen = true;
        return true;
    }

    boolean canPresentNavigation() {
        return homeVisible && !launcherOverlayOpen;
    }
}
