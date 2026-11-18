package com.cbkii.ts18launcher;

import android.content.ComponentName;
import android.content.Context;

import com.cbkii.ts18launcher.media.MediaBackend;
import com.cbkii.ts18launcher.media.direct.DirectSourceMediaBackend;

/** Composition point for optional engines. */
final class MediaEngines {
    private MediaEngines() {}

    /**
     * The direct (M2) engine. It shares only the launcher's notification-listener component with
     * the legacy engine and stays completely idle until a source is switched to it.
     */
    static MediaBackend createDirect(Context context) {
        Context app = context.getApplicationContext();
        return new DirectSourceMediaBackend(app,
                new ComponentName(app, MediaListenerService.class),
                new DirectSourceMediaBackend.Host() {
                    @Override public String appLabel(String packageName) {
                        return AppResolver.labelFor(app, packageName, "");
                    }
                    @Override public void trace(String event, String detail) {
                        MediaEventTrace.record("m2", event, detail);
                    }
                });
    }
}
