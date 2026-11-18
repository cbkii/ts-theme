package com.cbkii.ts18launcher.navigation.n3;

import android.view.Surface;

/**
 * One way of owning the N3 display. The public path is the launcher's own private virtual
 * display; the host path is the optional, separately installed display host. Callbacks arrive on
 * the main thread and carry the session generation they were started for.
 */
interface N3DisplayPath {
    enum Authority { LAUNCHER_PRIVATE_DISPLAY, OPTIONAL_HOST }

    interface Callback {
        void created(long generation, int displayId);
        void launched(long generation);
        void firstFrame(long generation);
        void lost(long generation, String reason);
        void failed(long generation, String reason);
    }

    Authority authority();
    void create(long generation, Surface surface, int width, int height, int dpi, Callback callback);
    void launch(long generation, String component, Callback callback);
    /** Present on a new surface (null detaches); the display and its content are kept. */
    void setSurface(Surface surface);
    N3InputTransport input();
    void release();
}
