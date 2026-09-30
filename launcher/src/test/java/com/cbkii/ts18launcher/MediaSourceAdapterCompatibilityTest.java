package com.cbkii.ts18launcher;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class MediaSourceAdapterCompatibilityTest {
    @Test public void unadvertisedSkipCompatibilityIsExactPackageScoped() {
        assertTrue(MediaSourceAdapter.allowsUnadvertisedSkip("com.tw.media"));
        assertTrue(MediaSourceAdapter.allowsUnadvertisedSkip("com.navimods.radio"));
        assertFalse(MediaSourceAdapter.allowsUnadvertisedSkip("com.tw.radio"));
        assertFalse(MediaSourceAdapter.allowsUnadvertisedSkip("example.player"));
    }
}
