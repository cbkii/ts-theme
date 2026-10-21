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
            'android/os/SystemClock.java': 'package android.os; public class SystemClock {public static long now=100; public static long uptimeMillis(){return now;}public static long elapsedRealtime(){return now;}}',
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
public static boolean commitOk=true;public static java.util.Map<String,String> strings=new java.util.HashMap<>();public String getString(String k,String d){return strings.getOrDefault(k,d);}long value=-1;public int getInt(String k,int d){return d;}public long getLong(String k,long d){return value;}public Editor edit(){return new Editor();}
public class Editor {public Editor remove(String k){strings.remove(k);return this;}public Editor putString(String k,String v){strings.put(k,v);return this;}public boolean commit(){return commitOk;}public Editor putLong(String k,long v){value=v;return this;}public void apply(){}}}''',
            'android/content/Context.java': '''package android.content;public class Context {
public static final String POWER_SERVICE="power",KEYGUARD_SERVICE="key",AUDIO_SERVICE="audio",ACTIVITY_SERVICE="activity";public static final int MODE_PRIVATE=0;
SharedPreferences prefs=new SharedPreferences();public Context getApplicationContext(){return this;}
public SharedPreferences getSharedPreferences(String s,int m){return prefs;}public Object getContentResolver(){return null;}
public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();}
public Object getSystemService(String s){return switch(s){case "power"->new android.os.PowerManager();case "key"->new android.app.KeyguardManager();case "audio"->new android.media.AudioManager();default->new android.app.ActivityManager();};}}
''',
            'android/app/Activity.java': 'package android.app;public class Activity extends android.content.Context {public boolean isFinishing(){return false;}public static boolean focused=true;public boolean hasWindowFocus(){return focused;}public int getTaskId(){return 10;}}',
            'android/app/ActivityManager.java': 'package android.app;public class ActivityManager {public static int moves;public static boolean reject,uncertain;public static Runnable moved=()->{};public void moveTaskToFront(int task,int flags){if(reject)throw new SecurityException();if(uncertain)throw new IllegalStateException();if(task!=10)throw new AssertionError("wrong HOME task");moves++;android.app.Activity.focused=true;moved.run();}}',
            'android/content/Intent.java': 'package android.content;public class Intent {String pkg;public Intent(String s){pkg=s;}public ComponentName getComponent(){return new ComponentName(pkg,pkg+".Main");}}',
            'android/content/ComponentName.java': 'package android.content;public class ComponentName {String p,c;public ComponentName(String p,String c){this.p=p;this.c=c;}public String flattenToShortString(){return p+"/"+c;}}',
            'android/content/pm/PackageManager.java': 'package android.content.pm;public class PackageManager {public android.content.Intent getLaunchIntentForPackage(String p){return new android.content.Intent(p);}}',
            'android/provider/Settings.java': 'package android.provider;public class Settings {public static class Global{public static String BOOT_COUNT="boot";public static int getInt(Object c,String k,int d){return 5;}}}',
            'com/cbkii/ts18launcher/platform/TopwayAdapter.java': 'package com.cbkii.ts18launcher.platform;public class TopwayAdapter {public static String defaultMusicPackage(android.content.Context c){return "com.tw.media";}}',
            'com/cbkii/ts18launcher/Doubles.java': '''package com.cbkii.ts18launcher;
