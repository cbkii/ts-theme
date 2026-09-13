package com.cbkii.ts18launcher;
import android.content.Context;
/** Experimental standard Android-10 pinned-stack PiP backend with strict configured-task identity. */
final class AndroidPipBackend extends RootNavigationBackend {
    AndroidPipBackend(Context context) { super(context, "TS18-AndroidPiP"); }
    @Override public String label() { return "Standard Android PiP · experimental/glance candidate"; }
    @Override String presentAction() { return "pip"; }
    @Override String verifyAction() { return "verify-pip"; }
}
