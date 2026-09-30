package com.cbkii.ts18launcher;

import android.content.Context;

/** Visible testing-build provenance so physical results can always be tied to exact source. */
final class BuildIdentity {
    private BuildIdentity() {}
    static String summary(Context context) {
        String sha = context.getString(R.string.build_source_revision);
        String ref = context.getString(R.string.build_source_ref);
        if (sha.length() > 12) sha = sha.substring(0, 12);
        return "source " + sha + " · " + ref;
    }
}
