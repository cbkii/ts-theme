// Callbacks from the optional N3 display host to its one client. The binder also serves as the
// client's death token: when the client process dies the host releases its display.
package com.cbkii.ts18launcher.navigation.n3;

oneway interface IN3DisplayHostClient {
    void onFirstFrame(int displayId);
    void onDisplayLost(int displayId, String reason);
}
