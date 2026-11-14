"""Run the production configuration transaction against failing SharedPreferences storage."""
from pathlib import Path
import subprocess
import tempfile
import unittest
from test_navigation_controller_recovery import has_java_compiler

ROOT = Path(__file__).resolve().parents[1]

@unittest.skipUnless(has_java_compiler(), "JDK required")
class ConfigurationRollbackTest(unittest.TestCase):
    def test_failed_import_restores_values_and_device_consent_together(self):
        files = {
            "android/content/Context.java": "package android.content; public class Context {}",
            "android/content/SharedPreferences.java": '''package android.content;
import java.util.*;
public class SharedPreferences {
 public final Map<String,Object> values=new LinkedHashMap<>();
 public final List<Map<String,Object>> commits=new ArrayList<>();public boolean failNext;
 public boolean contains(String k){return values.containsKey(k);}
 public boolean getBoolean(String k,boolean d){return (Boolean)values.getOrDefault(k,d);}
 public int getInt(String k,int d){return (Integer)values.getOrDefault(k,d);}
 public String getString(String k,String d){return (String)values.getOrDefault(k,d);}
 public Editor edit(){return new Editor();}
 public class Editor {
  Map<String,Object> next=new LinkedHashMap<>(values);
  public Editor remove(String k){next.remove(k);return this;}
  public Editor putString(String k,String v){next.put(k,v);return this;}
  public Editor putBoolean(String k,boolean v){next.put(k,v);return this;}
  public Editor putInt(String k,int v){next.put(k,v);return this;}
  public boolean commit(){values.clear();values.putAll(next);commits.add(new LinkedHashMap<>(values));boolean ok=!failNext;failNext=false;return ok;}
 }
}''',
            "Doubles.java": '''package com.cbkii.ts18launcher;
class LauncherPrefs {
static final android.content.SharedPreferences data=new android.content.SharedPreferences();
static String KEY_APPEARANCE_DAY_START="day",AUTO_SOURCE_SCHEDULE="schedule";
static android.content.SharedPreferences prefs(android.content.Context c){return data;}
static int appearanceDayStartMinutes(android.content.Context c){return 360;}
static int appearanceNightStartMinutes(android.content.Context c){return 1200;}
static String appearanceMode(android.content.Context c){return "day";}}
class HomeNavigationSurfacePolicy {static String mode(android.content.Context c){return "native";}}
class ConfigurationCodec {
enum Type {BOOLEAN,COUNT,MINUTES,APPEARANCE,AUTO_SOURCE,NAV_SURFACE,TEXT}
static java.util.Map<String,Type> KEYS=java.util.Map.of("music",Type.TEXT,"warmup",Type.BOOLEAN);
static String[] DEVICE_CONSENT_KEYS={"root-consent","grant-applied","absent-consent"};
static String[] RETIRED_BOOLEAN_KEYS={"retired"};}
class MediaEventTrace {static void record(String... args){}}
''',
            "Harness.java": '''package com.cbkii.ts18launcher;
public class Harness {static void check(boolean b){if(!b)throw new AssertionError();}
public static void main(String[] args){
 var c=new android.content.Context();var p=LauncherPrefs.data;
 p.values.put("music","original.player");p.values.put("warmup",false);p.values.put("root-consent",true);p.values.put("grant-applied",false);
 var before=new java.util.LinkedHashMap<>(p.values);p.failNext=true;
 check(!ConfigurationStore.replace(c,java.util.Map.of("music","replacement.player")));
 check(p.values.equals(before));check(p.commits.size()==2);
 check(p.commits.get(1).equals(before));check(!p.contains("absent-consent"));
 check(ConfigurationStore.replace(c,java.util.Map.of("music","replacement.player")));
 check(!p.contains("root-consent")&&!p.contains("grant-applied"));check(p.getString("music","").equals("replacement.player"));
 System.out.println("PASS failed import restores values and device consent atomically");}}
''',
        }
        files["ConfigurationStore.java"] = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/ConfigurationStore.java").read_text()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name, source in files.items():
                p = root / name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(source)
            subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-d", str(root), *map(str, root.rglob("*.java"))], check=True, timeout=30)
            subprocess.run(["java", "-cp", str(root), "com.cbkii.ts18launcher.Harness"], check=True, timeout=10)
