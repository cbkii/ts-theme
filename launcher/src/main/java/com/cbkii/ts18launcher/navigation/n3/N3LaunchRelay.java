package com.cbkii.ts18launcher.navigation.n3;

import java.util.HashMap;
import java.util.Map;

/** In-process hand-off from the trampoline back to the path that started it, keyed by a token. */
final class N3LaunchRelay {
    interface Result { void launched(); void refused(String reason); }

    private static final Map<Long, Result> pending = new HashMap<>();
    private static long next = 1L;

    private N3LaunchRelay() {}

    static synchronized long register(Result result) {
        long token = next++;
        pending.put(token, result);
        return token;
    }

    /** Each token is delivered at most once; unknown or replayed tokens are ignored. */
    static synchronized Result take(long token) { return pending.remove(token); }

    static synchronized void cancel(long token) { pending.remove(token); }
}
