"""Execute the production peer authenticator against controlled Android identities."""
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/CompactStartupPeer.java"
PACKAGE = "com.cbkii.ts18launcher"
CLASS = "CompactStartupPeer"

def has_compiler():
    try:
        result = subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-version"],
                                capture_output=True, text=True, timeout=10)
        return result.returncode == 0 and int(result.stdout.split()[1].split(".")[0]) >= 17
    except (OSError, ValueError, IndexError, subprocess.TimeoutExpired):
        return False

class CompactPeerTests(unittest.TestCase):
    @unittest.skipUnless(has_compiler(), "Java 17 compiler unavailable")
    def test_peer_requires_package_uid_user_and_release_certificate(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            files = {
                "android/os/Build.java": "package android.os; public class Build {public static class VERSION {public static int SDK_INT=29;}}",
                "android/os/Process.java": "package android.os; public class Process {public static int myUid(){return 23000;}}",
                "android/content/Context.java": "package android.content; public class Context {public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();}}",
                "android/content/pm/ProviderInfo.java": "package android.content.pm; public class ProviderInfo {public String packageName=PackageManager.providerPackage;public ApplicationInfo applicationInfo=new ApplicationInfo();}",
                "android/content/pm/PackageInfo.java": "package android.content.pm; public class PackageInfo {public ApplicationInfo applicationInfo=new ApplicationInfo();}",
                "android/content/pm/ApplicationInfo.java": "package android.content.pm; public class ApplicationInfo {public int uid=PackageManager.uid;}",
                "android/content/pm/PackageManager.java": """package android.content.pm; public class PackageManager {
public static class NameNotFoundException extends Exception {}
public static final int CERT_INPUT_SHA256=1;
public static int queriedUid; public static String providerPackage; public ProviderInfo resolveContentProvider(String a,int f){return new ProviderInfo();}
public static boolean cert=true,missing=false;public static int uid=24000;public static String[] owners;
public String[] getPackagesForUid(int u){queriedUid=u;return owners;}
public PackageInfo getPackageInfo(String p,int flags)throws NameNotFoundException {if(!p.equals("app.organicmaps.incar")||flags!=0)throw new AssertionError("wrong package lookup");if(missing)throw new NameNotFoundException();return new PackageInfo();}
public boolean hasSigningCertificate(String p,byte[] c,int type){if(!p.equals("app.organicmaps.incar")||c.length!=32||type!=1||!java.util.Arrays.equals(c,java.util.HexFormat.of().parseHex("40ad32351bf55902c617df2351a5761bb5e5462cc2f2ed268503082331139780")))throw new AssertionError("wrong pinned certificate");return cert;}
}""",
                "Harness.java": """package com.cbkii.ts18launcher;
import android.content.pm.PackageManager;
public class Harness {
static void check(boolean value){if(!value)throw new AssertionError();}
public static void main(String[] a){var c=new android.content.Context();
PackageManager.owners=new String[]{CompactStartupPeer.PACKAGE};check(CompactStartupPeer.trusted(c,24000));check(PackageManager.queriedUid==24000);
PackageManager.providerPackage=CompactStartupPeer.PACKAGE;check(CompactStartupPeer.ownsProvider(c,"authority"));
PackageManager.providerPackage="impostor";check(!CompactStartupPeer.ownsProvider(c,"authority"));
PackageManager.cert=false;check(!CompactStartupPeer.trusted(c,24000));PackageManager.cert=true;
check(!CompactStartupPeer.trusted(c,24001));check(PackageManager.queriedUid==24001);check(!CompactStartupPeer.trusted(c,124000));
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
