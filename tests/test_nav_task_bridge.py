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
WINDOW_PROBE_STUB = '''package com.cbkii.ts18launcher;
public class NavWindowProbe {
 static int reads;
 public static class Failure extends Exception { final String reason="TEST_IO"; }
 public static String read(ProcessBuilder p,long timeout)throws Failure {
  String s=System.getenv("SCENARIO"); reads++;
  String owner="recent-focus-overlay".equals(s)?"com.android.permissioncontroller/.GrantPermissionsActivity":"com.cbkii.ts18launcher/com.cbkii.ts18launcher.HomeAlias";
  String token="recent-focus-race".equals(s)&&reads%2==0?"changed":"home";
  return "mCurrentFocus=Window{"+token+" u0 "+owner+"}";
 }
}'''

STUBS = {
    "android/hardware/display/DisplayManagerGlobal.java": "package android.hardware.display;public class DisplayManagerGlobal {public static DisplayManagerGlobal getInstance(){return new DisplayManagerGlobal();}public Info getDisplayInfo(int display){return new Info();}public static class Info {public int logicalWidth=1280,logicalHeight=720;}}",
    "android/os/Bundle.java": "package android.os; public class Bundle { public int mode=-1, display=-1; }",
    "android/content/Intent.java": "package android.content; public class Intent { final ComponentName c; public Intent(String s){c=ComponentName.unflattenFromString(s);} public ComponentName getComponent(){return c;} }",
    "android/app/ActivityOptions.java": "package android.app; public class ActivityOptions { int mode=-1, display=-1; public static ActivityOptions makeBasic(){return new ActivityOptions();} public void setLaunchDisplayId(int d){display=d;} public void setLaunchWindowingMode(int m){mode=m;} public android.os.Bundle toBundle(){android.os.Bundle b=new android.os.Bundle();b.mode=mode;b.display=display;return b;} }",
    "android/app/Slice.java": "package android.app; public class Slice { final java.util.List<RecentTaskInfo> l; public Slice(java.util.List<RecentTaskInfo> l){this.l=l;} public java.util.List<RecentTaskInfo> getList(){return l;} }",
    "android/app/RecentTaskInfo.java": "package android.app; public class RecentTaskInfo { public int taskId=42, userId=0, stackId=-1, numActivities=0; public boolean isRunning=false; public android.content.Intent baseIntent=new android.content.Intent(\"app.organicmaps.incar/app.organicmaps.DownloadResourcesActivity\"); }",
    "android/util/Log.java": "package android.util; public class Log {public static int i(String t,String s){return 0;}}",
    "android/annotation/SuppressLint.java": "package android.annotation; public @interface SuppressLint { String[] value(); }",
    "android/os/Build.java": "package android.os; public class Build { public static class VERSION { public static int SDK_INT=29; }}",
    "android/os/SystemClock.java": "package android.os; public class SystemClock {public static long elapsedRealtime(){return 1000;}}",
    "android/os/Process.java": "package android.os; public class Process { public static int myUid(){return 0;} }",
    "android/graphics/Rect.java": "package android.graphics; public class Rect { public int left=0,top=141,right=1131,bottom=702; public Rect(){} public Rect(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;} public boolean isEmpty(){return left>=right||top>=bottom;} }",
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
public interface IActivityTaskManager { StackInfo getFocusedStackInfo(); List<StackInfo> getAllStackInfos(); Slice getRecentTasks(int max,int flags,int user); int startActivityFromRecents(int task,android.os.Bundle options); void setTaskWindowingMode(int id,int mode,boolean toTop); void resizeTask(int id,android.graphics.Rect bounds,int mode); void resizeStack(int id,android.graphics.Rect bounds,boolean dock,boolean preserve,boolean animate,int duration); }
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
import java.util.*; import android.content.ComponentName;
public class ActivityTaskManager {
 public static IActivityTaskManager getService(){return new Service();}
 public static class Service implements IActivityTaskManager {
  final StackInfo s=new StackInfo(); final String scenario=System.getenv("SCENARIO");
  Service(){
   if("fullscreen".equals(scenario))s.configuration.windowConfiguration.mode=1;
   if("shared".equals(scenario)){s.taskIds=new int[]{42,99};s.taskNames=new String[]{s.taskNames[0],"other.package/.Activity"};s.taskUserIds=new int[]{0,0};}
   if("wrong-user".equals(scenario))s.taskUserIds[0]=10;
   if("foreign-top".equals(scenario))s.topActivity=android.content.ComponentName.unflattenFromString("com.android.permissioncontroller/.GrantPermissionsActivity");
   if("no-top".equals(scenario))s.topActivity=null;
   if("unknown-name".equals(scenario))s.taskNames[0]="unreadable";
  }
  boolean resumed; int stackReads;
  StackInfo home() {
   StackInfo h=new StackInfo(); h.taskIds=new int[]{7}; h.taskUserIds=new int[]{0};
   h.taskNames=new String[]{"com.cbkii.ts18launcher/com.cbkii.ts18launcher.HomeAlias"};
   h.topActivity=ComponentName.unflattenFromString(h.taskNames[0]);
   h.stackId=2; h.configuration.windowConfiguration.mode=1;return h;
  }
  public StackInfo getFocusedStackInfo() {
   StackInfo h=home();
   if("recent-focus-foreign".equals(scenario)){
    h.taskIds[0]=99;h.taskNames[0]="other.package/.Activity";
    h.topActivity=ComponentName.unflattenFromString(h.taskNames[0]);
   }
   return h;
  }
  public Slice getRecentTasks(int max,int flags,int user){
   if(flags!=2)throw new AssertionError("unavailable recents requested");
   List<RecentTaskInfo> list=new ArrayList<>();
   RecentTaskInfo retained=new RecentTaskInfo();list.add(retained);
   if("recent-live".equals(scenario)){retained.stackId=8;retained.numActivities=1;retained.isRunning=true;}
   if("recent-two".equals(scenario)){RecentTaskInfo older=new RecentTaskInfo();older.taskId=41;list.add(older);}
   if("recent-other".equals(scenario)){retained.baseIntent=new android.content.Intent("other.package/.Main");}
   if("recent-user".equals(scenario))retained.userId=10;
   return new Slice(list);
  }
  public int startActivityFromRecents(int task,android.os.Bundle options){
   System.out.println("RECENT_START task="+task+" mode="+options.mode+" display="+options.display);
   if("recent-refused".equals(scenario))return -91;
   if("recent-missing".equals(scenario))throw new IllegalArgumentException("Task "+task+" not found.");
   resumed=true;s.configuration.windowConfiguration.mode=options.mode;return 0;
  }
  public List<StackInfo> getAllStackInfos(){
   stackReads++;
   if("denied".equals(scenario))throw new SecurityException("permission denied");
   if("absent".equals(scenario))return Collections.emptyList();
   if("recent-appeared".equals(scenario)&&stackReads>=2)return Arrays.asList(s,home());
   if(scenario!=null&&scenario.startsWith("recent")&&!resumed&&!"recent-live".equals(scenario))return Collections.singletonList(home());
   if("ambiguous".equals(scenario)){StackInfo other=new StackInfo();other.taskIds[0]=43;return Arrays.asList(s,other);}
   return Arrays.asList(s);
  }
  public void resizeTask(int id,android.graphics.Rect bounds,int mode){if(bounds!=null)throw new AssertionError("non-null task override");}
  public void resizeStack(int id,android.graphics.Rect bounds,boolean dock,boolean preserve,boolean animate,int duration){
   if(bounds!=null||preserve||animate||dock)throw new AssertionError("unsafe resize");
   System.out.println("CLEAR stack="+id+" null=true");
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
        stubs = dict(STUBS)
        # Keep this IO double local: the broker harness imports STUBS and runs the real probe.
        stubs["com/cbkii/ts18launcher/NavWindowProbe.java"] = WINDOW_PROBE_STUB
        for name, text in stubs.items():
            file = cls.work / name
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(text)
            files.append(str(file))
        # Use the JDK compiler module: also works where javac's wrapper is not installed.
        compiled = subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-d", str(cls.work),
                        *files, str(SOURCE), str(SOURCE.with_name("StartupMaskFocus.java")), str(SOURCE.with_name("NavCompactConsent.java")), str(SOURCE.with_name("NavigationTaskStage.java"))], capture_output=True, text=True)
        if compiled.returncode:
            raise AssertionError(compiled.stdout + compiled.stderr)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def run_bridge(self, action="status", hint="42", scenario="normal", mode="1", to_top="0"):
        args = [action, "0", "app.organicmaps.incar", hint]
        if action == "reacquire-recent": args += [mode]
        elif action not in ("status", "recent-task"): args += [mode, to_top]
        return subprocess.run(["java", "-cp", str(self.work),
                               "com.cbkii.ts18launcher.NavTaskBridge", *args],
                              env={**os.environ, "SCENARIO": scenario},
                              capture_output=True, text=True, timeout=5)

    def test_clear_fullscreen_bounds_uses_null_on_exclusive_stack(self):
        result = self.run_bridge("clear-fullscreen-bounds", scenario="fullscreen")
        self.assertEqual(0, result.returncode, result.stdout)
        self.assertIn("CLEAR stack=8 null=true", result.stdout)
        self.assertNotIn("CALL task=", result.stdout)

    def test_clear_fullscreen_bounds_refuses_freeform_shared_and_permission_flow(self):
        for scenario in ("normal", "shared", "foreign-top", "no-top"):
            result = self.run_bridge("clear-fullscreen-bounds", scenario=scenario)
            self.assertNotEqual(0, result.returncode, result.stdout)
            self.assertNotIn("CLEAR stack=", result.stdout)

    def test_base_and_top_activity_difference_keeps_same_task(self):
        result = self.run_bridge()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("FOUND 42 8 0 5", result.stdout)

    def test_missing_top_does_not_report_base_as_map_readiness(self):
        result = self.run_bridge(scenario="no-top")
        self.assertIn("FOUND 42 8 0 5", result.stdout)
        self.assertIn(" unknown", result.stdout)
        self.assertNotIn("app.organicmaps.MwmActivity", result.stdout)

    def test_unobserved_top_cannot_change_mode(self):
        result = self.run_bridge("mode", scenario="no-top")
        self.assertIn("TOP_ACTIVITY_UNOBSERVED", result.stdout)
        self.assertNotIn("CALL", result.stdout)

    def test_shared_stack_does_not_assign_stack_top_to_each_task(self):
        result = self.run_bridge(scenario="shared")
        self.assertIn("FOUND 42 8 0 5", result.stdout)
        self.assertIn(" unknown", result.stdout)

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

    # Run 2: Back/finish emptied task 9235 while its process lived; Recents resumed the same ID.

    def test_reports_unique_retained_recent_without_starting(self):
        result = self.run_bridge("recent-task", hint="0", scenario="recent")
        self.assertEqual("RECENT 42", result.stdout.strip())

    def test_resumes_exact_named_task_and_reads_back_same_identity(self):
        result = self.run_bridge("reacquire-recent", hint="42", scenario="recent", mode="1")
        self.assertEqual(0, result.returncode, result.stdout)
        self.assertIn("RECENT_START task=42 mode=1 display=0", result.stdout)
        self.assertIn("FOUND 42 8 0 1", result.stdout)

    def test_live_package_task_is_reported_not_restarted(self):
        result = self.run_bridge("reacquire-recent", hint="42", scenario="normal")
        self.assertIn("FOUND 42", result.stdout)
        self.assertNotIn("RECENT_START", result.stdout)

    def test_running_or_unaccounted_recent_is_contrary_evidence(self):
        result = self.run_bridge("recent-task", hint="0", scenario="recent-live")
        # recent-live keeps a live stack too: the live task is reported, never a restart.
        self.assertNotIn("RECENT ", result.stdout)
        self.assertNotIn("RECENT_START", result.stdout)

    def test_ambiguous_retained_tasks_need_an_exact_claimed_identity(self):
        self.assertIn("TASK_AMBIGUOUS", self.run_bridge("recent-task", hint="0", scenario="recent-two").stdout)
        self.assertEqual("RECENT 41", self.run_bridge("recent-task", hint="41", scenario="recent-two").stdout.strip())

    def test_other_package_or_user_is_not_reacquired(self):
        for scenario in ("recent-other", "recent-user"):
            result = self.run_bridge("reacquire-recent", hint="42", scenario=scenario)
            self.assertEqual("NONE", result.stdout.strip(), scenario)

    def test_reacquire_requires_named_task(self):
        result = self.run_bridge("reacquire-recent", hint="0", scenario="recent")
        self.assertIn("TASK_AUTHORITY_REQUIRED", result.stdout)
        self.assertNotIn("RECENT_START", result.stdout)

    def test_framework_refusal_is_distinct_from_uncertainty(self):
        for scenario in ("recent-refused", "recent-missing"):
            result = self.run_bridge("reacquire-recent", hint="42", scenario=scenario)
            self.assertEqual(5, result.returncode, result.stdout)
            self.assertIn("REFUSED", result.stdout)

    def test_retained_resume_refuses_foreign_permission_or_changed_window_focus(self):
        for scenario in ("recent-focus-foreign", "recent-focus-overlay", "recent-focus-race"):
            result = self.run_bridge("reacquire-recent", hint="42", scenario=scenario)
            self.assertEqual(5, result.returncode, result.stdout)
            self.assertIn("REFUSED FOREGROUND_NOT_VERIFIED", result.stdout)
            self.assertNotIn("RECENT_START", result.stdout)

    def test_task_appearing_during_recents_discovery_is_reused_without_a_start(self):
        result = self.run_bridge("reacquire-recent", hint="42", scenario="recent-appeared")
        self.assertEqual(0, result.returncode, result.stdout)
        self.assertIn("FOUND 42", result.stdout)
        self.assertNotIn("RECENT_START", result.stdout)
