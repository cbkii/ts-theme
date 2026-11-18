package com.cbkii.ts18launcher.navigation.n3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.view.MotionEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class N3MotionConverterTest {
    // 512x300 view presenting a 1024x600 display: exact 2x scale, no letterbox.
    private final N3MotionConverter converter = new N3MotionConverter(
            new N3InputMapper(512, 300, 0, 0, 0, 0, 1024, 600));

    private static MotionEvent two(int action, long down, long time, float x0, float y0, float x1, float y1) {
        MotionEvent.PointerProperties[] p = {new MotionEvent.PointerProperties(), new MotionEvent.PointerProperties()};
        p[0].id = 0; p[1].id = 5;
        p[0].toolType = p[1].toolType = MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerCoords[] c = {new MotionEvent.PointerCoords(), new MotionEvent.PointerCoords()};
        c[0].x = x0; c[0].y = y0; c[1].x = x1; c[1].y = y1;
        return MotionEvent.obtain(down, time, action, 2, p, c, 0, 0, 1f, 1f, 0, 0, 0, 0);
    }

    @Test public void preservesTimesActionIndexAndPointerIdsWhileScaling() {
        MotionEvent down = MotionEvent.obtain(100L, 100L, MotionEvent.ACTION_DOWN, 100f, 50f, 0);
        MotionEvent mapped = converter.convert(down);
        assertNotNull(mapped);
        assertEquals(200f, mapped.getX(), 0.01f);
        assertEquals(100f, mapped.getY(), 0.01f);
        assertEquals(100L, mapped.getDownTime());

        int pointerDown = MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
        MotionEvent second = converter.convert(two(pointerDown, 100L, 140L, 100f, 50f, 300f, 200f));
        assertEquals(MotionEvent.ACTION_POINTER_DOWN, second.getActionMasked());
        assertEquals(1, second.getActionIndex());
        assertEquals(5, second.getPointerId(1));
        assertEquals(600f, second.getX(1), 0.01f);
        assertEquals(400f, second.getY(1), 0.01f);
        assertEquals(140L, second.getEventTime());
    }

    @Test public void historyIsPreservedAndTransformed() {
        converter.convert(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 10f, 10f, 0));
        MotionEvent move = MotionEvent.obtain(0L, 16L, MotionEvent.ACTION_MOVE, 20f, 20f, 0);
        move.addBatch(32L, 30f, 30f, 1f, 1f, 0);
        MotionEvent mapped = converter.convert(move);
        assertEquals(1, mapped.getHistorySize());
        assertEquals(40f, mapped.getHistoricalX(0, 0), 0.01f);
        assertEquals(60f, mapped.getX(), 0.01f);
        assertEquals(32L, mapped.getEventTime());
    }

    @Test public void dragOffTheImageIsClampedInsideTheDisplay() {
        converter.convert(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 10f, 10f, 0));
        MotionEvent mapped = converter.convert(MotionEvent.obtain(0L, 10L, MotionEvent.ACTION_MOVE, 900f, -40f, 0));
        assertEquals(1023f, mapped.getX(), 0.01f);
        assertEquals(0f, mapped.getY(), 0.01f);
    }

    @Test public void gestureStartingOffTheImageIsDroppedAndMalformedInputCancels() {
        N3MotionConverter letterboxed = new N3MotionConverter(new N3InputMapper(600, 300, 0, 0, 0, 0, 1024, 600));
        assertNull(letterboxed.convert(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 5f, 150f, 0)));
        assertNull(letterboxed.convert(MotionEvent.obtain(0L, 5L, MotionEvent.ACTION_MOVE, 300f, 150f, 0)));

        converter.convert(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 10f, 10f, 0));
        MotionEvent hover = MotionEvent.obtain(0L, 5L, MotionEvent.ACTION_HOVER_MOVE, 10f, 10f, 0);
        assertEquals(MotionEvent.ACTION_CANCEL, converter.convert(hover).getActionMasked());
        assertNull(converter.convert(MotionEvent.obtain(0L, 6L, MotionEvent.ACTION_MOVE, 10f, 10f, 0)));
    }

    @Test public void secondDownCancelsTheOwnedGestureAndIsNotForwarded() {
        converter.convert(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 10f, 10f, 0));
        MotionEvent second = converter.convert(MotionEvent.obtain(5L, 5L, MotionEvent.ACTION_DOWN, 20f, 20f, 0));
        assertEquals(MotionEvent.ACTION_CANCEL, second.getActionMasked());
        assertNull(converter.convert(MotionEvent.obtain(5L, 10L, MotionEvent.ACTION_MOVE, 30f, 30f, 0)));
        assertNotNull(converter.convert(MotionEvent.obtain(20L, 20L, MotionEvent.ACTION_DOWN, 20f, 20f, 0)));
    }

    @Test public void nonFiniteCoordinatesCancelTheOwnedGesture() {
        converter.convert(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 10f, 10f, 0));
        MotionEvent nan = converter.convert(MotionEvent.obtain(0L, 10L, MotionEvent.ACTION_MOVE, Float.NaN, 10f, 0));
        assertEquals(MotionEvent.ACTION_CANCEL, nan.getActionMasked());
        assertNull(converter.convert(MotionEvent.obtain(0L, 15L, MotionEvent.ACTION_MOVE, 10f, 10f, 0)));
        converter.convert(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 10f, 10f, 0));
        MotionEvent infinite = converter.convert(
                MotionEvent.obtain(0L, 10L, MotionEvent.ACTION_MOVE, Float.POSITIVE_INFINITY, 10f, 0));
        assertEquals(MotionEvent.ACTION_CANCEL, infinite.getActionMasked());
    }
}
