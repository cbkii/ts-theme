"""Replay the captured metadata gap through both production readiness owners."""
import ast
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher"


def fixture(filename, variable):
    tree = ast.parse((ROOT / "tests" / filename).read_text())
    return ast.literal_eval(next(n.value for n in ast.walk(tree)
        if isinstance(n, ast.Assign) and any(isinstance(t, ast.Name) and t.id == variable for t in n.targets)))


class TransitionOwnershipTest(unittest.TestCase):
    def test_next_gap_neither_acknowledges_nor_reprepares_the_live_source(self):
        files = fixture("test_startup_runtime.py", "stubs")
        direct = fixture("test_direct_readiness_runtime.py", "files")
        files["android/media/session/PlaybackState.java"] = direct["android/media/session/PlaybackState.java"]
        files["com/cbkii/ts18launcher/Doubles.java"] = files["com/cbkii/ts18launcher/Doubles.java"].replace(
            'static void prepareDirectSource(android.content.Context c,com.cbkii.ts18launcher.media.MediaSourceKey k,long e){prepares++;calls.add(k);}',
            'static void prepareDirectSource(android.content.Context c,com.cbkii.ts18launcher.media.MediaSourceKey k,long e){prepares++;calls.add(k);if(k==com.cbkii.ts18launcher.media.MediaSourceKey.MUSIC) Harness.engine.preparePassive(e);}')
        files["com/cbkii/ts18launcher/Doubles.java"] = files["com/cbkii/ts18launcher/Doubles.java"].replace(
            'return live?new com.cbkii', 'if(k==com.cbkii.ts18launcher.media.MediaSourceKey.MUSIC)return Harness.engine.state();return live?new com.cbkii')
        files["com/cbkii/ts18launcher/Harness.java"] = HARNESS
        names = ["StartupReadinessCoordinator.java", "StartupReadinessPolicy.java",
            "media/MediaBackend.java", "media/MediaSourceState.java", "media/MediaEngine.java",
            "media/MediaCommandResult.java", "media/MediaUserAction.java",
            "media/direct/DirectSourceEngine.java", "media/direct/DirectOperation.java",
            "media/direct/SessionObservation.java", "media/direct/DirectSessionSelector.java"]
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            for name, content in files.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
            for name in names:
                path = root / "com/cbkii/ts18launcher" / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text((SRC / name).read_text())
            compile_run = subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-d", td,
                *map(str, root.rglob("*.java"))], capture_output=True, text=True, timeout=30)
            self.assertEqual(0, compile_run.returncode, compile_run.stderr)
            run = subprocess.run(["java", "-cp", td, "com.cbkii.ts18launcher.Harness"],
                capture_output=True, text=True, timeout=10)
            self.assertEqual(0, run.returncode, run.stdout + run.stderr)


HARNESS = r'''package com.cbkii.ts18launcher;
import com.cbkii.ts18launcher.media.*;
import com.cbkii.ts18launcher.media.direct.*;
import java.util.*;
public class Harness {
static DirectSourceEngine engine;
static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
static SessionObservation state(int s,String id){return new SessionObservation("token","com.tw.media",s,16438,id,id,"",true,true,id.isEmpty()?"":"file:///"+id);}
public static void main(String[] args)throws Exception{
 List<DirectOperation> sent=new ArrayList<>();List<MediaCommandResult> results=new ArrayList<>();
 engine=new DirectSourceEngine(MediaSourceKey.MUSIC,(t,o)->{sent.add(o);return true;},
 new DirectSourceEngine.Clock(){public Object schedule(long d,Runnable r){return r;}public void cancel(Object t){}},
 new DirectSourceEngine.Labels(){public String sourceLabel(){return "Music";}public String appLabel(String p){return p;}});
 engine.attach("com.tw.media");engine.onSessions(true,List.of(state(3,"old")));
 Facade.pkg="com.tw.media";
 var constructor=StartupReadinessCoordinator.class.getDeclaredConstructor(android.content.Context.class);constructor.setAccessible(true);
 var owner=constructor.newInstance(new android.content.Context());engine.setListener(owner::onMediaStateChanged);
 owner.onHomeResumed(new android.app.Activity());int before=Ts18LauncherApplication.prepares;
 engine.command("com.tw.media",MediaUserAction.NEXT,results::add);
 engine.onSessionChanged(state(6,""));owner.onTrigger(StartupReadinessPolicy.Trigger.READINESS_CHANGED);
 engine.preparePassive(999); // Defence at the engine boundary even if another caller requests it.
 check(results.isEmpty(),"blank metadata acknowledged Next");
 check(sent.equals(List.of(DirectOperation.NEXT)),"live transition sent passive PREPARE: "+sent);
 check(Ts18LauncherApplication.prepares==before,"coordinator rearmed readiness for the same live token");
 engine.onSessionChanged(state(3,"new"));check(results.size()==1&&results.get(0).outcome==MediaCommandResult.Outcome.ACKNOWLEDGED,"replacement not acknowledged");
 engine.onSessionChanged(state(2,""));engine.preparePassive(1000);
 check(sent.equals(List.of(DirectOperation.NEXT)),"warm paused metadata gap rearmed PREPARE");
 engine.onSessionChanged(state(3,"new"));

 // Same-source raw/primitive/canonical promotion is hydration, not a skip commit.
 engine.command("com.tw.media",MediaUserAction.NEXT,results::add);
 engine.onSessionChanged(new SessionObservation("token","com.tw.media",3,16438,"canonical-7","new","",true,true,"file:///new"));
 check(results.size()==1,"same-title ID promotion acknowledged Next");
 engine.onSessionChanged(new SessionObservation("token","com.tw.media",3,16438,"canonical-8","other","",true,true,"file:///different"));
 check(results.size()==2,"positive URI replacement did not settle");
 engine.onSessionChanged(new SessionObservation("token","com.tw.media",3,16438,"canonical-8","other","",true,true,"file:///different",5000,100));
 engine.command("com.tw.media",MediaUserAction.PREVIOUS,results::add);
 engine.onSessionChanged(new SessionObservation("token","com.tw.media",3,16438,"canonical-8","other","",true,true,"file:///different",0,200));
 check(results.size()==3,"legitimate Previous rewind was not acknowledged");
 engine.command("com.tw.media",MediaUserAction.NEXT,results::add);
 engine.command("com.tw.media",MediaUserAction.NEXT,results::add);
 engine.onSessionChanged(state(3,"third"));check(results.size()==4,"one commit acknowledged several rapid skips");
 engine.onSessionChanged(state(3,"fourth"));check(results.size()==5,"second rapid skip never settled");

 engine.onSessionChanged(new SessionObservation("token","com.tw.media",3,16438,"file:///raw","fallback","",true,true));
 engine.command("com.tw.media",MediaUserAction.NEXT,results::add);
 engine.onSessionChanged(new SessionObservation("token","com.tw.media",3,16438,"rich-id","hydrated title","",true,true,"file:///raw"));
 check(results.size()==5,"raw URI fallback -> rich tags was counted as a skip");
 engine.onSessionChanged(new SessionObservation("token","com.tw.media",3,16438,"next-id","next title","",true,true,"file:///next"));
 check(results.size()==6,"raw URI baseline did not observe a true replacement");
 int preparesBefore=Collections.frequency(sent,DirectOperation.PREPARE);
 engine.onSessionChanged(state(1,""));engine.preparePassive(1001);
 check(Collections.frequency(sent,DirectOperation.PREPARE)==preparesBefore+1,"genuinely stopped empty session never prepared");
 owner.destroy();System.out.println("PASS real coordinator -> engine metadata-gap boundary");
}}
'''
