package com.cbkii.ts18launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AndroidUserIdTest {
    @Test public void separatesApplicationUidFromAndroidUserId() {
        assertEquals(0, AndroidUserId.fromUid(10223));
        assertEquals(10, AndroidUserId.fromUid(1010223));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeUid() {
        AndroidUserId.fromUid(-1);
    }
}
