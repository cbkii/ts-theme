"""Execute Legacy Auto detach after its observed selection changes without a new command."""
from pathlib import Path
import subprocess
import tempfile
import unittest
from test_navigation_controller_recovery import has_java_compiler

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'launcher/src/main/java/com/cbkii/ts18launcher'


@unittest.skipUnless(has_java_compiler(), 'JDK required')
class LegacyDetachTest(unittest.TestCase):
    def test_detach_cancels_last_command_target_after_auto_selection_changes(self):
        files = {
            'android/content/Context.java': 'package android.content; public class Context {public Context getApplicationContext(){return this;}}',
            'Stubs.java': '''package com.cbkii.ts18launcher;
import android.content.Context;import java.util.*;
class LauncherPrefs {static final String MEDIA_MODE_AUTO="auto";static String mediaMode(Context c){return MEDIA_MODE_AUTO;}}
class MediaListenerService {enum Command {PREVIOUS,NEXT,PLAY_PAUSE}
static boolean hasNotificationAccess(Context c){return true;}
static class Snapshot {String packageName="",mediaId="",title="",artist="";int state;long actions,observationGeneration;Object sessionIdentity;}}
class ProcessMediaSessionMonitor {static String selected="old.player";
static String selectedMusicPackage(){return selected;}
static MediaListenerService.Snapshot selectedSnapshot(String p){return new MediaListenerService.Snapshot();}}
class MediaSourceBootstrapper {interface Callback {void result(boolean success,String message);}
Set<String> pending=new HashSet<>();int latePlays;
void command(String label,String pkg,MediaListenerService.Command action,Callback cb){pending.clear();pending.add(pkg);}
void cancelPendingFor(String pkg,String reason){pending.remove(pkg);}
boolean isPlaying(String pkg){return false;}
void offer(String pkg){if(pending.remove(pkg))latePlays++;}}
class Ts18LauncherApplication {static final MediaSourceBootstrapper owner=new MediaSourceBootstrapper();
static MediaSourceBootstrapper mediaSources(Context c){return owner;}}
class MediaEventTrace {static void record(String a,String b,String c){}}
''',
            'Harness.java': '''package com.cbkii.ts18launcher;
import com.cbkii.ts18launcher.media.*;
public class Harness {static void check(boolean b){if(!b)throw new AssertionError("late command survived detach");}
public static void main(String[] args){
LegacyMediaBackend backend=new LegacyMediaBackend(new android.content.Context());
backend.attach(MediaSourceKey.MUSIC,"configured.player");
backend.command(MediaSourceKey.MUSIC,"old.player",MediaUserAction.PLAY_PAUSE,null);
check(Ts18LauncherApplication.owner.pending.contains("old.player"));
ProcessMediaSessionMonitor.selected="new.player";
backend.detach(MediaSourceKey.MUSIC);Ts18LauncherApplication.owner.offer("old.player");
check(Ts18LauncherApplication.owner.latePlays==0);
backend.attach(MediaSourceKey.MUSIC,"other.player");
backend.command(MediaSourceKey.MUSIC,"other.player",MediaUserAction.NEXT,null);
check(Ts18LauncherApplication.owner.pending.contains("other.player"));
backend.detach(MediaSourceKey.MUSIC);check(Ts18LauncherApplication.owner.pending.isEmpty());
backend.command(MediaSourceKey.MUSIC,"old.player",MediaUserAction.NEXT,null);
check(Ts18LauncherApplication.owner.pending.isEmpty());
System.out.println("PASS Legacy Auto old-target cancellation and detached silence");}}
''',
            'LegacyMediaBackend.java': (SRC / 'LegacyMediaBackend.java').read_text(),
        }
        for name in ['MediaBackend', 'MediaCommandResult', 'MediaEngine', 'MediaSourceKey',
                     'MediaSourceState', 'MediaUserAction']:
            files[name + '.java'] = (SRC / 'media' / (name + '.java')).read_text()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name, source in files.items():
                file = root / name
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text(source)
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(root),
                            *map(str, root.rglob('*.java'))], check=True, timeout=30)
            subprocess.run(['java', '-cp', str(root), 'com.cbkii.ts18launcher.Harness'],
                           check=True, timeout=10)
