package com.cbkii.ts18launcher;

import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.os.Handler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Retains the exact wrapper that owns each callback; fresh enumerations are not owners. */
final class SessionControllerRegistry {
    static List<MediaController> reconcile(Map<MediaSession.Token, MediaController> watched,
            List<MediaController> active, MediaController.Callback callback, Handler handler) {
        Map<MediaSession.Token, MediaController> next = new HashMap<>();
        if (active != null) for (MediaController candidate : active) {
            if (candidate == null) continue;
            try {
                MediaSession.Token token = candidate.getSessionToken();
                if (token == null || next.containsKey(token)) continue;
                MediaController owner = watched.get(token);
                if (owner == null) {
                    candidate.registerCallback(callback, handler);
                    owner = candidate;
                }
                next.put(token, owner);
            } catch (RuntimeException unavailable) {
                // An unsuccessful registration is never retained; next discovery retries it.
            }
        }
        for (Map.Entry<MediaSession.Token, MediaController> entry : watched.entrySet()) {
            if (!next.containsKey(entry.getKey())) {
                try { entry.getValue().unregisterCallback(callback); }
                catch (RuntimeException gone) { /* Already destroyed. */ }
            }
        }
        watched.clear(); watched.putAll(next);
        return new ArrayList<>(next.values());
    }
}
