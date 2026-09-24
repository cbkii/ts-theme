package com.cbkii.ts18launcher;

import android.content.Context;

import com.cbkii.ts18launcher.platform.TopwayAdapter;

/** Builds the user-invoked Settings diagnostics report without performing mutations. */
final class MediaDiagnostics {
    private MediaDiagnostics() {}

    static String build(Context context) {
        String music = LauncherPrefs.packageFor(context, LauncherPrefs.KEY_MUSIC);
        if (music.isEmpty()) music = TopwayAdapter.defaultMusicPackage(context);
        String radio = RadioProvider.resolvePackage(context);

        StringBuilder out = new StringBuilder();
        out.append("Configured sources\n");
        out.append("Music: ").append(emptyAsNone(music)).append('\n');
        out.append("Radio: ").append(emptyAsNone(radio)).append('\n');
        out.append("Music route: ").append(adapterSummary(context, music)).append('\n');
        out.append("Radio route: ").append(adapterSummary(context, radio)).append('\n');
        out.append("Notification access: ")
                .append(MediaListenerService.hasNotificationAccess(context) ? "granted" : "not granted")
                .append("\n\nActive/bound controllers\n")
                .append(MediaListenerService.sessionDiagnostics(context))
                .append("\n\nReadiness/event timeline\n")
                .append(MediaEventTrace.dump());
        return out.toString();
    }

    private static String adapterSummary(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return "none";
        MediaSourceAdapter adapter = MediaSourceAdapter.resolve(context, packageName);
        StringBuilder text = new StringBuilder(adapter.kind.toString());
        if (adapter.rootPrime) text.append(" · root-first");
        text.append(adapter.passiveWarmSafe ? " · passive warm allowed" : " · interactive only");
        if (adapter.service != null) text.append(" · ").append(adapter.service.flattenToShortString());
        if (!adapter.unavailableReason.isEmpty()) text.append(" · ").append(adapter.unavailableReason);
        return text.toString();
    }

    private static String emptyAsNone(String value) {
        return value == null || value.isEmpty() ? "none" : value;
    }
}
