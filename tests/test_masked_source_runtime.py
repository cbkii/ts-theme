"""Run the production masked transaction against timed Activity/root/session boundaries."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'launcher/src/main/java/com/cbkii/ts18launcher'


class MaskedSourceRuntimeTests(unittest.TestCase):
    def test_draw_launch_park_session_and_cancellation_boundaries(self):
        if not shutil.which('java') or 'jdk.compiler@' not in subprocess.run(
                ['java', '--list-modules'], capture_output=True, text=True).stdout:
            self.skipTest('JDK unavailable')
        stubs = {
            'android/app/Activity.java': '''package android.app;public class Activity {
public String getString(int i){return "Preparing Radio";}public boolean isFinishing(){return false;} public boolean isDestroyed(){return false;}
public int getTaskId(){return 10;} public String getPackageName(){return "com.cbkii.ts18launcher";}
public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();}}
''',
            'android/content/ComponentName.java': '''package android.content;public class ComponentName {
String p,n;public ComponentName(String p,String n){this.p=p;this.n=n;}public String flattenToString(){return p+"/"+n;}}
''',
            'android/content/Intent.java': '''package android.content;public class Intent {
public ComponentName getComponent(){return new ComponentName("com.navimods.radio","com.navimods.radio.Main");}}
''',
            'android/content/pm/ActivityInfo.java': '''package android.content.pm;public class ActivityInfo {
public boolean enabled=true,exported=true;public String packageName="com.navimods.radio",name="com.navimods.radio.Main",targetActivity;}
''',
            'android/content/pm/PackageManager.java': '''package android.content.pm;public class PackageManager {
public static class NameNotFoundException extends Exception {} public android.content.Intent getLaunchIntentForPackage(String p){return new android.content.Intent();}
public ActivityInfo getActivityInfo(android.content.ComponentName c,int f)throws NameNotFoundException{return new ActivityInfo();}}
''',
            'android/os/Looper.java': 'package android.os;public class Looper {public static Looper getMainLooper(){return new Looper();}}',
            'android/os/SystemClock.java': '''package android.os;public class SystemClock {
public static volatile long offset;public static long elapsedRealtime(){return System.nanoTime()/1000000+offset;}}
''',
            'android/os/Handler.java': '''package android.os;import java.util.*;public class Handler {
static class Item {Runnable r;long at;Item(Runnable r,long a){this.r=r;at=a;}}
static List<Item> pending=new ArrayList<>();public Handler(Looper l){}
public void post(Runnable r){postDelayed(r,0);}public void postDelayed(Runnable r,long d){synchronized(pending){pending.add(new Item(r,SystemClock.elapsedRealtime()+d));}}
public void removeCallbacksAndMessages(Object x){synchronized(pending){pending.clear();}}
public static void pump() {List<Runnable> due=new ArrayList<>();synchronized(pending){long n=SystemClock.elapsedRealtime();pending.removeIf(i->{if(i.at<=n){due.add(i.r);return true;}return false;});}for(Runnable r:due)r.run();}}
''',
            'android/media/session/PlaybackState.java': 'package android.media.session;public class PlaybackState {public static long ACTION_PLAY=4,ACTION_PLAY_PAUSE=512;}',
            'com/cbkii/ts18launcher/Doubles.java': '''package com.cbkii.ts18launcher;
import com.cbkii.ts18launcher.media.*;
class StartupReadinessCoordinator {interface LoaderUi {boolean canMask();boolean ownsMask();void render(String s,boolean b);void mask(boolean b);void whenMaskDrawn(Runnable r);void hide();}interface NavigationHook {boolean busy();void deferAutomaticPresentation(boolean b);int taskId();}}
class CompactStartupClient {static boolean automaticAllowed(android.app.Activity a){return true;}}
class AndroidUserId {static int current(){return 0;}}
class StartupMaskFocus {static String activeTitle="mask";static boolean validTitle(String t){return !t.isEmpty();}}
class MediaEngineSelector {static String configuredPackage(android.app.Activity a,MediaSourceKey k){return "com.navimods.radio";}}
class R {static class string {static int startup_radio_preparing=1;}}
class MediaEventTrace {static void record(String a,String b,String c){}}
class Ts18LauncherApplication {static Facade facade=new Facade();static Facade mediaFacade(android.app.Activity a){return facade;}}
class Facade {volatile boolean live=true;long actions=4;String item="station";void refresh(){}MediaSourceState state(MediaSourceKey k){return live?new MediaSourceState(k,MediaEngine.DIRECT,"com.navimods.radio",MediaSourceState.Availability.LIVE,2,actions,"token",item,"FM","",1):MediaSourceState.unavailable(k,MediaEngine.DIRECT,"com.navimods.radio",MediaSourceState.Availability.NO_SESSION,1);}}
class RootTaskSession {
static RootTaskSession one=new RootTaskSession();static volatile int task=10,pendingFocus;static volatile String component="com.cbkii.ts18launcher/com.cbkii.ts18launcher.LauncherActivity";static int parks,mapParks;static volatile boolean blockHome,homeEntered,dropAfterPark,blockObservation,observationEntered;static java.util.List<String> events=new java.util.concurrent.CopyOnWriteArrayList<>();
static RootTaskSession get(android.app.Activity a){return one;}
static void waitGate(java.util.function.BooleanSupplier b){while(b.getAsBoolean()){try{Thread.sleep(2);}catch(Exception e){throw new RuntimeException(e);}}}
NavigationHelperResult runCurrent(long d,String... f){events.add(f[0]);if(d<=android.os.SystemClock.elapsedRealtime())return NavigationHelperResult.parse("FAIL code=PHASE_TIMEOUT");
if(f[0].equals("observe")&&NavigationRootHelper.entered){if(blockObservation){observationEntered=true;waitGate(()->blockObservation);}if(d<=android.os.SystemClock.elapsedRealtime())return NavigationHelperResult.parse("FAIL code=PHASE_TIMEOUT");if(pendingFocus>0&&--pendingFocus==0){task=20;component="com.navimods.radio/com.navimods.radio.Main";}}
if(f[0].equals("return-home")){mapParks++;task=10;component="com.cbkii.ts18launcher/com.cbkii.ts18launcher.LauncherActivity";}
if(f[0].equals("startup-home")){homeEntered=true;waitGate(()->blockHome);if(d<=android.os.SystemClock.elapsedRealtime())return NavigationHelperResult.parse("FAIL code=PHASE_TIMEOUT");parks++;task=10;component="com.cbkii.ts18launcher/com.cbkii.ts18launcher.LauncherActivity";if(dropAfterPark)Ts18LauncherApplication.facade.live=false;}
return NavigationHelperResult.parse("OK code=FOREGROUND user=0 task="+task+" component="+component);}}
class NavigationRootHelper {static int launches;static volatile boolean block,entered;static boolean permission;static int outcome;
long deadline;NavigationRootHelper(android.app.Activity a){}void setDeadline(long d){deadline=d;}
NavigationHelperResult run(String... f){if(deadline<=android.os.SystemClock.elapsedRealtime())return NavigationHelperResult.parse("FAIL code=PHASE_TIMEOUT");if(RootTaskSession.task!=10)throw new AssertionError("Maps not parked before launch");RootTaskSession.events.add("launch");launches++;entered=true;if(outcome==1)return NavigationHelperResult.parse("FAIL code=STARTUP_SOURCE_REJECTED");if(RootTaskSession.pendingFocus==0){RootTaskSession.task=20;RootTaskSession.component=permission?"com.android.permissioncontroller/GrantPermissionsActivity":"com.navimods.radio/com.navimods.radio.Main";}RootTaskSession.waitGate(()->block);return NavigationHelperResult.parse(deadline<=android.os.SystemClock.elapsedRealtime()?"FAIL code=PHASE_TIMEOUT":outcome==2?"FAIL code=STARTUP_SOURCE_UNCERTAIN":"OK code=STARTUP_SOURCE user=0 task=20");}}

''',
            'com/cbkii/ts18launcher/Harness.java': '''package com.cbkii.ts18launcher;
public class Harness {
static class Loader implements StartupReadinessCoordinator.LoaderUi {boolean visible;Runnable draw;int hides;public boolean canMask(){return true;}public boolean ownsMask(){return visible;}public void render(String s,boolean b){}public void mask(boolean b){visible=b;}public void whenMaskDrawn(Runnable r){draw=r;}public void hide(){visible=false;hides++;}}
static class Nav implements StartupReadinessCoordinator.NavigationHook {boolean deferred;public boolean busy(){return false;}public void deferAutomaticPresentation(boolean d){deferred=d;}public int taskId(){return 30;}}
static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
static void until(java.util.function.BooleanSupplier done)throws Exception{long end=android.os.SystemClock.elapsedRealtime()+3500;while(!done.getAsBoolean()&&android.os.SystemClock.elapsedRealtime()<end){android.os.Handler.pump();Thread.sleep(3);}check(done.getAsBoolean(),"transaction did not settle");}
static void reset(){android.os.SystemClock.offset=0;Ts18LauncherApplication.facade.actions=4;Ts18LauncherApplication.facade.item="station";NavigationRootHelper.outcome=0;RootTaskSession.task=10;RootTaskSession.component="com.cbkii.ts18launcher/com.cbkii.ts18launcher.LauncherActivity";RootTaskSession.parks=0;RootTaskSession.mapParks=0;RootTaskSession.pendingFocus=0;RootTaskSession.blockHome=false;RootTaskSession.homeEntered=false;RootTaskSession.dropAfterPark=false;RootTaskSession.blockObservation=false;RootTaskSession.observationEntered=false;RootTaskSession.events.clear();NavigationRootHelper.launches=0;NavigationRootHelper.entered=false;NavigationRootHelper.block=false;NavigationRootHelper.permission=false;Ts18LauncherApplication.facade.live=true;}
static void scenario(int kind)throws Exception{reset();Loader l=new Loader();Nav n=new Nav();boolean[] ended={false},ready={false};int[] callbacks={0};
var t=new MaskedSourcePreparation(new android.app.Activity(),l,n,()->ended[0]=true);
t.begin("com.navimods.radio",android.os.SystemClock.elapsedRealtime()+3000,r->{ready[0]=r;callbacks[0]++;});
check(l.visible&&n.deferred&&NavigationRootHelper.launches==0,"launched before opaque frame");
if(kind==0){t.cancel();check(ended[0]&&!l.visible&&NavigationRootHelper.launches==0,"no-draw cancel leaked");return;}
if(kind==2){RootTaskSession.task=99;RootTaskSession.component="foreign/Activity";}
if(kind==3)NavigationRootHelper.permission=true;
if(kind==4)NavigationRootHelper.block=true;
if(kind==5)RootTaskSession.dropAfterPark=true;
if(kind==6){RootTaskSession.task=30;RootTaskSession.component="app.organicmaps.incar/app.organicmaps.MwmActivity";}
if(kind==7)RootTaskSession.pendingFocus=3;
if(kind==8)RootTaskSession.blockHome=true;
if(kind==9)RootTaskSession.blockObservation=true;
if(kind==10)NavigationRootHelper.outcome=1;
if(kind==11)NavigationRootHelper.outcome=2;
if(kind==12)NavigationRootHelper.block=true;
if(kind==13)Ts18LauncherApplication.facade.actions=512;
if(kind==14)Ts18LauncherApplication.facade.item="";
l.draw.run();
if(kind==4){until(()->NavigationRootHelper.entered);t.cancel();check(l.visible&&!ended[0],"unmasked in-flight launch");NavigationRootHelper.block=false;}
if(kind==8){until(()->RootTaskSession.homeEntered);t.cancel();check(l.visible&&!ended[0],"unmasked HOME restoration in flight");RootTaskSession.blockHome=false;}
if(kind==9){until(()->RootTaskSession.observationEntered);t.cancel();RootTaskSession.blockObservation=false;}
if(kind==12){until(()->NavigationRootHelper.entered);android.os.SystemClock.offset=4000;android.os.Handler.pump();check(l.visible&&!ended[0],"deadline unmasked accepted launch in flight");NavigationRootHelper.block=false;}
until(()->ended[0]);check(!l.visible&&!n.deferred&&callbacks[0]==1&&l.hides==1,"cleanup not exactly once");
check(NavigationRootHelper.launches==(kind==2?0:1),"unexpected launch replay");
if(kind==1||kind==6||kind==7||kind==13||kind==14){check(ready[0]&&RootTaskSession.task==10&&RootTaskSession.parks==1,"ready before park/session receipt");}
else check(!ready[0],"false readiness");
if(kind==3)check(RootTaskSession.parks==0&&RootTaskSession.task==20,"permission flow hidden");
if(kind==4||kind==8||kind==9||kind==11||kind==12)check(RootTaskSession.parks==1&&RootTaskSession.task==10,"late launch not parked");
if(kind==10)check(RootTaskSession.parks==0,"rejected launch repaired unrelated HOME");
if(kind==5)check(RootTaskSession.parks==1&&!Ts18LauncherApplication.facade.live,"source death after park not observed");
if(kind==6)check(RootTaskSession.mapParks==1&&RootTaskSession.events.indexOf("return-home")<RootTaskSession.events.indexOf("launch"),"valid Maps task was not parked first");
t.cancel();check(callbacks[0]==1,"double cleanup");}
public static void main(String[] a)throws Exception{for(int i=0;i<15;i++)scenario(i);reset();long expired=android.os.SystemClock.elapsedRealtime();check(!RootTaskSession.one.runCurrent(expired,"startup-home").success&&RootTaskSession.parks==0,"expired park mutated task");var helper=new NavigationRootHelper(new android.app.Activity());helper.setDeadline(expired);check(!helper.run("startup-source").success&&NavigationRootHelper.launches==0,"expired launch mutated source");System.out.println("PASS opaque barrier / exact task / permission yield / in-flight cancellation / session loss");}}
''',
        }
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name, text in stubs.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text)
            for name in ('MaskedSourcePreparation.java', 'NavigationHelperResult.java',
                         'media/MediaSourceState.java', 'media/MediaSourceKey.java', 'media/MediaEngine.java'):
                path = root / 'com/cbkii/ts18launcher' / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text((SRC / name).read_text())
            compile_result = subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main',
                                             '-d', tmp, *map(str, root.rglob('*.java'))],
                                            capture_output=True, text=True)
            self.assertEqual(0, compile_result.returncode, compile_result.stderr)
            result = subprocess.run(['java', '-cp', tmp, 'com.cbkii.ts18launcher.Harness'],
                                    timeout=15, capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
