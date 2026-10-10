"""Execute the production controller with deterministic Android/backend doubles."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'launcher/src/main/java/com/cbkii/ts18launcher'


@unittest.skipUnless(shutil.which('java'), 'Java compiler module required')
class ControllerRecoveryTests(unittest.TestCase):
    def test_recovery_without_more_lifecycle_callbacks(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            android = {
                'android/app/Activity.java': '''package android.app;
public class Activity { public android.view.Window getWindow(){return new android.view.Window();}
public String getPackageName(){return "com.cbkii.ts18launcher";} public int getTaskId(){return 7;}
public boolean hasWindowFocus(){return true;} public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();} }''',
                'android/view/Window.java': 'package android.view; public class Window {public View getDecorView(){return View.INSTANCE;}}',
                'android/view/View.java': '''package android.view; public class View {
public static final View INSTANCE=new View();
public record Job(Runnable action,long due){} public static final java.util.ArrayDeque<Job> jobs=new java.util.ArrayDeque<>();
public boolean postDelayed(Runnable r,long d){jobs.add(new Job(r,android.os.SystemClock.now+d));return true;}
public boolean removeCallbacks(Runnable r){return jobs.removeIf(j->j.action()==r);}
public static void drain(){int n=0;while(!jobs.isEmpty()){if(++n>30)throw new AssertionError("unbounded retry");
Job j=jobs.remove();android.os.SystemClock.now=Math.max(android.os.SystemClock.now,j.due());j.action().run();}}
}''',
                'android/content/ComponentName.java': '''package android.content; public class ComponentName { final String p,c;
public ComponentName(String p,String c){this.p=p;this.c=c;} public String getPackageName(){return p;}
public String flattenToString(){return p+"/"+c;} }''',
                'android/content/Intent.java': '''package android.content; public class Intent {
public ComponentName getComponent(){return new ComponentName("app.organicmaps.incar","app.organicmaps.SplashActivity");}
public android.content.pm.ActivityInfo resolveActivityInfo(android.content.pm.PackageManager p,int f){return new android.content.pm.ActivityInfo();}}''',
                'android/content/pm/ActivityInfo.java': '''package android.content.pm; public class ActivityInfo {
public boolean exported=true;public String packageName="app.organicmaps.incar",name="app.organicmaps.SplashActivity";}''',
                'android/content/pm/PackageManager.java': '''package android.content.pm; public class PackageManager {
public static final int MATCH_DEFAULT_ONLY=1;public static class NameNotFoundException extends Exception{}
public android.content.Intent getLaunchIntentForPackage(String p){return new android.content.Intent();}
public ActivityInfo getActivityInfo(android.content.ComponentName c,int f)throws NameNotFoundException{return new ActivityInfo();}}''',
                'android/location/Location.java': 'package android.location; public class Location {}',
                'android/os/SystemClock.java': 'package android.os; public class SystemClock {public static long now=1000;public static long elapsedRealtime(){return now;}}',
                'android/util/Log.java': 'package android.util; public class Log {public static int i(String t,String s){return 0;}public static int w(String t,String s){return 0;}}',
            }
            for path, content in android.items():
                file = root / path; file.parent.mkdir(parents=True, exist_ok=True); file.write_text(content)
            package = root / 'com/cbkii/ts18launcher'; package.mkdir(parents=True)
            for name in ('NavigationWindowController', 'NavigationRecoveryPolicy', 'NavigationHelperResult',
                         'NavigationSurfaceBackend', 'NavigationWindowUiState', 'NavigationOverlayGate'):
                shutil.copyfile(SRC / (name + '.java'), package / (name + '.java'))
            (package / 'Doubles.java').write_text(r'''package com.cbkii.ts18launcher;
class NavigationWindowBounds {public int left=0,top=100,right=1100,bottom=700;public String toString(){return "0,100,1100,700";}
public boolean equals(Object o){return o instanceof NavigationWindowBounds;} public int hashCode(){return 1;}}
class NativeNavigationPanel {int configured;String error="";Runnable retry,fullscreen;
interface BoundsListener{void onBoundsChanged(NavigationWindowBounds b);} void setBoundsListener(BoundsListener l){}
NavigationWindowBounds currentBounds(){return new NavigationWindowBounds();} void setAction(String l,Runnable r){fullscreen=r;}
void showStarting(String l,String b){} void showConfigured(String d,Runnable r){configured++;fullscreen=r;}
void showFailure(String d,Runnable r,Runnable f){error=d;retry=r;fullscreen=f;} void showUnavailable(String d,Runnable r){}
void showFullscreenOnly(String d,Runnable r){} }
class HomeNavigationSurfacePolicy {static final String NATIVE_WINDOW="native",LEAFLET="leaflet",FULLSCREEN="full";
static String mode(android.app.Activity a){return NATIVE_WINDOW;}}
class LauncherPrefs {static final String KEY_NAV="nav";static String packageFor(android.app.Activity a,String k){return "app.organicmaps.incar";}}
class AppResolver {static String labelFor(android.app.Activity a,String p,String f){return p;}}
class AndroidUserId {static int current(){return 0;}}
class MediaEventTrace {static void record(String a,String b){}}
class NavigationProvider {static final String ORGANIC_MAPS_INCAR="app.organicmaps.incar";static int launches;
static boolean hasLauncherActivity(android.app.Activity a,String p){return true;}
static boolean open(android.app.Activity a,String p,android.location.Location l){launches++;return true;}}
class RawFreeformTaskBackend implements NavigationSurfaceBackend {
static int presents,resumes,verifies,fullscreens;static long workMs;static String first="TASK_OBSERVATION_UNCERTAIN";static boolean resumeMiss,alwaysMiss;
RawFreeformTaskBackend(android.app.Activity a){}public String label(){return "fake";}
static NavigationHelperResult good(){return NavigationHelperResult.parse("OK code=RESUMED_NATIVE user=0 task=42 stack=4 package=app.organicmaps.incar component=app.organicmaps.incar/app.organicmaps.MwmActivity display=0 windowingMode=5 bounds=0,100,1100,700 visible=1 drawn=1");}
public void present(String p,String c,NavigationWindowBounds b,int t,int id,Callback cb){presents++;android.os.SystemClock.now+=workMs;
if(alwaysMiss||presents==1){
 if("BOOTSTRAP_PENDING".equals(first))cb.onResult(NavigationHelperResult.withCode(good(),first,""));
 else if("HIDDEN".equals(first))cb.onResult(NavigationHelperResult.parse(good().raw.replace("visible=1 drawn=1","visible=unknown drawn=unknown")));
 else cb.onResult(NavigationHelperResult.failure(first,""));
}else cb.onResult(good());}
public void verify(String p,NavigationWindowBounds b,int t,Callback cb){verifies++;cb.onResult(good());}
public void resume(String p,NavigationWindowBounds b,int t,String h,int ht,Callback cb){resumes++;
if(resumeMiss){resumeMiss=false;cb.onResult(NavigationHelperResult.failure("FOREGROUND_CHANGED",""));}else cb.onResult(good());}
public void status(String p,int t,Callback cb){cb.onResult(good());}
public void fullscreen(String p,String c,int t,Callback cb){fullscreens++;cb.onResult(NavigationHelperResult.failure("ROOT_UNAVAILABLE",""));}
public void backgroundFullscreen(String p,int t,String h,int ht,boolean e,Callback cb){cb.onResult(good());}
public void suspend(String p,int t,String h,int ht,Callback cb){cb.onResult(good());}public void destroy(){}
static void reset(){presents=resumes=verifies=fullscreens=0;alwaysMiss=resumeMiss=false;first="TASK_OBSERVATION_UNCERTAIN";
NavigationProvider.launches=0;workMs=0;android.os.SystemClock.now=1000;android.view.View.jobs.clear();}}
''')
            (package / 'Harness.java').write_text(r'''package com.cbkii.ts18launcher;
public class Harness {
static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
static NavigationWindowController start(NativeNavigationPanel p){NavigationWindowController c=new NavigationWindowController(new android.app.Activity(),p);c.onHomeVisible();return c;}
public static void main(String[] args){
 RawFreeformTaskBackend.reset();NativeNavigationPanel p=new NativeNavigationPanel();start(p);android.view.View.drain();
 check(p.configured==1&&RawFreeformTaskBackend.presents==2,"transient acquisition never recovered");
 RawFreeformTaskBackend.reset();RawFreeformTaskBackend.first="COMPONENT_UNKNOWN";p=new NativeNavigationPanel();start(p);android.view.View.drain();
 check(p.configured==1&&RawFreeformTaskBackend.presents==2,"missing top Activity never recovered");
 RawFreeformTaskBackend.reset();RawFreeformTaskBackend.first="BOOTSTRAP_PENDING";p=new NativeNavigationPanel();start(p);android.view.View.drain();
 check(p.configured==1&&RawFreeformTaskBackend.presents==1&&RawFreeformTaskBackend.verifies==1,"bootstrap duplicated launch");
 RawFreeformTaskBackend.reset();RawFreeformTaskBackend.first="BOOTSTRAP_PENDING";RawFreeformTaskBackend.resumeMiss=true;p=new NativeNavigationPanel();start(p);android.view.View.drain();
 check(p.configured==1&&RawFreeformTaskBackend.resumes==2,"foreground mismatch had no follow-up");
 RawFreeformTaskBackend.reset();RawFreeformTaskBackend.first="HIDDEN";p=new NativeNavigationPanel();start(p);
 check(p.configured==0,"geometry incorrectly claimed visibility");android.view.View.drain();check(p.configured==1&&RawFreeformTaskBackend.presents==1,"surface recovery relaunched task");
 RawFreeformTaskBackend.reset();RawFreeformTaskBackend.alwaysMiss=true;p=new NativeNavigationPanel();start(p);android.view.View.drain();
 check(RawFreeformTaskBackend.presents==7&&p.error.contains("TASK_OBSERVATION_UNCERTAIN"),"recovery budget not enforced");
 RawFreeformTaskBackend.reset();RawFreeformTaskBackend.alwaysMiss=true;RawFreeformTaskBackend.workMs=20000;p=new NativeNavigationPanel();start(p);android.view.View.drain();
 check(RawFreeformTaskBackend.presents==4&&p.error.contains("TASK_OBSERVATION_UNCERTAIN"),"elapsed recovery deadline ignored");
 RawFreeformTaskBackend.reset();p=new NativeNavigationPanel();NavigationWindowController c=start(p);c.onHomeStopped();android.view.View.drain();
 check(RawFreeformTaskBackend.presents==1,"recovery ran after HOME stopped");c.onHomeVisible();android.view.View.drain();check(p.configured==1,"real HOME return failed to recover");
 RawFreeformTaskBackend.reset();RawFreeformTaskBackend.first="ROOT_UNAVAILABLE";p=new NativeNavigationPanel();start(p);android.view.View.drain();p.fullscreen.run();
 check(NavigationProvider.launches==1&&RawFreeformTaskBackend.fullscreens==0,"explicit fallback depended on failed root");
 RawFreeformTaskBackend.reset();RawFreeformTaskBackend.first="ROOT_UNAVAILABLE";p=new NativeNavigationPanel();c=start(p);p.retry.run();android.view.View.drain();p.fullscreen.run();
 check(RawFreeformTaskBackend.fullscreens==1,"successful recovery retained stale root failure");
 check(!NavigationRecoveryPolicy.ordinaryFullscreenAllowed("FULLSCREEN_POLICY_BLOCKED"),"permission policy bypassed");
 check(!NavigationRecoveryPolicy.ordinaryFullscreenAllowed("TASK_OBSERVATION_UNCERTAIN")&&!NavigationRecoveryPolicy.ordinaryFullscreenAllowed("NO_RESPONSE"),"uncertain native result replayed via fallback");
 RawFreeformTaskBackend.reset();p=new NativeNavigationPanel();c=start(p);c.destroy();android.view.View.drain();check(RawFreeformTaskBackend.presents==1,"destroyed session retried");
 System.out.println("PASS controller acquisition/bootstrap/focus/visibility/budget/lifecycle/fallback");
}}
''')
            (root / 'classes').mkdir()
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(root / 'classes'),
                            *map(str, root.rglob('*.java'))], check=True, capture_output=True, text=True)
            result = subprocess.run(['java', '-cp', str(root / 'classes'),
                                     'com.cbkii.ts18launcher.Harness'], check=True, capture_output=True, text=True)
            self.assertIn('PASS controller', result.stdout)
