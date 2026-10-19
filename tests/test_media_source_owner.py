"""Execute the production process-owner factory across effective identity changes."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'launcher/src/main/java/com/cbkii/ts18launcher'


class MediaSourceOwnerTest(unittest.TestCase):
    def test_equivalent_default_keeps_owner_and_real_change_replaces_it(self):
        if not shutil.which('java'):
            self.skipTest('Java unavailable')
        modules = subprocess.run(['java', '--list-modules'], capture_output=True,
                                 text=True, timeout=10)
        if modules.returncode or 'jdk.compiler@' not in modules.stdout:
            self.skipTest('Java compiler module required')
        source = (SRC / 'Ts18LauncherApplication.java').read_text()
        factory = source.split('    static MediaSourceBootstrapper mediaSources(', 1)[1]
        factory = '    static MediaSourceBootstrapper mediaSources(' + factory.split(
            '    private final BroadcastReceiver wakeReceiver', 1)[0]
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / 'Harness.java').write_text('''
class Context { Context getApplicationContext(){return this;} }
class LauncherPrefs {static String music="";static String KEY_MUSIC="music";
static String packageFor(Context c,String k){return music;} }
class RadioProvider {static String radio="com.navimods.radio";
static String resolvePackage(Context c){return radio;} }
class TopwayAdapter {static String defaultMusicPackage(Context c){return "com.tw.media";} }
class AndroidUserId {static int user;static int current(){return user;} }
class MediaSourceBootstrapper {boolean destroyed;MediaSourceBootstrapper(Context c){}
void destroy(){destroyed=true;} }
class Ts18LauncherApplication extends Context {
private MediaSourceBootstrapper mediaBootstrapper;private String mediaConfiguration="";
''' + factory + '''}
public class Harness {
static void check(boolean b){if(!b)throw new AssertionError("process owner identity");}
public static void main(String[] args){
Ts18LauncherApplication app=new Ts18LauncherApplication();
MediaSourceBootstrapper first=Ts18LauncherApplication.mediaSources(app);
LauncherPrefs.music="com.tw.media";
check(first==Ts18LauncherApplication.mediaSources(app)&&!first.destroyed);
LauncherPrefs.music="other.music";
MediaSourceBootstrapper second=Ts18LauncherApplication.mediaSources(app);
check(second!=first&&first.destroyed);
check(second==Ts18LauncherApplication.mediaSources(app)&&!second.destroyed);
RadioProvider.radio="other.radio";
MediaSourceBootstrapper third=Ts18LauncherApplication.mediaSources(app);
check(third!=second&&second.destroyed);
AndroidUserId.user=10;MediaSourceBootstrapper profile=Ts18LauncherApplication.mediaSources(app);
check(profile!=third&&third.destroyed);check(profile==Ts18LauncherApplication.mediaSources(app));
System.out.println("PASS effective source identity preserves one process owner");
}}
''')
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main',
                            '-d', str(root), str(root / 'Harness.java')], check=True, timeout=20)
            subprocess.run(['java', '-cp', str(root), 'Harness'], check=True, timeout=10)
