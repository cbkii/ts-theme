"""Run the actual engine facade with two legacy owners and the N3 display boundary."""
from pathlib import Path
import subprocess
import tempfile
import unittest
from test_navigation_controller_recovery import has_java_compiler
ROOT=Path(__file__).resolve().parents[1]
@unittest.skipUnless(has_java_compiler(), "JDK required")
class NavigationAdmissionTest(unittest.TestCase):
    def test_second_owner_cannot_release_or_overlap_an_existing_claim(self):
        files={
            "android/content/Context.java": "package android.content;public class Context{public static int MODE_PRIVATE=0;public Context getApplicationContext(){return this;}public SharedPreferences getSharedPreferences(String s,int m){return new SharedPreferences();}}",
            "android/content/SharedPreferences.java": "package android.content;public class SharedPreferences{public boolean getBoolean(String k,boolean d){return true;}public SharedPreferences edit(){return this;}public SharedPreferences putBoolean(String k,boolean v){return this;}public void apply(){}}",
            "com/cbkii/ts18launcher/navigation/n3/N3NavigationBackend.java": """package com.cbkii.ts18launcher.navigation.n3;
public class N3NavigationBackend{public static boolean holding;public N3NavigationBackend(android.content.Context c){}public void shutdown(){}public Snapshot snapshot(){return new Snapshot();}public static class Snapshot{public N3State state=holding?N3State.DISPLAY_CREATING:N3State.DISABLED;}}""",
            "Harness.java": """import com.cbkii.ts18launcher.navigation.NavigationFeatureFacade;
import com.cbkii.ts18launcher.navigation.n3.N3NavigationBackend;
public class Harness{static void check(boolean b){if(!b)throw new AssertionError();}
public static void main(String[] a)throws Exception{var c=new android.content.Context();var facade=NavigationFeatureFacade.get(c);
check(NavigationFeatureFacade.beginLegacyAutomaticLaunch(c));check(!NavigationFeatureFacade.beginLegacyAutomaticLaunch(c));
boolean[] may={true};Thread reader=new Thread(()->may[0]=facade.n3MayAcquireDisplay());reader.start();reader.join();check(!may[0]);
NavigationFeatureFacade.endLegacyAutomaticLaunch(c);check(facade.n3MayAcquireDisplay());
N3NavigationBackend.holding=true;check(!NavigationFeatureFacade.beginLegacyAutomaticLaunch(c));
N3NavigationBackend.holding=false;check(NavigationFeatureFacade.beginLegacyAutomaticLaunch(c));NavigationFeatureFacade.endLegacyAutomaticLaunch(c);
System.out.println("PASS actual facade single-owner admission and cross-thread N3 refusal");}}
""",
        }
        for path in ["launcher/src/main/java/com/cbkii/ts18launcher/navigation/NavigationFeatureFacade.java", "launcher/src/main/java/com/cbkii/ts18launcher/navigation/n3/N3State.java"]:
            files[path.rsplit("/",1)[1]]=(ROOT/path).read_text()
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            for name,source in files.items():
                p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source)
            subprocess.run(["java","-m","jdk.compiler/com.sun.tools.javac.Main","-d",str(root),*map(str,root.rglob("*.java"))],check=True,timeout=30)
            subprocess.run(["java","-cp",str(root),"Harness"],check=True,timeout=10)
