package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class PerfSamplerTest {
    @Test public void measuresFrameIntervalsAndIgnoresNonAdvancingTimestamps() {
        PerfSampler.Frames frames = new PerfSampler.Frames();
        frames.add(0L); frames.add(16000000L); frames.add(49000000L);
        frames.add(150000000L); frames.add(150000000L);
        assertEquals(3, frames.intervals);
        assertEquals(2, frames.over32);
        assertEquals(1, frames.over100);
    }
}
