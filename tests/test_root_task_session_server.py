"""Execute the production root broker; substitute only Android/Binder and window-probe I/O.

This exercises IPC, epochs, same-task mode/bounds/focus, silence of foreign/protected
paths and EOF cleanup. It is not physical TS18 or Binder-permission qualification.
"""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import unittest

from test_nav_task_bridge import STUBS, SOURCE


class RootBrokerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not shutil.which("java"):
            raise unittest.SkipTest("JDK unavailable")
        cls.temp = tempfile.TemporaryDirectory()
        cls.root = Path(cls.temp.name)
        stubs = dict(STUBS)
        stubs["android/graphics/Rect.java"] = """package android.graphics;
public class Rect {public int left,top,right,bottom;public Rect(){}public Rect(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
public boolean isEmpty(){return left>=right||top>=bottom;}public boolean equals(Object o){return o instanceof Rect r&&left==r.left&&top==r.top&&right==r.right&&bottom==r.bottom;}}
"""
        stubs["android/content/res/Configuration.java"] = """package android.content.res;
public class Configuration {public Window windowConfiguration=new Window();public static class Window {public int mode=1,type=1;public int getWindowingMode(){return mode;}public int getActivityType(){return type;}}}
"""
        stubs["android/app/IActivityTaskManager.java"] = """package android.app;
public interface IActivityTaskManager {java.util.List<StackInfo> getAllStackInfos();StackInfo getFocusedStackInfo();void setFocusedTask(int id);
void setTaskWindowingMode(int id,int mode,boolean top);void resizeTask(int id,android.graphics.Rect bounds,int mode);
void resizeStack(int id,android.graphics.Rect b,boolean d,boolean p,boolean a,int duration);}
"""
        stubs["android/app/ActivityTaskManager.java"] = """package android.app;
import java.util.*;import java.nio.file.*;import android.content.ComponentName;import android.graphics.Rect;
public class ActivityTaskManager {static final Service SERVICE=new Service();public static IActivityTaskManager getService(){return SERVICE;}
public static class Service implements IActivityTaskManager {
final StackInfo map=new StackInfo(),home=new StackInfo(),music=new StackInfo(),foreign=new StackInfo();int focused=7;
Service(){home.taskIds=new int[]{7};home.taskNames=new String[]{"com.cbkii.ts18launcher/com.cbkii.ts18launcher.HomeAlias"};home.stackId=0;
home.topActivity=ComponentName.unflattenFromString("com.cbkii.ts18launcher/com.cbkii.ts18launcher.LauncherActivity");home.configuration.windowConfiguration.type=2;
music.taskIds=new int[]{84};music.taskNames=new String[]{"com.tw.media/com.tw.music.MusicActivity"};music.stackId=5;
music.topActivity=ComponentName.unflattenFromString("com.tw.media/org.oxycblt.auxio.MainActivity");
foreign.taskIds=new int[]{99};foreign.taskNames=new String[]{"other.app/.Main"};foreign.stackId=8;foreign.topActivity=ComponentName.unflattenFromString("other.app/.Main");
if("source".equals(System.getenv("CASE")))focused=84;
if("bootstrap".equals(System.getenv("CASE")))focused=42;
if("foreign".equals(System.getenv("CASE")))focused=99;
if("shared".equals(System.getenv("CASE"))){map.taskIds=new int[]{42,43};map.taskUserIds=new int[]{0,0};map.taskNames=new String[]{map.taskNames[0],"other.app/.Main"};}
writeFocus();}
public List<StackInfo> getAllStackInfos(){return Arrays.asList(map,home,music,foreign);}
public StackInfo getFocusedStackInfo(){return focused==7?home:focused==84?music:focused==99?foreign:map;}
void writeFocus(){try{String component=getFocusedStackInfo().topActivity.flattenToString();if("dialog".equals(System.getenv("CASE")))component="com.android.permissioncontroller/.GrantPermissionsActivity";
String mask="TS18Ready_10211_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
String dump="mCurrentFocus=Window{abc u0 "+component+"}\\n";
if("mask".equals(System.getenv("CASE")))dump="  Window #0 Window{abc u0 "+mask+"}:\\n    mOwnerUid=10211 showForAllUsers=false\\n    mAttrs={ ty=APPLICATION_OVERLAY fmt=OPAQUE }\\n  mCurrentFocus=Window{abc u0 "+mask+"}\\n";
Files.writeString(Path.of(System.getenv("FOCUS_FILE")),dump);}catch(Exception e){throw new RuntimeException(e);}}
public void setFocusedTask(int id){System.out.println("FOCUS "+id);focused=id;writeFocus();}
public void setTaskWindowingMode(int id,int mode,boolean top){if(top)throw new AssertionError("mode stole focus");System.out.println("MODE "+id+" "+mode);
map.configuration.windowConfiguration.mode=mode;
if("cancel".equals(System.getenv("CASE"))){try{Files.writeString(Path.of(System.getenv("SIGNAL")),"mode");long end=System.nanoTime()+2_000_000_000L;while(!Files.exists(Path.of(System.getenv("RELEASE")))&&System.nanoTime()<end)Thread.sleep(5);}catch(Exception e){throw new RuntimeException(e);}}}
public void resizeTask(int id,Rect b,int mode){if(id!=42||mode!=0)throw new AssertionError("wrong task");System.out.println("RESIZE "+id);map.taskBounds=new Rect[]{b};}
public void resizeStack(int id,Rect b,boolean d,boolean p,boolean a,int duration){if(b!=null||d||p||a)throw new AssertionError("unsafe bounds");System.out.println("CLEAR "+id);map.taskBounds=new Rect[]{new Rect()};}
}}
"""
        source = SOURCE.read_text().replace(
            'new ProcessBuilder("/system/bin/dumpsys", "window", scope)',
            'new ProcessBuilder("/bin/cat", System.getenv("FOCUS_FILE"))',
        )
        assert 'new ProcessBuilder("/bin/cat"' in source
        stubs["NavTaskBridge.java"] = source
        stubs["NavWindowProbe.java"] = SOURCE.with_name("NavWindowProbe.java").read_text()
        stubs["NavCompactConsent.java"] = SOURCE.with_name("NavCompactConsent.java").read_text()
        stubs["StartupMaskFocus.java"] = SOURCE.with_name("StartupMaskFocus.java").read_text()
        for name, text in stubs.items():
            p = cls.root / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(text)
        subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-d", str(cls.root),
                        *map(str, cls.root.rglob("*.java"))], check=True, capture_output=True, text=True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def launch(self, case="home"):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.work = Path(temp.name)
        env = {**os.environ, "CASE": case, "FOCUS_FILE": str(self.work / "focus"),
               "SIGNAL": str(self.work / "signal"), "RELEASE": str(self.work / "release")}
        child = subprocess.Popen(["java", "-cp", str(self.root), "com.cbkii.ts18launcher.NavTaskBridge", "serve"],
                                 stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                 text=True, env=env)
        self.addCleanup(lambda: child.poll() is None and child.kill())
        self.assertEqual("TS18_READY", child.stdout.readline().strip())
        child.stdin.write("EPOCH\t1\n"); child.stdin.flush()
        return child

    def request(self, child, fields, request_id=1, epoch=1):
        child.stdin.write(f"REQUEST\t{request_id}\t10000\t{epoch}\t" + "\t".join(map(str, fields)) + "\n")
        child.stdin.flush()
        lines = []
        while True:
            line = child.stdout.readline()
            self.assertTrue(line, "broker exited without a result")
            lines.append(line.strip())
            if line.startswith(f"TS18_RESULT {request_id} "):
                return lines

    def finish(self, child):
        child.stdin.close()
        child.wait(timeout=3)
        self.assertEqual(0, child.returncode)
        child.stdout.close()

    def test_bootstrap_restores_same_task_then_reuses_process_for_return(self):
        child = self.launch("bootstrap")
        fields = ["resume-windowed", 0, "app.organicmaps.incar", 0, 141, 1131, 702, 42, "com.cbkii.ts18launcher", 7]
        first = self.request(child, fields)
        self.assertIn("MODE 42 5", first)
        self.assertLess(first.index("FOCUS 7"), first.index("FOCUS 42"))
        self.assertIn("bounds=0,141,1131,702", first[-1])
        second = self.request(child, fields, 2)
        self.assertFalse(any(x.startswith(("MODE", "RESIZE")) for x in second))
        self.assertIn("OK code=RESUMED_NATIVE", second[-1])
        self.finish(child)

    def test_dialog_foreign_and_shared_stack_cannot_be_repaired_or_focused(self):
        for case in ("dialog", "foreign", "shared"):
            with self.subTest(case=case):
                child = self.launch(case)
                result = self.request(child, ["resume-windowed", 0, "app.organicmaps.incar", 0, 141, 1131, 702, 42, "com.cbkii.ts18launcher", 7])
                self.assertIn("FAIL code=", result[-1])
                self.assertFalse(any(x.startswith(("MODE", "RESIZE", "FOCUS")) for x in result))
                self.finish(child)

    def test_owned_focusable_mask_preserves_task_and_window_authority(self):
        child = self.launch("mask")
        title = "TS18Ready_10211_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        result = self.request(child, ["observe", 0, title])
        self.assertIn("OK code=FOREGROUND", result[-1])
        self.assertFalse(any(x.startswith(("MODE", "RESIZE", "FOCUS")) for x in result))
        self.finish(child)

    def test_fullscreen_departure_does_not_focus_the_map(self):
        child = self.launch("foreign")
        result = self.request(child, ["background-fullscreen", 0, "app.organicmaps.incar", 42, "com.cbkii.ts18launcher", 7, 1])
        self.assertIn("OK code=BACKGROUND_FULLSCREEN", result[-1])
        self.assertFalse(any(x.startswith("FOCUS") for x in result))
        self.finish(child)

    def test_source_return_only_focuses_the_existing_home_task(self):
        child = self.launch("source")
        result = self.request(child, ["startup-home", 0, "com.tw.media", "com.tw.media/org.oxycblt.auxio.MainActivity", 84, "com.cbkii.ts18launcher", 7])
        self.assertIn("FOCUS 7", result)
        self.assertIn("OK code=STARTUP_HOME_RESTORED", result[-1])
        self.assertFalse(any(x.startswith(("MODE", "RESIZE")) for x in result))
        self.finish(child)

    def test_new_epoch_interrupts_an_accepted_operation_before_later_mutations(self):
        child = self.launch("cancel")
        fields = ["resume-windowed", 0, "app.organicmaps.incar", 0, 141, 1131, 702, 42, "com.cbkii.ts18launcher", 7]
        child.stdin.write("REQUEST\t1\t10000\t1\t" + "\t".join(map(str, fields)) + "\n"); child.stdin.flush()
        end = time.monotonic() + 2
        while not (self.work / "signal").exists() and time.monotonic() < end:
            time.sleep(.005)
        self.assertTrue((self.work / "signal").exists())
        child.stdin.write("EPOCH\t2\n"); child.stdin.flush()
        time.sleep(.03)
        (self.work / "release").touch()
        lines = []
        while True:
            line = child.stdout.readline(); self.assertTrue(line)
            lines.append(line.strip())
            if line.startswith("TS18_RESULT 1 "): break
        self.assertIn("STALE_PRESENTATION", lines[-1])
        self.assertFalse(any(x.startswith(("RESIZE", "FOCUS")) for x in lines))
        self.finish(child)
