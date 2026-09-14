package com.cbkii.ts18launcher;
import android.content.Context;
/** Android-10 mode-5 task backend; it does not claim to reproduce Topway OEM policy. */
final class RawFreeformTaskBackend extends RootNavigationBackend {
    RawFreeformTaskBackend(Context context) { super(context, "TS18-NativeNavigation"); }
    @Override public String label() { return "Native navigation window · TESTING"; }
}
