package com.cbkii.ts18launcher.oem;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.os.Process;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Read-only description of what this launcher process can establish about Topway services and
 * radio strategies. Facts come from PackageManager and this process's own identity; nothing is
 * bound, started or written while reading them. classify is pure.
 */
public final class DeviceCapabilities {
    private DeviceCapabilities() {}

    public static final String NAVRADIO_PACKAGE = "com.navimods.radio";
    public static final String TOPWAY_RADIO_PACKAGE = "com.tw.radio";
    static final long MAX_HASHED_APK_BYTES = 64L * 1024 * 1024;

    /** Everything classification needs, gathered once in the caller's own context. */
    public static final class Facts {
        public int uid;
        public int userId;
        public String selinux = "";
        public String callerPackage = "";
        public boolean xtInstalled;
        public String xtVersion = "";
        public String xtSharedUid = "";
        public String xtSignerSha256 = "";
        /** Empty when the APK could not be read from this process. */
        public String xtApkSha256 = "";
        public String xtApkReadFailure = "";
        public boolean xtServiceDeclared;
        public boolean xtServiceExported;
        public boolean xtServiceEnabled;
        public String xtServicePermission = "";
        public boolean navRadioInstalled;
        public boolean topwayRadioInstalled;
        /** A source observation was asked for but withheld because the binary is not the verified one. */
        public boolean observeWithheld;
    }

    /** The source query is only sent to the exact binary the transaction codes were read from. */
    public static boolean mayObserve(Facts f) {
        return TopwayContract.VERIFIED_APK_SHA256.equals(f.xtApkSha256);
    }

    public static List<Capability> classify(Facts f, TopwayStatusProbe.Result probe) {
        List<Capability> out = new ArrayList<>();
        out.add(new Capability("caller", Capability.Status.AVAILABLE, "process",
                f.callerPackage + " uid=" + f.uid + " user=" + f.userId
                        + (f.selinux.isEmpty() ? " selinux=unreadable" : " selinux=" + f.selinux)));

        Capability identity = identity(f);
        out.add(identity);
        Capability endpoint = endpoint(f, identity);
        out.add(endpoint);
        out.add(source(endpoint, probe, f.observeWithheld));
        out.add(observed("topway.reverse", probe, probe == null ? null : probe.reverse));
        out.add(observed("topway.sleep", probe, probe == null ? null : probe.sleep));

        out.add(f.navRadioInstalled
                ? new Capability("radio.navradio.session", Capability.Status.AVAILABLE, "package",
                        "NavRadio+ installed; controlled through its own MediaSession")
                : new Capability("radio.navradio.session", Capability.Status.UNAVAILABLE, "package", "not installed"));
        out.add(f.topwayRadioInstalled
                ? new Capability("radio.topway.session", Capability.Status.AVAILABLE, "package",
                        "stock radio installed; session-only, never started or bound by the launcher")
                : new Capability("radio.topway.session", Capability.Status.UNAVAILABLE, "package", "not installed"));
        out.add(TopwayNativeRadio.capability());
        out.add(new Capability("illumination", Capability.Status.UNKNOWN, "contract",
                "no verified read-only light signal; appearance uses manual or scheduled day/night"));
        return out;
    }

    private static Capability identity(Facts f) {
        String id = "topway.service.identity";
        if (!f.xtInstalled) return new Capability(id, Capability.Status.UNAVAILABLE, "package", "com.tw.service.xt not installed");
        String who = "version=" + f.xtVersion + " sharedUid=" + (f.xtSharedUid.isEmpty() ? "-" : f.xtSharedUid)
                + " signer=" + (TopwayContract.TOPWAY_SIGNER_SHA256.equals(f.xtSignerSha256) ? "topway" : shortHash(f.xtSignerSha256));
        if (f.xtApkSha256.isEmpty()) {
            return new Capability(id, Capability.Status.UNKNOWN, "apk",
                    who + "; APK not readable here (" + f.xtApkReadFailure + ")");
        }
        if (!TopwayContract.VERIFIED_APK_SHA256.equals(f.xtApkSha256)) {
            return new Capability(id, Capability.Status.UNKNOWN, "apk",
                    who + "; different binary " + shortHash(f.xtApkSha256) + ", contract not verified for it");
        }
        return new Capability(id, Capability.Status.AVAILABLE, "apk", who + "; exact verified binary");
    }

    private static Capability endpoint(Facts f, Capability identity) {
        String id = "topway.service.endpoint";
        if (!f.xtInstalled) return new Capability(id, Capability.Status.UNAVAILABLE, "package", "service package absent");
        if (!f.xtServiceDeclared) return new Capability(id, Capability.Status.UNAVAILABLE, "manifest", "CommandService not declared");
        if (!f.xtServiceEnabled) return new Capability(id, Capability.Status.UNAVAILABLE, "manifest", "CommandService disabled");
        if (!f.xtServiceExported) return new Capability(id, Capability.Status.BLOCKED, "manifest", "CommandService not exported");
        if (!f.xtServicePermission.isEmpty()) {
            return new Capability(id, Capability.Status.BLOCKED, "manifest", "requires " + f.xtServicePermission);
        }
        if (identity.status != Capability.Status.AVAILABLE) {
            return new Capability(id, Capability.Status.UNKNOWN, "identity", "exported, but binary not the verified one");
        }
        return new Capability(id, Capability.Status.UNKNOWN, "manifest",
                "exported without permission; not yet bound from this app");
    }

