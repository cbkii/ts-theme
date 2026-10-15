package com.cbkii.ts18launcher.platform;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * Minimal read-only implementation of the current-HOME config provider queried by
 * the exact com.tw.video TW_THEME.20241022 client.
 *
 * desktop_window_setting deliberately returns zero rows until the launcher actually
 * hosts a cooperative Topway floating window. This makes the client's moveToNext()
 * path report no active WindowInfo without guessing DoFun's still-unknown windowName
 * or host-selection policy. The theme flag is explicitly false for the standalone
 * launcher, which is not a DoFun desktop-window theme instance.
 */
public final class TopwayDesktopWindowProvider extends ContentProvider {
    private static final int DESKTOP_WINDOW_SETTING = 1;
    private static final int THEME_DESKTOP_WINDOW = 2;
    private static final String VALUE_COLUMN = "value";
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        MATCHER.addURI(TopwayDesktopWindowContract.PROVIDER_AUTHORITY,
                TopwayDesktopWindowContract.PROVIDER_PATH + "/"
                        + TopwayDesktopWindowContract.KEY_DESKTOP_WINDOW_SETTING,
                DESKTOP_WINDOW_SETTING);
        MATCHER.addURI(TopwayDesktopWindowContract.PROVIDER_AUTHORITY,
                TopwayDesktopWindowContract.PROVIDER_PATH + "/"
                        + TopwayDesktopWindowContract.KEY_THEME_DESKTOP_WINDOW,
                THEME_DESKTOP_WINDOW);
    }

    @Override public boolean onCreate() {
        return true;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(new String[] {VALUE_COLUMN});
        switch (MATCHER.match(uri)) {
            case DESKTOP_WINDOW_SETTING:
                // No active cooperative Topway window: zero rows => no WindowInfo.
                break;
            case THEME_DESKTOP_WINDOW:
                cursor.addRow(new Object[] {"false"});
                break;
            default:
                throw new IllegalArgumentException("Unsupported desktop-window URI");
        }
        Context context = getContext();
        if (context != null) cursor.setNotificationUri(context.getContentResolver(), uri);
        return cursor;
    }

    @Override public String getType(Uri uri) {
        return null;
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Topway desktop-window provider is read-only");
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Topway desktop-window provider is read-only");
    }

    @Override public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        throw new UnsupportedOperationException("Topway desktop-window provider is read-only");
    }
}
