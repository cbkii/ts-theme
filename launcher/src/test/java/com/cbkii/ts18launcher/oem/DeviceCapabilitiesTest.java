package com.cbkii.ts18launcher.oem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class DeviceCapabilitiesTest {
    private static DeviceCapabilities.Facts exact() {
        DeviceCapabilities.Facts f = new DeviceCapabilities.Facts();
        f.uid = 10223; f.callerPackage = "com.cbkii.ts18launcher"; f.selinux = "u:r:untrusted_app:s0";
        f.xtInstalled = true; f.xtVersion = "2022.02.17"; f.xtSharedUid = "android.uid.system";
        f.xtSignerSha256 = TopwayContract.TOPWAY_SIGNER_SHA256;
        f.xtApkSha256 = TopwayContract.VERIFIED_APK_SHA256;
        f.xtServiceDeclared = true; f.xtServiceExported = true; f.xtServiceEnabled = true;
        return f;
    }

    private static Capability get(List<Capability> list, String id) {
        for (Capability c : list) if (c.id.equals(id)) return c;
        throw new AssertionError("missing " + id);
    }

    private static TopwayStatusProbe.Result result(Capability.Status s, TopwayStatusProbe.Stage stage, boolean observe,
                                                  Integer source, Integer reverse) {
        return new TopwayStatusProbe.Result(s, stage, "d", observe, source, reverse, null);
    }

    @Test public void nothingIsAvailableWithoutTheServicePackage() {
        DeviceCapabilities.Facts f = new DeviceCapabilities.Facts();
        List<Capability> c = DeviceCapabilities.classify(f, null);
        assertEquals(Capability.Status.UNAVAILABLE, get(c, "topway.service.identity").status);
        assertEquals(Capability.Status.UNAVAILABLE, get(c, "topway.source").status);
        assertEquals(Capability.Status.BLOCKED, get(c, "radio.topway.native").status);
        assertEquals(Capability.Status.UNKNOWN, get(c, "illumination").status);
    }

    @Test public void exactBinaryWithoutProbeIsUnknownNotAvailable() {
        List<Capability> c = DeviceCapabilities.classify(exact(), null);
        assertEquals(Capability.Status.AVAILABLE, get(c, "topway.service.identity").status);
        assertEquals(Capability.Status.UNKNOWN, get(c, "topway.service.endpoint").status);
        assertEquals(Capability.Status.UNKNOWN, get(c, "topway.source").status);
        assertTrue(DeviceCapabilities.mayObserve(exact()));
    }

    @Test public void differentBinaryIsUnknownAndMayNotBeObserved() {
        DeviceCapabilities.Facts f = exact();
        f.xtApkSha256 = "00ff00ff00ff00ff";
        assertEquals(Capability.Status.UNKNOWN, get(DeviceCapabilities.classify(f, null), "topway.service.identity").status);
        assertFalse(DeviceCapabilities.mayObserve(f));
        f.xtApkSha256 = "";
        f.xtApkReadFailure = "EACCES";
        assertFalse(DeviceCapabilities.mayObserve(f));
        f.observeWithheld = true;
        Capability source = get(DeviceCapabilities.classify(f,
                result(Capability.Status.AVAILABLE, TopwayStatusProbe.Stage.DESCRIPTOR, false, null, null)), "topway.source");
        assertEquals(Capability.Status.UNKNOWN, source.status);
        assertTrue(source.detail.contains("withheld"));
    }

    @Test public void permissionOrUnexportedServiceIsBlocked() {
        DeviceCapabilities.Facts f = exact();
        f.xtServicePermission = "android.permission.SOMETHING";
        assertEquals(Capability.Status.BLOCKED, get(DeviceCapabilities.classify(f, null), "topway.source").status);
        f = exact();
        f.xtServiceExported = false;
        assertEquals(Capability.Status.BLOCKED, get(DeviceCapabilities.classify(f, null), "topway.service.endpoint").status);
    }

    @Test public void observedSourceAndReverseAreAvailableWithKind() {
        List<Capability> c = DeviceCapabilities.classify(exact(),
                result(Capability.Status.AVAILABLE, TopwayStatusProbe.Stage.REPLY, true, 1, 0));
        Capability source = get(c, "topway.source");
        assertEquals(Capability.Status.AVAILABLE, source.status);
        assertTrue(source.detail.startsWith("radio"));
        assertEquals(Capability.Status.AVAILABLE, get(c, "topway.reverse").status);
        assertEquals(Capability.Status.UNKNOWN, get(c, "topway.sleep").status);
    }

    @Test public void probeFailureStageIsCarried() {
        List<Capability> c = DeviceCapabilities.classify(exact(),
                result(Capability.Status.BLOCKED, TopwayStatusProbe.Stage.REGISTER, true, null, null));
        assertEquals(Capability.Status.BLOCKED, get(c, "topway.source").status);
        assertEquals("register", get(c, "topway.source").stage);
    }

    @Test public void radioStrategiesAreDistinct() {
        DeviceCapabilities.Facts f = exact();
        f.navRadioInstalled = true;
        List<Capability> c = DeviceCapabilities.classify(f, null);
        assertEquals(Capability.Status.AVAILABLE, get(c, "radio.navradio.session").status);
        assertEquals(Capability.Status.UNAVAILABLE, get(c, "radio.topway.session").status);
        assertEquals(Capability.Status.BLOCKED, get(c, "radio.topway.native").status);
        for (TopwayNativeRadio.Operation op : TopwayNativeRadio.Operation.values()) {
            assertEquals(Capability.Status.BLOCKED, TopwayNativeRadio.command(op).status);
        }
    }

    @Test public void sourceValuesMapToKinds() {
        assertEquals(TopwayContract.Source.RADIO, TopwayContract.Source.of(1));
        assertEquals(TopwayContract.Source.LOCAL_MUSIC, TopwayContract.Source.of(3));
        assertEquals(TopwayContract.Source.BLUETOOTH, TopwayContract.Source.of(8));
        assertEquals(TopwayContract.Source.VIDEO, TopwayContract.Source.of(9));
        assertEquals(TopwayContract.Source.OTHER, TopwayContract.Source.of(42));
    }
}
