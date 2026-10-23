"""Real subprocess deadlines and structural cold-consent parsing; no mocked probe results."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'launcher/src/main/java/com/cbkii/ts18launcher'


@unittest.skipUnless(shutil.which('java'), 'JDK required')
class AcquisitionIoTests(unittest.TestCase):
    def test_real_process_exit_spawn_timeout_cap_and_preference_contract(self):
        files = {
            'android/annotation/SuppressLint.java': 'package android.annotation;public @interface SuppressLint{String[] value();}',
            'android/content/pm/IPackageManager.java': '''package android.content.pm;
public interface IPackageManager {android.app.AppGlobals.App getApplicationInfo(String p,int flags,int user);}
''',
            'android/app/AppGlobals.java': '''package android.app;
public class AppGlobals {
 public static class Meta {public int protocol=1;public int getInt(String k,int d){return protocol;}}
 public static class App {public int uid=10209;public String dataDir=System.getenv("PREF_ROOT");public Meta metaData=new Meta();}
 public static class PM implements android.content.pm.IPackageManager {public App getApplicationInfo(String p,int f,int u){if(!p.equals("app.organicmaps.incar")||f!=128)throw new AssertionError();return app;}}
 public static App app=new App();public static android.content.pm.IPackageManager getPackageManager(){return new PM();}
}
''',
            'android/app/IActivityManager.java': '''package android.app;public interface IActivityManager {java.util.List<ActivityManager.Entry> getRunningAppProcesses();}''',
            'android/app/ActivityManager.java': '''package android.app;
public class ActivityManager {public static boolean live;public static class Entry {public int uid=10209;}
public static class Service implements IActivityManager {public java.util.List<Entry> getRunningAppProcesses(){return live?java.util.List.of(new Entry()):java.util.List.of();}}
public static IActivityManager getService(){return new Service();}}
''',
            'android/system/Os.java': '''package android.system;public class Os {public static int owner=10209;
public static class Stat {public int st_uid=owner;}public static Stat stat(String path){return new Stat();}}
''',
            'Harness.java': r'''package com.cbkii.ts18launcher;
import java.nio.file.*;import java.util.concurrent.TimeUnit;
public class Harness {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 interface Operation {void run()throws Exception;}
 static void rejects(Operation op)throws Exception {try{op.run();throw new AssertionError("invalid evidence accepted");}catch(AssertionError e){throw e;}catch(Exception expected){}}
 static ProcessBuilder child(String mode) {return new ProcessBuilder(System.getProperty("java.home")+"/bin/java","-cp",System.getProperty("java.class.path"),Harness.class.getName(),mode);}
 static void failure(String mode,String expected,long deadline)throws Exception {
  try{NavWindowProbe.read(child(mode),deadline);throw new AssertionError("probe accepted "+mode);}
  catch(NavWindowProbe.Failure error){check(expected.equals(error.reason),mode+" -> "+error.reason);if(mode.equals("exit")){check(error.getMessage().contains("exit=7"),"exit lost");check(!error.getMessage().contains("private/path"),"private stderr path escaped");}}
 }
 public static void main(String[] args)throws Exception {
  if(args.length>0){switch(args[0]){
   case "ok":System.out.print("mCurrentFocus=Window{abc u0 p/.A}\n");return;
   case "exit":System.err.println("cannot execute /private/path/tool: unsupported");System.exit(7);return;
   case "cap":System.out.print("x".repeat(600000));return;
   case "stderr-cap":System.err.print("x".repeat(10000));return;
   case "hang":Thread.sleep(10000);return;
   default:throw new AssertionError();}}
  check(NavWindowProbe.read(child("ok"),2000).contains("mCurrentFocus="),"output lost");
  failure("exit","PROBE_EXIT",2000);failure("hang","PROBE_TIMEOUT",150);
  failure("cap","PROBE_CAPPED",2000);failure("stderr-cap","PROBE_CAPPED",2000);
  try{NavWindowProbe.read(new ProcessBuilder("/nonexistent/ts18-probe"),2000);throw new AssertionError("spawn accepted");}
  catch(NavWindowProbe.Failure error){check(error.reason.equals("PROBE_SPAWN"),"spawn misclassified");}
  check(NavCompactConsent.parse("<map/>"),"unset InCar default");
  check(!NavCompactConsent.parse("<map><boolean value='false' name='InCarCompactStartup'/></map>"),"OFF ignored");
  check(NavCompactConsent.parse("<map><boolean name='InCarCompactStartup' value='true'/></map>"),"ON lost");
  for(String xml:new String[]{"", "<bad/>","<map><string name='InCarCompactStartup'>false</string></map>",
   "<map><boolean name='InCarCompactStartup' value='yes'/></map>",
   "<map><boolean name='InCarCompactStartup' value='false'/><boolean name='InCarCompactStartup' value='true'/></map>",
   "<!DOCTYPE map SYSTEM 'file:///private/secret'><map/>","<!DOCTYPE map [<!ENTITY x 'true'>]><map/>"}) rejects(()->NavCompactConsent.parse(xml));
  Path root=Path.of(System.getenv("PREF_ROOT"));Files.createDirectories(root.resolve("shared_prefs"));
  check(NavCompactConsent.read(0),"new install default not recognised");
  Path prefs=root.resolve("shared_prefs/OrganicMapsPrefs.xml");Files.writeString(prefs,"<map><boolean name='InCarCompactStartup' value='false'/></map>");
  check(!NavCompactConsent.read(0),"disk OFF not honoured");
  android.app.ActivityManager.live=true;rejects(()->NavCompactConsent.read(0));android.app.ActivityManager.live=false;
  android.system.Os.owner=999;rejects(()->NavCompactConsent.read(0));android.system.Os.owner=10209;
  android.app.AppGlobals.app.metaData.protocol=0;rejects(()->NavCompactConsent.read(0));android.app.AppGlobals.app.metaData.protocol=1;
  rejects(()->NavCompactConsent.read(1));Files.writeString(Path.of(prefs+".bak"),"backup");rejects(()->NavCompactConsent.read(0));Files.delete(Path.of(prefs+".bak"));
  Files.delete(prefs);Files.createSymbolicLink(prefs,Files.writeString(root.resolve("outside.xml"),"<map/>"));rejects(()->NavCompactConsent.read(0));
  System.out.println("PASS production probe real I/O + cold consent default/OFF/live-UID/owner/protocol/user/XML/backup/symlink gates");
 }
}
''',
        }
        for name in ('NavWindowProbe.java', 'NavCompactConsent.java'):
            files[name] = (SRC / name).read_text()
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for name, content in files.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(root),
                            *map(str, root.rglob('*.java'))], check=True, timeout=30)
            import os
            subprocess.run(['java', '-cp', str(root), 'com.cbkii.ts18launcher.Harness'],
                           env={**os.environ, 'PREF_ROOT': str(root / 'maps-data')}, check=True, timeout=20)