    private static Capability source(Capability endpoint, TopwayStatusProbe.Result probe, boolean withheld) {
        String id = "topway.source";
        if (endpoint.status == Capability.Status.UNAVAILABLE || endpoint.status == Capability.Status.BLOCKED) {
            return new Capability(id, endpoint.status, "endpoint", endpoint.detail);
        }
        if (probe == null) return new Capability(id, Capability.Status.UNKNOWN, "probe", "not observed");
        if (!probe.observe) {
            if (probe.status == Capability.Status.AVAILABLE && withheld) {
                return new Capability(id, Capability.Status.UNKNOWN, "identity",
                        "service answers; source query withheld for an unverified binary");
            }
            return new Capability(id, probe.status == Capability.Status.AVAILABLE ? Capability.Status.UNKNOWN : probe.status,
                    probe.stage.name().toLowerCase(Locale.ROOT),
                    probe.status == Capability.Status.AVAILABLE ? "service answers; source not requested" : probe.detail);
        }
        if (probe.source != null) {
            TopwayContract.Source kind = TopwayContract.Source.of(probe.source);
            return new Capability(id, Capability.Status.AVAILABLE, "reply",
                    kind.name().toLowerCase(Locale.ROOT) + " (" + probe.source + ")");
        }
        return new Capability(id, probe.status, probe.stage.name().toLowerCase(Locale.ROOT), probe.detail);
    }

    private static Capability observed(String id, TopwayStatusProbe.Result probe, Integer value) {
        if (value != null) return new Capability(id, Capability.Status.AVAILABLE, "callback", "value=" + value);
        return new Capability(id, Capability.Status.UNKNOWN, "callback",
                probe != null && probe.observe ? "not delivered during the probe" : "not observed");
    }

    private static String shortHash(String sha) {
        return sha == null || sha.length() < 12 ? "unknown" : sha.substring(0, 12) + "…";
    }

    /** Reads facts in this process's context. Hashes one APK; call off the main thread. */
    public static Facts read(Context context) {
        Facts f = new Facts();
        f.uid = Process.myUid();
        f.userId = f.uid / 100000;
        f.callerPackage = context.getPackageName();
        f.selinux = readSmall("/proc/self/attr/current");
        PackageManager pm = context.getPackageManager();
        f.navRadioInstalled = installed(pm, NAVRADIO_PACKAGE);
        f.topwayRadioInstalled = installed(pm, TOPWAY_RADIO_PACKAGE);
        try {
            @SuppressWarnings("deprecation")
            PackageInfo info = pm.getPackageInfo(TopwayContract.PACKAGE,
                    PackageManager.GET_SIGNATURES | PackageManager.GET_SERVICES | PackageManager.MATCH_DISABLED_COMPONENTS);
            f.xtInstalled = true;
            f.xtVersion = info.versionName == null ? "" : info.versionName;
            f.xtSharedUid = info.sharedUserId == null ? "" : info.sharedUserId;
            @SuppressWarnings("deprecation")
            Signature[] signatures = info.signatures;
            if (signatures != null && signatures.length == 1) f.xtSignerSha256 = sha256(signatures[0].toByteArray());
            if (info.services != null) for (ServiceInfo service : info.services) {
                if (!TopwayContract.SERVICE.equals(service.name)) continue;
                f.xtServiceDeclared = true;
                f.xtServiceExported = service.exported;
                f.xtServiceEnabled = service.enabled && pm.getComponentEnabledSetting(
                        new ComponentName(TopwayContract.PACKAGE, TopwayContract.SERVICE))
                        != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                f.xtServicePermission = service.permission == null ? "" : service.permission;
            }
            ApplicationInfo app = info.applicationInfo;
            hashApk(f, app == null ? null : app.sourceDir);
        } catch (PackageManager.NameNotFoundException absent) {
            f.xtInstalled = false;
        } catch (RuntimeException unavailable) {
            f.xtInstalled = true;
            f.xtApkReadFailure = "package query failed";
        }
        return f;
    }

    private static void hashApk(Facts f, String path) {
        if (path == null) { f.xtApkReadFailure = "no source path"; return; }
        File apk = new File(path);
        if (apk.length() <= 0 || apk.length() > MAX_HASHED_APK_BYTES) { f.xtApkReadFailure = "size out of bounds"; return; }
        try (InputStream in = new FileInputStream(apk)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            for (int n; (n = in.read(buffer)) > 0; ) digest.update(buffer, 0, n);
            f.xtApkSha256 = hex(digest.digest());
        } catch (Exception unreadable) {
            f.xtApkReadFailure = unreadable.getClass().getSimpleName();
        }
    }

    private static boolean installed(PackageManager pm, String pkg) {
        try {
            pm.getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException absent) {
            return false;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private static String readSmall(String path) {
        try (InputStream in = new FileInputStream(path)) {
            byte[] buffer = new byte[256];
            int n = in.read(buffer);
            if (n <= 0) return "";
            return new String(buffer, 0, n, java.nio.charset.StandardCharsets.US_ASCII).trim().replace("\u0000", "");
        } catch (Exception unreadable) {
            return "";
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception impossible) {
            return "";
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return out.toString();
    }
}
