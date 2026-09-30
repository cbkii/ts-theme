package com.cbkii.ts18launcher;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NavigationWindowUiStateTest {
    @Test public void pendingDrawerSurvivesTransientHomeStopWithoutReclaimingMap() {
        NavigationWindowUiState state = new NavigationWindowUiState();
        state.onHomeVisible();
        state.onLauncherOverlayOpened();
        state.onHomeStopped();
        state.onHomeVisibleWithOverlay();
        assertFalse(state.canPresentNavigation());
        state.onHomeVisible();
        assertTrue(state.canPresentNavigation());
    }

    @Test public void homeReturnClearsDrawerOverlaySuppression() {
        NavigationWindowUiState state = new NavigationWindowUiState();

        assertTrue(state.onHomeVisible());
        assertTrue(state.canPresentNavigation());
        assertTrue(state.onLauncherOverlayOpened());
        assertFalse(state.canPresentNavigation());

        assertTrue(state.onHomeVisible());
        assertTrue(state.canPresentNavigation());
    }

    @Test public void duplicateDrawerOpenIsIdempotentAndHomeStopBlocksPresentation() {
        NavigationWindowUiState state = new NavigationWindowUiState();

        state.onHomeVisible();
        assertTrue(state.onLauncherOverlayOpened());
        assertFalse(state.onLauncherOverlayOpened());
        state.onHomeVisible();
        state.onHomeStopped();

        assertFalse(state.canPresentNavigation());
    }
}
