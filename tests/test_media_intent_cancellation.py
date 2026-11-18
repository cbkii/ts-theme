"""Cross-source cancellation through the real facade and Direct engines."""
import ast
from pathlib import Path
import subprocess
import tempfile
import unittest
ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher"

class IntentCancellationTest(unittest.TestCase):
    def test_late_music_session_cannot_revive_superseded_cold_play(self):
        fixture = ast.parse((ROOT / "tests/test_direct_readiness_runtime.py").read_text())
        files = ast.literal_eval(next(n.value for n in ast.walk(fixture) if isinstance(n, ast.Assign) and any(isinstance(t, ast.Name) and t.id == "files" for t in n.targets)))
        name = "com/cbkii/ts18launcher/media/direct/Harness.java"
        files[name] = files[name].split("public static void main(String[] args)")[0] + HARNESS
        sources = ["media/MediaBackend.java", "media/MediaSourceKey.java", "media/MediaEngine.java", "media/MediaSourceState.java", "media/MediaUserAction.java", "media/MediaCommandResult.java", "media/MediaFeatureFacade.java", "media/direct/DirectSourceEngine.java", "media/direct/DirectOperation.java", "media/direct/SessionObservation.java", "media/direct/DirectSessionSelector.java", "media/direct/DirectSessionRegistry.java"]
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            for n, content in files.items():
                p = root / n
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(content)
            for n in sources:
                p = root / "com/cbkii/ts18launcher" / n
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text((MAIN / n).read_text())
            result = subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-d", td, *map(str, root.rglob("*.java"))], capture_output=True, text=True, timeout=30)
            self.assertEqual(result.returncode, 0, result.stderr)
            result = subprocess.run(["java", "-cp", td, "com.cbkii.ts18launcher.media.direct.Harness"], capture_output=True, text=True, timeout=10)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

HARNESS = r'''static class Backend implements MediaBackend {
 final Map<MediaSourceKey,DirectSourceEngine> engines=new EnumMap<>(MediaSourceKey.class);
 final Map<MediaSourceKey,Transport> transports=new EnumMap<>(MediaSourceKey.class);
 final Clock clock=new Clock(); StateListener listener;
 Backend(){for(MediaSourceKey k:MediaSourceKey.values()){Transport t=new Transport();transports.put(k,t);DirectSourceEngine e=new DirectSourceEngine(k,t,clock,new DirectSourceEngine.Labels(){public String sourceLabel(){return k.toString();}public String appLabel(String p){return p;}},new Starter());e.setListener(s->{if(listener!=null)listener.onMediaSourceStateChanged(s);});engines.put(k,e);}}
 public MediaEngine engine(){return MediaEngine.DIRECT;}
 public void attach(MediaSourceKey k,String p){engines.get(k).attach(p);engines.get(k).onSessions(false,Collections.emptyList());}
 public void detach(MediaSourceKey k){engines.get(k).detach();}
 public boolean attached(MediaSourceKey k){return engines.get(k).attached();}
 public MediaSourceState state(MediaSourceKey k){return engines.get(k).state();}
 public boolean playing(MediaSourceKey k,String p){return engines.get(k).playing(p);}
 public void command(MediaSourceKey k,String p,MediaUserAction a,ResultCallback c){engines.get(k).command(p,a,c);}
 public void commandUntil(MediaSourceKey k,String p,MediaUserAction a,long until,ResultCallback c){engines.get(k).commandUntil(p,a,until,c);}
 public void cancelPendingStart(MediaSourceKey k){engines.get(k).cancelPendingStart();}
 public void pauseForHandover(MediaSourceKey k){engines.get(k).pauseForHandover();}
 public void setStateListener(StateListener l){listener=l;}
 void offer(MediaSourceKey k,int state,String item){String pkg=k==MediaSourceKey.MUSIC?"com.tw.media":"com.navimods.radio";engines.get(k).onSessions(false,List.of(new SessionObservation(k.toString(),pkg,state,6,item,"track","artist",true,true)));}
}

public static void main(String[] args){
 Backend b=new Backend();MediaFeatureFacade facade=new MediaFeatureFacade(new MediaFeatureFacade.Configuration(){public MediaEngine requestedEngine(MediaSourceKey k){return MediaEngine.DIRECT;}public String configuredPackage(MediaSourceKey k){return k==MediaSourceKey.MUSIC?"com.tw.media":"com.navimods.radio";}},new Backend(),b,()->b.clock.now);
 facade.reconcile();b.offer(MediaSourceKey.RADIO,2,"station");
 List<MediaCommandResult> music=new ArrayList<>();
 facade.commandUntil(MediaSourceKey.MUSIC,"com.tw.media",MediaUserAction.PLAY_PAUSE,500,music::add);
 facade.commandUntil(MediaSourceKey.RADIO,"com.navimods.radio",MediaUserAction.PLAY,500,r->{});
 check(music.size()==1&&!music.get(0).dispatched,"superseded user request was not settled");
 b.offer(MediaSourceKey.RADIO,3,"station");
 b.offer(MediaSourceKey.MUSIC,2,"late-item");b.offer(MediaSourceKey.MUSIC,3,"late-item");
 check(b.transports.get(MediaSourceKey.MUSIC).sent.isEmpty(),"late MUSIC PLAY survived RADIO selection");
 check(b.transports.get(MediaSourceKey.RADIO).sent.equals(List.of(DirectOperation.PLAY)),"replacement changed or was paused");
 check(facade.state(MediaSourceKey.MUSIC).live(),"cancellation detached healthy session observation");
 // A new deliberate request can use the retained session; no permanent cancellation latch.
 b.offer(MediaSourceKey.MUSIC,2,"late-item");
 facade.commandUntil(MediaSourceKey.MUSIC,"com.tw.media",MediaUserAction.PLAY,450,r->{});
 check(b.transports.get(MediaSourceKey.MUSIC).deadline==450,"new explicit intent lost its deadline");
}
}
'''
