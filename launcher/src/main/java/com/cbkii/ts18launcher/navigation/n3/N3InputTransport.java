package com.cbkii.ts18launcher.navigation.n3;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;

/** Delivers converted input to the N3 display only. Implementations fail closed. */
public interface N3InputTransport {
    /** Empty when usable; otherwise why input cannot reach the display from this caller. */
    String unavailableReason();
    boolean motion(MotionEvent displayEvent);
    boolean back();
    void close();

    /** Display-scoped Back as a key down/up pair, the same keys a system Back would send. */
    static KeyEvent[] backKeys() {
        long now = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD);
        KeyEvent up = KeyEvent.changeAction(down, KeyEvent.ACTION_UP);
        return new KeyEvent[] {down, up};
    }

    N3InputTransport NONE = new N3InputTransport() {
        @Override public String unavailableReason() { return "No input path to the N3 display"; }
        @Override public boolean motion(MotionEvent displayEvent) { return false; }
        @Override public boolean back() { return false; }
        @Override public void close() { }
    };
}
