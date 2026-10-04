"""Execute the production reflection bridge against an API-29 contract double.

These are JVM contract tests, not proof of TS18 Binder permissions or window behaviour.
"""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/NavTaskBridge.java"
STUBS = {
    "android/annotation/SuppressLint.java": "package android.annotation; public @interface SuppressLint { String[] value(); }",
    "android/os/Build.java": "package android.os; public class Build { public static class VERSION { public static int SDK_INT=29; }}",
    "android/os/Process.java": "package android.os; public class Process { public static int myUid(){return 0;} }",
    "android/graphics/Rect.java": "package android.graphics; public class Rect { public int left=0,top=141,right=1131,bottom=702; }",
    "android/content/ComponentName.java": '''package android.content;
public class ComponentName {
 private final String text; private ComponentName(String s){text=s;}
 public static ComponentName unflattenFromString(String s){return s.contains("/")?new ComponentName(s):null;}
 public String getPackageName(){return text.split("/")[0];} public String flattenToString(){return text;}
}''',
    "android/content/res/Configuration.java": '''package android.content.res;
public class Configuration { public Window windowConfiguration=new Window();
 public static class Window { public int mode=5; public int getWindowingMode(){return mode;} public int getActivityType(){return 1;} }
}''',
    "android/app/IActivityTaskManager.java": '''package android.app;
import java.util.List;
public interface IActivityTaskManager { List<StackInfo> getAllStackInfos(); void setTaskWindowingMode(int id,int mode,boolean toTop); }
''',
    "android/app/StackInfo.java": '''package android.app;
import android.content.ComponentName; import android.graphics.Rect; import android.content.res.Configuration;
public class StackInfo {
 public int[] taskIds={42}; public String[] taskNames={"app.organicmaps.incar/app.organicmaps.DownloadResourcesActivity"};
 public int[] taskUserIds={0}; public Rect[] taskBounds={new Rect()};
 public int stackId=8, displayId=0; public Configuration configuration=new Configuration();
 public ComponentName topActivity=ComponentName.unflattenFromString("app.organicmaps.incar/app.organicmaps.MwmActivity");
}''',
    "android/app/ActivityTaskManager.java": '''package android.app;
import java.util.*;
public class ActivityTaskManager {
 public static IActivityTaskManager getService(){return new Service();}
 public static class Service implements IActivityTaskManager {
  final StackInfo s=new StackInfo(); final String scenario=System.getenv("SCENARIO");
  Service(){
   if("shared".equals(scenario)){s.taskIds=new int[]{42,99};s.taskNames=new String[]{s.taskNames[0],"other.package/.Activity"};s.taskUserIds=new int[]{0,0};}
   if("wrong-user".equals(scenario))s.taskUserIds[0]=10;
   if("foreign-top".equals(scenario))s.topActivity=android.content.ComponentName.unflattenFromString("com.android.permissioncontroller/.GrantPermissionsActivity");
   if("unknown-name".equals(scenario))s.taskNames[0]="unreadable";
  }
  public List<StackInfo> getAllStackInfos(){
   if("denied".equals(scenario))throw new SecurityException("permission denied");
   if("absent".equals(scenario))return Collections.emptyList();
   if("ambiguous".equals(scenario)){StackInfo other=new StackInfo();other.taskIds[0]=43;return Arrays.asList(s,other);}
   return Arrays.asList(s);
  }
  public void setTaskWindowingMode(int id,int mode,boolean toTop){
   System.out.println("CALL task="+id+" mode="+mode+" toTop="+toTop);
   s.configuration.windowConfiguration.mode=mode;
  }
 }
}''',
}


class NavTaskBridgeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if shutil.which("java") is None:
            raise unittest.SkipTest("Java unavailable: bridge JVM checks NOT_RUN")
        cls.temp = tempfile.TemporaryDirectory()
        cls.work = Path(cls.temp.name)
        files = []
        for name, text in STUBS.items():
            file = cls.work / name
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(text)
            files.append(str(file))
        # Use the JDK compiler module: also works where javac's wrapper is not installed.
        subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-d", str(cls.work),
                        *files, str(SOURCE)], check=True, capture_output=True, text=True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def run_bridge(self, action="status", hint="42", scenario="normal", mode="1", to_top="0"):
        args = [action, "0", "app.organicmaps.incar", hint]
        if action != "status": args += [mode, to_top]
        return subprocess.run(["java", "-cp", str(self.work),
                               "com.cbkii.ts18launcher.NavTaskBridge", *args],
                              env={**os.environ, "SCENARIO": scenario},
                              capture_output=True, text=True, timeout=5)

    def test_base_and_top_activity_difference_keeps_same_task(self):
        result = self.run_bridge()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("FOUND 42 8 0 5", result.stdout)

    def test_background_mode_never_brings_task_to_top(self):
        result = self.run_bridge("mode")
        self.assertEqual(0, result.returncode, result.stdout)
        self.assertIn("CALL task=42 mode=1 toTop=false", result.stdout)
        self.assertIn("FOUND 42 8 0 1", result.stdout)

    def test_capability_probe_reapplies_observed_mode_without_focus(self):
        result = self.run_bridge("probe-mode")
        self.assertIn("CALL task=42 mode=5 toTop=false", result.stdout)

    def test_shared_stack_cannot_mutate_other_tasks(self):
        result = self.run_bridge("mode", scenario="shared")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("TASK_STACK_NOT_EXCLUSIVE", result.stdout)
        self.assertNotIn("CALL", result.stdout)

    def test_permission_flow_does_not_change_mode(self):
        result = self.run_bridge("mode", scenario="foreign-top")
        self.assertIn("LEGITIMATE_FOREIGN_ACTIVITY", result.stdout)
        self.assertNotIn("CALL", result.stdout)

    def test_permission_denied_is_unknown_not_absence(self):
        result = self.run_bridge(scenario="denied")
        self.assertIn("UNKNOWN SecurityException", result.stdout)
        self.assertNotIn("NONE", result.stdout)

    def test_wrong_user_does_not_acquire_task(self):
        self.assertEqual("NONE", self.run_bridge(scenario="wrong-user").stdout.strip())

    def test_unreadable_task_identity_is_unknown(self):
        result = self.run_bridge(scenario="unknown-name")
        self.assertIn("TASK_NAME_UNREADABLE", result.stdout)
        self.assertNotIn("NONE", result.stdout)

    def test_true_absence(self):
        self.assertEqual("NONE", self.run_bridge(scenario="absent").stdout.strip())

    def test_multiple_tasks_require_explicit_identity(self):
        result = self.run_bridge(hint="0", scenario="ambiguous")
        self.assertIn("TASK_AMBIGUOUS", result.stdout)
        self.assertNotIn("CALL", result.stdout)
