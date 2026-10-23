"""Execute the production media coordinator against controlled Android/session doubles.

These prove dispatch/lifecycle contracts, not tuner, USB or audible TS18 behaviour.
"""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher"


class MediaValidationRepairsTest(unittest.TestCase):
    def test_cold_guard_exactly_once_and_observed_handover(self):
        if not shutil.which("java"):
            self.skipTest("Java unavailable")
        modules = subprocess.run(["java", "--list-modules"], capture_output=True, text=True, timeout=10)
        if modules.returncode != 0 or "jdk.compiler@" not in modules.stdout:
            self.skipTest("Java compiler module unavailable")
        stubs = {
            "android/content/Context.java": '''package android.content;
public class Context { public static String MEDIA_SESSION_SERVICE="media";
public Context getApplicationContext(){return this;} public Object getSystemService(String s){return new android.media.session.MediaSessionManager();}
public ComponentName startService(Intent i){return null;} public ComponentName startForegroundService(Intent i){return null;} }''',
            "android/content/ComponentName.java": "package android.content; public class ComponentName {public ComponentName(Context c,Class<?> t){}}",
            "android/content/Intent.java": "package android.content; public class Intent {}",
            "android/os/Bundle.java": "package android.os; public class Bundle {public java.util.Map<String,Long> values=new java.util.HashMap<>();public void putInt(String k,int v){values.put(k,(long)v);}public void putLong(String k,long v){values.put(k,v);}public long get(String k){return values.getOrDefault(k,-1L);}}",
            "android/os/SystemClock.java": "package android.os; public class SystemClock {public static long now=1000,elapsedOffset;public static long uptimeMillis(){return now;}public static long elapsedRealtime(){return now+elapsedOffset;}}",
            "android/os/Looper.java": "package android.os; public class Looper {static Looper l=new Looper();public static Looper getMainLooper(){return l;}public static Looper myLooper(){return l;}}",
            "android/os/Handler.java": '''package android.os; import java.util.*; public class Handler {
record Job(Runnable r,long time){} public static List<Job> jobs=new ArrayList<>(); public Handler(Looper l){}
public boolean post(Runnable r){r.run();return true;}public boolean postDelayed(Runnable r,long d){jobs.add(new Job(r,SystemClock.now+d));return true;}
public void removeCallbacks(Runnable r){jobs.removeIf(j->j.r()==r);} public void removeCallbacksAndMessages(Object o){jobs.clear();}
public static void until(long time){int count=0;while(!jobs.isEmpty()){
Job j=jobs.stream().min(Comparator.comparingLong(Job::time)).orElseThrow();if(j.time()>time)break;
if(++count>200)throw new AssertionError("unbounded");jobs.remove(j);SystemClock.now=j.time();j.r().run();}SystemClock.now=time;}}
''',
            "android/media/session/PlaybackState.java": '''package android.media.session; public class PlaybackState {
public static final int STATE_NONE=0,STATE_STOPPED=1,STATE_PAUSED=2,STATE_PLAYING=3,STATE_BUFFERING=6,STATE_ERROR=7,STATE_CONNECTING=8;
public static final long ACTION_PLAY=1,ACTION_PAUSE=2,ACTION_PLAY_PAUSE=4,ACTION_PREPARE=8,ACTION_SKIP_TO_PREVIOUS=16,ACTION_SKIP_TO_NEXT=32;
public int state=STATE_PAUSED;public int getState(){return state;}public long getActions(){return 63;}}
''',
            "android/media/session/MediaSession.java": "package android.media.session; public class MediaSession {public static class Token{}public static class QueueItem{}}",
            "android/media/MediaMetadata.java": "package android.media;public class MediaMetadata {public static String METADATA_KEY_MEDIA_ID=\"id\",METADATA_KEY_TITLE=\"title\",METADATA_KEY_DISPLAY_TITLE=\"display\";public String text;public String getString(String s){return text;}}",
            "android/media/session/MediaController.java": '''package android.media.session;
public class MediaController {public String pkg;public PlaybackState state=new PlaybackState();public int plays,pauses,customPlays,ordinaryPlays;public long lastDeadline;public boolean buffer,ignorePause;public long playDelay,pauseDelay;
public MediaController(android.content.Context c,MediaSession.Token t){}public MediaController(String p){pkg=p;}
public String getPackageName(){return pkg;}public PlaybackState getPlaybackState(){return state;}
public android.media.MediaMetadata metadata;public java.util.List<MediaSession.QueueItem> queue;public java.util.List<MediaSession.QueueItem> getQueue(){return queue;}public android.media.MediaMetadata getMetadata(){return metadata;}public MediaSession.Token getSessionToken(){return token;}private final MediaSession.Token token=new MediaSession.Token();
public void registerCallback(Callback c){}public void registerCallback(Callback c,android.os.Handler h){} public void unregisterCallback(Callback c){}
public Controls getTransportControls(){return new Controls();}public class Controls {public void sendCustomAction(String a,android.os.Bundle b){customPlays++;lastDeadline=b.get("deadline_elapsed_ms");long remaining=lastDeadline-android.os.SystemClock.elapsedRealtime();if(b.get("protocol")!=1||remaining<=0||remaining>5000)return;plays++;if(playDelay>0)new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->state.state=3,playDelay);else state.state=3;}
public void play(){ordinaryPlays++;plays++;if(playDelay>0)new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->state.state=3,playDelay);else state.state=buffer?6:3;}public void pause(){pauses++;if(!ignorePause){if(pauseDelay>0)new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->state.state=2,pauseDelay);else state.state=2;}}
public void prepare(){}public void skipToPrevious(){}public void skipToNext(){}}
public static class Callback {public void onPlaybackStateChanged(PlaybackState p){}public void onMetadataChanged(android.media.MediaMetadata m){}public void onQueueChanged(java.util.List<MediaSession.QueueItem> q){}public void onSessionDestroyed(){}} }
''',
            "android/media/session/MediaSessionManager.java": '''package android.media.session;import java.util.*;
public class MediaSessionManager {public static int queries;public static List<MediaController> sessions=new ArrayList<>();
public List<MediaController> getActiveSessions(android.content.ComponentName n){queries++;return sessions;}}''',
            "android/media/browse/MediaBrowser.java": '''package android.media.browse;public class MediaBrowser {
public static int failures,attempts;ConnectionCallback callback;
public MediaBrowser(android.content.Context c,android.content.ComponentName n,ConnectionCallback cb,android.os.Bundle b){callback=cb;}
public boolean isConnected(){return false;}public void connect(){attempts++;if(failures-->0)callback.onConnectionFailed();}public void disconnect(){}
public android.media.session.MediaSession.Token getSessionToken(){return null;}
public static class ConnectionCallback {public void onConnected(){}public void onConnectionSuspended(){}public void onConnectionFailed(){}}}''',
            "com/cbkii/ts18launcher/platform/TopwayAdapter.java": "package com.cbkii.ts18launcher.platform;public class TopwayAdapter {public static String defaultMusicPackage(android.content.Context c){return \"com.tw.media\";}}",
            "com/cbkii/ts18launcher/Doubles.java": '''package com.cbkii.ts18launcher;
class ProcessMediaSessionMonitor {static java.util.Map<String,android.media.session.MediaController> bound=new java.util.HashMap<>();
static android.media.session.MediaController selectedController(String p){for(var c:android.media.session.MediaSessionManager.sessions)if(p.equals(c.getPackageName()))return c;return bound.get(p);}
static void observeExternal(android.content.Context c,android.media.session.MediaController controller){bound.put(controller.getPackageName(),controller);}
static void forgetExternal(android.media.session.MediaController controller){bound.remove(controller.getPackageName(),controller);}interface Observer {void onProcessMediaStateChanged(MediaListenerService.Snapshot m,MediaListenerService.Snapshot r,boolean a);}static void addObserver(android.content.Context c,Observer o){}static void removeObserver(Observer o){}}
class MediaListenerService {static class Snapshot {}enum Command {PLAY_PAUSE,PREVIOUS,NEXT}
static boolean hasNotificationAccess(android.content.Context c){return true;} static void refreshActiveSessions(){}
static void observeExternalController(android.media.session.MediaController c){} static void forgetExternalController(android.media.session.MediaController c){} }
class MediaSourceAdapter {enum Kind{SESSION_ONLY,MEDIA_BROWSER,EXPLICIT_SERVICE} Kind kind=Kind.SESSION_ONLY;
static final String AUXIO_PACKAGE="com.tw.media",NAVRADIO_PACKAGE="com.navimods.radio";
String packageName;android.content.ComponentName service;boolean rootPrime,passiveWarmSafe,foregroundService;
static MediaSourceAdapter resolve(android.content.Context c,String p){MediaSourceAdapter a=new MediaSourceAdapter();a.packageName=p;return a;}
static boolean allowsUnadvertisedSkip(String p){return false;}String notReadyMessage(String s){return "not ready";}
String rootStartCommand(){return "";}android.content.Intent explicitServiceIntent(){return null;} }
class AuxioPreparationContract {static boolean paired;static boolean supported(android.content.Context c){return paired;}}
class MediaSelection {static String RADIO="radio";}
class LauncherPrefs {static String lastSource(android.content.Context c){return "music";}static String KEY_MUSIC="music";static String packageFor(android.content.Context c,String k){return "com.tw.media";}}
class RadioProvider {static String resolvePackage(android.content.Context c){return MediaSourceAdapter.NAVRADIO_PACKAGE;}}
class TestingProfiles {static String summary(android.content.Context c){return "";}static String radio(android.content.Context c){return "auto";}static boolean prepareAuxio(android.content.Context c){return false;}}
class MediaEventTrace {static void record(String a,String b,String... c){}}
class RootShell {static class Result{boolean completed;int exitCode;String output;boolean success(){return completed&&exitCode==0;}Result(boolean c,int e,String o){completed=c;exitCode=e;output=o;}}
static Result runMillis(String s,long t){return new Result(false,-1,"");}static Result runWithin(String s,long t){return runMillis(s,t);}}
''',
            "com/cbkii/ts18launcher/Harness.java": '''package com.cbkii.ts18launcher;
import android.media.session.*;import android.os.*;import java.lang.reflect.*;import java.util.*;
public class Harness {
static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
static class Inline extends java.util.concurrent.AbstractExecutorService {public void execute(Runnable r){r.run();}public void shutdown(){}public java.util.List<Runnable> shutdownNow(){return java.util.List.of();}public boolean isShutdown(){return false;}public boolean isTerminated(){return false;}public boolean awaitTermination(long t,java.util.concurrent.TimeUnit u){return true;}}
static class Holding extends Inline {java.util.List<Runnable> pending=new java.util.ArrayList<>();public void execute(Runnable r){pending.add(r);}}
static MediaSourceBootstrapper create()throws Exception{SystemClock.now=1000;SystemClock.elapsedOffset=0;Handler.jobs.clear();var b=new MediaSourceBootstrapper(new android.content.Context());var f=b.getClass().getDeclaredField("readinessWorker");f.setAccessible(true);((java.util.concurrent.ExecutorService)f.get(b)).shutdownNow();f.set(b,new Inline());b.refreshStartupObservation();return b;}
static void seedCold(MediaSourceBootstrapper b)throws Exception{
 Class<?> s=Class.forName("com.cbkii.ts18launcher.MediaSourceBootstrapper$ServiceStart");
 Constructor<?> c=s.getDeclaredConstructors()[0];c.setAccessible(true);Object start=c.newInstance(MediaSourceAdapter.resolve(null,MediaSourceAdapter.NAVRADIO_PACKAGE));
 Field at=s.getDeclaredField("lastAttemptMs");at.setAccessible(true);at.setLong(start,1000);
 Field f=MediaSourceBootstrapper.class.getDeclaredField("serviceStarts");f.setAccessible(true);((Map)f.get(b)).put(MediaSourceAdapter.NAVRADIO_PACKAGE,start);
}
public static void main(String[] args)throws Exception {
 MediaController music=new MediaController("com.tw.media"),radio=new MediaController("com.navimods.radio");
 MediaSessionManager.sessions=Arrays.asList(music,radio);music.state.state=3;
 MediaSourceBootstrapper b=create();seedCold(b);b.pausePackage(music.pkg);int[] result={0};
 b.command("Radio",radio.pkg,MediaListenerService.Command.PLAY_PAUSE,(ok,m)->result[0]=ok?1:-1);
 b.command("Radio",radio.pkg,MediaListenerService.Command.PLAY_PAUSE,(ok,m)->{});
 Handler.until(4199);check(radio.plays==0&&music.pauses==0,"cold guard/handover bypassed");
 Handler.until(5000);check(radio.plays==1&&music.pauses==1&&result[0]==1,"cold dispatch/coalescing/handover failed");b.destroy();
 b=create();music.state.state=3;radio.state.state=2;radio.buffer=true;result[0]=0;int pauses=music.pauses;
 b.pausePackage(music.pkg);b.command("Radio",radio.pkg,MediaListenerService.Command.PLAY_PAUSE,(ok,m)->result[0]=ok?1:-1);
 Handler.until(3000);check(result[0]==-1&&music.pauses==pauses,"buffering incorrectly committed handover");b.destroy();
 b=create();radio.state.state=2;radio.buffer=false;music.state.state=3;music.ignorePause=true;result[0]=0;
 b.pausePackage(music.pkg);b.command("Radio",radio.pkg,MediaListenerService.Command.PLAY_PAUSE,(ok,m)->result[0]=ok?1:-1);
 Handler.until(3000);check(result[0]==-1,"unacknowledged opposite Pause reported success");b.destroy();
 check(MediaSourceBootstrapper.commandTimeoutMs(radio.pkg,MediaCommandPolicy.Desired.PLAY,true)==4500+3200+3200,"handover budget truncated");
 check(MediaSourceBootstrapper.commandTimeoutMs(radio.pkg,MediaCommandPolicy.Desired.PAUSE,true)==4500+1600,"Pause has cold Play delay");
 check(MediaSourceBootstrapper.commandTimeoutMs(radio.pkg,MediaCommandPolicy.Desired.NEXT,false)==4500+1600,"Next has cold Play delay");
 // Late primary acknowledgement still leaves a full opposite Pause interval.
 b=create();music.state.state=3;music.ignorePause=false;music.pauseDelay=1300;radio.state.state=2;radio.playDelay=1500;result[0]=0;
 Class<?> pc=Class.forName("com.cbkii.ts18launcher.MediaSourceBootstrapper$Pending");
 Constructor<?> ctor=pc.getDeclaredConstructors()[0];ctor.setAccessible(true);
 MediaSourceBootstrapper.ResultCallback done=(ok,m)->result[0]=ok?1:-1;
 Object pending=ctor.newInstance("Radio",radio.pkg,MediaCommandPolicy.Desired.PLAY,done,
     1000+MediaSourceBootstrapper.commandTimeoutMs(radio.pkg,MediaCommandPolicy.Desired.PLAY,true));
 Field opposite=pc.getDeclaredField("pauseOnPlayPackage");opposite.setAccessible(true);opposite.set(pending,music.pkg);
 Method dispatch=MediaSourceBootstrapper.class.getDeclaredMethod("dispatchToController",MediaController.class,pc);dispatch.setAccessible(true);
 SystemClock.now=8500;int playsBefore=radio.plays,pausesBefore=music.pauses;dispatch.invoke(b,radio,pending);
 Handler.until(11400);check(result[0]==1&&radio.plays==playsBefore+1&&music.pauses==pausesBefore+1,"late Play starved opposite Pause");b.destroy();
 radio.playDelay=music.pauseDelay=0;
 b=create();radio.state.state=2;radio.metadata=new android.media.MediaMetadata();
 b.refreshStartupObservation();check(!b.hasUsableQueue(radio.pkg),"empty metadata marked ready");
 radio.metadata.text="ArtSound";b.refreshStartupObservation();check(b.hasUsableQueue(radio.pkg),"real station content not ready");
 radio.metadata.text="NavRadio+";b.refreshStartupObservation();check(!b.hasUsableQueue(radio.pkg),"placeholder marked ready");
 radio.metadata.text="Radio";b.refreshStartupObservation();check(!b.hasUsableQueue(radio.pkg),"generic placeholder marked ready");
 radio.metadata.text="ArtSound";b.refreshStartupObservation();int queries=MediaSessionManager.queries;
 for(int tick=0;tick<20;tick++){check(b.hasUsableQueue(radio.pkg),"cached readiness missing");b.startupPlaying(radio.pkg);b.startupSessionAbsent(music.pkg);}
 check(MediaSessionManager.queries==queries,"readiness tick issued Binder query");
 Holding delayed=new Holding();var worker=MediaSourceBootstrapper.class.getDeclaredField("readinessWorker");worker.setAccessible(true);worker.set(b,delayed);
 b.refreshStartupObservation();Handler.until(SystemClock.now+2000);check(!b.hasUsableQueue(radio.pkg),"timeout retained ready evidence");
 check(!b.startupSessionAbsent(radio.pkg),"unknown observation proved session death");
 check(b.hasSessionIdentity(radio.pkg),"unknown observation revoked a known token");
 delayed.pending.remove(0).run();check(!b.hasUsableQueue(radio.pkg),"late timed-out completion restored readiness");
 worker.set(b,new Inline());b.refreshStartupObservation();check(b.hasUsableQueue(radio.pkg),"new observation did not recover");
 int before=radio.pauses;long commands=b.userCommandGeneration();
 b.command("Radio",radio.pkg,MediaListenerService.Command.NEXT,(ok,m)->{});
 check(!b.pauseUnrequestedPlayback(radio.pkg,commands)&&radio.pauses==before,"startup overwrote newer user command");b.destroy();
 // A pre-suspend Play cannot acquire a fresh deadline after waking.
 b=create();AuxioPreparationContract.paired=true;music.metadata=new android.media.MediaMetadata();music.metadata.text="Saved track";Object expired=ctor.newInstance("Music",music.pkg,MediaCommandPolicy.Desired.PLAY,done,5500L);
 Method send=MediaSourceBootstrapper.class.getDeclaredMethod("sendPending",MediaController.class,pc);send.setAccessible(true);
 SystemClock.elapsedOffset=10000;int priorPlays=music.plays;
 check(!(Boolean)send.invoke(b,music,expired)&&music.plays==priorPlays,"suspend extended an old Play deadline");
 Field toggles=MediaSourceBootstrapper.class.getDeclaredField("inFlightToggle");toggles.setAccessible(true);
 ((Map)toggles.get(b)).put(music.pkg,expired);music.state.state=2;
 b.command("Music",music.pkg,MediaListenerService.Command.PLAY_PAUSE,(ok,m)->{});
 check(music.plays==priorPlays+1,"fresh wake tap coalesced into an expired Play");priorPlays=music.plays;
 b.refreshStartupObservation();Object fresh=ctor.newInstance("Music",music.pkg,MediaCommandPolicy.Desired.PLAY,done,5500L);
 check((Boolean)send.invoke(b,music,fresh)&&music.plays==priorPlays+1,"fresh post-wake Play was rejected");b.destroy();SystemClock.elapsedOffset=0;
 // Immediate warm PLAY must bypass the custom protocol completely.
 b=create();AuxioPreparationContract.paired=true;music.state.state=2;
 int ordinary=music.ordinaryPlays,custom=music.customPlays;
 b.command("Music",music.pkg,MediaListenerService.Command.PLAY_PAUSE,(ok,m)->{});
 check(music.ordinaryPlays==ordinary+1&&music.customPlays==custom,"warm PLAY did not use ordinary transport");b.destroy();
 // Real V1 receiver limits: both originating command budgets must be shortened, never renewed.
 for(long budget:new long[]{6100L,7700L}){
  b=create();AuxioPreparationContract.paired=true;music.metadata=null;music.queue=null;music.state.state=2;b.refreshStartupObservation();
  Object cold=ctor.newInstance("Music",music.pkg,MediaCommandPolicy.Desired.PLAY,done,SystemClock.uptimeMillis()+budget);
  long original=SystemClock.elapsedRealtime()+budget;int beforeCustom=music.customPlays;
  check((Boolean)send.invoke(b,music,cold),"cold PLAY not dispatched");
  check(music.customPlays==beforeCustom+1&&music.state.state==3,"paired receiver rejected cold PLAY");
  check(music.lastDeadline<=original&&music.lastDeadline-SystemClock.elapsedRealtime()==5000,"transport deadline not bounded");b.destroy();
 }
 // A cold tap joins preparation once; no PLAY before the prepared session appears.
 b=create();AuxioPreparationContract.paired=true;music.metadata=null;music.state.state=2;
 int[] prepared={0};b.setPreparationRequester((pkg,deadline)->prepared[0]++);int beforeCold=music.plays;
 b.command("Music",music.pkg,MediaListenerService.Command.PLAY_PAUSE,(ok,m)->{});
 check(prepared[0]==1&&music.plays==beforeCold,"cold tap bypassed preparation gate");
 music.metadata=new android.media.MediaMetadata();music.metadata.text="Restored track";Handler.until(1250);
 check(music.plays==beforeCold+1,"prepared cold tap did not PLAY");b.destroy();
 // Cold restore may acknowledge after the warm 1.6s interval, within the original deadline.
 b=create();AuxioPreparationContract.paired=true;music.metadata=null;music.state.state=2;music.playDelay=2500;result[0]=0;
 int coldDispatches=music.customPlays;
 b.command("Music",music.pkg,MediaListenerService.Command.PLAY_PAUSE,(ok,m)->result[0]=ok?1:-1);
 Handler.until(7000);check(result[0]==1&&music.customPlays==coldDispatches+1,"slow valid cold restore was failed or replayed");b.destroy();music.playDelay=0;
 // An active paused replacement token must outrank a stale bound browser token.
 b=create();music.state.state=2;music.metadata=new android.media.MediaMetadata();music.metadata.text="Current session";
 var stale=new MediaController("com.tw.media");stale.state.state=2;
 Class<?> connectionClass=Class.forName("com.cbkii.ts18launcher.MediaSourceBootstrapper$Connection");
 var connectionCtor=connectionClass.getDeclaredConstructors()[0];connectionCtor.setAccessible(true);
 Object connection=connectionCtor.newInstance(MediaSourceAdapter.resolve(null,music.pkg));
 var controllerField=connectionClass.getDeclaredField("controller");controllerField.setAccessible(true);controllerField.set(connection,stale);
 var connectionsField=MediaSourceBootstrapper.class.getDeclaredField("connections");connectionsField.setAccessible(true);((Map)connectionsField.get(b)).put(music.pkg,connection);
 b.refreshStartupObservation();int currentPlays=music.plays;
 b.command("Music",music.pkg,MediaListenerService.Command.PLAY_PAUSE,(ok,m)->{});
 check(music.plays==currentPlays+1&&stale.plays==0,"stale browser stole paused replacement transport");
 controllerField.set(connection,null);b.destroy();
 // Failed startup binds retry with backoff only inside the first preparation deadline.
 b=create();MediaSourceAdapter adapter=new MediaSourceAdapter();adapter.packageName="browser.test";adapter.kind=MediaSourceAdapter.Kind.MEDIA_BROWSER;
 android.media.browse.MediaBrowser.attempts=0;android.media.browse.MediaBrowser.failures=20;
 Method browser=MediaSourceBootstrapper.class.getDeclaredMethod("queueBrowser",MediaSourceAdapter.class,pc);browser.setAccessible(true);
 browser.invoke(b,adapter,null);Handler.until(6000);
 check(android.media.browse.MediaBrowser.attempts==3,"browser discovery unbounded or no retry");b.destroy();
 System.out.println("PASS cold guard/coalescing/buffering/handover/content/newer intent");
}}
''',
        }
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name, text in stubs.items():
                p = root / name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(text)
            for name in ("MediaSourceBootstrapper", "StartupMediaObservation", "MediaCommandPolicy", "MediaPreparationPolicy", "MediaAdmissionPolicy"):
                shutil.copyfile(SRC / (name + ".java"), root / "com/cbkii/ts18launcher" / (name + ".java"))
            subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-d", str(root),
                            *map(str, root.rglob("*.java"))], check=True)
            subprocess.run(["java", "-cp", str(root), "com.cbkii.ts18launcher.Harness"], check=True)
