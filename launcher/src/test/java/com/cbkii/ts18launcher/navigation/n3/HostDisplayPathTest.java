package com.cbkii.ts18launcher.navigation.n3;

import static org.junit.Assert.assertEquals;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.Surface;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowPackageManager;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class HostDisplayPathTest {
    @Test public void releasedBindingCannotReopenTheHostEvenAfterANewCreate() {
        Context app = RuntimeEnvironment.getApplication();
        ResolveInfo info = new ResolveInfo();
        info.serviceInfo = new ServiceInfo();
        info.serviceInfo.packageName = HostDisplayPath.HOST_PACKAGE;
        info.serviceInfo.name = HostDisplayPath.HOST_PACKAGE + ".DisplayHostService";
        info.serviceInfo.exported = true;
        ShadowPackageManager packages = Shadow.extract(app.getPackageManager());
        packages.addResolveInfoForIntent(
                new Intent(HostDisplayPath.ACTION).setPackage(HostDisplayPath.HOST_PACKAGE), info);
        ServiceConnection[] bound = new ServiceConnection[1];
        Context caller = new ContextWrapper(app) {
            @Override public boolean bindService(Intent intent, ServiceConnection connection, int flags) {
                bound[0] = connection;
                return true;
            }
            @Override public void unbindService(ServiceConnection connection) { }
        };
        int[] opened = {0};
        IN3DisplayHost.Stub remote = new IN3DisplayHost.Stub() {
            @Override public int protocolVersion() { opened[0]++; return 1; }
            @Override public Bundle open(Surface s, int w, int h, int dpi, IN3DisplayHostClient client) {
                Bundle result = new Bundle();
                result.putString("decision", "ALLOW");
                result.putInt("displayId", 7);
                result.putLong("lease", 42L);
                return result;
            }
            @Override public int launch(int id, long lease, String component) { return 0; }
            @Override public int motion(int id, long lease, MotionEvent event) { return 0; }
            @Override public int back(int id, long lease) { return 0; }
            @Override public int setSurface(int id, long lease, Surface surface) { return 0; }
            @Override public int close(int id, long lease) { return 0; }
        };
        N3DisplayPath.Callback callback = new N3DisplayPath.Callback() {
            @Override public void created(long generation, int displayId) { }
            @Override public void launched(long generation) { }
            @Override public void firstFrame(long generation) { }
            @Override public void lost(long generation, String reason) { }
            @Override public void failed(long generation, String reason) { }
        };
        HostDisplayPath path = new HostDisplayPath(caller, new Handler(Looper.getMainLooper()));
        ComponentName service = new ComponentName(info.serviceInfo.packageName, info.serviceInfo.name);
        path.create(1L, null, 1024, 600, 160, callback);
        ServiceConnection old = bound[0];
        path.release();
        old.onServiceConnected(service, remote);
        assertEquals("release invalidates queued connection callbacks", 0, opened[0]);
        path.create(2L, null, 1024, 600, 160, callback);
        old.onServiceConnected(service, remote);
        assertEquals("a later create does not revive the old binding", 0, opened[0]);
        bound[0].onServiceConnected(service, remote);
        assertEquals(1, opened[0]);
        path.release();
    }
}
