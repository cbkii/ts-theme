"""Execute the production background orchestrator against hand-driven source/task boundaries."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'launcher/src/main/java/com/cbkii/ts18launcher'

class StartupRuntimeTests(unittest.TestCase):
    def test_independent_background_readiness_boot_dedup_and_focus_yield(self):
        if not shutil.which('java'): self.skipTest('JDK unavailable')
        stubs = {
          'android/annotation/SuppressLint.java': 'package android.annotation;public @interface SuppressLint {String[] value();}',
          'android/app/Activity.java': 'package android.app;public class Activity {}',
          'android/os/Looper.java': 'package android.os;public class Looper {static Looper main=new Looper();public static Looper getMainLooper(){return main;}public static Looper myLooper(){return main;}}',
          'android/os/Handler.java': '''package android.os;public class Handler {static class Item {Runnable r;long at;Item(Runnable r,long at){this.r=r;this.at=at;}}static java.util.List<Item> q=new java.util.ArrayList<>();public Handler(Looper l){}public void post(Runnable r){r.run();}public void postDelayed(Runnable r,long d){q.add(new Item(r,SystemClock.now+d));}public void removeCallbacks(Runnable r){q.removeIf(i->i.r==r);}public void removeCallbacksAndMessages(Object o){q.clear();}public static void advance(long ms){long end=SystemClock.now+ms;while(!q.isEmpty()){q.sort((a,b)->Long.compare(a.at,b.at));if(q.get(0).at>end)break;Item item=q.remove(0);SystemClock.now=item.at;item.r.run();}SystemClock.now=end;}}''',
          'android/os/SystemClock.java': 'package android.os;public class SystemClock {public static long now=100;public static long elapsedRealtime(){return now;}}',
          'android/content/Context.java': 'package android.content;public class Context {public static int MODE_PRIVATE=0;SharedPreferences p=new SharedPreferences();public Context getApplicationContext(){return this;}public Object getContentResolver(){return null;}public SharedPreferences getSharedPreferences(String s,int m){return p;}}',
          'android/content/SharedPreferences.java': 'package android.content;public class SharedPreferences {long boot=-1;public long getLong(String k,long d){return boot;}public Editor edit(){return new Editor();}public class Editor {public Editor putLong(String k,long b){boot=b;return this;}public Editor remove(String k){return this;}public void apply(){}}}',
          'android/provider/Settings.java': 'package android.provider;public class Settings {public static class Global {public static String BOOT_COUNT="boot";public static int getInt(Object c,String k,int d){return 5;}}}',
          'com/cbkii/ts18launcher/media/MediaSourceKey.java': 'package com.cbkii.ts18launcher.media;public enum MediaSourceKey {MUSIC,RADIO}',
          'com/cbkii/ts18launcher/Doubles.java': '''package com.cbkii.ts18launcher;
class Ts18LauncherApplication {static int prepares;static void prepareDirectSource(android.content.Context c,com.cbkii.ts18launcher.media.MediaSourceKey k,long e){prepares++;}static boolean legacyMediaEngineOwns(android.content.Context c,boolean radio){return false;}static MediaSourceBootstrapper mediaSources(android.content.Context c){throw new AssertionError("unused legacy owner created");}static Facade mediaFacade(android.content.Context c){return new Facade();}}
class Facade {com.cbkii.ts18launcher.media.MediaSourceState state(com.cbkii.ts18launcher.media.MediaSourceKey k){return com.cbkii.ts18launcher.media.MediaSourceState.unavailable(k,com.cbkii.ts18launcher.media.MediaEngine.DIRECT,"source",com.cbkii.ts18launcher.media.MediaSourceState.Availability.NO_SESSION,0);}}
class MaskedSourcePreparation {MaskedSourcePreparation(android.app.Activity a,StartupReadinessCoordinator.LoaderUi u,StartupReadinessCoordinator.NavigationHook n,Runnable r){}boolean serves(String p){return false;}void join(java.util.function.Consumer<Boolean> c){}void begin(String p,long d,java.util.function.Consumer<Boolean> c){}void cancel(){}}
class MediaEngineSelector {static String configuredPackage(android.content.Context c,com.cbkii.ts18launcher.media.MediaSourceKey k){return "source";}}
class MediaSourceBootstrapper {void warm(String p){}}
class UiPersonalizationPrefs {static boolean mediaStartupWarmup(android.content.Context c){return true;}}
class StartupTimeline {static void mark(String a,String... b){}}
class MediaEventTrace {static void record(String a,String b,String... c){}}
''',
          'com/cbkii/ts18launcher/Harness.java': '''package com.cbkii.ts18launcher;
import com.cbkii.ts18launcher.StartupReadinessPolicy.Trigger;
public class Harness {static int reconciles;static boolean deferred;static void check(boolean b){if(!b)throw new AssertionError();}
public static void main(String[] x)throws Exception {var constructor=StartupReadinessCoordinator.class.getDeclaredConstructor(android.content.Context.class);constructor.setAccessible(true);var c=constructor.newInstance(new android.content.Context());
c.attachNavigationHook(new StartupReadinessCoordinator.NavigationHook(){public boolean ready(){return false;}public boolean busy(){return false;}public void reconcile(){reconciles++;}public void deferAutomaticPresentation(boolean d){deferred=d;}});
c.onHomeResumed(new android.app.Activity());check(reconciles==1&&!deferred);check(Ts18LauncherApplication.prepares==2);
c.onTrigger(Trigger.BOOT);check(Ts18LauncherApplication.prepares==2);
android.os.SystemClock.now=20000;c.onTrigger(Trigger.READINESS_CHANGED);check(Ts18LauncherApplication.prepares==2&&reconciles>=2);
c.noteExternalNavigation();int before=reconciles;c.onTrigger(Trigger.SESSION_LOST);check(reconciles==before);
c.onHomePaused();c.onScreenOff();android.os.SystemClock.now=40000;c.onTrigger(Trigger.SCREEN_ON);check(Ts18LauncherApplication.prepares==4&&reconciles==before);
c.onHomeResumed(new android.app.Activity());check(reconciles>before&&!deferred);
c.onScreenOff();before=reconciles;android.os.SystemClock.now=60000;c.onTrigger(Trigger.SCREEN_ON);check(reconciles>before);
c.onScreenOff();before=reconciles;android.os.SystemClock.now=80000;c.onTrigger(Trigger.ACC_ON);check(reconciles>before);c.destroy();int count=Ts18LauncherApplication.prepares;c.onTrigger(Trigger.RESTART);check(Ts18LauncherApplication.prepares==count);
Ts18LauncherApplication.prepares=0;var retry=constructor.newInstance(new android.content.Context());retry.onHomeResumed(new android.app.Activity());check(Ts18LauncherApplication.prepares==2);android.os.Handler.advance(100000);check(Ts18LauncherApplication.prepares==6);retry.onScreenOff();retry.onTrigger(Trigger.SCREEN_ON);check(Ts18LauncherApplication.prepares==8);retry.destroy();android.os.Handler.advance(100000);check(Ts18LauncherApplication.prepares==8);
Ts18LauncherApplication.prepares=0;var cold=constructor.newInstance(new android.content.Context());cold.onTrigger(Trigger.RESTART);check(Ts18LauncherApplication.prepares==0);android.os.Handler.advance(749);check(Ts18LauncherApplication.prepares==0);cold.attachNavigationHook(new StartupReadinessCoordinator.NavigationHook(){public boolean ready(){return false;}public boolean busy(){return false;}public void reconcile(){check(Ts18LauncherApplication.prepares==0);}});cold.onHomeResumed(new android.app.Activity());check(Ts18LauncherApplication.prepares==2);cold.destroy();
Ts18LauncherApplication.prepares=0;var headless=constructor.newInstance(new android.content.Context());headless.onTrigger(Trigger.RESTART);android.os.Handler.advance(751);check(Ts18LauncherApplication.prepares==2);headless.destroy();
System.out.println("PASS Maps first at process creation / bounded missing-host grace / bounded retries / wake rearm / independent background sources / no legacy construction / late boot / task yield / wake / destruction");}}
'''}
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            for name,text in stubs.items():
                p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)
            for name in ('StartupReadinessCoordinator.java','StartupReadinessPolicy.java','media/MediaSourceState.java','media/MediaEngine.java'):
                target=root/'com/cbkii/ts18launcher'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text((SRC/name).read_text())
            subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',tmp,*map(str,root.rglob('*.java'))],check=True,capture_output=True,text=True)
            subprocess.run(['java','-cp',tmp,'com.cbkii.ts18launcher.Harness'],check=True,timeout=5)
