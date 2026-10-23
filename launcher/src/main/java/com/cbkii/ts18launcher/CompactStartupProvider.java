package com.cbkii.ts18launcher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;

/** Read-only authenticated capability; does not launch, resize, configure or acquire root. */
public final class CompactStartupProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        android.content.Context context = getContext();
        if (context == null || !CompactStartupPeer.trusted(context, Binder.getCallingUid()))
            throw new SecurityException("Untrusted compact startup peer");
        int boot = android.provider.Settings.Global.getInt(context.getContentResolver(), android.provider.Settings.Global.BOOT_COUNT, -1);
        if (!"/status".equals(uri.getPath()) || !"1".equals(uri.getQueryParameter("v"))
                || boot < 0 || !Integer.toString(boot).equals(uri.getQueryParameter("boot")))
            throw new IllegalArgumentException("Unsupported compact startup protocol");
        boolean available = android.os.Build.VERSION.SDK_INT == 29 && HomeMode.isDefaultHome(context)
                && HomeNavigationSurfacePolicy.NATIVE_WINDOW.equals(HomeNavigationSurfacePolicy.mode(context));
        String configured = LauncherPrefs.packageFor(context, LauncherPrefs.KEY_NAV);
        available &= (configured.isEmpty() || CompactStartupPeer.PACKAGE.equals(configured))
                && NavigationProvider.hasLauncherActivity(context, CompactStartupPeer.PACKAGE);
        MatrixCursor cursor = new MatrixCursor(new String[] {"protocol", "available", "root_qualified", "boot", "user"});
        cursor.addRow(new Object[] {1, available ? 1 : 0, 0, boot, android.os.Process.myUid() / 100000});
        return cursor;
    }
    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/compact-startup"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String where, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String where, String[] args) { throw new UnsupportedOperationException(); }
}
