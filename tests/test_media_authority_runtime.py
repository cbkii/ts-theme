"""Actual process observer -> Activity merge -> metadata view with deterministic API29 boundaries.
The worker and main queues are stepped explicitly; callback payloads and wrapper identities are adversarial.
"""
import ast
from pathlib import Path
import subprocess
import tempfile
import unittest
from test_radio_snapshot_merge import method

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'launcher/src/main/java/com/cbkii/ts18launcher'

class MediaAuthorityRuntimeTest(unittest.TestCase):
    def test_observer_to_effective_ui_transition_and_lifetimes(self):
        tree=ast.parse((ROOT/'tests/test_metadata_effective_snapshot.py').read_text())
        files=ast.literal_eval(next(n.value for n in ast.walk(tree) if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='stubs' for t in n.targets)))
        files.pop('com/cbkii/ts18launcher/Harness.java')
        files['android/widget/TextView.java']=files['android/widget/TextView.java'].replace('public void setText(String s){text=s;}', 'public int writes;public void setText(String s){text=s;writes++;}')
        doubles=files['com/cbkii/ts18launcher/Doubles.java']
        a=doubles.index('class MediaSelection');b=doubles.index('class AppResolver',a)
        listener=(SRC/'MediaListenerService.java').read_text()
        snapshot=method(listener,'public static final class Snapshot')
        helpers='\n'.join(method(listener,x) for x in ('private static String normalise(', 'private static boolean usesPauseAction(', 'static String radioDetails(', 'static String meaningfulRadioTitle(', 'private static boolean isRadioPlaceholder('))
        doubles=doubles[:a]+doubles[b:]
        doubles=doubles[:doubles.index('class LauncherPrefs')]+'''class LauncherPrefs {
static String KEY_MUSIC="music",MEDIA_MODE_PREFER_MUSIC="dedicated",mode="auto",remembered="A",configured="A";
static String lastMusicPackage(android.content.Context c){return remembered;}static String packageFor(android.content.Context c,String k){return configured;}
static String mediaMode(android.content.Context c){return mode;}}
class MediaSourceAdapter{static boolean allowsUnadvertisedSkip(String p){return false;}}
class MediaEventTrace{static void record(String... v){}}
'''
        doubles=doubles.replace('public void', 'public void')
        files['com/cbkii/ts18launcher/Doubles.java']=doubles
        lifecycle=method(listener, '@Override public void onListenerDisconnected()')+method(listener,'private static void publishEmpty(')
        files['com/cbkii/ts18launcher/MediaListenerService.java']='''package com.cbkii.ts18launcher;import android.media.MediaMetadata;import android.media.session.PlaybackState;
class MediaListenerService extends android.service.notification.NotificationListenerService {
static MediaListenerService instance;boolean connected;int listenerGeneration;
static Snapshot lastGeneric=new Snapshot("","","",false),lastRadio=lastGeneric;
void releaseAll(){}void detachSessionListener(){}static void notifyObservers(){}
enum Command {PREVIOUS,PLAY_PAUSE,NEXT}static boolean access=true;static boolean hasNotificationAccess(android.content.Context c){return access;}
'''+snapshot+helpers+lifecycle+'}'
        files['android/widget/ImageButton.java']='package android.widget;public class ImageButton{Object tag;public int icon;public String description;public Object getTag(){return tag;}public void setTag(Object value){tag=value;}public void setImageResource(int value){icon=value;}public void setContentDescription(String value){description=value;}}'
        files['android/service/notification/NotificationListenerService.java']='package android.service.notification;public class NotificationListenerService extends android.content.Context{public void onListenerDisconnected(){}}'
        files['com/cbkii/ts18launcher/MediaNotificationAccessBootstrapper.java']='package com.cbkii.ts18launcher;class MediaNotificationAccessBootstrapper{static void ensureConnected(android.content.Context c){}}'
        files.update({
'android/content/ComponentName.java':'package android.content;public class ComponentName{public ComponentName(Context c,Class<?> k){}}',
'android/content/Context.java':'''package android.content;public class Context {public static String MEDIA_SESSION_SERVICE="media";public static android.media.session.MediaSessionManager manager=new android.media.session.MediaSessionManager();public Context getApplicationContext(){return this;}public Object getSystemService(String s){return manager;}public android.content.res.Resources getResources(){return new android.content.res.Resources();}}''',
'android/os/Looper.java':'''package android.os;public class Looper{static Looper main=new Looper();public static boolean onMain=true;public static Looper getMainLooper(){return main;}public static Looper myLooper(){return onMain?main:null;}}''',
'android/os/SystemClock.java':'package android.os;public class SystemClock{public static long now=1000;public static long uptimeMillis(){return now;}}',
'android/os/Handler.java':'''package android.os;import java.util.*;public class Handler {
record Task(Runnable r,long at){}static List<Task> tasks=new ArrayList<>();public Handler(Looper l){}
public boolean post(Runnable r){return postDelayed(r,0);}public boolean postDelayed(Runnable r,long d){tasks.add(new Task(r,SystemClock.now+d));return true;}
public void removeCallbacks(Runnable r){tasks.removeIf(t->t.r==r);}public static boolean step(){Task next=null;for(Task t:tasks)if(t.at<=SystemClock.now&&(next==null||t.at<next.at))next=t;if(next==null)return false;tasks.remove(next);next.r.run();return true;}}
''',
'android/media/MediaDescription.java':'''package android.media;public class MediaDescription{String id,uri;public MediaDescription(String id){this.id=id;}public String getMediaId(){return id;}public String getMediaUri(){return uri;}}''',
'android/media/MediaMetadata.java':'''package android.media;import java.util.*;public class MediaMetadata{
public static final String METADATA_KEY_MEDIA_ID="id",METADATA_KEY_TITLE="title",METADATA_KEY_ARTIST="artist",METADATA_KEY_DISPLAY_TITLE="dt",METADATA_KEY_ALBUM_ARTIST="aa",METADATA_KEY_DISPLAY_SUBTITLE="ds",METADATA_KEY_DISPLAY_DESCRIPTION="dd",METADATA_KEY_ALBUM="album",METADATA_KEY_GENRE="genre",METADATA_KEY_MEDIA_URI="uri";
Map<String,String> values=new HashMap<>();public MediaMetadata(String id,String title){values.put("id",id);values.put("title",title);values.put("artist","Artist "+title);}public CharSequence getText(String k){return values.get(k);}}
''',
'android/media/session/PlaybackState.java':'''package android.media.session;public class PlaybackState{
public static final int STATE_NONE=0,STATE_STOPPED=1,STATE_PAUSED=2,STATE_PLAYING=3,STATE_BUFFERING=6,STATE_ERROR=7,STATE_CONNECTING=8;
public static final long ACTION_PAUSE=2,ACTION_PLAY=4,ACTION_SKIP_TO_PREVIOUS=16,ACTION_SKIP_TO_NEXT=32,ACTION_PLAY_PAUSE=512;
int state;long queue;public PlaybackState(int s,long q){state=s;queue=q;}public int getState(){return state;}public long getActions(){return 518;}public long getActiveQueueItemId(){return queue;}}
''',
'android/media/session/MediaSession.java':'''package android.media.session;public class MediaSession{public record Token(int id){}public static class QueueItem{public static final long UNKNOWN_ID=-1;long id;android.media.MediaDescription description;public QueueItem(long id,String mediaId){this.id=id;description=new android.media.MediaDescription(mediaId);}public long getQueueId(){return id;}public android.media.MediaDescription getDescription(){return description;}}}''',
'android/media/session/MediaController.java':'''package android.media.session;import java.util.*;import android.media.*;public class MediaController{
public static class Callback{public void onMetadataChanged(MediaMetadata m){}public void onPlaybackStateChanged(PlaybackState s){}public void onQueueChanged(List<MediaSession.QueueItem> q){}public void onSessionDestroyed(){}}
public final MediaSession.Token token;String pkg;public MediaMetadata metadata;public PlaybackState state;public List<MediaSession.QueueItem> queue=new ArrayList<>();public Callback callback;public int registrations,removals;public boolean fail,packageUnavailable;public Runnable duringRead;
public MediaController(String pkg,int token){this.pkg=pkg;this.token=new MediaSession.Token(token);}public String getPackageName(){if(packageUnavailable)throw new IllegalStateException();return pkg;}public MediaSession.Token getSessionToken(){return token;}
public MediaMetadata getMetadata(){if(android.os.Looper.onMain)throw new AssertionError("metadata Binder read on main");if(duringRead!=null){Runnable r=duringRead;duringRead=null;r.run();}return metadata;}
public PlaybackState getPlaybackState(){if(android.os.Looper.onMain)throw new AssertionError("state Binder read on main");return state;}public List<MediaSession.QueueItem> getQueue(){return queue;}
public void registerCallback(Callback cb,android.os.Handler h){registrations++;if(fail)throw new SecurityException();callback=cb;}public void unregisterCallback(Callback cb){if(callback==cb){callback=null;removals++;}}
public void track(String id,String title,int q,int s){metadata=id==null?null:new MediaMetadata(id,title);state=new PlaybackState(s,q);queue=List.of(new MediaSession.QueueItem(1,"a"),new MediaSession.QueueItem(2,"b"),new MediaSession.QueueItem(3,"c"));}
}''',
'android/media/session/MediaSessionManager.java':'''package android.media.session;import java.util.*;public class MediaSessionManager {public interface OnActiveSessionsChangedListener {void onActiveSessionsChanged(List<MediaController> c);}public boolean fail;public List<MediaController> active=new ArrayList<>();public OnActiveSessionsChangedListener listener;
public List<MediaController> getActiveSessions(android.content.ComponentName c){if(android.os.Looper.onMain)throw new AssertionError("membership Binder read on main");if(fail)throw new SecurityException();return active;}
public void addOnActiveSessionsChangedListener(OnActiveSessionsChangedListener l,android.content.ComponentName c,android.os.Handler h){listener=l;}public void removeOnActiveSessionsChangedListener(OnActiveSessionsChangedListener l){listener=null;}}
''',
'com/cbkii/ts18launcher/platform/TopwayAdapter.java':'package com.cbkii.ts18launcher.platform;public class TopwayAdapter{public static String defaultMusicPackage(android.content.Context c){return "A";}}',
        })
        # Resource IDs are distinct so the production presentation policy can be asserted.
        files['com/cbkii/ts18launcher/Doubles.java']=files['com/cbkii/ts18launcher/Doubles.java'].replace('class R{','''class R{static class drawable{static int ic_pause=1,ic_play=2,ic_media_buffering=3,ic_media_connecting=4,ic_media_unknown=5,ic_media_unavailable=6;}static class string{static int media_playing_pause=1,media_paused_play=2,media_buffering_pause=3,media_connecting_pause=4,media_unavailable=5,media_state_unknown=6;}''')
        activity=(SRC/'LauncherActivity.java').read_text()
        merge='\n'.join(method(activity,x) for x in ('private void updatePlaybackButton(', 'private static MediaListenerService.Snapshot mergeSessionSnapshot(', 'private static MediaListenerService.Snapshot normaliseSnapshot(', 'private static MediaListenerService.Snapshot emptyMediaSnapshot('))
        files['com/cbkii/ts18launcher/Harness.java']='''package com.cbkii.ts18launcher;
import android.widget.ImageButton;import java.util.*;import java.util.concurrent.*;import android.media.*;import android.media.session.*;import android.content.*;import android.os.*;
public class Harness {
'''+merge+'''
static class Worker extends AbstractExecutorService {Deque<Runnable> tasks=new ArrayDeque<>();boolean stopped;public void execute(Runnable r){tasks.add(r);}public void shutdown(){stopped=true;}public List<Runnable> shutdownNow(){stopped=true;tasks.clear();return List.of();}public boolean isShutdown(){return stopped;}public boolean isTerminated(){return stopped;}public boolean awaitTermination(long n,TimeUnit u){return stopped;}boolean step(){if(tasks.isEmpty())return false;Looper.onMain=false;try{tasks.remove().run();}finally{Looper.onMain=true;}return true;}}
String getString(int id,Object... values){return id+":"+values[0];}
static Harness activity=new Harness();static ImageButton button=new ImageButton();
static Worker worker=new Worker();static Context context=new Context();static MediaMetadataView view;static MediaListenerService.Snapshot effective;static int publications;
static void pump(){for(int n=0;n<1000;n++){if(Handler.step())continue;if(worker.step())continue;return;}throw new AssertionError("unbounded publication loop");}
static android.widget.TextView primary()throws Exception{var f=MediaMetadataView.class.getDeclaredField("primary");f.setAccessible(true);return (android.widget.TextView)f.get(view);}static String title()throws Exception{return primary().text;}
static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
static void refresh(){ProcessMediaSessionMonitor.refresh(context);pump();}
static void membership(MediaController... c){Context.manager.active=List.of(c);if(Context.manager.listener!=null)Context.manager.listener.onActiveSessionsChanged(List.of(c));else ProcessMediaSessionMonitor.refresh(context);pump();}
public static void main(String[] args)throws Exception {
ProcessMediaSessionMonitor monitor=new ProcessMediaSessionMonitor(context,worker);var owner=ProcessMediaSessionMonitor.class.getDeclaredField("instance");owner.setAccessible(true);owner.set(null,monitor);
view=new MediaMetadataView(context);
ProcessMediaSessionMonitor.Observer observer=(m,r,available)->{effective=mergeSessionSnapshot(m,new MediaListenerService.Snapshot("A","STALE","",3,2,"old"),available);view.setSnapshot(effective,"music");activity.updatePlaybackButton(button,effective,"Music");publications++;};
MediaController a=new MediaController("A",1);a.track("a","Track A",1,3);Context.manager.active=List.of(a);
ProcessMediaSessionMonitor.addObserver(context,observer);pump();check(title().equals("Track A"),"initial process -> effective UI");check(ProcessMediaSessionMonitor.selectedController("A")==a,"wrong callback/transport owner");long generation=effective.observationGeneration;
// Same session automatically advances; B's metadata has not arrived. A must disappear.
a.track(null,"",2,3);a.callback.onPlaybackStateChanged(a.state);pump();check(title().isEmpty(),"A masquerades as B during blank metadata");
a.track("b","Track B",2,3);a.callback.onMetadataChanged(a.metadata);pump();check(title().equals("Track B")&&effective.mediaId.equals("b")&&effective.queueItemId==2,"B not published through UI");check(effective.observationGeneration==generation,"item change mistaken for session change");
// Callback payload from A arrives late, then a publisher actually exposes A while queue still says B.
a.callback.onMetadataChanged(new MediaMetadata("a","OLD ART"));pump();check(title().equals("Track B"),"callback payload resurrected A");
a.metadata=new MediaMetadata("a","OLD ART");a.callback.onMetadataChanged(a.metadata);pump();check(title().isEmpty(),"old artwork metadata crossed queue identity");
a.track("b","Track B",2,3);refresh();check(title().equals("Track B"),"current B did not recover");
for(int state:new int[]{6,8,3,2,1}){a.track("b","Track B",2,state);a.callback.onPlaybackStateChanged(a.state);pump();check(effective.playing==(state==3),"false confirmed playback");check(MediaPlaybackPresentation.icon(state)==(state==3?1:state==6?3:state==8?4:2),"native state icon");check(button.icon==MediaPlaybackPresentation.icon(state)&&button.description.equals(MediaPlaybackPresentation.description(state)+":Music"),"actual Activity icon and accessibility diverged");if(state==6||state==8)check(MediaCommandPolicy.resolve(MediaListenerService.Command.PLAY_PAUSE,MediaCommandPolicy.usesPauseAction(state))==MediaCommandPolicy.Desired.PAUSE,"loading duplicate PLAY");}
// An item change with identical displayed text still resets the actual view's marquee text.
int writes=primary().writes;a.track("c","Track B",3,1);refresh();check(primary().writes==writes+1&&title().equals("Track B"),"same-title new ID did not reset marquee");
a.track("b","Track B",2,1);refresh();
// A mid-read transition cannot combine the old queue identity with new metadata.
a.duringRead=()->a.track("c","Track C",3,1);refresh();check(title().isEmpty(),"torn Binder observation published mixed track");refresh();check(title().equals("Track C"),"coherent transition did not recover");a.track("b","Track B",2,1);refresh();
// A framework getter failure yields unknown and does not strand the read worker.
a.packageUnavailable=true;refresh();check(title().isEmpty(),"failed controller identity published live text");a.packageUnavailable=false;refresh();check(title().equals("Track B"),"failed getter stranded future observation");
// Wrapper enumerations retain callback ownership; same-package playing token wins.
MediaController wrapper=new MediaController("A",1);wrapper.track("c","WRAPPER",3,3);membership(wrapper);check(ProcessMediaSessionMonitor.selectedController("A")==a&&wrapper.registrations==0,"fresh wrapper replaced registered owner");
MediaController a2=new MediaController("A",2);a2.track("b","Same title",2,3);membership(a,a2);check(ProcessMediaSessionMonitor.selectedController("A")==a2,"same-package playing token not selected");check(effective.observationGeneration!=generation,"replacement generation unchanged");
MediaController b=new MediaController("B",3);b.track("c","B playing",3,3);a2.track("b","A paused",2,2);membership(a2,b);check(effective.packageName.equals("B"),"AUTO remembered paused A beats playing B");
b.track("c","B playing",3,6);b.callback.onPlaybackStateChanged(b.state);pump();check(effective.packageName.equals("B"),"buffering switched to paused preference");
LauncherPrefs.mode="dedicated";refresh();check(effective.packageName.equals("A"),"dedicated A replaced by B");LauncherPrefs.mode="auto";b.track("c","B playing",3,3);
// A stale browser alternative must not override a live package's token.
MediaController staleBrowser=new MediaController("A",99);staleBrowser.track("a","STALE BROWSER",1,3);ProcessMediaSessionMonitor.observeExternal(context,staleBrowser);pump();check(ProcessMediaSessionMonitor.selectedController("A")==a2,"browser overrode live session");
// A failed active-session query must not promote the suppressed alternative browser token.
Context.manager.fail=true;refresh();check(title().isEmpty()&&ProcessMediaSessionMonitor.selectedController("A")==null,"failed membership query promoted stale browser authority");Context.manager.fail=false;refresh();
// The actual listener disconnect method clears static replay while the process publication survives.
MediaListenerService listener=new MediaListenerService();MediaListenerService.instance=listener;listener.connected=true;
MediaListenerService.lastGeneric=new MediaListenerService.Snapshot("A","STALE","",true);
listener.onListenerDisconnected();check(MediaListenerService.lastGeneric.isEmpty()&&!MediaListenerService.lastGeneric.playing,"disconnect retained listener static playback");
check(effective.packageName.equals("B"),"listener disconnect invalidated valid process data");
refresh();check(effective.packageName.equals("B"),"listener fallback changed process authority");check(!mergeSessionSnapshot(null,new MediaListenerService.Snapshot("A","STALE","",3,2,a.token),false).playing,"listener survives process unavailability");
// No removed Activity may receive a queued initial callback; new Activity recovers current state.
int before=publications;ProcessMediaSessionMonitor.removeObserver(observer);ProcessMediaSessionMonitor.addObserver(context,observer);ProcessMediaSessionMonitor.removeObserver(observer);pump();check(publications==before,"removed Activity got queued callback");ProcessMediaSessionMonitor.addObserver(context,observer);pump();check(effective.packageName.equals("B"),"recreated Activity failed recovery");
// In-flight result is invalidated by a newer callback. Never publish its old title.
ProcessMediaSessionMonitor.refresh(context);while(Handler.step()){};worker.step();while(Handler.step()){};worker.step(); // Old observation is now queued for publication.
b.track("c","NEW",3,3);b.callback.onMetadataChanged(new MediaMetadata("a","OLD"));pump();check(title().equals("NEW"),"stale worker publication replaced newer callback");
// A completed old read queued before the timeout also expires if the main looper was delayed.
ProcessMediaSessionMonitor.refresh(context);while(Handler.step()){};worker.step();while(Handler.step()){};worker.step();
SystemClock.now+=2001;Handler.step();check(title().isEmpty()&&ProcessMediaSessionMonitor.selectedController("B")==null,"expired queued result published before timeout message");pump();refresh();
// Bounded timeout: unknown UI/transport while Binder is stalled, no growing worker queue.
ProcessMediaSessionMonitor.refresh(context);while(Handler.step()){};SystemClock.now+=2001;while(Handler.step()){};check(title().isEmpty()&&ProcessMediaSessionMonitor.selectedController("B")==null,"timed-out read still authoritative");check(worker.tasks.size()==1,"unbounded worker backlog");pump();refresh();
// Destroyed owner's delayed callback cannot restore it. Fresh membership/reconnect must register anew.
var old=b.callback;old.onSessionDestroyed();pump();Context.manager.active=List.of(a2);old.onMetadataChanged(new MediaMetadata("c","GHOST"));pump();check(!title().equals("GHOST")&&ProcessMediaSessionMonitor.selectedController("B")==null,"destroyed session resurrected");
MediaListenerService.access=false;refresh();check(title().equals("STALE BROWSER")&&ProcessMediaSessionMonitor.selectedController("A")==staleBrowser,"access loss must only retain independently authorised browser");
ProcessMediaSessionMonitor.forgetExternal(staleBrowser);pump();check(ProcessMediaSessionMonitor.selectedController("A")==null,"forgotten browser retained");MediaListenerService.access=true;membership(a2);check(effective.packageName.equals("A"),"reconnection failed fresh observation");
ProcessMediaSessionMonitor.stop();pump();check(a2.callback==null,"process stop leaked registration");
System.out.println("PASS actual observer -> Activity merge -> metadata view: automatic transition, delayed callbacks/artwork, native state, selection, ownership, timeout, recreation and reconnect");}}
'''
        for name in ('ProcessMediaSessionMonitor','MediaItemObservation','MediaSelection','MediaCommandPolicy','MediaMetadataView','MediaMetadataPolicy','MediaTickerPolicy','MediaPlaybackPresentation','RadioFrequency'):
            files['com/cbkii/ts18launcher/'+name+'.java']=(SRC/(name+'.java')).read_text()
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp)
            for name,content in files.items():
                p=out/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content)
            subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',str(out),*map(str,out.rglob('*.java'))],check=True,timeout=30)
            subprocess.run(['java','-cp',str(out),'com.cbkii.ts18launcher.Harness'],check=True,timeout=30)
