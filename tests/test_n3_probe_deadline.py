"""Exercise the probe's production latch arbitration without an Android emulator."""
from pathlib import Path
import subprocess
import tempfile
import unittest
from test_music_identity import method
from test_navigation_controller_recovery import has_java_compiler
ROOT = Path(__file__).resolve().parents[1]
@unittest.skipUnless(has_java_compiler(), "JDK required")
class ProbeDeadlineTest(unittest.TestCase):
    def test_already_failed_phase_stops_before_success_or_full_timeout(self):
        source = (ROOT / "launcher/src/androidTest/java/com/cbkii/ts18launcher/navigation/n3/N3PathIpcProbe.java").read_text()
        text = "import java.util.concurrent.*;import java.util.concurrent.atomic.*;public class Harness {" + method(source, "static void awaitPhase(") + method(source, "private static void check(") + """
public static void main(String[] args)throws Exception {
 var failed=new CountDownLatch(0);var error=new AtomicReference<String>("display lost");
 long start=System.nanoTime();
 try {awaitPhase(new CountDownLatch(1),failed,error,10,"fixture");throw new RuntimeException("ignored failure");}
 catch(AssertionError expected){if(!expected.getMessage().contains("display lost"))throw expected;}
 if(System.nanoTime()-start>TimeUnit.SECONDS.toNanos(1))throw new AssertionError("failure waited for timeout");
 awaitPhase(new CountDownLatch(0),new CountDownLatch(1),new AtomicReference<>(""),1,"fixture");
 try {awaitPhase(new CountDownLatch(0),failed,error,1,"fixture");throw new RuntimeException("failure raced success");}
 catch(AssertionError expected){}
 System.out.println("PASS probe failure promptly wins over latch/timeout success");}}
"""
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);(root / "Harness.java").write_text(text)
            subprocess.run(["java","-m","jdk.compiler/com.sun.tools.javac.Main","-d",str(root),str(root / "Harness.java")],check=True,timeout=30)
            subprocess.run(["java","-cp",str(root),"Harness"],check=True,timeout=10)
