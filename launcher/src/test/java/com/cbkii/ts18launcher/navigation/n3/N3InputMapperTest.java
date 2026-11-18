package com.cbkii.ts18launcher.navigation.n3;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class N3InputMapperTest {
    private static int[] id(int... v) { return v; }
    private static float[] f(float... v) { return v; }

    // 1000x600 view, 100 px right inset (sidebar), 900x600 content; 1280x720 display.
    private final N3InputMapper mapper = new N3InputMapper(1000, 600, 0, 0, 100, 0, 1280, 720);

    @Test public void fitsAndCentresInsideContent() {
        float[] b = mapper.fittedBounds();
        float scale = 900f / 1280f; // width-limited
        assertEquals(0f, b[0], 0.01f);
        assertEquals((600f - 720f * scale) / 2f, b[1], 0.01f);
        assertEquals(900f, b[2], 0.01f);
    }

    @Test public void mapsSinglePointerToDisplayPixels() {
        float[] b = mapper.fittedBounds();
        N3InputMapper.Mapped m = mapper.map(N3InputMapper.ACTION_DOWN, id(0), f(450f), f((b[1] + b[3]) / 2f));
        assertNotNull(m);
        assertEquals(640f, m.xs[0], 0.5f);
        assertEquals(360f, m.ys[0], 0.5f);
    }

    @Test public void gestureStartingInLetterboxOrInsetIsDroppedWhole() {
        assertNull(mapper.map(N3InputMapper.ACTION_DOWN, id(0), f(950f), f(300f))); // inset
        assertNull(mapper.map(N3InputMapper.ACTION_MOVE, id(0), f(450f), f(300f)));
        assertNull(mapper.map(N3InputMapper.ACTION_UP, id(0), f(450f), f(300f)));
        assertNull(mapper.map(N3InputMapper.ACTION_DOWN, id(0), f(450f), f(2f))); // letterbox
    }

    @Test public void ownedGestureIsClampedAndEndsOnUp() {
        assertNotNull(mapper.map(N3InputMapper.ACTION_DOWN, id(0), f(10f), f(300f)));
        N3InputMapper.Mapped move = mapper.map(N3InputMapper.ACTION_MOVE, id(0), f(-50f), f(5000f));
        assertEquals(0f, move.xs[0], 0f);
        assertEquals(719f, move.ys[0], 0f);
        assertNotNull(mapper.map(N3InputMapper.ACTION_UP, id(0), f(10f), f(300f)));
        assertNull(mapper.map(N3InputMapper.ACTION_MOVE, id(0), f(10f), f(300f)));
    }

    @Test public void multiPointerKeepsIdsAndMapsEachPointer() {
        mapper.map(N3InputMapper.ACTION_DOWN, id(0), f(100f), f(300f));
        N3InputMapper.Mapped two = mapper.map(N3InputMapper.ACTION_POINTER_DOWN, id(0, 3), f(100f, 800f), f(300f, 300f));
        assertArrayEquals(new int[] {0, 3}, two.ids);
        assertEquals(100f * 1280f / 900f, two.xs[0], 0.5f);
        assertEquals(800f * 1280f / 900f, two.xs[1], 0.5f);
    }

    @Test public void malformedEventsCancelAnOwnedGestureOnce() {
        mapper.map(N3InputMapper.ACTION_DOWN, id(0), f(100f), f(300f));
        int[] eleven = new int[11];
        N3InputMapper.Mapped cancel = mapper.map(N3InputMapper.ACTION_MOVE, eleven, new float[11], new float[11]);
        assertEquals(N3InputMapper.ACTION_CANCEL, cancel.action);
        assertNull(mapper.map(N3InputMapper.ACTION_MOVE, id(0), f(100f), f(300f)));
        mapper.map(N3InputMapper.ACTION_DOWN, id(0), f(100f), f(300f));
        assertEquals(N3InputMapper.ACTION_CANCEL,
                mapper.map(N3InputMapper.ACTION_MOVE, id(0), f(Float.NaN), f(1f)).action);
    }

    @Test public void replacementDownCancelsTheInterruptedGestureAndIsNotAccepted() {
        assertNotNull(mapper.map(N3InputMapper.ACTION_DOWN, id(0), f(100f), f(300f)));
        N3InputMapper.Mapped cancel = mapper.map(N3InputMapper.ACTION_DOWN, id(0), f(200f), f(300f));
        assertEquals(N3InputMapper.ACTION_CANCEL, cancel.action);
        assertNull(mapper.map(N3InputMapper.ACTION_MOVE, id(0), f(200f), f(300f)));
        assertNotNull(mapper.map(N3InputMapper.ACTION_DOWN, id(0), f(200f), f(300f)));
    }

    @Test public void invalidGeometryDropsEverything() {
        N3InputMapper bad = new N3InputMapper(100, 100, 60, 0, 60, 0, 1280, 720);
        assertFalse(bad.valid());
        assertNull(bad.map(N3InputMapper.ACTION_DOWN, id(0), f(10f), f(10f)));
    }
}