class ProcessMediaSessionMonitor {interface Observer{void onProcessMediaStateChanged(MediaListenerService.Snapshot m,MediaListenerService.Snapshot r,boolean a);}static void addObserver(android.content.Context c,Observer o){}static void removeObserver(Observer o){}}
class MediaListenerService {static boolean access=true;static boolean hasNotificationAccess(android.content.Context c){return access;}static class Snapshot{}}
class Ts18LauncherApplication {static MediaSourceBootstrapper shared=new MediaSourceBootstrapper();static MediaSourceBootstrapper mediaSources(android.content.Context c){return shared;}}
class MediaSourceBootstrapper {interface PreparationRequester{void request(String p,long d);}void setPreparationRequester(PreparationRequester r){}boolean musicReady,radioReady,musicPlaying;long commands;int warms,pauses;
boolean hasUsableQueue(String p){return p.equals("com.tw.media")?musicReady:radioReady;}
boolean hasSessionIdentity(String p){return hasUsableQueue(p);}void warm(String p){warms++;}boolean isPlaying(String p){return p.equals("com.tw.media")&&musicPlaying;}
boolean startupSessionAbsent(String p){return !hasUsableQueue(p);}boolean startupPlaying(String p){return isPlaying(p);}void setStartupObserver(Runnable r){}long userCommandGeneration(){return commands;}String userCommandPackage(){return "com.tw.media";}boolean pauseUnrequestedPlayback(String p,long g){pauses++;return true;}}
class AuxioPreparationContract {static boolean capability=true;static boolean supported(android.content.Context c){return capability;}static String canonicalEntry(android.content.Context c,android.content.ComponentName n){return n.flattenToShortString();}}
class UiPersonalizationPrefs {static boolean enabled=true;static boolean mediaStartupWarmup(android.content.Context c){return enabled;}}
class TestingProfiles {static String mode="auto";static String startup(android.content.Context c){return mode;}}
class LauncherPrefs {static String KEY_MUSIC="music",KEY_NAV="nav";static String music="com.tw.media";
static String packageFor(android.content.Context c,String k){return k.equals("music")?music:"app.organicmaps.incar";}}
class RadioProvider {static String resolvePackage(android.content.Context c){return "com.navimods.radio";}}
class NavigationProvider {static String ORGANIC_MAPS_INCAR="app.organicmaps.incar";static boolean hasLauncherActivity(android.content.Context c,String p){return true;}}
class MediaSourceAdapter {static String AUXIO_PACKAGE="com.tw.media",NAVRADIO_PACKAGE="com.navimods.radio";}
class HomeMode {static boolean isDefaultHome(android.content.Context c){return true;}}
class AndroidUserId {static int current(){return 0;}}
class MediaEventTrace {static void record(String a,String b,String... c){}}
class AppResolver {enum LaunchOutcome {ACCEPTED,REJECTED,UNCERTAIN}static LaunchOutcome outcome=LaunchOutcome.ACCEPTED;static int launches;static Runnable launched=()->{};
static LaunchOutcome launchForStartup(android.content.Context c,String p){launches++;
if(outcome==LaunchOutcome.ACCEPTED){RootShell.top=RootShell.forComponent(p+"/"+p+".Main",20);launched.run();}return outcome;}}
class NavigationHelperResult {boolean success;int userId=0,taskId=10;String component="";String code;NavigationHelperResult(boolean s,String c){success=s;code=c;}}
class StartupRootFallback {static String code="STARTUP_SOURCE_DISPATCHED",returnCode="STARTUP_HOME_RESTORED";static int starts,returns;static Runnable before=()->{},launched=()->{};StartupRootFallback(android.content.Context c){}NavigationHelperResult observe(){return null;}
NavigationHelperResult launch(String p,String e,int h){starts++;before.run();if(code.equals("STARTUP_SOURCE_DISPATCHED")){RootShell.top=RootShell.forComponent(e,20);launched.run();}return new NavigationHelperResult(code.equals("STARTUP_SOURCE_DISPATCHED"),code);}
NavigationHelperResult restoreHome(String p,String e,int t,int h){returns++;if(returnCode.equals("STARTUP_HOME_RESTORED"))android.app.Activity.focused=true;android.app.ActivityManager.moved.run();return new NavigationHelperResult(returnCode.equals("STARTUP_HOME_RESTORED"),returnCode);}
static boolean definitelyNotStarted(NavigationHelperResult r){return r.code.equals("STARTUP_SOURCE_REJECTED");}}

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
static boolean navigationDeferred, navReady;
static int navigationStarts;static boolean coldMusic, coldHomeUnfocused;
static class Inline extends AbstractExecutorService {public void execute(Runnable r){r.run();}public void shutdown(){}public java.util.List<Runnable> shutdownNow(){return java.util.List.of();}public boolean isShutdown(){return false;}public boolean isTerminated(){return false;}public boolean awaitTermination(long t,TimeUnit u){return true;}}
static StartupReadinessCoordinator create()throws Exception{return create(true);}
static StartupReadinessCoordinator create(boolean ready)throws Exception{return create(ready,true);}
static StartupReadinessCoordinator create(boolean ready,boolean clear)throws Exception{
navReady=ready;navigationStarts=0;AuxioPreparationContract.capability=true;
Handler.reset();if(clear)android.content.SharedPreferences.strings.clear();android.content.SharedPreferences.commitOk=true;android.app.Activity.focused=!coldHomeUnfocused;navigationDeferred=false;TestingProfiles.mode="auto";AppResolver.outcome=AppResolver.LaunchOutcome.ACCEPTED;
StartupRootFallback.returnCode="STARTUP_HOME_RESTORED";android.app.ActivityManager.moved=()->{};
StartupRootFallback.code="STARTUP_SOURCE_DISPATCHED";StartupRootFallback.starts=0;StartupRootFallback.returns=0;StartupRootFallback.before=()->{};
android.app.ActivityManager.reject=false;android.app.ActivityManager.uncertain=false;
MediaListenerService.access=true;RootShell.probes=0;AppResolver.launches=0;android.app.ActivityManager.moves=0;RootShell.afterProbe=()->{};
RootShell.top=RootShell.forComponent("com.cbkii.ts18launcher/.HomeAlias",10);
Ts18LauncherApplication.shared=new MediaSourceBootstrapper();Ts18LauncherApplication.shared.musicReady=!coldMusic;Ts18LauncherApplication.shared.radioReady=coldMusic;
Constructor<StartupReadinessCoordinator> c=StartupReadinessCoordinator.class.getDeclaredConstructor(android.content.Context.class);c.setAccessible(true);
StartupReadinessCoordinator s=c.newInstance(new android.content.Context());Field f=s.getClass().getDeclaredField("observer");f.setAccessible(true);((ExecutorService)f.get(s)).shutdownNow();f.set(s,new Inline());AppResolver.launched=s::onHomePaused;StartupRootFallback.launched=s::onHomePaused;
if(coldMusic)s.onTrigger(Trigger.RESTART);
s.attachNavigationHook(new StartupReadinessCoordinator.NavigationHook(){public boolean ready(){return navReady;}public boolean busy(){return false;}public void reconcile(){check(!navigationDeferred,"Maps acquired while media pending");navigationStarts++;navReady=true;android.app.Activity.focused=false;}
public void deferAutomaticPresentation(boolean defer){navigationDeferred=defer;}});
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
check(navigationDeferred,"qualified pending stage did not reserve foreground lane");
Handler.until(4000);check(AppResolver.launches==1&&navigationDeferred,"radio stage did not hold navigation presentation");
Ts18LauncherApplication.shared.radioReady=true;
android.app.ActivityManager.moved=()->{RootShell.top=RootShell.forComponent("com.cbkii.ts18launcher/.HomeAlias",10);s.onHomeResumed(new android.app.Activity());};
Handler.until(8000);check(android.app.ActivityManager.moves==1,"exact HOME not restored");check(ledger(s).record(Source.RADIO).state==State.READY,"content not ready");check(!navigationDeferred,"Maps stayed deferred after media");s.destroy();
StartupReadinessCoordinator ordered=create(false);Handler.until(4000);
check(navigationStarts==1&&AppResolver.launches==0,"Radio postponed the primary map surface");
Ts18LauncherApplication.shared.radioReady=true;android.app.ActivityManager.moved=()->ordered.onHomeResumed(new android.app.Activity());
Handler.until(8000);check(navigationStarts==1&&!navigationDeferred,"Maps presentation was repeated after background Radio readiness");ordered.destroy();
StartupReadinessCoordinator root=create();AppResolver.outcome=AppResolver.LaunchOutcome.REJECTED;Handler.until(4000);
check(AppResolver.launches==1&&StartupRootFallback.starts==1,"definite rejection did not select root once");root.destroy();
StartupReadinessCoordinator rootFirst=create();TestingProfiles.mode="aggressive";Handler.until(4000);
check(AppResolver.launches==0&&StartupRootFallback.starts==1,"aggressive did not select root first");rootFirst.destroy();
StartupReadinessCoordinator fallback=create();TestingProfiles.mode="aggressive";StartupRootFallback.code="STARTUP_SOURCE_REJECTED";Handler.until(4000);
check(AppResolver.launches==1&&StartupRootFallback.starts==1,"definite root refusal did not allow normal once");fallback.destroy();
StartupReadinessCoordinator ambiguous=create();TestingProfiles.mode="aggressive";StartupRootFallback.code="PHASE_TIMEOUT";Handler.until(15000);
check(AppResolver.launches==0&&StartupRootFallback.starts==1,"root uncertainty replayed normal launch");ambiguous.destroy();
StartupReadinessCoordinator normalUnknown=create();AppResolver.outcome=AppResolver.LaunchOutcome.UNCERTAIN;Handler.until(15000);
check(StartupRootFallback.starts==0,"uncertain normal start replayed via root");normalUnknown.destroy();
StartupReadinessCoordinator homeRejected=create();Handler.until(4000);Ts18LauncherApplication.shared.radioReady=true;
android.app.ActivityManager.reject=true;android.app.ActivityManager.moved=()->homeRejected.onHomeResumed(new android.app.Activity());Handler.until(8000);
check(StartupRootFallback.returns==1&&ledger(homeRejected).record(Source.RADIO).state==State.READY,"definite HOME refusal not recovered");homeRejected.destroy();
StartupReadinessCoordinator rootHomeFailure=create();Handler.until(4000);Ts18LauncherApplication.shared.radioReady=true;
android.app.ActivityManager.reject=true;StartupRootFallback.returnCode="HOME_FOCUS_FAILED";Handler.until(15000);
check(StartupRootFallback.returns==1&&ledger(rootHomeFailure).record(Source.RADIO).state==State.UNCERTAIN,"failed root return marked ready");rootHomeFailure.destroy();
StartupReadinessCoordinator noHomeReturn=create();Handler.until(4000);Ts18LauncherApplication.shared.radioReady=true;Handler.until(15000);
check(StartupRootFallback.returns==0&&ledger(noHomeReturn).record(Source.RADIO).state==State.UNCERTAIN,"silent/unconfirmed return repeated or marked ready");noHomeReturn.destroy();
StartupReadinessCoordinator cancelledRoot=create();AppResolver.outcome=AppResolver.LaunchOutcome.REJECTED;RootShell.afterProbe=cancelledRoot::noteExternalNavigation;Handler.until(15000);
check(StartupRootFallback.starts==0&&AppResolver.launches==0,"cancelled executor admitted source");cancelledRoot.destroy();
StartupReadinessCoordinator homeUncertain=create();Handler.until(4000);Ts18LauncherApplication.shared.radioReady=true;android.app.ActivityManager.uncertain=true;Handler.until(15000);
check(StartupRootFallback.returns==0&&ledger(homeUncertain).record(Source.RADIO).state==State.UNCERTAIN,"uncertain HOME return replayed");homeUncertain.destroy();
StartupReadinessCoordinator unknown=create();RootShell.top="mCurrentFocus=null";Handler.until(15000);
check(AppResolver.launches==0&&android.app.ActivityManager.moves==0,"unknown focus mutated");unknown.destroy();
StartupReadinessCoordinator permission=create();Handler.until(4000);
RootShell.top=RootShell.forComponent("com.android.permissioncontroller/.GrantPermissionsActivity",20);
Ts18LauncherApplication.shared.radioReady=true;Handler.until(15000);
check(android.app.ActivityManager.moves==0,"permission screen stolen");permission.onTrigger(Trigger.SESSION_LOST);Handler.until(20000);
check(AppResolver.launches==1,"uncertain launch repeated");permission.destroy();
StartupReadinessCoordinator interrupted=create();Handler.until(4000);interrupted.noteExternalNavigation();check(!navigationDeferred,"user navigation stayed blocked");Ts18LauncherApplication.shared.radioReady=true;Handler.until(15000);
check(android.app.ActivityManager.moves==0&&Ts18LauncherApplication.shared.pauses==0,"user cancellation ignored");interrupted.destroy();
StartupReadinessCoordinator playing=create();Ts18LauncherApplication.shared.musicPlaying=true;Handler.until(15000);
check(AppResolver.launches==0,"playing music disrupted");playing.destroy();
StartupReadinessCoordinator off=create();Handler.until(4000);off.onScreenOff();Handler.until(15000);
check(android.app.ActivityManager.moves==0,"screen-off restore");off.destroy();
StartupReadinessCoordinator denied=create();MediaListenerService.access=false;Handler.until(15000);
check(AppResolver.launches==0,"unobservable radio launched");denied.destroy();
StartupReadinessCoordinator commands=create();Handler.until(4000);Ts18LauncherApplication.shared.commands++;Ts18LauncherApplication.shared.radioReady=true;Handler.until(15000);
check(android.app.ActivityManager.moves==0&&Ts18LauncherApplication.shared.pauses==0,"transport request overwritten");commands.destroy();
UiPersonalizationPrefs.enabled=false;
StartupReadinessCoordinator navOnly=create(false);Handler.until(15000);
check(navigationStarts==1&&AppResolver.launches==0,"media opt-out suppressed navigation or staged media");navOnly.destroy();
// An active media lane invalidates the old wait; navigation gets a fresh budget on release.
StartupReadinessCoordinator deferred=create(false);navReady=false;
Field busy=deferred.getClass().getDeclaredField("foregroundBusy");busy.setAccessible(true);busy.setBoolean(deferred,true);
java.lang.reflect.Method update=deferred.getClass().getDeclaredMethod("updateNavigationAdmission");update.setAccessible(true);update.invoke(deferred);
Handler.until(22000);check(ledger(deferred).record(Source.NAVIGATION).state==State.STARTING,"deferred navigation wait expired");
busy.setBoolean(deferred,false);update.invoke(deferred);check(navigationStarts==2,"navigation observation not restarted after deferral");
Handler.until(22500);check(ledger(deferred).record(Source.NAVIGATION).state==State.READY,"released navigation did not become ready");deferred.destroy();
UiPersonalizationPrefs.enabled=true;
StartupReadinessCoordinator lost=create();lost.reportNavigation(true,"");lost.reportNavigation(false,"surface-detached");
check(ledger(lost).record(Source.NAVIGATION).state==State.UNCERTAIN,"readiness loss retained READY");lost.destroy();
// A new process loses its in-memory ledger, but must retain an uncertain admission.
StartupReadinessCoordinator died=create();Handler.until(4000);check(AppResolver.launches==1,"first admission missing");died.destroy();
StartupReadinessCoordinator reborn=create(true,false);Handler.until(30000);
check(AppResolver.launches==0&&StartupRootFallback.starts==0,"process death replayed claimed Activity");
reborn.onTrigger(Trigger.STORAGE_MOUNT);Handler.until(35000);check(AppResolver.launches==0,"late storage replayed uncertain radio admission");reborn.destroy();
StartupReadinessCoordinator diskFailure=create();android.content.SharedPreferences.commitOk=false;Handler.until(15000);
check(AppResolver.launches==0&&StartupRootFallback.starts==0,"journal failure admitted Activity");diskFailure.destroy();
StartupReadinessCoordinator noFocus=create(false);android.app.Activity.focused=false;noFocus.onHomeFocusChanged();
check(!navigationDeferred&&navigationStarts==1,"ineligible media kept navigation reserved");noFocus.destroy();
// Actual boot ordering: reserve Music before the HOME resume callback and let Maps
// proceed after the short Activity handoff, without waiting for full library readiness.
coldMusic=true;
StartupReadinessCoordinator immediate=create(false);
check(navigationDeferred&&navigationStarts==0,"Maps raced the pre-resume Music lease");
android.app.ActivityManager.moved=()->{RootShell.top=RootShell.forComponent("com.cbkii.ts18launcher/.HomeAlias",10);immediate.onHomeResumed(new android.app.Activity());};
immediate.noteUserInteraction();Handler.until(800);
check(StartupRootFallback.starts==1&&AppResolver.launches==0,"cold Music did not choose root immediately");
check(StartupRootFallback.returns==1&&navigationStarts==1,"Music held Maps for library readiness");
Ts18LauncherApplication.shared.musicReady=true;immediate.onProcessMediaStateChanged(null,null,true);Handler.until(1000);
check(ledger(immediate).record(Source.MUSIC).state==State.READY&&StartupRootFallback.starts==1,"late readiness replayed Music Activity");immediate.destroy();
// Cold Activity admission may return before its asynchronous Main handoff is resumed.
StartupReadinessCoordinator delayed=create(false);
StartupRootFallback.launched=()->{delayed.onHomePaused();RootShell.top=RootShell.forComponent("com.cbkii.ts18launcher/.HomeAlias",10);Handler h=new Handler(Looper.getMainLooper());h.postDelayed(()->RootShell.top=RootShell.forComponent("com.tw.media/com.tw.media.Main",20),900);};
android.app.ActivityManager.moved=()->{RootShell.top=RootShell.forComponent("com.cbkii.ts18launcher/.HomeAlias",10);delayed.onHomeResumed(new android.app.Activity());};
Handler.until(700);check(StartupRootFallback.starts==1&&StartupRootFallback.returns==0&&navigationDeferred,"slow handoff abandoned or replayed early");
Handler.until(1800);check(StartupRootFallback.starts==1&&StartupRootFallback.returns==1&&navigationStarts==1,"slow cold handoff did not restore HOME and release Maps");delayed.destroy();
StartupReadinessCoordinator foreignDuringHandoff=create(false);
StartupRootFallback.launched=()->{foreignDuringHandoff.onHomePaused();RootShell.top=RootShell.forComponent("com.android.permissioncontroller/.GrantPermissionsActivity",20);};
Handler.until(1500);check(StartupRootFallback.starts==1&&StartupRootFallback.returns==0&&!navigationDeferred,"foreign cold handoff focus stolen or lane retained");foreignDuringHandoff.destroy();
StartupReadinessCoordinator absentEntry=create(false);
StartupRootFallback.launched=()->{RootShell.top=RootShell.forComponent("com.cbkii.ts18launcher/.HomeAlias",10);};
Handler.until(6000);check(StartupRootFallback.starts==1&&StartupRootFallback.returns==0&&!navigationDeferred,"absent accepted entry was replayed or waited forever");absentEntry.destroy();
// Android resumes a new HOME before delivering its first real window-focus callback.
coldHomeUnfocused=true;
StartupReadinessCoordinator firstFocus=create(false);
check(navigationDeferred&&navigationStarts==0,"Maps raced HOME resume before first focus");
Handler.until(600);check(StartupRootFallback.starts==0&&navigationStarts==0,"unfocused HOME dispatched work");
android.app.ActivityManager.moved=()->{RootShell.top=RootShell.forComponent("com.cbkii.ts18launcher/.HomeAlias",10);firstFocus.onHomeResumed(new android.app.Activity());};
android.app.Activity.focused=true;firstFocus.onHomeFocusChanged();Handler.until(1400);
check(StartupRootFallback.starts==1&&StartupRootFallback.returns==1&&navigationStarts==1,"first HOME focus did not release Music then Maps");firstFocus.destroy();
StartupReadinessCoordinator noFirstFocus=create(false);Handler.until(1800);
check(StartupRootFallback.starts==0&&!navigationDeferred&&navigationStarts==1,"missing first focus held Maps indefinitely");noFirstFocus.destroy();coldHomeUnfocused=false;
StartupReadinessCoordinator unsupported=create(false);AuxioPreparationContract.capability=false;Handler.until(800);
check(StartupRootFallback.starts==0&&AppResolver.launches==0&&navigationStarts==1,"old Auxio was automated or blocked Maps");unsupported.destroy();
StartupReadinessCoordinator explicitPlay=create(false);
android.app.ActivityManager.moved=()->{RootShell.top=RootShell.forComponent("com.cbkii.ts18launcher/.HomeAlias",10);explicitPlay.onHomeResumed(new android.app.Activity());};
Handler.until(200);Ts18LauncherApplication.shared.commands++;Ts18LauncherApplication.shared.musicReady=true;Ts18LauncherApplication.shared.musicPlaying=true;
Handler.until(900);check(StartupRootFallback.returns==1&&ledger(explicitPlay).record(Source.MUSIC).state==State.ACTIVE&&Ts18LauncherApplication.shared.pauses==0,"live Play was cancelled or paused by preparation");explicitPlay.destroy();
StartupReadinessCoordinator preDispatch=create(false);RootShell.afterProbe=preDispatch::noteExternalNavigation;Handler.until(800);
check(StartupRootFallback.starts==0,"cancelled reservation dispatched a source");RootShell.afterProbe=()->{};
android.app.ActivityManager.moved=()->{RootShell.top=RootShell.forComponent("com.cbkii.ts18launcher/.HomeAlias",10);preDispatch.onHomeResumed(new android.app.Activity());};
preDispatch.onHomeResumed(new android.app.Activity());Handler.until(3000);
check(StartupRootFallback.starts==1&&StartupRootFallback.returns==1,"safe pre-dispatch cancellation blocked this boot: starts="+StartupRootFallback.starts+" returns="+StartupRootFallback.returns+" "+preDispatch.summary());preDispatch.destroy();coldMusic=false;
// Passive warm-up OFF must not block one explicit cold Play preparation.
coldMusic=true;StartupReadinessCoordinator requested=create(false);UiPersonalizationPrefs.enabled=false;
requested.prepareForPlay("com.tw.media",SystemClock.elapsedRealtime()+6100);
requested.prepareForPlay("com.tw.media",SystemClock.elapsedRealtime()+6100);
android.app.ActivityManager.moved=()->{RootShell.top=RootShell.forComponent("com.cbkii.ts18launcher/.HomeAlias",10);requested.onHomeResumed(new android.app.Activity());};
Handler.until(1200);check(StartupRootFallback.starts==1&&StartupRootFallback.returns==1,"explicit Play duplicated or skipped source staging");
requested.destroy();UiPersonalizationPrefs.enabled=true;
StartupReadinessCoordinator isolated=create(true);UiPersonalizationPrefs.enabled=false;
Ts18LauncherApplication.shared.radioReady=false;ledger(isolated).begin(Source.RADIO,Trigger.READINESS_CHANGED,StartupReadinessPolicy.Method.ACTIVITY_BOOTSTRAP,SystemClock.uptimeMillis());
var queueField=StartupReadinessCoordinator.class.getDeclaredField("foregroundQueue");queueField.setAccessible(true);
((java.util.ArrayDeque<Source>)queueField.get(isolated)).addLast(Source.RADIO);
isolated.prepareForPlay("com.tw.media",SystemClock.elapsedRealtime()+6100);
android.app.ActivityManager.moved=()->{RootShell.top=RootShell.forComponent("com.cbkii.ts18launcher/.HomeAlias",10);isolated.onHomeResumed(new android.app.Activity());};
Handler.until(1200);check(StartupRootFallback.starts==1&&AppResolver.launches==0,"explicit Music preparation authorised disabled Radio staging");
isolated.destroy();UiPersonalizationPrefs.enabled=true;coldMusic=false;
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
