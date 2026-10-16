"""Execute startup production policy/coordinator against a deterministic API29 boundary model."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'launcher/src/main/java/com/cbkii/ts18launcher'


class StartupRuntimeTests(unittest.TestCase):
    def test_admission_ownership_cancellation_and_exact_home_return(self):
        if not shutil.which('java'):
            self.skipTest('JDK unavailable')
        stubs = {
            'android/os/Looper.java': 'package android.os; public class Looper {static Looper l=new Looper(); public static Looper getMainLooper(){return l;} public static Looper myLooper(){return l;}}',
            'android/os/SystemClock.java': 'package android.os; public class SystemClock {public static long now=100; public static long uptimeMillis(){return now;}}',
            'android/os/Handler.java': '''package android.os;import java.util.*;
public class Handler {record Job(Runnable r,long at){} static List<Job> jobs=new ArrayList<>();
public Handler(Looper l){}public boolean post(Runnable r){return postDelayed(r,0);}
public boolean postDelayed(Runnable r,long d){jobs.add(new Job(r,SystemClock.now+d));return true;}
public void removeCallbacksAndMessages(Object o){jobs.clear();}
public static void until(long end){int n=0;while(!jobs.isEmpty()){
Job j=jobs.stream().min(Comparator.comparingLong(Job::at)).orElseThrow();if(j.at()>end)break;
if(++n>1000)throw new AssertionError("unbounded jobs");jobs.remove(j);SystemClock.now=j.at();j.r().run();}SystemClock.now=end;}
public static void reset(){jobs.clear();SystemClock.now=100;}}
''',
            'android/os/PowerManager.java': 'package android.os;public class PowerManager {public boolean isInteractive(){return true;}}',
            'android/app/KeyguardManager.java': 'package android.app;public class KeyguardManager {public boolean isKeyguardLocked(){return false;}}',
            'android/media/AudioManager.java': 'package android.media;public class AudioManager {public static final int MODE_NORMAL=0;public int getMode(){return 0;}}',
            'android/content/SharedPreferences.java': '''package android.content;public class SharedPreferences {
long value=-1;public long getLong(String k,long d){return value;}public Editor edit(){return new Editor();}
public class Editor {public Editor putLong(String k,long v){value=v;return this;}public void apply(){}}}''',
            'android/content/Context.java': '''package android.content;public class Context {
public static final String POWER_SERVICE="power",KEYGUARD_SERVICE="key",AUDIO_SERVICE="audio",ACTIVITY_SERVICE="activity";public static final int MODE_PRIVATE=0;
SharedPreferences prefs=new SharedPreferences();public Context getApplicationContext(){return this;}
public SharedPreferences getSharedPreferences(String s,int m){return prefs;}public Object getContentResolver(){return null;}
public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();}
public Object getSystemService(String s){return switch(s){case "power"->new android.os.PowerManager();case "key"->new android.app.KeyguardManager();case "audio"->new android.media.AudioManager();default->new android.app.ActivityManager();};}}
''',
            'android/app/Activity.java': 'package android.app;public class Activity extends android.content.Context {public boolean isFinishing(){return false;}public boolean hasWindowFocus(){return true;}public int getTaskId(){return 10;}}',
            'android/app/ActivityManager.java': 'package android.app;public class ActivityManager {public static int moves;public static Runnable moved=()->{};public void moveTaskToFront(int task,int flags){if(task!=10)throw new AssertionError("wrong HOME task");moves++;moved.run();}}',
            'android/content/Intent.java': 'package android.content;public class Intent {String pkg;public Intent(String s){pkg=s;}public ComponentName getComponent(){return new ComponentName(pkg,pkg+".Main");}}',
            'android/content/ComponentName.java': 'package android.content;public class ComponentName {String p,c;public ComponentName(String p,String c){this.p=p;this.c=c;}public String flattenToShortString(){return p+"/"+c;}}',
            'android/content/pm/PackageManager.java': 'package android.content.pm;public class PackageManager {public android.content.Intent getLaunchIntentForPackage(String p){return new android.content.Intent(p);}}',
            'android/provider/Settings.java': 'package android.provider;public class Settings {public static class Global{public static String BOOT_COUNT="boot";public static int getInt(Object c,String k,int d){return 5;}}}',
            'com/cbkii/ts18launcher/platform/TopwayAdapter.java': 'package com.cbkii.ts18launcher.platform;public class TopwayAdapter {public static String defaultMusicPackage(android.content.Context c){return "com.tw.media";}}',
            'com/cbkii/ts18launcher/Doubles.java': '''package com.cbkii.ts18launcher;
class ProcessMediaSessionMonitor {interface Observer{void onProcessMediaStateChanged(MediaListenerService.Snapshot m,MediaListenerService.Snapshot r,boolean a);}static void addObserver(android.content.Context c,Observer o){}static void removeObserver(Observer o){}}
class MediaListenerService {static boolean access=true;static boolean hasNotificationAccess(android.content.Context c){return access;}static class Snapshot{}}
class Ts18LauncherApplication {static MediaSourceBootstrapper shared=new MediaSourceBootstrapper();static MediaSourceBootstrapper mediaSources(android.content.Context c){return shared;}}
class MediaSourceBootstrapper {boolean musicReady,radioReady,musicPlaying;long commands;int warms,pauses;
boolean hasUsableQueue(String p){return p.equals("com.tw.media")?musicReady:radioReady;}
boolean hasSessionIdentity(String p){return hasUsableQueue(p);}void warm(String p){warms++;}boolean isPlaying(String p){return p.equals("com.tw.media")&&musicPlaying;}
long userCommandGeneration(){return commands;}boolean pauseUnrequestedPlayback(String p,long g){pauses++;return true;}}
class UiPersonalizationPrefs {static boolean mediaStartupWarmup(android.content.Context c){return true;}}
class TestingProfiles {static String startup(android.content.Context c){return "auto";}}
class LauncherPrefs {static String KEY_MUSIC="music",KEY_NAV="nav";static String music="com.tw.media";
static String packageFor(android.content.Context c,String k){return k.equals("music")?music:"app.organicmaps.incar";}}
class RadioProvider {static String resolvePackage(android.content.Context c){return "com.navimods.radio";}}
class NavigationProvider {static String ORGANIC_MAPS_INCAR="app.organicmaps.incar";static boolean hasLauncherActivity(android.content.Context c,String p){return true;}}
class MediaSourceAdapter {static String AUXIO_PACKAGE="com.tw.media",NAVRADIO_PACKAGE="com.navimods.radio";}
class HomeMode {static boolean isDefaultHome(android.content.Context c){return true;}}
class AndroidUserId {static int current(){return 0;}}
class MediaEventTrace {static void record(String a,String b,String... c){}}
class AppResolver {static int launches;static boolean launchPackageQuietly(android.content.Context c,String p){launches++;
RootShell.top=RootShell.forComponent(p+"/"+p+".Main",20);return true;}}
class RootShell {static String top;static int probes;static Runnable afterProbe=()->{};
static String forComponent(String c,int task){return "mResumedActivity: ActivityRecord{abc u0 "+c+" t"+task+"}\\n mCurrentFocus=Window{def u0 "+c+"}\\n";}
static class Result {String output;Result(String o){output=o;}boolean success(){return true;}}
static Result runWithin(String c,long t){probes++;String old=top;afterProbe.run();return new Result(old);}}
''',
            'com/cbkii/ts18launcher/Harness.java': '''package com.cbkii.ts18launcher;
import android.os.*;import java.lang.reflect.*;import java.util.concurrent.*;
import static com.cbkii.ts18launcher.StartupReadinessPolicy.*;
public class Harness {
static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
static class Inline extends AbstractExecutorService {public void execute(Runnable r){r.run();}public void shutdown(){}public java.util.List<Runnable> shutdownNow(){return java.util.List.of();}public boolean isShutdown(){return false;}public boolean isTerminated(){return false;}public boolean awaitTermination(long t,TimeUnit u){return true;}}
static StartupReadinessCoordinator create()throws Exception{
Handler.reset();MediaListenerService.access=true;RootShell.probes=0;AppResolver.launches=0;android.app.ActivityManager.moves=0;RootShell.afterProbe=()->{};
RootShell.top=RootShell.forComponent("com.cbkii.ts18launcher/.HomeAlias",10);
Ts18LauncherApplication.shared=new MediaSourceBootstrapper();Ts18LauncherApplication.shared.musicReady=true;
Constructor<StartupReadinessCoordinator> c=StartupReadinessCoordinator.class.getDeclaredConstructor(android.content.Context.class);c.setAccessible(true);
StartupReadinessCoordinator s=c.newInstance(new android.content.Context());Field f=s.getClass().getDeclaredField("observer");f.setAccessible(true);((ExecutorService)f.get(s)).shutdownNow();f.set(s,new Inline());
s.attachNavigationHook(new StartupReadinessCoordinator.NavigationHook(){public boolean ready(){return true;}public boolean busy(){return false;}public void reconcile(){throw new AssertionError("duplicate Maps owner");}});
s.onHomeResumed(new android.app.Activity());return s;}
static StartupReadinessPolicy ledger(StartupReadinessCoordinator s)throws Exception{Field f=s.getClass().getDeclaredField("policy");f.setAccessible(true);return (StartupReadinessPolicy)f.get(s);}
public static void main(String[] args)throws Exception {
StartupForegroundPolicy top=StartupForegroundPolicy.parse(RootShell.forComponent("com.cbkii.ts18launcher/.HomeAlias",10));
check(top.matches(0,10,"com.cbkii.ts18launcher/.LauncherActivity"),"HOME alias identity");
check(StartupForegroundPolicy.parse("mResumedActivity: ActivityRecord{a u0 p/.A t1}\\nmCurrentFocus=null")==null,"blank focus accepted");
check(StartupForegroundPolicy.parse(RootShell.forComponent("p/.A",1)+RootShell.forComponent("q/.B",2))==null,"ambiguous display accepted");
check(!top.matches(1,10,"com.cbkii.ts18launcher/.LauncherActivity"),"wrong user");
StartupReadinessCoordinator s=create();int g=ledger(s).record(Source.RADIO).generation;
s.onTrigger(Trigger.BOOT);check(ledger(s).record(Source.RADIO).generation==g,"late boot reset active generation");
Handler.until(4000);check(AppResolver.launches==1,"radio not staged once");
Ts18LauncherApplication.shared.radioReady=true;
android.app.ActivityManager.moved=()->{RootShell.top=RootShell.forComponent("com.cbkii.ts18launcher/.HomeAlias",10);s.onHomeResumed(new android.app.Activity());};
Handler.until(8000);check(android.app.ActivityManager.moves==1,"exact HOME not restored");check(ledger(s).record(Source.RADIO).state==State.READY,"content not ready");s.destroy();
StartupReadinessCoordinator unknown=create();RootShell.top="mCurrentFocus=null";Handler.until(15000);
check(AppResolver.launches==0&&android.app.ActivityManager.moves==0,"unknown focus mutated");unknown.destroy();
StartupReadinessCoordinator permission=create();Handler.until(4000);
RootShell.top=RootShell.forComponent("com.android.permissioncontroller/.GrantPermissionsActivity",20);
Ts18LauncherApplication.shared.radioReady=true;Handler.until(15000);
check(android.app.ActivityManager.moves==0,"permission screen stolen");permission.onTrigger(Trigger.SESSION_LOST);Handler.until(20000);
check(AppResolver.launches==1,"uncertain launch repeated");permission.destroy();
StartupReadinessCoordinator interrupted=create();Handler.until(4000);interrupted.noteUserInteraction();Ts18LauncherApplication.shared.radioReady=true;Handler.until(15000);
check(android.app.ActivityManager.moves==0&&Ts18LauncherApplication.shared.pauses==0,"user cancellation ignored");interrupted.destroy();
StartupReadinessCoordinator playing=create();Ts18LauncherApplication.shared.musicPlaying=true;Handler.until(15000);
check(AppResolver.launches==0,"playing music disrupted");playing.destroy();
StartupReadinessCoordinator off=create();Handler.until(4000);off.onScreenOff();Handler.until(15000);
check(android.app.ActivityManager.moves==0,"screen-off restore");off.destroy();
StartupReadinessCoordinator denied=create();MediaListenerService.access=false;Handler.until(15000);
check(AppResolver.launches==0,"unobservable radio launched");denied.destroy();
StartupReadinessCoordinator commands=create();Handler.until(4000);Ts18LauncherApplication.shared.commands++;Ts18LauncherApplication.shared.radioReady=true;Handler.until(15000);
check(android.app.ActivityManager.moves==0&&Ts18LauncherApplication.shared.pauses==0,"transport request overwritten");commands.destroy();
System.out.println("PASS startup boot dedupe/owners/exact HOME/permissions/unknown focus/user/audio/screen-off");
}}
'''
        }
        stubs['org/junit/Test.java'] = 'package org.junit;@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Test {}'
        stubs['org/junit/Assert.java'] = 'package org.junit;public class Assert {public static void assertTrue(boolean b){if(!b)throw new AssertionError();}public static void assertFalse(boolean b){assertTrue(!b);}public static void assertEquals(Object a,Object b){assertTrue(java.util.Objects.equals(a,b));}}'
        stubs['com/cbkii/ts18launcher/Harness.java'] = stubs['com/cbkii/ts18launcher/Harness.java'].replace(
            'StartupForegroundPolicy top=',
            'int tested=0;for(java.lang.reflect.Method m:StartupReadinessPolicyTest.class.getDeclaredMethods()){if(m.getAnnotation(org.junit.Test.class)!=null){m.invoke(new StartupReadinessPolicyTest());tested++;}}check(tested>=14,"policy coverage missing");StartupForegroundPolicy top=')
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name, content in stubs.items():
                p = root / name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(content)
            # Stub Context's identity mirrors the real standalone launcher.
            p = root / 'android/content/Context.java'
            p.write_text(p.read_text().replace('SharedPreferences prefs=', 'public String getPackageName(){return "com.cbkii.ts18launcher";} SharedPreferences prefs='))
            shutil.copyfile(ROOT / 'launcher/src/test/java/com/cbkii/ts18launcher/StartupReadinessPolicyTest.java', root / 'com/cbkii/ts18launcher/StartupReadinessPolicyTest.java')
            for name in ('StartupReadinessPolicy', 'StartupForegroundPolicy', 'StartupReadinessCoordinator'):
                shutil.copyfile(SRC / (name + '.java'), root / 'com/cbkii/ts18launcher' / (name + '.java'))
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(root), *map(str, root.rglob('*.java'))], check=True)
            subprocess.run(['java', '-cp', str(root), 'com.cbkii.ts18launcher.Harness'], check=True, timeout=30)
