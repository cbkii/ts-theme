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
        stubs = {
            "android/content/Context.java": '''package android.content;
public class Context { public static String MEDIA_SESSION_SERVICE="media";
public Context getApplicationContext(){return this;} public Object getSystemService(String s){return new android.media.session.MediaSessionManager();}
public ComponentName startService(Intent i){return null;} public ComponentName startForegroundService(Intent i){return null;} }''',
            "android/content/ComponentName.java": "package android.content; public class ComponentName {public ComponentName(Context c,Class<?> t){}}",
            "android/content/Intent.java": "package android.content; public class Intent {}",
            "android/os/Bundle.java": "package android.os; public class Bundle {}",
            "android/os/SystemClock.java": "package android.os; public class SystemClock {public static long now=1000;public static long uptimeMillis(){return now;}}",
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
            "android/media/session/MediaSession.java": "package android.media.session; public class MediaSession {public static class Token{}}",
            "android/media/MediaMetadata.java": "package android.media;public class MediaMetadata {public static String METADATA_KEY_MEDIA_ID=\"id\";public String getString(String s){return null;}}",
            "android/media/session/MediaController.java": '''package android.media.session;
public class MediaController {public String pkg;public PlaybackState state=new PlaybackState();public int plays,pauses;public boolean buffer,ignorePause;
public MediaController(android.content.Context c,MediaSession.Token t){}public MediaController(String p){pkg=p;}
public String getPackageName(){return pkg;}public PlaybackState getPlaybackState(){return state;}
public android.media.MediaMetadata getMetadata(){return null;}public MediaSession.Token getSessionToken(){return new MediaSession.Token();}
public void registerCallback(Callback c){} public void unregisterCallback(Callback c){}
public Controls getTransportControls(){return new Controls();}public class Controls {
public void play(){plays++;state.state=buffer?6:3;}public void pause(){pauses++;if(!ignorePause)state.state=2;}
public void prepare(){}public void skipToPrevious(){}public void skipToNext(){}}
public static class Callback {public void onPlaybackStateChanged(PlaybackState p){}public void onSessionDestroyed(){}} }
''',
            "android/media/session/MediaSessionManager.java": '''package android.media.session;import java.util.*;
public class MediaSessionManager {public static List<MediaController> sessions=new ArrayList<>();
public List<MediaController> getActiveSessions(android.content.ComponentName n){return sessions;}}''',
            "android/media/browse/MediaBrowser.java": '''package android.media.browse;public class MediaBrowser {
public MediaBrowser(android.content.Context c,android.content.ComponentName n,ConnectionCallback cb,android.os.Bundle b){}
public boolean isConnected(){return false;}public void connect(){}public void disconnect(){}
public android.media.session.MediaSession.Token getSessionToken(){return null;}
public static class ConnectionCallback {public void onConnected(){}public void onConnectionSuspended(){}public void onConnectionFailed(){}}}''',
            "com/cbkii/ts18launcher/platform/TopwayAdapter.java": "package com.cbkii.ts18launcher.platform;public class TopwayAdapter {public static String defaultMusicPackage(android.content.Context c){return \"com.tw.media\";}}",
            "com/cbkii/ts18launcher/Doubles.java": '''package com.cbkii.ts18launcher;
class MediaListenerService {enum Command {PLAY_PAUSE,PREVIOUS,NEXT}
static boolean hasNotificationAccess(android.content.Context c){return true;} static void refreshActiveSessions(){}
static void observeExternalController(android.media.session.MediaController c){} static void forgetExternalController(android.media.session.MediaController c){} }
class MediaSourceAdapter {enum Kind{SESSION_ONLY,MEDIA_BROWSER,EXPLICIT_SERVICE} Kind kind=Kind.SESSION_ONLY;
static final String AUXIO_PACKAGE="com.tw.media",NAVRADIO_PACKAGE="com.navimods.radio";
String packageName;android.content.ComponentName service;boolean rootPrime,passiveWarmSafe,foregroundService;
static MediaSourceAdapter resolve(android.content.Context c,String p){MediaSourceAdapter a=new MediaSourceAdapter();a.packageName=p;return a;}
static boolean allowsUnadvertisedSkip(String p){return false;}String notReadyMessage(String s){return "not ready";}
String rootStartCommand(){return "";}android.content.Intent explicitServiceIntent(){return null;} }
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
static MediaSourceBootstrapper create(){SystemClock.now=1000;Handler.jobs.clear();return new MediaSourceBootstrapper(new android.content.Context());}
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
 check((Long)MediaSourceBootstrapper.class.getDeclaredMethod("commandTimeoutMs",String.class).invoke(null,radio.pkg)>=4500+3200+1600,"radio budget truncated");
 System.out.println("PASS cold guard/coalescing/buffering/handover");
}}
''',
        }
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name, text in stubs.items():
                p = root / name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(text)
            for name in ("MediaSourceBootstrapper", "MediaCommandPolicy", "MediaPreparationPolicy", "MediaAdmissionPolicy"):
                shutil.copyfile(SRC / (name + ".java"), root / "com/cbkii/ts18launcher" / (name + ".java"))
            subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-d", str(root),
                            *map(str, root.rglob("*.java"))], check=True)
            subprocess.run(["java", "-cp", str(root), "com.cbkii.ts18launcher.Harness"], check=True)
