package com.cbkii.ts18launcher.platform;

import android.net.Uri;

/**
 * Exact current-HOME discovery/configuration surface recovered from the pinned
 * com.tw.video TW_THEME.20241022 client (SHA-256 07c37275f86c495f8e62a23bcfae32e75674f4ad30ccdbc269f25235cc159562).
 *
 * This is deliberately not the app-side floating-window Binder ABI. Cooperative
 * Topway apps expose FLOATING_WINDOW_SERVER themselves; ordinary navigation apps
 * do not need that protocol to use the launcher's bounded-task navigation lane.
 */
public final class TopwayDesktopWindowContract {
    public static final String MARKER_ACTION = "cn.cardoor.desktop.window.DESKTOP_WINDOW_SERVICE";
    public static final String PROVIDER_AUTHORITY = "com.cbkii.ts18launcher.ExportedProvider";
    public static final String PROVIDER_PATH = "kv_config";
    public static final String KEY_DESKTOP_WINDOW_SETTING = "desktop_window_setting";
    public static final String KEY_THEME_DESKTOP_WINDOW = "isCurrentUsedThemeInstanceDesktopWindow";

    private TopwayDesktopWindowContract() {}

    public static Uri uriFor(String key) {
        return new Uri.Builder()
                .scheme("content")
                .authority(PROVIDER_AUTHORITY)
                .appendPath(PROVIDER_PATH)
                .appendPath(key)
                .build();
    }
}
