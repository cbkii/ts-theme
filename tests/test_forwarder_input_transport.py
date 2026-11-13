"""Production reflection against public AIDL interfaces and inaccessible proxy classes.
This tests lookup/failure semantics, not Android or TS18 input authority.
"""
from pathlib import Path
import subprocess
import tempfile
import unittest
from test_navigation_controller_recovery import has_java_compiler

ROOT = Path(__file__).resolve().parents[1]

@unittest.skipUnless(has_java_compiler(), "JDK required")
class ForwarderTransportTests(unittest.TestCase):
    def test_interface_lookup_and_failed_send_withdraw_capability(self):
        files = {
            "android/view/InputEvent.java": "package android.view; public class InputEvent {}",
            "android/view/MotionEvent.java": "package android.view; public class MotionEvent extends InputEvent {}",
            "android/view/KeyEvent.java": "package android.view; public class KeyEvent extends InputEvent {}",
            "android/app/IActivityManager.java": "package android.app; public interface IActivityManager { IInputForwarder createInputForwarder(int id); }",
            "android/app/IInputForwarder.java": "package android.app; public interface IInputForwarder { boolean forwardEvent(android.view.InputEvent event); }",
            "android/app/ActivityManager.java": """package android.app;
public class ActivityManager {public static boolean fail;public static int sends;
 public static Object getService(){return new Proxy();}
 private static class Proxy implements IActivityManager {
  public IInputForwarder createInputForwarder(int id){if(id!=9)throw new SecurityException();return new Forwarder();}}
 private static class Forwarder implements IInputForwarder {
  public boolean forwardEvent(android.view.InputEvent e){sends++;return !fail;}}
}""",
            "N3InputTransport.java": """package com.cbkii.ts18launcher.navigation.n3;
interface N3InputTransport {String unavailableReason();boolean motion(android.view.MotionEvent e);boolean back();void close();
static android.view.KeyEvent[] backKeys(){return new android.view.KeyEvent[]{new android.view.KeyEvent(),new android.view.KeyEvent()};}}""",
            "Harness.java": """package com.cbkii.ts18launcher.navigation.n3;
import android.app.ActivityManager;
public class Harness {static void check(boolean b){if(!b)throw new AssertionError();}
public static void main(String[] args){
 N3InputTransport denied=ForwarderInputTransport.open(8);check(!denied.unavailableReason().isEmpty());
 N3InputTransport input=ForwarderInputTransport.open(9);check(input.unavailableReason().isEmpty());
 check(input.motion(new android.view.MotionEvent()));check(input.back());check(ActivityManager.sends==3);
 ActivityManager.fail=true;check(!input.motion(new android.view.MotionEvent()));check(!input.unavailableReason().isEmpty());
 ActivityManager.fail=false;check(!input.back());check(ActivityManager.sends==4);
 input=ForwarderInputTransport.open(9);input.close();check(!input.motion(new android.view.MotionEvent()));
 System.out.println("PASS interface proxy lookup / send rejection / close fail closed");}}
""",
        }
        files["ForwarderInputTransport.java"] = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/navigation/n3/ForwarderInputTransport.java").read_text()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name, source in files.items():
                file = root / name
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text(source)
            subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-d", str(root), *map(str, root.rglob("*.java"))], check=True, timeout=30)
            subprocess.run(["java", "-cp", str(root), "com.cbkii.ts18launcher.navigation.n3.Harness"], check=True, timeout=10)
