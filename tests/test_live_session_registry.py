"""Exercise the shared production callback registry with fresh API29 controller wrappers."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'launcher/src/main/java/com/cbkii/ts18launcher'


class LiveSessionRegistryTest(unittest.TestCase):
    def test_callback_owner_registration_retry_external_state_and_replacement(self):
        files = {
            'android/os/Handler.java': 'package android.os;public class Handler {}',
            'android/media/session/MediaSession.java': '''package android.media.session;public class MediaSession {
public record Token(int id) {}}
''',
            'android/media/session/MediaController.java': '''package android.media.session;
public class MediaController {
public static class Callback {public int metadata,state;public void onMetadataChanged(){metadata++;}public void onPlaybackStateChanged(){state++;}}
public final MediaSession.Token token; public Callback registered;public android.os.Handler handler;public boolean fail;public int adds,removes;
public MediaController(int id){token=new MediaSession.Token(id);}public MediaSession.Token getSessionToken(){return token;}
public void registerCallback(Callback c,android.os.Handler h){adds++;if(fail)throw new SecurityException();registered=c;handler=h;}
public void unregisterCallback(Callback c){if(registered!=c)throw new AssertionError("unregistered wrapper teardown");removes++;registered=null;}
public void externalChange(){if(registered!=null){registered.onMetadataChanged();registered.onPlaybackStateChanged();}}}
''',
            'com/cbkii/ts18launcher/Harness.java': '''package com.cbkii.ts18launcher;
import java.util.*;import android.media.session.*;import android.os.Handler;
public class Harness {
static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
public static void main(String[] a){
Map<MediaSession.Token,MediaController> watched=new HashMap<>();Handler handler=new Handler();MediaController.Callback cb=new MediaController.Callback();
MediaController original=new MediaController(1),fresh=new MediaController(1);
SessionControllerRegistry.reconcile(watched,List.of(original),cb,handler);
for(int n=0;n<100;n++)check(SessionControllerRegistry.reconcile(watched,List.of(fresh),cb,handler).get(0)==original,"callback owner replaced");
check(original.adds==1&&fresh.adds==0&&original.handler==handler,"registration lifetime/handler");
original.externalChange();check(cb.metadata==1&&cb.state==1,"external control/metadata callbacks stopped");
MediaController replacement=new MediaController(2);replacement.fail=true;
check(SessionControllerRegistry.reconcile(watched,List.of(replacement),cb,handler).isEmpty(),"failed registration retained");
check(original.removes==1,"original owner not torn down");replacement.fail=false;
SessionControllerRegistry.reconcile(watched,List.of(replacement,replacement),cb,handler);
check(replacement.adds==2&&watched.size()==1,"retry/duplicate token");replacement.externalChange();
check(cb.metadata==2&&cb.state==2,"replacement callback missing");
SessionControllerRegistry.reconcile(watched,List.of(),cb,handler);check(replacement.removes==1&&watched.isEmpty(),"revocation leaked callback");
String title="TS18Ready_10211_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
String dump="  Window #0 Window{abc u0 "+title+"}:\\n    mOwnerUid=10211 showForAllUsers=false\\n    mAttrs={ ty=APPLICATION_OVERLAY fmt=OPAQUE }\\n  mCurrentFocus=Window{abc u0 "+title+"}\\n";
check(StartupMaskFocus.ownsFocusedWindow(dump,title,0),"owned mask not recognised");
check(!StartupMaskFocus.ownsFocusedWindow(dump.replace("mOwnerUid=10211","mOwnerUid=10212"),title,0),"wrong UID admitted");
check(!StartupMaskFocus.ownsFocusedWindow(dump.replace("APPLICATION_OVERLAY","APPLICATION"),title,0),"wrong window type admitted");
check(!StartupMaskFocus.ownsFocusedWindow(dump,title,1),"wrong user admitted");
check(!StartupMaskFocus.ownsFocusedWindow(dump+"mCurrentFocus=null\\n",title,0),"ambiguous focus admitted");
check(!StartupMaskFocus.ownsFocusedWindow(dump.replace("Window{abc u0 "+title+"}\\n","Window{def u0 other/.Permission}\\n"),title,0),"permission focus masked");
System.out.println("PASS production callback lifetime / failed registration retry / external controls / replacement / teardown");}}
'''
        }
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp)
            for name in ('SessionControllerRegistry', 'StartupMaskFocus'):
                files['com/cbkii/ts18launcher/' + name + '.java'] = (SRC / (name + '.java')).read_text()
            for name, text in files.items():
                path = out / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text)
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(out), *map(str, out.rglob('*.java'))], check=True, timeout=30)
            subprocess.run(['java', '-cp', str(out), 'com.cbkii.ts18launcher.Harness'], check=True, timeout=30)
