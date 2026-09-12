package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class Ts18GeometryTest {
    @Test
    public void physicalWindowKeepsVerifiedInsetsWithLeftRail() {
        Ts18Geometry.Layout g = Ts18Geometry.resolve(1280, 720, false);
        assertEquals(55, g.top);
        assertEquals(0, g.railX);
        assertEquals(96, g.railWidth());
        assertEquals(96, g.contentLeft);
        assertEquals(1225, g.contentRight);
        assertEquals(702, g.safeBottom);
        assertEquals(143, g.mapY());
        assertEquals(1129, g.mapWidth());
        assertEquals(559, g.mapHeight());
        assertEquals(88, g.stripHeight);
        assertEquals(Ts18Geometry.CONTROL_WIDTH, g.radioWidth);
        assertEquals(Ts18Geometry.CONTROL_WIDTH, g.musicWidth);
        assertEquals(144, g.dateWidth());
        assertTrue(g.metadataWidth() > 300);
    }

    @Test
    public void physicalWindowMirrorsRailWithoutChangingContentAreaWidth() {
        Ts18Geometry.Layout g = Ts18Geometry.resolve(1280, 720, true);
        assertEquals(55, g.top);
        assertEquals(1129, g.railX);
        assertEquals(0, g.contentLeft);
        assertEquals(1129, g.contentRight);
        assertEquals(1129, g.mapWidth());
        assertEquals(559, g.mapHeight());
        assertEquals(Ts18Geometry.CONTROL_WIDTH, g.radioWidth);
        assertEquals(Ts18Geometry.CONTROL_WIDTH, g.musicWidth);
        assertEquals(144, g.dateWidth());
        assertTrue(g.metadataWidth() > 300);
    }

    @Test
    public void decorFittedWindowKeepsSameUsableContentDimensions() {
        Ts18Geometry.Layout g = Ts18Geometry.resolve(1225, 665, true);
        assertEquals(0, g.top);
        assertEquals(1129, g.railX);
        assertEquals(647, g.safeBottom);
        assertEquals(88, g.mapY());
        assertEquals(1129, g.mapWidth());
        assertEquals(559, g.mapHeight());
    }

    @Test
    public void sidebarHiddenProfileUsesOnlyPhysicalDisplayEdge() {
        Ts18Geometry.Layout g = Ts18Geometry.resolveForSidebar(1280, 720, false, false, false);
        assertEquals(1280, g.contentRight);
        assertEquals(1184, g.mapWidth());
        assertTrue(g.metadataWidth() > 300);
    }

    @Test
    public void sidebarVisibleProfileRetainsProvenSafeRightBoundary() {
        Ts18Geometry.Layout g = Ts18Geometry.resolveForSidebar(1280, 720, false, false, true);
        assertEquals(1225, g.contentRight);
        assertEquals(1129, g.mapWidth());
    }

    @Test
    public void smallerWindowRemainsBounded() {
        Ts18Geometry.Layout g = Ts18Geometry.resolve(1000, 600, false);
        assertTrue(g.railWidth() > 0);
        assertTrue(g.dateWidth() > 0);
        assertTrue(g.mapWidth() > 0);
        assertTrue(g.mapHeight() > 0);
        assertTrue(g.contentRight <= 1000);
        assertTrue(g.safeBottom <= 600);
    }
}
