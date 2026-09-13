package com.cbkii.ts18launcher;
import android.content.Context;
/** Experimental raw Android-10 freeform backend; it does not claim to reproduce Topway OEM policy. */
final class RawFreeformTaskBackend extends RootNavigationBackend {
    RawFreeformTaskBackend(Context context) { super(context, "TS18-RawFreeform"); }
    @Override public String label() { return "Raw Android freeform task · experimental"; }
    @Override String presentAction() { return "freeform"; }
    @Override String verifyAction() { return "verify-freeform"; }
}
