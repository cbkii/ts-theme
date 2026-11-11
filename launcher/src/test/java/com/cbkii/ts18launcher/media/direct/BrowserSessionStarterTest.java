package com.cbkii.ts18launcher.media.direct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;

import com.cbkii.ts18launcher.media.direct.DirectTestKit.FakeSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The starter's attempt lifecycle against a controllable browser connection: no callback,
 * delayed callback, rejection, suspension, wrong owner, abandonment, retry and shared owners.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, manifest = Config.NONE, application = android.app.Application.class)
public class BrowserSessionStarterTest {
    private static final String AUXIO = "com.tw.media";
    private static final Object MUSIC = "MUSIC";
    private static final Object RADIO = "RADIO";

    private static final class Link implements BrowserSessionStarter.Connection {
        final BrowserSessionStarter.ConnectionCallbacks callbacks;
        boolean disconnected;
        Link(BrowserSessionStarter.ConnectionCallbacks callbacks) { this.callbacks = callbacks; }
        @Override public void disconnect() { disconnected = true; }
    }

    private final Map<String, ComponentName> services = new HashMap<>();
    private final List<Link> links = new ArrayList<>();
    private final List<String> events = new ArrayList<>();
    private BrowserSessionStarter starter;
    private boolean refuseConnect;

    @Before public void setUp() {
        services.put(AUXIO, new ComponentName(AUXIO, "com.tw.music.MusicService"));
        starter = new BrowserSessionStarter(new BrowserSessionStarter.Connector() {
            @Override public ComponentName resolve(String packageName) { return services.get(packageName); }
            @Override public BrowserSessionStarter.Connection connect(ComponentName service,
                    BrowserSessionStarter.ConnectionCallbacks callbacks) {
                if (refuseConnect) throw new SecurityException("bind refused");
                Link link = new Link(callbacks);
                links.add(link);
                return link;
            }
        }, new BrowserSessionStarter.Events() {
            @Override public void onSessionOffered(String packageName, DirectSessionRegistry.Session session) {
                events.add("offered " + session.token());
            }
            @Override public void onSessionWithdrawn(String packageName, Object token) { events.add("withdrawn " + token); }
            @Override public void onStartFailed(String packageName, String reason) { events.add("failed " + reason); }
        });
    }

    private Link last() { return links.get(links.size() - 1); }

    @Test public void startableOnlyWithExactlyOneExportedBrowserService() {
        assertTrue(starter.canStart(AUXIO));
        assertFalse(starter.canStart("com.tw.radio"));
        assertFalse(starter.canStart(""));
        ComponentName a = new ComponentName("p", "A");
        assertEquals(a, BrowserSessionStarter.choose(Collections.singletonList(a)));
        assertNull("two candidates are ambiguous, not first-match",
                BrowserSessionStarter.choose(Arrays.asList(a, new ComponentName("p", "B"))));
        assertNull(BrowserSessionStarter.choose(Collections.emptyList()));
    }

    @Test public void connectionThatNeverAnswersIsTornDownOnAbandonAndRetryIsFresh() {
        starter.start(MUSIC, AUXIO);
        Link stalled = last();
        starter.abandon(MUSIC, AUXIO);
        assertTrue(stalled.disconnected);
        assertEquals(0, starter.attemptCount());

        starter.start(MUSIC, AUXIO);
        assertEquals("a deliberate retry opens a new connection", 2, links.size());
        // The abandoned attempt answering late is ignored entirely.
        stalled.callbacks.onConnected(AUXIO, new FakeSession("late", AUXIO));
        assertTrue(events.isEmpty());
        last().callbacks.onConnected(AUXIO, new FakeSession("fresh", AUXIO));
        assertEquals(Arrays.asList("offered fresh"), events);
    }

    @Test public void repeatedStartWhileConnectingJoinsTheOneAttempt() {
        starter.start(MUSIC, AUXIO);
        starter.start(MUSIC, AUXIO);
        assertEquals(1, links.size());
    }

