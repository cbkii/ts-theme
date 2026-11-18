package com.cbkii.ts18launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

/**
 * The cold consent read distinguishes proven absence from storage it could not inspect. Only
 * proven absence may fall back to the documented default; everything else must throw, because a
 * thrown read never authorises an automatic Maps start.
 */
public class NavCompactConsentEvidenceTest {
    private static final int UID = 10123;
    private static final String DATA = "/data/user/0/app.organicmaps.incar";
    private static final String DIR = DATA + "/shared_prefs";
    private static final String FILE = DIR + "/OrganicMapsPrefs.xml";
    private static final int DIR_MODE = 0040771, FILE_MODE = 0100660, LINK_MODE = 0120777;

    /** Scripted root view of the filesystem. */
    private static final class Fs implements NavCompactConsent.Fs {
        final Map<String, NavCompactConsent.Entry> entries = new HashMap<>();
        final Set<String> unreadable = new HashSet<>();
        final Map<String, byte[]> contents = new HashMap<>();
        Runnable onRead;
        @Override public NavCompactConsent.Entry lstat(String path) throws NavCompactConsent.Absent {
            if (unreadable.contains(path)) throw new IllegalStateException("CONSENT_FILE_UNREADABLE");
            NavCompactConsent.Entry entry = entries.get(path);
            if (entry == null) throw new NavCompactConsent.Absent();
            return entry;
        }
        @Override public byte[] read(String path, int limit) {
            if (unreadable.contains(path)) throw new IllegalStateException("CONSENT_FILE_UNREADABLE");
            byte[] bytes = contents.get(path);
            if (onRead != null) onRead.run();
            return bytes;
        }
        void dir(String path, int uid) { entries.put(path, new NavCompactConsent.Entry(uid, DIR_MODE, 4096, 1)); }
        void file(String path, String xml) {
            byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
            contents.put(path, bytes);
            entries.put(path, new NavCompactConsent.Entry(UID, FILE_MODE, bytes.length, 100));
        }
    }

    private Fs fs;

    @Before public void setUp() {
        fs = new Fs();
        fs.dir(DATA, UID);
    }

    private boolean read() throws Exception { return NavCompactConsent.readConsent(fs, DATA, UID); }

    private void expectThrow(String code) {
        try {
            boolean value = read();
            fail("expected " + code + " but read " + value);
        } catch (IllegalStateException expected) {
            assertEquals(code, expected.getMessage());
        } catch (Exception other) {
            fail("unexpected " + other);
        }
    }

    @Test public void savedOffIsOff() throws Exception {
        fs.dir(DIR, UID);
        fs.file(FILE, "<map><boolean name=\"InCarCompactStartup\" value=\"false\" /></map>");
        assertFalse(read());
    }

    @Test public void savedOnIsOn() throws Exception {
        fs.dir(DIR, UID);
        fs.file(FILE, "<map><boolean name=\"InCarCompactStartup\" value=\"true\" /></map>");
        assertTrue(read());
    }

    @Test public void provenAbsentDirectoryIsTheDocumentedDefault() throws Exception {
        assertTrue(read());
    }

    @Test public void provenAbsentFileInReadableDirectoryIsTheDocumentedDefault() throws Exception {
        fs.dir(DIR, UID);
        assertTrue(read());
    }

    @Test public void unreadablePreferencesDirectoryIsNeverUnset() {
        fs.unreadable.add(DIR);
        expectThrow("CONSENT_FILE_UNREADABLE");
    }

    @Test public void unreadablePreferencesFileIsNeverUnset() {
        fs.dir(DIR, UID);
        fs.unreadable.add(FILE);
        expectThrow("CONSENT_FILE_UNREADABLE");
    }

    @Test public void unreadableDataDirectoryIsNeverUnset() {
        fs.unreadable.add(DATA);
        expectThrow("CONSENT_FILE_UNREADABLE");
    }

    @Test public void missingDataDirectoryIsNotEvidenceOfAnything() {
        fs.entries.remove(DATA);
        expectThrow("CONSENT_DATA_ABSENT");
    }

    @Test public void dataDirectoryOfAnotherUidIsANamespaceMismatch() {
        fs.dir(DATA, 10999);
        expectThrow("CONSENT_OWNER_MISMATCH");
    }

    @Test public void preferencesDirectoryOfAnotherUidIsRefused() {
        fs.dir(DIR, 0);
        expectThrow("CONSENT_OWNER_MISMATCH");
    }

    @Test public void fileOfAnotherUidIsRefused() {
        fs.dir(DIR, UID);
        fs.file(FILE, "<map/>");
        fs.entries.put(FILE, new NavCompactConsent.Entry(0, FILE_MODE, 6, 100));
        expectThrow("CONSENT_OWNER_MISMATCH");
    }

    @Test public void symlinkedFileIsRefused() {
        fs.dir(DIR, UID);
        fs.entries.put(FILE, new NavCompactConsent.Entry(UID, LINK_MODE, 20, 100));
        expectThrow("CONSENT_FILE_INVALID");
    }

    @Test public void symlinkedDirectoryIsRefused() {
        fs.entries.put(DIR, new NavCompactConsent.Entry(UID, LINK_MODE, 20, 100));
        expectThrow("CONSENT_OWNER_MISMATCH");
    }

    @Test public void pendingBackupMeansAWriteIsInProgress() {
        fs.dir(DIR, UID);
        fs.file(FILE, "<map/>");
        fs.file(FILE + ".bak", "<map/>");
        expectThrow("CONSENT_FILE_UNSTABLE");
    }

    @Test public void backupWithMissingMainFileIsStillUnstable() {
        fs.dir(DIR, UID);
        fs.file(FILE + ".bak", "<map><boolean name=\"InCarCompactStartup\" value=\"false\" /></map>");
        expectThrow("CONSENT_FILE_UNSTABLE");
    }

    @Test public void unreadableBackupProbeIsNotAbsence() {
        fs.dir(DIR, UID);
        fs.file(FILE, "<map/>");
        fs.unreadable.add(FILE + ".bak");
        expectThrow("CONSENT_FILE_UNREADABLE");
    }

    @Test public void fileChangingDuringTheReadIsRefused() {
        fs.dir(DIR, UID);
        fs.file(FILE, "<map/>");
        fs.onRead = () -> fs.entries.put(FILE, new NavCompactConsent.Entry(UID, FILE_MODE, 6, 101));
        expectThrow("CONSENT_FILE_UNSTABLE");
    }

    @Test public void fileVanishingDuringTheReadIsRefused() {
        fs.dir(DIR, UID);
        fs.file(FILE, "<map/>");
        fs.onRead = () -> fs.entries.remove(FILE);
        expectThrow("CONSENT_FILE_UNSTABLE");
    }

    @Test public void emptyOrOversizedFileIsInvalid() {
        fs.dir(DIR, UID);
        fs.entries.put(FILE, new NavCompactConsent.Entry(UID, FILE_MODE, 0, 100));
        expectThrow("CONSENT_FILE_INVALID");
        fs.entries.put(FILE, new NavCompactConsent.Entry(UID, FILE_MODE, 262145, 100));
        expectThrow("CONSENT_FILE_INVALID");
    }

    @Test public void entityOrMalformedXmlIsInvalid() {
        fs.dir(DIR, UID);
        fs.file(FILE, "<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc\">]><map>&e;</map>");
        expectThrow("CONSENT_XML_INVALID");
    }
}
