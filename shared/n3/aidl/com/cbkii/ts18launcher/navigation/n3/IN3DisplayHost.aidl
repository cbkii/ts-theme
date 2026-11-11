// Optional N3 display host protocol, version 1. Implemented only by the separate n3host APK,
// which is inert by default. Every call is authorised from Binder caller identity by the host;
// nothing in a parcel is trusted as identity, user or geometry authority.
package com.cbkii.ts18launcher.navigation.n3;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.Surface;
import com.cbkii.ts18launcher.navigation.n3.IN3DisplayHostClient;

interface IN3DisplayHost {
    int protocolVersion();
    /** Reserve and create one private display rendering to surface. Bundle: decision, displayId, lease. */
    Bundle open(in Surface surface, int width, int height, int dpi, IN3DisplayHostClient client);
    /** Launch the exact allowlisted component on the caller's display. Returns a decision ordinal. */
    int launch(int displayId, long lease, String component);
    int motion(int displayId, long lease, in MotionEvent event);
    int back(int displayId, long lease);
    /** Replace the output surface (null detaches; the display and its content are kept). */
    int setSurface(int displayId, long lease, in Surface surface);
    int close(int displayId, long lease);
}
