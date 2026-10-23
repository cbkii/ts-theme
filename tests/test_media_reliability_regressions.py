"""Production snapshot and selection regressions; no Android runtime or timing sleeps."""
from pathlib import Path
import subprocess
import tempfile
import unittest
from test_radio_snapshot_merge import method

SRC = Path(__file__).resolve().parents[1] / 'launcher/src/main/java/com/cbkii/ts18launcher'

class MediaReliabilityRegressionTest(unittest.TestCase):
    def test_transition_state_and_auto_selection(self):
        listener = (SRC / 'MediaListenerService.java').read_text()
        snapshot = method(listener, 'public static final class Snapshot')
        helpers = '\n'.join(method(listener, sig) for sig in (
            'private static String normalise(', 'private static boolean usesPauseAction(',
            'static Snapshot stabiliseSnapshot('))
        files = {
            'android/media/session/PlaybackState.java': '''package android.media.session;public class PlaybackState {
public static final int STATE_NONE=0,STATE_STOPPED=1,STATE_PAUSED=2,STATE_PLAYING=3,STATE_BUFFERING=6,STATE_CONNECTING=8;
public static final long ACTION_PAUSE=2,ACTION_PLAY=4,ACTION_SKIP_TO_PREVIOUS=16,ACTION_SKIP_TO_NEXT=32,ACTION_PLAY_PAUSE=512;
}''',
            'com/cbkii/ts18launcher/MediaListenerService.java': '''package com.cbkii.ts18launcher;import android.media.session.PlaybackState;
class MediaListenerService {enum Command {PREVIOUS,PLAY_PAUSE,NEXT}\n''' + snapshot + helpers + '}',
            'com/cbkii/ts18launcher/MediaSourceAdapter.java': 'package com.cbkii.ts18launcher;class MediaSourceAdapter {static boolean allowsUnadvertisedSkip(String p){return false;}}',
            'com/cbkii/ts18launcher/MediaEventTrace.java': 'package com.cbkii.ts18launcher;class MediaEventTrace {static void record(String... s){}}',
            'com/cbkii/ts18launcher/Harness.java': '''package com.cbkii.ts18launcher;import java.util.*;import android.media.session.PlaybackState;
public class Harness {public static void main(String[] args){int failures=0;Object token=new Object();
var a=new MediaListenerService.Snapshot("music","A","Artist A",3,2,token);
var gap=new MediaListenerService.Snapshot("music","","",3,2,token);
if(!MediaListenerService.stabiliseSnapshot(a,gap).title.isEmpty()){System.out.println("FAIL F1: unverified blank transition retained Track A");failures++;}
for(int state:new int[]{6,8})if(new MediaListenerService.Snapshot("music","A","",state,2,token).playing){System.out.println("FAIL F2: transitional state "+state+" claims playing");failures++;}
int selected=MediaSelection.pick(List.of(new MediaSelection.Candidate("A",true,true,false,true),new MediaSelection.Candidate("B",true,true,true,false)),"A",false,"A");
if(selected!=1){System.out.println("FAIL F6: remembered paused A beats playing B");failures++;}
if(failures!=0)throw new AssertionError(failures+" production regressions");System.out.println("PASS production snapshot / native state / AUTO");}}
'''
        }
        # Only needed by the historical retention trace; retain the production method verbatim.
        files['com/cbkii/ts18launcher/MediaListenerService.java'] = files['com/cbkii/ts18launcher/MediaListenerService.java'].replace(helpers + '}', helpers + 'static String stateName(int s){return "";} }')
        for name in ('RadioFrequency', 'MediaSelection'):
            files['com/cbkii/ts18launcher/' + name + '.java'] = (SRC / (name + '.java')).read_text()
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp)
            for name, content in files.items():
                path=out/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content)
            subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',str(out),*map(str,out.rglob('*.java'))],check=True,timeout=30)
            subprocess.run(['java','-cp',str(out),'com.cbkii.ts18launcher.Harness'],check=True,timeout=30)
