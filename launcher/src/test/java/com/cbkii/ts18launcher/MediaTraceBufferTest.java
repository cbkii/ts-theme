package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class MediaTraceBufferTest {
    @Test public void boundedRingDropsOldestEntries() {
        MediaTraceBuffer buffer = new MediaTraceBuffer(2);
        buffer.add(10L, "one", "a");
        buffer.add(20L, "two", "b");
        buffer.add(30L, "three", "c");
        List<MediaTraceBuffer.Entry> entries = buffer.snapshot();
        assertEquals(2, entries.size());
        assertEquals("two", entries.get(0).category);
        assertEquals("three", entries.get(1).category);
    }

    @Test public void dumpUsesRelativeMonotonicDurations() {
        MediaTraceBuffer buffer = new MediaTraceBuffer(4);
        buffer.add(1000L, "start", "home");
        buffer.add(1250L, "ack", "playing");
        String dump = buffer.dump(1500L);
        assertTrue(dump.contains("+0ms age=500ms start · home"));
        assertTrue(dump.contains("+250ms age=250ms ack · playing"));
    }

    @Test public void fieldsAreSanitisedAndBounded() {
        MediaTraceBuffer buffer = new MediaTraceBuffer(2);
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 400; i++) longText.append('x');
        buffer.add(1L, "cat\nnext", longText.toString());
        MediaTraceBuffer.Entry entry = buffer.snapshot().get(0);
        assertFalse(entry.category.contains("\n"));
        assertTrue(entry.detail.length() <= 180);
    }
}
