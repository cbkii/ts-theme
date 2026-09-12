package com.cbkii.ts18launcher.platform;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Resolution-only HOME capability marker recovered from the exact Topway Video client.
 *
 * The inspected TW_THEME.20241022 path package-scopes queryIntentServices() to the
 * current HOME but does not bind this service. Returning no Binder is intentional:
 * inventing a DoFun-side Binder contract would be less compatible than exposing only
 * the exact behaviour currently evidenced.
 */
public final class TopwayDesktopWindowMarkerService extends Service {
    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
