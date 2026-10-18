package com.cbkii.ts18launcher;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

/** Independent InCar signer from Organic Maps TESTING publish run 38863163942. */
final class CompactStartupPeer {
    static final String PACKAGE = "app.organicmaps.incar";
    private static final byte[] CERT = hex("40ad32351bf55902c617df2351a5761bb5e5462cc2f2ed268503082331139780");
    static boolean trusted(Context context, int uid) {
        if (Build.VERSION.SDK_INT < 28 || uid / 100000 != android.os.Process.myUid() / 100000) return false;
        try {
            PackageManager pm = context.getPackageManager();
            String[] packages = pm.getPackagesForUid(uid);
            PackageInfo info = pm.getPackageInfo(PACKAGE, 0);
            return packages != null && packages.length == 1 && PACKAGE.equals(packages[0])
                    && info.applicationInfo != null && info.applicationInfo.uid == uid
                    && pm.hasSigningCertificate(PACKAGE, CERT, PackageManager.CERT_INPUT_SHA256);
        } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) { return false; }
    }
    private static byte[] hex(String text) {
        byte[] bytes = new byte[text.length() / 2];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
        return bytes;
    }
}
