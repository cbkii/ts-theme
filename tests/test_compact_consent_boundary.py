"""Run the production cold-consent client through identity/update/result boundaries."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SRC = Path(__file__).resolve().parents[1] / 'launcher/src/main/java/com/cbkii/ts18launcher'


@unittest.skipUnless(shutil.which('java'), 'JDK required')
class ColdConsentBoundaryTests(unittest.TestCase):
    def test_installed_identity_version_and_typed_result_are_rechecked(self):
        files = {
            'android/content/Context.java': 'package android.content;public class Context{public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();}}',
            'android/content/pm/ApplicationInfo.java': '''package android.content.pm;public class ApplicationInfo {
public boolean enabled=true;public int uid=10209;public String sourceDir="/data/app/maps/base.apk";public Meta metaData=new Meta();
public static class Meta{public int version=1;public int getInt(String k,int d){return version;}}}''',
            'android/content/pm/PackageInfo.java': 'package android.content.pm;public class PackageInfo{public long lastUpdateTime=PackageManager.updated;}',
            'android/content/pm/PackageManager.java': '''package android.content.pm;public class PackageManager{
public static final int GET_META_DATA=128;public static long updated=1;public static ApplicationInfo app=new ApplicationInfo();
public static class NameNotFoundException extends Exception{}public ApplicationInfo getApplicationInfo(String p,int flags)throws NameNotFoundException{return app;}
public PackageInfo getPackageInfo(String p,int flags)throws NameNotFoundException{return new PackageInfo();}}''',
            'android/os/SystemClock.java': 'package android.os;public class SystemClock{public static long elapsedRealtime(){return 1000;}}',
            'Doubles.java': '''package com.cbkii.ts18launcher;
class NavCompactConsent{static final String CAPABILITY="app.organicmaps.incar.COMPACT_CONSENT_PREFS_PROTOCOL";}
class CompactStartupPeer {static String PACKAGE="app.organicmaps.incar";static boolean valid=true;static boolean trusted(android.content.Context c,int uid){return valid&&uid==10209;}}
class AndroidUserId{static int current(){return 0;}}class MediaEventTrace{static void record(String a,String b,String... c){}}
class RootTaskSession{static String result="OK code=COMPACT_CONSENT_ENABLED user=0 package=app.organicmaps.incar";static int reads;static Runnable during=()->{};
static RootTaskSession get(android.content.Context c){return new RootTaskSession();}NavigationHelperResult runCurrent(long d,String... fields){if(!fields[0].equals("compact-consent")||!fields[1].equals("0")||d!=2200)throw new AssertionError();reads++;during.run();return NavigationHelperResult.parse(result);}}
''',
            'Harness.java': '''package com.cbkii.ts18launcher;
import android.content.pm.PackageManager;
public class Harness {
static android.content.Context context=new android.content.Context();
static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
public static void main(String[] args){
 check(Boolean.TRUE.equals(CompactStartupConsent.read(context)),"valid cold consent rejected");
 RootTaskSession.result="OK code=COMPACT_CONSENT_DISABLED user=0 package=app.organicmaps.incar";
 check(Boolean.FALSE.equals(CompactStartupConsent.read(context)),"saved OFF ignored");
 for(String result:new String[]{"", "FAIL code=PHASE_TIMEOUT", "OK code=COMPACT_CONSENT_ENABLED user=1 package=app.organicmaps.incar",
  "OK code=COMPACT_CONSENT_ENABLED user=0 package=other.app", "OK code=RESUMED_NATIVE user=0 package=app.organicmaps.incar"}){
  RootTaskSession.result=result;check(CompactStartupConsent.read(context)==null,"invalid result admitted");}
 RootTaskSession.result="OK code=COMPACT_CONSENT_ENABLED user=0 package=app.organicmaps.incar";
 int before=RootTaskSession.reads;PackageManager.app.enabled=false;check(CompactStartupConsent.read(context)==null&&RootTaskSession.reads==before,"disabled peer read");PackageManager.app.enabled=true;
 PackageManager.app.metaData.version=0;check(CompactStartupConsent.read(context)==null&&RootTaskSession.reads==before,"old peer read");PackageManager.app.metaData.version=1;
 CompactStartupPeer.valid=false;check(CompactStartupConsent.read(context)==null&&RootTaskSession.reads==before,"untrusted peer read");CompactStartupPeer.valid=true;
 RootTaskSession.during=()->PackageManager.updated++;check(CompactStartupConsent.read(context)==null,"package replacement crossed read");
 RootTaskSession.during=()->CompactStartupPeer.valid=false;check(CompactStartupConsent.read(context)==null,"signer change crossed read");CompactStartupPeer.valid=true;
 RootTaskSession.during=()->PackageManager.app.enabled=false;check(CompactStartupConsent.read(context)==null,"disabled during read accepted");PackageManager.app.enabled=true;
 RootTaskSession.during=()->PackageManager.app.uid=10300;check(CompactStartupConsent.read(context)==null,"UID change crossed read");
 System.out.println("PASS installed capability/signer/UID/update/result and OFF gates");}}
''',
        }
        for name in ('CompactStartupConsent.java', 'NavigationHelperResult.java'):
            files[name] = (SRC / name).read_text()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name, text in files.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text)
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(root),
                            *map(str, root.rglob('*.java'))], check=True, timeout=30)
            subprocess.run(['java', '-cp', str(root), 'com.cbkii.ts18launcher.Harness'], check=True, timeout=10)
