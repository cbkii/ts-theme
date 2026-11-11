package com.cbkii.ts18launcher;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/** Root-side read-only v1 contract advertised by the signed InCar APK. Never renderer evidence. */
final class NavCompactConsent {
    static final String CAPABILITY = "app.organicmaps.incar.COMPACT_CONSENT_PREFS_PROTOCOL";
    private static final String PACKAGE = "app.organicmaps.incar";

    @android.annotation.SuppressLint({"BlockedPrivateApi", "PrivateApi"})
    static boolean read(int user) throws Exception {
        if (user < 0 || user > 999) throw new IllegalStateException("CONSENT_USER_INVALID");
        Class<?> api = Class.forName("android.content.pm.IPackageManager");
        Object pm = Class.forName("android.app.AppGlobals").getMethod("getPackageManager").invoke(null);
        Object app = api.getMethod("getApplicationInfo", String.class, int.class, int.class)
                .invoke(pm, PACKAGE, 128, user);
        if (app == null) throw new IllegalStateException("CONSENT_PEER_UNAVAILABLE");
        int uid = app.getClass().getField("uid").getInt(app);
        Object metadata = app.getClass().getField("metaData").get(app);
        if (uid / 100000 != user || uid % 100000 < 10000 || metadata == null
                || (Integer) metadata.getClass().getMethod("getInt", String.class, int.class)
                    .invoke(metadata, CAPABILITY, 0) != 1)
            throw new IllegalStateException("CONSENT_CAPABILITY_REQUIRED");
        requireCold(uid);
        String path = (String) app.getClass().getField("dataDir").get(app);
        File data = new File(path).getCanonicalFile();
        boolean enabled = readConsent(FRAMEWORK_FS, data.getPath(), uid);
        requireCold(uid); // Do not trust a read made behind a live SharedPreferences writer.
        return enabled;
    }

    /** One lstat result; only the fields the contract needs. */
    static final class Entry {
        final int uid; final int mode; final long size; final long modified;
        Entry(int uid, int mode, long size, long modified) {
            this.uid = uid; this.mode = mode; this.size = size; this.modified = modified;
        }
        boolean directory() { return (mode & S_IFMT) == S_IFDIR; }
        boolean regular() { return (mode & S_IFMT) == S_IFREG; }
        boolean sameAs(Entry other) {
            return other != null && uid == other.uid && mode == other.mode
                    && size == other.size && modified == other.modified;
        }
    }

    /** Proven absence: lstat failed with ENOENT, and only ENOENT. */
    static final class Absent extends Exception {
        Absent() { super("ENOENT"); }
    }

    /** The filesystem as root sees it. Any failure other than ENOENT must throw an unreadable code. */
    interface Fs {
        Entry lstat(String path) throws Absent, IllegalStateException;
        byte[] read(String path, int limit) throws Exception;
    }

    private static final int S_IFMT = 0170000, S_IFDIR = 0040000, S_IFREG = 0100000;
    private static final int LIMIT = 262144;
    private static final String PREFS = "shared_prefs/OrganicMapsPrefs.xml";

    /**
     * Read the consent preference with evidence for every branch.
     *
     * The documented v1 default (unset means ON) applies only when absence is proven: the data
     * directory is readable and owned by the peer, and the preferences directory or file is
     * reported missing with ENOENT. Permission, namespace, locked-storage or I/O failures, a wrong
     * owner, a link, a backup file or a file that changes while being read are never treated as
     * unset; they throw, and a thrown read never authorises an automatic start.
     */
    static boolean readConsent(Fs fs, String dataDir, int uid) throws Exception {
        Entry data = present(fs, dataDir, "CONSENT_DATA_ABSENT");
        if (!data.directory() || data.uid != uid) throw new IllegalStateException("CONSENT_OWNER_MISMATCH");
        String prefsDir = dataDir + "/shared_prefs";
        String file = dataDir + "/" + PREFS;
        Entry dir;
        try {
            dir = fs.lstat(prefsDir);
        } catch (Absent absent) {
            return true; // The app has never written any preference: genuine unset.
        }
        if (!dir.directory() || dir.uid != uid) throw new IllegalStateException("CONSENT_OWNER_MISMATCH");
        requireAbsent(fs, file + ".bak");
        Entry before;
        try {
            before = fs.lstat(file);
        } catch (Absent absent) {
            requireAbsent(fs, file + ".bak");
            return true; // Directory present and readable, file proven missing: genuine unset.
        }
        if (!before.regular()) throw new IllegalStateException("CONSENT_FILE_INVALID");
        if (before.uid != uid) throw new IllegalStateException("CONSENT_OWNER_MISMATCH");
        if (before.size <= 0 || before.size > LIMIT) throw new IllegalStateException("CONSENT_FILE_INVALID");
        byte[] bytes = fs.read(file, LIMIT);
        Entry after = present(fs, file, "CONSENT_FILE_UNSTABLE");
        requireAbsent(fs, file + ".bak");
        if (!before.sameAs(after) || bytes.length != before.size)
            throw new IllegalStateException("CONSENT_FILE_UNSTABLE");
        return parse(new String(bytes, StandardCharsets.UTF_8));
    }

