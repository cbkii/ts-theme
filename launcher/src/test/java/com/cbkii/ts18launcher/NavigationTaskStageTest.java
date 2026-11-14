package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Integration review F6: Organic Maps' own screens are user activity, never bootstrap. */
public class NavigationTaskStageTest {
    private static final String P = "app.organicmaps.incar/app.organicmaps.";

    @Test public void onlyTheMapActivityIsTheMap() {
        assertEquals("MAP", NavigationTaskStage.of(P + "MwmActivity"));
    }

    @Test public void splashAndResourceSetupAreBootstrap() {
        assertEquals("BOOTSTRAP_PENDING", NavigationTaskStage.of(P + "SplashActivity"));
        assertEquals("BOOTSTRAP_PENDING", NavigationTaskStage.of(P + "DownloadResourcesActivity"));
        assertEquals("BOOTSTRAP_PENDING", NavigationTaskStage.of(P + "DownloadResourcesLegacyActivity"));
        assertEquals("BOOTSTRAP_PENDING", NavigationTaskStage.of("unknown"));
    }

    @Test public void ownScreensAreUserActivity() {
        for (String screen : new String[] {"settings.SettingsActivity", "downloader.DownloaderActivity",
                "bookmarks.BookmarkCategoriesActivity", "editor.EditorActivity", "settings.DrivingOptionsActivity",
                "help.HelpActivity", "MapPlaceholderActivity"})
            assertEquals(screen, "USER_ACTIVITY", NavigationTaskStage.of(P + screen));
    }
}
