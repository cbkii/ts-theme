package com.cbkii.ts18launcher.media.direct;

import static org.junit.Assert.*;
import android.content.ComponentName;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Handler;
import android.os.Looper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, manifest = Config.NONE, application = android.app.Application.class,
        shadows = FrameworkSessionsSourceTest.MembershipBoundary.class)
public class FrameworkSessionsSourceTest {
    @Implements(MediaSessionManager.class)
    public static class MembershipBoundary {
        static volatile Thread readThread;
        static volatile int reads;
        @Implementation protected java.util.List<MediaController> getActiveSessions(ComponentName component) {
            readThread = Thread.currentThread(); reads++; return java.util.Collections.emptyList();
        }
        @Implementation protected void addOnActiveSessionsChangedListener(
                MediaSessionManager.OnActiveSessionsChangedListener listener, ComponentName component, Handler handler) { }
        @Implementation protected void removeOnActiveSessionsChangedListener(
                MediaSessionManager.OnActiveSessionsChangedListener listener) { }
    }

    @Test public void membershipBinderReadIsOffMainAndPublishedWithoutARefreshLoop() throws Exception {
        MembershipBoundary.reads = 0; MembershipBoundary.readThread = null;
        FrameworkSessions source = new FrameworkSessions(RuntimeEnvironment.getApplication(),
                new ComponentName("test.launcher", "test.Listener"), new Handler(Looper.getMainLooper()));
        final int[] changes = {0};
        source.setChangeListener(() -> changes[0]++);
        try { source.activeSessions(); fail("pending read must remain unknown"); }
        catch (IllegalStateException pending) { assertEquals("MEMBERSHIP_READ_PENDING", pending.getMessage()); }
        long end = System.nanoTime() + 2_000_000_000L;
        while (changes[0] == 0 && System.nanoTime() < end) {
            ((org.robolectric.shadows.ShadowLooper) org.robolectric.shadow.api.Shadow.extract(
                    Looper.getMainLooper())).idle(); Thread.sleep(5);
        }
        assertTrue(changes[0] > 0);
        assertNotEquals(Thread.currentThread(), MembershipBoundary.readThread);
        assertTrue(source.activeSessions().isEmpty());
        assertTrue(source.activeSessions().isEmpty());
        assertEquals(1, MembershipBoundary.reads);
        source.setChangeListener(null);
    }
}
