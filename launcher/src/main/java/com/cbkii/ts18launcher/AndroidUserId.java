package com.cbkii.ts18launcher;

import android.os.Process;

/** Resolves the Android user which owns this launcher process, not its application UID. */
final class AndroidUserId {
    static final int UID_RANGE_PER_USER = 100000;

    private AndroidUserId() {}

    static int current() {
        return fromUid(Process.myUid());
    }

    static int fromUid(int uid) {
        if (uid < 0) throw new IllegalArgumentException("UID must be non-negative");
        return uid / UID_RANGE_PER_USER;
    }
}
