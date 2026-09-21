package com.cbkii.ts18launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;

import java.util.List;

/**
 * Background-readiness contract for one configured media source.
 *
 * Exact adapters may use a bounded root service start as their activation path. Generic sources
 * remain limited to an exported Android MediaBrowserService. No adapter is allowed to launch an
 * Activity; foreground app opening stays owned by the explicit Radio/Music source icons.
 */
final class MediaSourceAdapter {
    static final String MEDIA_BROWSER_ACTION = "android.media.browse.MediaBrowserService";
    static final String MEDIA3_SESSION_ACTION = "androidx.media3.session.MediaSessionService";
    static final String AUXIO_PACKAGE = "com.tw.media";
    static final String AUXIO_BROWSER_SERVICE = "com.tw.music.MusicService";
    static final String NAVRADIO_PACKAGE = "com.navimods.radio";
    static final String NAVRADIO_SERVICE = "com.navimods.radio.RadioService";
    static final String STOCK_TW_RADIO_PACKAGE = "com.tw.radio";

    enum Kind { MEDIA_BROWSER, EXPLICIT_SERVICE, SESSION_ONLY }

    final String packageName;
    final Kind kind;
    final ComponentName service;
    final String action;
    final boolean rootPrime;
    final boolean foregroundService;
    final String unavailableReason;

    private MediaSourceAdapter(String packageName, Kind kind, ComponentName service, String action,
                               boolean rootPrime, boolean foregroundService,
                               String unavailableReason) {
        this.packageName = packageName;
        this.kind = kind;
        this.service = service;
        this.action = action;
        this.rootPrime = rootPrime;
        this.foregroundService = foregroundService;
        this.unavailableReason = unavailableReason;
    }

    static MediaSourceAdapter resolve(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return sessionOnly("", "No media app configured");
        }

        if (NAVRADIO_PACKAGE.equals(packageName)) {
            ComponentName exact = new ComponentName(NAVRADIO_PACKAGE, NAVRADIO_SERVICE);
            ComponentName service = exportedService(context, exact)
                    ? exact : findExportedService(context, packageName, MEDIA3_SESSION_ACTION);
            if (service != null) {
                return new MediaSourceAdapter(packageName, Kind.EXPLICIT_SERVICE, service,
                        MEDIA3_SESSION_ACTION, true, true, "");
            }
            return sessionOnly(packageName,
                    "NavRadio+ background service is not available in the installed build");
        }

        // Exact current TW Radio bytes declare no Android service component. Do not turn its
        // Activity into a hidden warm-up path and do not infer private Topway commands from strings.
        if (STOCK_TW_RADIO_PACKAGE.equals(packageName)) {
            return sessionOnly(packageName,
                    "Stock TW Radio has no exported background media service");
        }

        ComponentName browser = findExportedService(context, packageName, MEDIA_BROWSER_ACTION);
        if (browser != null) {
            boolean exactAuxio = AUXIO_PACKAGE.equals(packageName)
                    && AUXIO_BROWSER_SERVICE.equals(browser.getClassName());
            return new MediaSourceAdapter(packageName, Kind.MEDIA_BROWSER, browser,
                    MEDIA_BROWSER_ACTION, exactAuxio, false, "");
        }

        return sessionOnly(packageName, "No exported background media service was found");
    }

    String rootStartCommand() {
        if (!rootPrime || service == null || action == null || action.isEmpty()) return "";
        String verb = foregroundService ? "start-foreground-service" : "startservice";
        return "/system/bin/am " + verb + " --user 0 -a " + action
                + " -n " + service.flattenToShortString();
    }

    Intent explicitServiceIntent() {
        return service == null ? null : new Intent(action).setComponent(service);
    }

    String notReadyMessage(String label) {
        String source = label == null || label.isEmpty() ? "Source" : label;
        if (!unavailableReason.isEmpty()) {
            return unavailableReason + "; tap the " + source + " source icon";
        }
        return source + " did not become ready in background; tap the "
                + source + " source icon";
    }

    private static MediaSourceAdapter sessionOnly(String packageName, String reason) {
        return new MediaSourceAdapter(packageName, Kind.SESSION_ONLY, null, "",
                false, false, reason);
    }

    private static ComponentName findExportedService(Context context, String packageName,
                                                     String action) {
        Intent query = new Intent(action).setPackage(packageName);
        final List<ResolveInfo> services;
        try {
            services = context.getPackageManager().queryIntentServices(query, 0);
        } catch (RuntimeException ignored) {
            return null;
        }
        for (ResolveInfo info : services) {
            if (info == null || info.serviceInfo == null) continue;
            ServiceInfo service = info.serviceInfo;
            if (!service.enabled || !service.exported) continue;
            return new ComponentName(service.packageName, service.name);
        }
        return null;
    }

    private static boolean exportedService(Context context, ComponentName component) {
        try {
            ServiceInfo info = context.getPackageManager().getServiceInfo(component, 0);
            return info != null && info.enabled && info.exported;
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            return false;
        }
    }
}
