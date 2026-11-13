"""Execute the Activity's M2 presentation path with a prior failed command."""
from pathlib import Path
import subprocess
import tempfile
import unittest
from test_music_identity import method
from test_navigation_controller_recovery import has_java_compiler

ROOT = Path(__file__).resolve().parents[1]

@unittest.skipUnless(has_java_compiler(), "JDK required")
class DirectCommandPresentationTest(unittest.TestCase):
    def test_old_command_error_cannot_hide_live_source_metadata(self):
        activity = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java").read_text()
        harness = '''
enum MediaSourceKey {MUSIC,RADIO} enum MediaEngine {DIRECT,LEGACY}
class MediaSourceState {int nativeState=2;long actions=4;boolean available,starting,preparing,startable;String packageName="com.tw.media";boolean live(){return available;}}
class MediaFeatureFacade {MediaSourceState music=new MediaSourceState(),radio=new MediaSourceState();
MediaEngine engine(MediaSourceKey k){return MediaEngine.DIRECT;}MediaSourceState state(MediaSourceKey k){return k==MediaSourceKey.MUSIC?music:radio;}}
class MediaSelection {String displayed(){return "music";}}
class MediaEngineSelector {static MediaSourceKey keyFor(String s){return MediaSourceKey.MUSIC;}}
class DirectMediaPresentation {static boolean skipEnabled(MediaSourceState s,boolean n){return s.available;}static boolean playPauseEnabled(MediaSourceState s){return s.available;}static String absentText(MediaSourceState s){return "Source unavailable";}}
class AppResolver {static String labelFor(Object c,String p,String s){return s;}}
class ImageButton {void setEnabled(boolean b){}void setTag(int n){}void setImageResource(int n){}void setContentDescription(String s){}}
class R {static class drawable{static int ic_media_connecting,ic_play,ic_media_unavailable;}static class string{static int media_source_radio,media_source_music;}}
class Metadata {String status="";void setStatus(String p,String s){status=p;}}
public class Harness {
MediaFeatureFacade facade=new MediaFeatureFacade();MediaFeatureFacade mediaFacade(){return facade;}
MediaSelection mediaSelection=new MediaSelection();ImageButton radioPlayPause=new ImageButton(),playPause=new ImageButton(),radioPrevious=new ImageButton(),mediaPrevious=new ImageButton(),radioNext=new ImageButton(),mediaNext=new ImageButton();
MediaSourceKey directFailureKey=MediaSourceKey.MUSIC;String directFailureText="Previous command refused";
Metadata mediaText=new Metadata();String getString(int id){return "Music";}
''' + method(activity, 'private void applyDirectSourcePresentation()') + '''
static void check(boolean b){if(!b)throw new AssertionError();}
public static void main(String[] args){var h=new Harness();
h.facade.music.available=true;h.applyDirectSourcePresentation();check(h.mediaText.status.isEmpty());
h.facade.music.available=false;h.applyDirectSourcePresentation();check(h.mediaText.status.equals("Previous command refused"));
h.directFailureText="";h.applyDirectSourcePresentation();check(!h.mediaText.status.isEmpty());
h.facade.music.available=true;h.applyDirectSourcePresentation();check(h.mediaText.status.isEmpty());
System.out.println("PASS actual Activity: live metadata survives old command error; absent source remains truthful");}}
'''
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            android = root / 'android/media/session/PlaybackState.java'
            android.parent.mkdir(parents=True,exist_ok=True)
            android.write_text('package android.media.session;public class PlaybackState {public static int STATE_NONE=0;public static long ACTION_PLAY=4,ACTION_PLAY_PAUSE=512;}')
            (root / 'Harness.java').write_text(harness)
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(root), str(root / 'Harness.java'), str(android)], check=True, timeout=30)
            subprocess.run(['java', '-cp', str(root), 'Harness'], check=True, timeout=10)