    private static Entry present(Fs fs, String path, String absentCode) {
        try {
            return fs.lstat(path);
        } catch (Absent absent) {
            throw new IllegalStateException(absentCode);
        }
    }

    private static void requireAbsent(Fs fs, String path) {
        try {
            fs.lstat(path);
        } catch (Absent absent) {
            return;
        }
        // A pending SharedPreferences backup means the main file may be mid-replace.
        throw new IllegalStateException("CONSENT_FILE_UNSTABLE");
    }

    /** android.system.Os, reached reflectively from the root helper process. */
    static final Fs FRAMEWORK_FS = new Fs() {
        @Override public Entry lstat(String path) throws Absent {
            Object stat;
            try {
                stat = Class.forName("android.system.Os").getMethod("lstat", String.class).invoke(null, path);
            } catch (java.lang.reflect.InvocationTargetException failed) {
                Throwable cause = failed.getTargetException();
                if (errno(cause) == enoent()) throw new Absent();
                throw new IllegalStateException("CONSENT_FILE_UNREADABLE");
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                throw new IllegalStateException("CONSENT_FILE_UNREADABLE");
            }
            try {
                Class<?> type = stat.getClass();
                return new Entry(type.getField("st_uid").getInt(stat), type.getField("st_mode").getInt(stat),
                        type.getField("st_size").getLong(stat), type.getField("st_mtime").getLong(stat));
            } catch (ReflectiveOperationException unavailable) {
                throw new IllegalStateException("CONSENT_FILE_UNREADABLE");
            }
        }

        @Override public byte[] read(String path, int limit) throws Exception {
            try (java.io.InputStream input = new java.io.FileInputStream(path)) {
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int n;
                while ((n = input.read(buffer)) >= 0) {
                    if (output.size() + n > limit) throw new IllegalStateException("CONSENT_FILE_INVALID");
                    output.write(buffer, 0, n);
                }
                return output.toByteArray();
            } catch (java.io.IOException unreadable) {
                throw new IllegalStateException("CONSENT_FILE_UNREADABLE");
            }
        }
    };

    private static int errno(Throwable error) {
        try {
            if (error != null && "android.system.ErrnoException".equals(error.getClass().getName()))
                return error.getClass().getField("errno").getInt(error);
        } catch (ReflectiveOperationException unavailable) {
            // Not an ErrnoException we can read.
        }
        return Integer.MIN_VALUE;
    }

    private static int enoent() {
        try {
            return Class.forName("android.system.OsConstants").getField("ENOENT").getInt(null);
        } catch (ReflectiveOperationException unavailable) {
            return Integer.MAX_VALUE; // Unknown constant: nothing can be proven absent.
        }
    }

    @android.annotation.SuppressLint({"BlockedPrivateApi", "PrivateApi"})
    private static void requireCold(int uid) throws Exception {
        Object service = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        Object raw = Class.forName("android.app.IActivityManager").getMethod("getRunningAppProcesses").invoke(service);
        if (!(raw instanceof java.util.List)) throw new IllegalStateException("CONSENT_PROCESS_UNKNOWN");
        for (Object process : (java.util.List<?>) raw)
            if (process.getClass().getField("uid").getInt(process) == uid)
                throw new IllegalStateException("CONSENT_PEER_RUNNING");
    }

    static boolean parse(String xml) throws Exception {
        if (xml.length() > 262144 || xml.contains("<!DOCTYPE") || xml.contains("<!ENTITY"))
            throw new IllegalStateException("CONSENT_XML_INVALID");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setExpandEntityReferences(false);
        javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
            @Override public void error(org.xml.sax.SAXParseException error) throws org.xml.sax.SAXException {
                throw new org.xml.sax.SAXException("CONSENT_XML_INVALID");
            }
            @Override public void fatalError(org.xml.sax.SAXParseException error) throws org.xml.sax.SAXException {
                throw new org.xml.sax.SAXException("CONSENT_XML_INVALID");
            }
        });
        builder.setEntityResolver((publicId, systemId) -> { throw new org.xml.sax.SAXException("external entity denied"); });
        Element map = builder.parse(new InputSource(new StringReader(xml))).getDocumentElement();
        if (!"map".equals(map.getTagName())) throw new IllegalStateException("CONSENT_XML_INVALID");
        Boolean enabled = null;
        for (Node node = map.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (!(node instanceof Element)) continue;
            Element entry = (Element) node;
            if (!"InCarCompactStartup".equals(entry.getAttribute("name"))) continue;
            String value = entry.getAttribute("value");
            if (enabled != null || !"boolean".equals(entry.getTagName())
                    || !("true".equals(value) || "false".equals(value)))
                throw new IllegalStateException("CONSENT_XML_INVALID");
            enabled = Boolean.valueOf(value);
        }
        return enabled == null || enabled;
    }
}
