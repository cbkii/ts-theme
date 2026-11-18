"""Exercise actual Activity admission after an observed source-browser refusal."""
from pathlib import Path
import subprocess
import tempfile
import unittest
from test_music_identity import method
from test_navigation_controller_recovery import has_java_compiler

ROOT = Path(__file__).resolve().parents[1]


@unittest.skipUnless(has_java_compiler(), "JDK required")
class RefusedSourceStagingTest(unittest.TestCase):
    def test_refused_radio_changes_method_without_rebinding_or_resetting_deadline(self):
        source = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java").read_text()
        code = '''
enum MediaSourceKey {MUSIC,RADIO} enum MediaEngine {DIRECT,LEGACY}
class MediaSourceState {enum Availability {LIVE,CONNECTION_FAILED} Availability availability=Availability.CONNECTION_FAILED;}
class MediaListenerService {enum Command {PLAY_PAUSE,NEXT}}
class Facade {MediaEngine selected=MediaEngine.DIRECT;MediaSourceState value=new MediaSourceState();MediaEngine engine(MediaSourceKey k){return selected;}MediaSourceState state(MediaSourceKey k){return value;}}
class AppResolver {static String labelFor(Object c,String p,String l){return l;}}
class StartupReadinessCoordinator {static StartupReadinessCoordinator get(Object o){return new StartupReadinessCoordinator();}void onExplicitSource(String p){}}
class Metadata {void setMetadata(String a,String b){}}
public class Harness {
int mediaStatusGeneration;MediaSourceKey directFailureKey;String directFailureText;boolean mediaStatusActive;
Metadata mediaText=new Metadata();Facade facade=new Facade();Facade mediaFacade(){return facade;}
static final long TAP_BUDGET_MS=12000;int staging,dispatches;long until;boolean admitted=true;
boolean prepareSourceForCommand(MediaSourceKey k,String l,String p,MediaListenerService.Command c,int g,long d){staging++;until=d;return admitted;}
void dispatchSourceCommand(MediaSourceKey k,String l,String p,MediaListenerService.Command c,int g,long d,boolean staged){dispatches++;until=d;}
''' + method(source, 'private void runSourceCommand(') + '''
static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
public static void main(String[] args){
for(MediaSourceKey key:MediaSourceKey.values()){
Harness h=new Harness();h.runSourceCommand(key,"source","configured",MediaListenerService.Command.PLAY_PAUSE);
check(h.staging==1&&h.dispatches==0,"refused source retried the same browser method");
check(h.until==1000+TAP_BUDGET_MS,"staging reset the original tap deadline");
}
Harness live=new Harness();live.facade.value.availability=MediaSourceState.Availability.LIVE;
live.runSourceCommand(MediaSourceKey.RADIO,"Radio","configured",MediaListenerService.Command.PLAY_PAUSE);
check(live.staging==0&&live.dispatches==1,"healthy Radio was staged");
Harness legacy=new Harness();legacy.facade.selected=MediaEngine.LEGACY;
legacy.runSourceCommand(MediaSourceKey.RADIO,"Radio","configured",MediaListenerService.Command.PLAY_PAUSE);
check(legacy.staging==0&&legacy.dispatches==1,"Legacy source crossed into Direct staging");
Harness skip=new Harness();skip.runSourceCommand(MediaSourceKey.RADIO,"Radio","configured",MediaListenerService.Command.NEXT);
check(skip.staging==0&&skip.dispatches==1,"Next activated the source UI");
Harness denied=new Harness();denied.admitted=false;denied.runSourceCommand(MediaSourceKey.RADIO,"Radio","configured",MediaListenerService.Command.PLAY_PAUSE);
check(denied.staging==1&&denied.dispatches==1,"denied staging consumed normal source handling");
}}
'''
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'Harness.java').write_text(code)
            clock = root / 'android/os/SystemClock.java'
            clock.parent.mkdir(parents=True)
            clock.write_text('package android.os;public class SystemClock {public static long elapsedRealtime(){return 1000;}}')
            result = subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main',
                                     '-d', directory, str(root / 'Harness.java'), str(clock)],
                                    capture_output=True, text=True, timeout=30)
            self.assertEqual(0, result.returncode, result.stderr)
            result = subprocess.run(['java', '-cp', directory, 'Harness'],
                                    capture_output=True, text=True, timeout=10)
            self.assertEqual(0, result.returncode, result.stderr)