    @Test public void delayedSuccessIsOfferedOnce() {
        starter.start(MUSIC, AUXIO);
        last().callbacks.onConnected(AUXIO, new FakeSession("t1", AUXIO));
        last().callbacks.onConnected(AUXIO, new FakeSession("t1", AUXIO));
        assertEquals("a second answer from the same attempt is not a second offer",
                Arrays.asList("offered t1"), events);
    }

    @Test public void rejectionAndSuspensionAreReportedAndDisconnect() {
        starter.start(MUSIC, AUXIO);
        Link rejected = last();
        rejected.callbacks.onFailed();
        assertTrue(rejected.disconnected);
        assertEquals(Arrays.asList("failed refused the connection"), events);

        starter.start(MUSIC, AUXIO);
        Link live = last();
        live.callbacks.onConnected(AUXIO, new FakeSession("t2", AUXIO));
        live.callbacks.onSuspended();
        assertTrue(live.disconnected);
        assertEquals("a suspension after a session was offered withdraws it",
                "withdrawn t2", events.get(events.size() - 1));
    }

    @Test public void bindRefusalIsAFailureNotAHang() {
        refuseConnect = true;
        starter.start(MUSIC, AUXIO);
        assertEquals(Arrays.asList("failed could not be reached"), events);
        assertEquals(0, starter.attemptCount());
    }

    @Test public void sessionOwnedByAnotherPackageIsRefused() {
        starter.start(MUSIC, AUXIO);
        last().callbacks.onConnected("com.spotify.music", new FakeSession("foreign", "com.spotify.music"));
        assertEquals(Arrays.asList("failed offered a session it does not own"), events);
        assertTrue(last().disconnected);
    }

    @Test public void deadSessionDuringConnectIsAFailure() {
        starter.start(MUSIC, AUXIO);
        last().callbacks.onConnected("", null);
        assertEquals(Arrays.asList("failed stopped while starting"), events);
    }

    @Test public void sharedPackageConnectionLivesUntilItsLastOwnerReleases() {
        starter.start(MUSIC, AUXIO);
        starter.start(RADIO, AUXIO);
        assertEquals(1, links.size());
        last().callbacks.onConnected(AUXIO, new FakeSession("t", AUXIO));
        starter.release(MUSIC, AUXIO);
        assertFalse("Radio still holds the shared connection", last().disconnected);
        starter.release(RADIO, AUXIO);
        assertTrue(last().disconnected);
        assertEquals("withdrawn t", events.get(events.size() - 1));
    }

    @Test public void oneOwnerTimingOutDoesNotStrandTheOther() {
        starter.start(MUSIC, AUXIO);
        starter.start(RADIO, AUXIO);
        starter.abandon(MUSIC, AUXIO);
        assertFalse(last().disconnected);
        starter.abandon(RADIO, AUXIO);
        assertTrue(last().disconnected);
    }

    @Test public void abandonNeverDropsAnEstablishedSession() {
        starter.start(MUSIC, AUXIO);
        last().callbacks.onConnected(AUXIO, new FakeSession("t", AUXIO));
        starter.abandon(MUSIC, AUXIO);
        assertFalse(last().disconnected);
    }

    @Test public void startAfterAnOfferedSessionVanishedReplacesTheConnection() {
        starter.start(MUSIC, AUXIO);
        Link first = last();
        first.callbacks.onConnected(AUXIO, new FakeSession("t1", AUXIO));
        starter.start(MUSIC, AUXIO);
        assertTrue(first.disconnected);
        assertEquals(2, links.size());
        assertTrue(events.contains("withdrawn t1"));
        first.callbacks.onConnected(AUXIO, new FakeSession("stale", AUXIO));
        assertFalse(events.contains("offered stale"));
    }

    @Test public void unknownPackageReportsFailureImmediately() {
        starter.start(MUSIC, "com.tw.radio");
        assertEquals(Arrays.asList("failed has no single background media service"), events);
        assertTrue(links.isEmpty());
    }
}
