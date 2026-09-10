package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class Ts18GeometryTest {
    @Test
    public void physicalWindowUsesVerifiedSystemInsetsAndWiderTouchRail() {
        Ts18Geometry.Layout g = Ts18Geometry.resolve(1280, 720);
        assertEquals(55, g.top);
        assertEquals(96, g.left);
        assertEquals(1225, g.safeRight);
        assertEquals(702, g.safeBottom);
        assertEquals(127, g.mapY());
        assertEquals(1129, g.mapWidth());
        assertEquals(575, g.mapHeight());
        assertEquals(72, g.stripHeight);
        assertEquals(350, g.radioWidth);
        assertEquals(616, g.musicWidth);
    }

    @Test
    public void decorFittedWindowKeepsSameContentGeometry() {
        Ts18Geometry.Layout g = Ts18Geometry.resolve(1225, 665);
        assertEquals(0, g.top);
        assertEquals(96, g.left);
        assertEquals(1225, g.safeRight);
        assertEquals(647, g.safeBottom);
        assertEquals(72, g.mapY());
        assertEquals(1129, g.mapWidth());
        assertEquals(575, g.mapHeight());
    }

    @Test
    public void smallerWindowRemainsBounded() {
        Ts18Geometry.Layout g = Ts18Geometry.resolve(1000, 600);
        assertTrue(g.left > 0);
        assertTrue(g.dateWidth() > 0);
        assertTrue(g.mapWidth() > 0);
        assertTrue(g.mapHeight() > 0);
        assertTrue(g.safeRight <= 1000);
        assertTrue(g.safeBottom <= 600);
    }
}
