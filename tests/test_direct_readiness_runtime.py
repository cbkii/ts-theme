"""Run the actual M2 state machine with delayed capabilities and deterministic time."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'launcher/src/main/java'


class DirectReadinessRuntimeTest(unittest.TestCase):
    def test_pending_play_and_passive_restore_use_callbacks_without_duplicate_dispatch(self):
        if not shutil.which('java'):
            self.skipTest('JDK unavailable')
        files = {
            'android/os/SystemClock.java': 'package android.os; public class SystemClock {public static long elapsedRealtime(){return 100;}}',
            'android/media/session/PlaybackState.java': '''package android.media.session;
public class PlaybackState {public static final int STATE_NONE=0,STATE_STOPPED=1,STATE_PAUSED=2,STATE_PLAYING=3,STATE_BUFFERING=6,STATE_ERROR=7,STATE_CONNECTING=8,STATE_FAST_FORWARDING=4,STATE_REWINDING=5,STATE_SKIPPING_TO_NEXT=10,STATE_SKIPPING_TO_PREVIOUS=9,STATE_SKIPPING_TO_QUEUE_ITEM=11;
public static final long ACTION_PLAY=4,ACTION_PAUSE=2,ACTION_PLAY_PAUSE=512,ACTION_SKIP_TO_NEXT=32,ACTION_SKIP_TO_PREVIOUS=16,ACTION_PREPARE=16384;}
''',
            'com/cbkii/ts18launcher/media/direct/Harness.java': '''package com.cbkii.ts18launcher.media.direct;
import android.media.session.PlaybackState;import com.cbkii.ts18launcher.media.*;import java.util.*;
public class Harness {
static class Clock implements DirectSourceEngine.Clock {long now=100;List<Runnable> timers=new ArrayList<>();public long nowMs(){return now;}public Object schedule(long d,Runnable r){timers.add(r);return r;}public void cancel(Object o){timers.remove(o);}void expire(){now+=20000;for(Runnable r:new ArrayList<>(timers)){timers.remove(r);r.run();}}}
static class Transport implements DirectSourceEngine.Transport {List<DirectOperation> sent=new ArrayList<>();long deadline;public boolean dispatch(Object t,DirectOperation o){sent.add(o);return true;}public boolean dispatchDeadline(Object t,long d){deadline=d;return dispatch(t,DirectOperation.PLAY);}}
static class Starter implements DirectSourceEngine.Starter {int starts;boolean passive=true;public boolean canStart(String p){return true;}public boolean canPreparePassive(String p){return passive;}public void start(Object o,String p){starts++;}public void abandon(Object o,String p){}public void release(Object o,String p){}}
static class Rig {Clock c=new Clock();Transport t=new Transport();Starter s=new Starter();List<MediaCommandResult> results=new ArrayList<>();String pkg;DirectSourceEngine e;Rig(String p){pkg=p;e=new DirectSourceEngine(MediaSourceKey.MUSIC,t,c,new DirectSourceEngine.Labels(){public String sourceLabel(){return "Music";}public String appLabel(String p){return p;}},s);e.attach(p);e.onSessions(false,Collections.emptyList());}SessionObservation state(long actions,boolean deadline,int nativeState,String item){return new SessionObservation("token",pkg,nativeState,actions,item,"cached title","",deadline,true);}void offer(SessionObservation x){e.onSessions(false,Collections.singletonList(x));}void tap(){e.command(pkg,MediaUserAction.PLAY_PAUSE,results::add);}}
static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
public static void main(String[] args){
Rig r=new Rig("com.tw.media");r.tap();check(r.s.starts==1,"no cold bind");r.offer(r.state(0,false,0,""));check(r.results.isEmpty()&&r.t.sent.isEmpty()&&r.e.state().starting,"initial capability snapshot consumed tap");r.tap();r.e.onSessionChanged(r.state(4,false,0,""));check(r.t.sent.isEmpty(),"Auxio deadline bypassed");r.e.onSessionChanged(r.state(4,true,0,""));check(r.t.sent.equals(Collections.singletonList(DirectOperation.PLAY))&&r.t.deadline>r.c.now,"no one bounded Play");r.e.onSessionChanged(r.state(4,true,3,"song"));check(r.results.size()==2,"joined tap lost");
Rig generic=new Rig("com.navimods.radio");generic.offer(generic.state(4,false,0,""));generic.tap();check(generic.t.sent.equals(Collections.singletonList(DirectOperation.PLAY)),"generic player required Auxio action");
Rig passive=new Rig("com.tw.media");passive.e.preparePassive(1);passive.offer(passive.state(0,true,0,""));check(passive.t.sent.isEmpty()&&passive.e.preparing(),"early state ended PREPARE");passive.e.onSessionChanged(passive.state(16384,true,0,""));check(passive.t.sent.equals(Collections.singletonList(DirectOperation.PREPARE)),"delayed PREPARE absent");passive.e.onSessionChanged(passive.state(16384,true,2,"saved"));check(!passive.e.preparing(),"paused restore not observed");
Rig buffering=new Rig("com.tw.media");buffering.offer(buffering.state(16384,true,6,"saved"));buffering.e.preparePassive(1);check(buffering.t.sent.isEmpty(),"in-flight decoder restoration restarted");
Rig expired=new Rig("com.tw.media");expired.tap();expired.offer(expired.state(0,false,0,""));expired.c.expire();expired.e.onSessionChanged(expired.state(4,true,2,""));check(expired.t.sent.isEmpty()&&expired.results.size()==1,"expired tap revived");
Rig disabled=new Rig("com.navimods.radio");disabled.s.passive=false;disabled.e.preparePassive(1);check(disabled.s.starts==0,"passive Radio selected hardware");disabled.tap();check(disabled.s.starts==1,"user Radio Play could not activate");
System.out.println("PASS M2 delayed actions/capability, joined tap, bounded Auxio, standard Radio, passive restore, no replay");
}}
''',
        }
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name, text in files.items():
                p = root / name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(text)
            sources = ['media/MediaBackend.java', 'media/MediaSourceKey.java', 'media/MediaEngine.java',
                       'media/MediaSourceState.java', 'media/MediaUserAction.java', 'media/MediaCommandResult.java',
                       'media/direct/DirectSourceEngine.java', 'media/direct/DirectOperation.java', 'media/direct/SessionObservation.java',
                       'media/direct/DirectSessionSelector.java']
            for name in sources:
                source = MAIN / 'com/cbkii/ts18launcher' / name
                dest = root / 'com/cbkii/ts18launcher' / name
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_text(source.read_text())
            result = subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', tmp,
                                     *map(str, root.rglob('*.java'))], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            result = subprocess.run(['java', '-cp', tmp, 'com.cbkii.ts18launcher.media.direct.Harness'],
                                    capture_output=True, text=True, timeout=5)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
