"""The real automatic navigation admission gate is token-owned and single-flight."""
from pathlib import Path
import subprocess
import tempfile
import unittest
from test_navigation_controller_recovery import has_java_compiler
ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'launcher/src/main/java/com/cbkii/ts18launcher/navigation/NavigationAutomaticAdmission.java'

@unittest.skipUnless(has_java_compiler(), "JDK required")
class NavigationAdmissionTest(unittest.TestCase):
    def test_concurrent_claims_and_wrong_owner_release(self):
        harness = '''import com.cbkii.ts18launcher.navigation.NavigationAutomaticAdmission;
public class Harness {
static void check(boolean value) { if (!value) throw new AssertionError(); }
public static void main(String[] args) throws Exception {
Object first = NavigationAutomaticAdmission.tryAcquire(); check(first != null);
Object[] second = {first};
Thread competing = new Thread(() -> second[0] = NavigationAutomaticAdmission.tryAcquire());
competing.start(); competing.join(); check(second[0] == null);
check(NavigationAutomaticAdmission.isActive());
NavigationAutomaticAdmission.release(new Object()); check(NavigationAutomaticAdmission.isActive());
NavigationAutomaticAdmission.release(first); check(!NavigationAutomaticAdmission.isActive());
Object next = NavigationAutomaticAdmission.tryAcquire(); check(next != null);
NavigationAutomaticAdmission.release(next); check(!NavigationAutomaticAdmission.isActive());
System.out.println("PASS token-owned exclusive automatic navigation admission");
}}
'''
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            test = root / 'Harness.java'
            test.write_text(harness)
            source = root / 'com/cbkii/ts18launcher/navigation/NavigationAutomaticAdmission.java'
            source.parent.mkdir(parents=True, exist_ok=True)
            source.write_text(SOURCE.read_text())
            subprocess.run(['javac', '-d', str(root), str(test), str(source)], check=True, timeout=30)
            subprocess.run(['java', '-cp', str(root), 'Harness'], check=True, timeout=10)
