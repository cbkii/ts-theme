"""Execute the production peer authenticator against controlled Android identities."""
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/CompactStartupPeer.java"
PACKAGE = "com.cbkii.ts18launcher"
CLASS = "CompactStartupPeer"

class CompactPeerTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which("java"), "Java 17 unavailable")
    def test_peer_requires_package_uid_user_and_release_certificate(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            files = {
                "android/os/Build.java": "package android.os; public class Build {public static class VERSION {public static int SDK_INT=29;}}",
                "android/os/Process.java": "package android.os; public class Process {public static int myUid(){return 23000;}}",
                "android/content/Context.java": "package android.content; public class Context {public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();}}",
                "android/content/pm/PackageInfo.java": "package android.content.pm; public class PackageInfo {public ApplicationInfo applicationInfo=new ApplicationInfo();}",
                "android/content/pm/ApplicationInfo.java": "package android.content.pm; public class ApplicationInfo {public int uid=PackageManager.uid;}",
                "android/content/pm/PackageManager.java": """package android.content.pm; public class PackageManager {
public static class NameNotFoundException extends Exception {}
public static final int CERT_INPUT_SHA256=1;
public static boolean cert=true,missing=false;public static int uid=24000;public static String[] owners;
public String[] getPackagesForUid(int u){return owners;}
public PackageInfo getPackageInfo(String p,int flags)throws NameNotFoundException {if(missing)throw new NameNotFoundException();return new PackageInfo();}
public boolean hasSigningCertificate(String p,byte[] c,int type){if(c.length!=32||type!=1)throw new AssertionError();return cert;}
}""",
                "Harness.java": """package com.cbkii.ts18launcher;
import android.content.pm.PackageManager;
public class Harness {
static void check(boolean value){if(!value)throw new AssertionError();}
public static void main(String[] a){var c=new android.content.Context();
PackageManager.owners=new String[]{CompactStartupPeer.PACKAGE};check(CompactStartupPeer.trusted(c,24000));
PackageManager.cert=false;check(!CompactStartupPeer.trusted(c,24000));PackageManager.cert=true;
check(!CompactStartupPeer.trusted(c,24001));check(!CompactStartupPeer.trusted(c,124000));
PackageManager.owners=new String[]{CompactStartupPeer.PACKAGE,"untrusted.shared"};check(!CompactStartupPeer.trusted(c,24000));
PackageManager.owners=new String[]{"impostor"};check(!CompactStartupPeer.trusted(c,24000));
PackageManager.owners=new String[]{CompactStartupPeer.PACKAGE};PackageManager.missing=true;check(!CompactStartupPeer.trusted(c,24000));
PackageManager.missing=false;android.os.Build.VERSION.SDK_INT=27;check(!CompactStartupPeer.trusted(c,24000));
System.out.println("PASS production peer package/UID/user/certificate/shared-UID gates");}}
""",
            }
            files["CompactStartupPeer.java"] = SOURCE.read_text()
            for name, source in files.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(source)
            subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-d", str(root),
                            *map(str, root.rglob("*.java"))], check=True, timeout=30)
            subprocess.run(["java", "-cp", str(root), PACKAGE + ".Harness"], check=True, timeout=10)

if __name__ == "__main__":
    unittest.main()
