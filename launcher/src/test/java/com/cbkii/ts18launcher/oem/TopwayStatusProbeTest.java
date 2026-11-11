package com.cbkii.ts18launcher.oem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TopwayStatusProbeTest {
    static final class FakeHost implements TopwayStatusProbe.Host {
        boolean bindResult = true;
        int binds, unbinds;
        Consumer<TopwayStatusProbe.Remote> connected;
        Runnable lost;
        final List<Runnable> scheduled = new ArrayList<>();
        final List<Long> delays = new ArrayList<>();

        @Override public boolean bind(Consumer<TopwayStatusProbe.Remote> onConnected, Runnable onLost) {
            binds++;
            connected = onConnected;
            lost = onLost;
            return bindResult;
        }
        @Override public void unbind() { unbinds++; }
        @Override public Object schedule(Runnable task, long delayMs) { scheduled.add(task); delays.add(delayMs); return task; }
        @Override public void cancel(Object token) { scheduled.remove(token); }
        void fireDeadline() { new ArrayList<>(scheduled).forEach(Runnable::run); }
    }

    static final class FakeRemote implements TopwayStatusProbe.Remote {
        String descriptor = TopwayContract.COMMAND_DESCRIPTOR;
        boolean registerOk = true, requestOk = true, throwDescriptor;
        int registers, requests, unregisters;
        TopwayStatusProbe.StatusListener listener;

        @Override public String descriptor() throws Exception {
            if (throwDescriptor) throw new android.os.RemoteException();
            return descriptor;
        }
        @Override public boolean registerStatus(TopwayStatusProbe.StatusListener l) { registers++; listener = l; return registerOk; }
        @Override public boolean requestSource() { requests++; return requestOk; }
        @Override public void unregisterStatus() { unregisters++; }
    }

    private final List<TopwayStatusProbe.Result> results = new ArrayList<>();

    @Test public void checkModeVerifiesDescriptorWithoutAnyTransaction() {
        FakeHost host = new FakeHost();
        FakeRemote remote = new FakeRemote();
        new TopwayStatusProbe(host).start(false, results::add);
        host.connected.accept(remote);
        assertEquals(1, results.size());
        assertEquals(Capability.Status.AVAILABLE, results.get(0).status);
        assertEquals(TopwayStatusProbe.Stage.DESCRIPTOR, results.get(0).stage);
        assertEquals(0, remote.registers + remote.requests + remote.unregisters);
        assertEquals(1, host.unbinds);
        assertTrue(host.scheduled.isEmpty());
    }

    @Test public void observeReportsSourceAndAlwaysUnregistersAndUnbinds() {
        FakeHost host = new FakeHost();
        FakeRemote remote = new FakeRemote();
        new TopwayStatusProbe(host).start(true, results::add);
        host.connected.accept(remote);
        remote.listener.onReverse(0);
        remote.listener.onSource(3);
        assertEquals(1, results.size());
        TopwayStatusProbe.Result r = results.get(0);
        assertEquals(Capability.Status.AVAILABLE, r.status);
        assertEquals(Integer.valueOf(3), r.source);
        assertEquals(Integer.valueOf(0), r.reverse);
        assertEquals(1, remote.registers);
        assertEquals(1, remote.requests);
        assertEquals(1, remote.unregisters);
        assertEquals(1, host.unbinds);
        assertTrue(host.scheduled.isEmpty());
    }

    @Test public void lateCallbacksAfterFinishAreIgnored() {
        FakeHost host = new FakeHost();
        FakeRemote remote = new FakeRemote();
        new TopwayStatusProbe(host).start(true, results::add);
        host.connected.accept(remote);
        remote.listener.onSource(1);
        remote.listener.onSource(8);
        host.lost.run();
        assertEquals(1, results.size());
        assertEquals(1, remote.unregisters);
    }

    @Test public void noReplyTimesOutAsUnknownAndCleansUp() {
        FakeHost host = new FakeHost();
        FakeRemote remote = new FakeRemote();
        new TopwayStatusProbe(host).start(true, results::add);
        host.connected.accept(remote);
        assertEquals(Long.valueOf(TopwayStatusProbe.REPLY_DEADLINE_MS), host.delays.get(host.delays.size() - 1));
        host.fireDeadline();
        assertEquals(Capability.Status.UNKNOWN, results.get(0).status);
        assertEquals(TopwayStatusProbe.Stage.REPLY, results.get(0).stage);
        assertNull(results.get(0).source);
        assertEquals(1, remote.unregisters);
        assertEquals(1, host.unbinds);
    }

    @Test public void refusedBindIsBlockedAndNeverUnbinds() {
        FakeHost host = new FakeHost();
        host.bindResult = false;
        new TopwayStatusProbe(host).start(true, results::add);
        assertEquals(Capability.Status.BLOCKED, results.get(0).status);
        assertEquals(TopwayStatusProbe.Stage.BIND, results.get(0).stage);
        assertEquals(0, host.unbinds);
    }

    @Test public void noConnectionTimesOutAsUnknown() {
        FakeHost host = new FakeHost();
        new TopwayStatusProbe(host).start(false, results::add);
        host.fireDeadline();
        assertEquals(Capability.Status.UNKNOWN, results.get(0).status);
        assertEquals(1, host.unbinds);
        host.connected.accept(new FakeRemote());
        assertEquals(1, results.size());
    }

    @Test public void wrongDescriptorIsBlockedBeforeAnyRegistration() {
        FakeHost host = new FakeHost();
        FakeRemote remote = new FakeRemote();
        remote.descriptor = "something.else";
        new TopwayStatusProbe(host).start(true, results::add);
        host.connected.accept(remote);
        assertEquals(Capability.Status.BLOCKED, results.get(0).status);
        assertEquals(TopwayStatusProbe.Stage.DESCRIPTOR, results.get(0).stage);
        assertEquals(0, remote.registers);
    }

    @Test public void silentDescriptorIsUnknown() {
        FakeHost host = new FakeHost();
        FakeRemote remote = new FakeRemote();
        remote.throwDescriptor = true;
        new TopwayStatusProbe(host).start(false, results::add);
        host.connected.accept(remote);
        assertEquals(Capability.Status.UNKNOWN, results.get(0).status);
    }

    @Test public void refusedRegistrationIsBlockedWithoutUnregister() {
        FakeHost host = new FakeHost();
        FakeRemote remote = new FakeRemote();
        remote.registerOk = false;
        new TopwayStatusProbe(host).start(true, results::add);
        host.connected.accept(remote);
        assertEquals(TopwayStatusProbe.Stage.REGISTER, results.get(0).stage);
        assertEquals(0, remote.requests);
        assertEquals(0, remote.unregisters);
    }

    @Test public void refusedQueryStillUnregisters() {
        FakeHost host = new FakeHost();
        FakeRemote remote = new FakeRemote();
        remote.requestOk = false;
        new TopwayStatusProbe(host).start(true, results::add);
        host.connected.accept(remote);
        assertEquals(TopwayStatusProbe.Stage.REQUEST, results.get(0).stage);
        assertEquals(1, remote.unregisters);
    }

    @Test public void singleFlightAndRestartableAfterFinish() {
        FakeHost host = new FakeHost();
        TopwayStatusProbe probe = new TopwayStatusProbe(host);
        assertTrue(probe.start(false, results::add));
        assertFalse(probe.start(false, results::add));
        assertEquals(1, host.binds);
        Consumer<TopwayStatusProbe.Remote> first = host.connected;
        first.accept(new FakeRemote());
        assertTrue(probe.start(false, results::add));
        first.accept(new FakeRemote()); // stale connection from the first run
        assertEquals(1, results.size());
        host.connected.accept(new FakeRemote());
        assertEquals(2, results.size());
    }
}
