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
        requireOwner(data, uid);
        File prefs = new File(data, "shared_prefs/OrganicMapsPrefs.xml");
        if (!prefs.getCanonicalPath().equals(data + "/shared_prefs/OrganicMapsPrefs.xml")
                || new File(prefs + ".bak").exists())
            throw new IllegalStateException("CONSENT_FILE_UNSTABLE");
        long size = prefs.length(), modified = prefs.lastModified();
        boolean exists = prefs.exists();
        boolean enabled = true; // Explicit v1 InCar contract: unset preference defaults ON.
        if (exists) {
            requireOwner(prefs, uid);
            if (!prefs.isFile() || size <= 0 || size > 262144L)
                throw new IllegalStateException("CONSENT_FILE_INVALID");
            byte[] bytes;
            try (java.io.InputStream input = new java.io.FileInputStream(prefs)) {
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096]; int n;
                while ((n = input.read(buffer)) >= 0) {
                    if (output.size() + n > 262144) throw new IllegalStateException("CONSENT_FILE_INVALID");
                    output.write(buffer, 0, n);
                }
                bytes = output.toByteArray();
            }
            enabled = parse(new String(bytes, StandardCharsets.UTF_8));
        }
        requireCold(uid); // Do not read behind a live SharedPreferences writer/cache.
        if (exists != prefs.exists() || size != prefs.length() || modified != prefs.lastModified()
                || new File(prefs + ".bak").exists()) throw new IllegalStateException("CONSENT_FILE_UNSTABLE");
        return enabled;
    }

    private static void requireOwner(File file, int uid) throws Exception {
        Object stat = Class.forName("android.system.Os").getMethod("stat", String.class)
                .invoke(null, file.getPath());
        if (stat.getClass().getField("st_uid").getInt(stat) != uid)
            throw new IllegalStateException("CONSENT_OWNER_MISMATCH");
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
