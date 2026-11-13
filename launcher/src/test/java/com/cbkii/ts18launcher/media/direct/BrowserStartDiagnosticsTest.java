package com.cbkii.ts18launcher.media.direct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Integration review F2: every start attempt reports how it ended and how long it took, so a
 * physical capture can tell a platform bind refusal from a source's own null root, and a bind is
 * never attempted before user unlock.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, manifest = Config.NONE, application = android.app.Application.class)
public class BrowserStartDiagnosticsTest {
    private static final String AUXIO = "com.tw.media";
    private final List<String> events = new ArrayList<>();
    private final List<BrowserSessionStarter.ConnectionCallbacks> links = new ArrayList<>();
    private final long[] now = {1000L};
    private boolean unlocked = true;

    private BrowserSessionStarter starter() {
        return new BrowserSessionStarter(new BrowserSessionStarter.Connector() {
            @Override public ComponentName resolve(String packageName) {
                return new ComponentName(packageName, "com.tw.music.MusicService");
            }
            @Override public boolean userUnlocked() { return unlocked; }
            @Override public BrowserSessionStarter.Connection connect(ComponentName service,
                    BrowserSessionStarter.ConnectionCallbacks callbacks) {
                links.add(callbacks);
                return () -> { };
            }
        }, new BrowserSessionStarter.Events() {
            @Override public void onSessionOffered(String p, DirectSessionRegistry.Session s) { events.add("offered"); }
            @Override public void onSessionWithdrawn(String p, Object t) { events.add("withdrawn"); }
            @Override public void onStartFailed(String p, String reason) { events.add("failed " + reason); }
            @Override public void onStartDiagnostic(String p, String detail) { events.add("diag " + detail); }
        }, () -> now[0]);
    }

    @Test public void immediateFrameworkFailureIsClassifiedAsBindRefusal() {
        BrowserSessionStarter starter = starter();
        starter.start("MUSIC", AUXIO);
        now[0] += 20L;
        links.get(0).onFailed();
        assertTrue(events.toString(), events.get(0).startsWith("diag outcome=failed elapsedMs=20 class=bind-refused"));
        assertEquals("failed media service connection failed", events.get(1));
    }

    @Test public void lateFailureIsClassifiedAsRootOrLate() {
        BrowserSessionStarter starter = starter();
        starter.start("MUSIC", AUXIO);
        now[0] += 900L;
        links.get(0).onFailed();
        assertTrue(events.toString(), events.get(0).contains("class=root-null-or-late"));
    }

    @Test public void noBindIsAttemptedBeforeUserUnlock() {
        unlocked = false;
        starter().start("MUSIC", AUXIO);
        assertTrue("bind attempted while locked", links.isEmpty());
        assertEquals("diag outcome=deferred reason=user-locked", events.get(0));
        assertEquals("failed is waiting for the device to unlock", events.get(1));
    }
}
