"""Exercise actual default-package resolution and dedicated transport selection."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'launcher/src/main/java/com/cbkii/ts18launcher'


def method(source, signature):
    start = source.index(signature)
    opening = source.index('{', start)
    end, depth = opening + 1, 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


class MusicIdentityTest(unittest.TestCase):
    def test_real_default_and_dedicated_recipient(self):
        if not shutil.which('java'):
            self.skipTest('JDK unavailable')
        activity = (SRC / 'LauncherActivity.java').read_text()
        prefs = (SRC / 'LauncherPrefs.java').read_text()
        methods = '\n'.join(method(activity, sig) for sig in (
            'private String configuredMusicPackage()', 'private String musicControlPackage()'))
        mode = method(prefs, 'static String mediaMode(')
        files = {
            'android/content/Context.java': """package android.content;public class Context {
public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();}}""",
            'android/content/pm/PackageManager.java': """package android.content.pm;import java.util.*;public class PackageManager {
public static Set<String> installed=new HashSet<>();public static boolean unavailable;
public Object getApplicationInfo(String p,int f)throws NameNotFoundException{
if(unavailable)throw new IllegalStateException();if(!installed.contains(p))throw new NameNotFoundException();return new Object();}
public static class NameNotFoundException extends Exception{}}""",
            'Harness.java': """import android.content.Context;
import android.content.pm.PackageManager;
import com.cbkii.ts18launcher.platform.TopwayAdapter;
class LauncherPrefs {static String KEY_MUSIC="music",KEY_MEDIA_MODE="mode",MEDIA_MODE_AUTO="auto",MEDIA_MODE_PREFER_MUSIC="prefer_music";
static String configured="",storedMode;static String packageFor(Context c,String k){return configured;}
static LauncherPrefs prefs(Context c){return new LauncherPrefs();}String getString(String k,String d){return storedMode==null?d:storedMode;}
""" + mode + """}
class Snapshot {String packageName="other.player";}
public class Harness extends Context {
Snapshot genericSnapshot=new Snapshot();
""" + methods + """
static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
public static void main(String[] args){Harness h=new Harness();
PackageManager.installed.add("com.tw.music");PackageManager.installed.add("com.tw.media");
check("com.tw.media".equals(TopwayAdapter.defaultMusicPackage(h)),"unset Music selected protected stock over installed Auxio");
check("com.tw.media".equals(h.musicControlPackage()),"dedicated Music followed unrelated active player");
LauncherPrefs.configured="chosen.player";check("chosen.player".equals(h.musicControlPackage()),"explicit Music choice overridden");
PackageManager.installed.remove("com.tw.media");check("com.tw.music".equals(TopwayAdapter.defaultMusicPackage(h)),"stock-only fallback lost");
check("chosen.player".equals(h.musicControlPackage()),"unavailable explicit source silently replaced");
LauncherPrefs.configured="";PackageManager.installed.clear();check(TopwayAdapter.defaultMusicPackage(h).isEmpty(),"invented unavailable default");
PackageManager.installed.add("com.tw.media");check("com.tw.media".equals(h.musicControlPackage()),"late Auxio install ignored");
LauncherPrefs.storedMode="auto";check("other.player".equals(h.musicControlPackage()),"explicit generic mode lost");
LauncherPrefs.storedMode="prefer_music";check("com.tw.media".equals(h.musicControlPackage()),"configured mode leaked generic recipient");
PackageManager.unavailable=true;check(TopwayAdapter.defaultMusicPackage(h).isEmpty(),"failed package observation invented source");
System.out.println("PASS actual Auxio/stock/default/explicit/unavailable/generic Music identity");}}
"""}
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name, text in files.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text)
            package = root / 'com/cbkii/ts18launcher/platform'
            package.mkdir(parents=True)
            shutil.copyfile(SRC / 'platform/TopwayAdapter.java', package / 'TopwayAdapter.java')
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(root),
                            *map(str, root.rglob('*.java'))], check=True, timeout=30)
            subprocess.run(['java', '-cp', str(root), 'Harness'], check=True, timeout=10)
