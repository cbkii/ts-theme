"""Execute the shared production appearance owner, without an Android runtime."""
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]


class AppearanceOwnerTest(unittest.TestCase):
    def test_day_text_and_accent_foregrounds_have_high_contrast(self):
        import re
        import xml.etree.ElementTree as ET
        colours = {x.get('name'): x.text for x in ET.parse(
            ROOT / 'launcher/src/main/res/values/colors.xml').getroot()}
        def rgb(value):
            return [int(value.lstrip('#')[i:i+2], 16) for i in (0, 2, 4)]
        def luminance(channels):
            c = [x / 255 for x in channels]
            c = [x / 12.92 if x <= .04045 else ((x + .055) / 1.055) ** 2.4 for x in c]
            return sum(x * y for x, y in zip(c, (.2126, .7152, .0722)))
        def contrast(a, b):
            a, b = sorted((luminance(a), luminance(b)))
            return (b + .05) / (a + .05)
        backgrounds = [rgb(colours[x]) for x in ('ui_high_black', 'ui_high_surface', 'ui_high_surface_elevated')]
        for bg in backgrounds:
            for role in ('ui_high_text', 'ui_high_secondary'):
                self.assertGreaterEqual(contrast(rgb(colours[role]), bg), 7)
        source = (ROOT / 'launcher/src/main/java/com/cbkii/ts18launcher/AccentPalette.java').read_text()
        base = source.split('private static final int[] BASE = {', 1)[1].split('};', 1)[0]
        for colour in re.findall(r'0xFF([0-9A-F]{6})', base):
            foreground = [int(x * .48 + .5) for x in rgb(colour)]
            for bg in backgrounds:
                self.assertGreaterEqual(contrast(foreground, bg), 4.5)

    def test_shortcut_dialogs_share_live_binding_and_keep_app_identity(self):
        source = (ROOT / 'launcher/src/main/java/com/cbkii/ts18launcher/ShortcutEditorView.java').read_text()
        self.assertNotIn('new AlertDialog.Builder', source)
        self.assertEqual(3, source.count('dialogs.builder()'))
        self.assertIn('dialogs.bind(dialog', source)
        self.assertIn('tile.setTag(preview.tint)', source)
        self.assertIn('else tile.clearColorFilter()', source)
        manager = (ROOT / 'launcher/src/main/java/com/cbkii/ts18launcher/AppearanceDialogs.java').read_text()
        self.assertIn('public AlertDialog create()', manager)
        self.assertIn('setOnShowListener', manager)
        self.assertIn('bindings.remove(dialog)', manager)

    def test_actual_preferences_recover_and_export_legacy_anchors_atomically(self):
        java = shutil.which('java')
        if not java:
            self.skipTest('Java required')
        stubs = {
            'android/content/SharedPreferences.java': '''package android.content; public interface SharedPreferences {
                String getString(String k,String d); int getInt(String k,int d); boolean getBoolean(String k,boolean d);
                boolean contains(String k); Editor edit(); interface Editor {
                Editor putString(String k,String v); Editor putInt(String k,int v); Editor putBoolean(String k,boolean v);
                Editor remove(String k); void apply(); boolean commit();} }''',
            'android/content/Context.java': '''package android.content; import java.util.*;
                public class Context {public static final int MODE_PRIVATE=0; public final Prefs data=new Prefs();
                public SharedPreferences getSharedPreferences(String f,int m){return data;}
                public static class Prefs implements SharedPreferences {public Map<String,Object> values=new HashMap<>(); public int writes;
                public String getString(String k,String d){return (String)values.getOrDefault(k,d);}
                public int getInt(String k,int d){return (Integer)values.getOrDefault(k,d);}
                public boolean getBoolean(String k,boolean d){return (Boolean)values.getOrDefault(k,d);}
                public boolean contains(String k){return values.containsKey(k);}
                public Editor edit(){return new Editor(){Map<String,Object> pending=new HashMap<>();
                public Editor putString(String k,String v){pending.put(k,v);return this;}
                public Editor putInt(String k,int v){pending.put(k,v);return this;}
                public Editor putBoolean(String k,boolean v){pending.put(k,v);return this;}
                public Editor remove(String k){pending.put(k,null);return this;}
                public void apply(){commit();} public boolean commit(){writes++;pending.forEach((k,v)->{if(v==null)values.remove(k);else values.put(k,v);});return true;}};} } }''',
            'com/cbkii/ts18launcher/RoleIconCatalog.java': '''package com.cbkii.ts18launcher; class RoleIconCatalog {
                static boolean isKnown(String r){return true;} static String defaultQuickRole(int i){return "role";}
                static String defaultDrawerRole(int i){return "role";} }''',
            'com/cbkii/ts18launcher/MediaSelection.java': '''package com.cbkii.ts18launcher; class MediaSelection {
                static final String RADIO="radio",MUSIC="music"; }''',
            'com/cbkii/ts18launcher/ConfigurationCodec.java': '''package com.cbkii.ts18launcher; import java.util.*;
                class ConfigurationCodec {enum Type{BOOLEAN,COUNT,MINUTES,APPEARANCE,AUTO_SOURCE}
                static final Map<String,Type> KEYS=Map.of(LauncherPrefs.KEY_APPEARANCE_DAY_START,Type.MINUTES,
                LauncherPrefs.KEY_APPEARANCE_NIGHT_START,Type.MINUTES,LauncherPrefs.KEY_APPEARANCE_MODE,Type.APPEARANCE,
                LauncherPrefs.KEY_APPEARANCE_AUTO_SOURCE,Type.AUTO_SOURCE);}''',
            'com/cbkii/ts18launcher/PrefsHarness.java': '''package com.cbkii.ts18launcher; import android.content.*;
                public class PrefsHarness {static void check(boolean v){if(!v)throw new AssertionError();}
                public static void main(String[] args){Context c=new Context(); var p=c.data.values;
                String day=LauncherPrefs.KEY_APPEARANCE_DAY_START,night=LauncherPrefs.KEY_APPEARANCE_NIGHT_START;
                p.put(day,420);p.put(night,420);p.put(LauncherPrefs.KEY_APPEARANCE_MODE,"dim");
                p.put(LauncherPrefs.KEY_APPEARANCE_AUTO_SOURCE,"sensor");
                check(LauncherPrefs.appearanceDayStartMinutes(c)==420);check(LauncherPrefs.appearanceNightStartMinutes(c)==1140);
                var exported=ConfigurationStore.read(c); check(exported.get(day).equals(420));check(exported.get(night).equals(1140));
                check(exported.get(LauncherPrefs.KEY_APPEARANCE_MODE).equals("night"));
                check(exported.get(LauncherPrefs.KEY_APPEARANCE_AUTO_SOURCE).equals("schedule"));
                LauncherPrefs.setAppearanceDayStartMinutes(c,480);
                check(c.data.writes==1);check(p.get(day).equals(480));check(p.get(night).equals(1140));
                LauncherPrefs.setAppearanceNightStartMinutes(c,480);check(c.data.writes==1);check(p.get(night).equals(1140));
                LauncherPrefs.clearAppearanceSchedule(c);check(LauncherPrefs.appearanceNightStartMinutes(c)==1140);
                p.put(day,-1);p.put(night,420);LauncherPrefs.setAppearanceNightStartMinutes(c,1200);
                check(p.get(day).equals(420));check(p.get(night).equals(1200));
                p.put(LauncherPrefs.KEY_APPEARANCE_MODE,"high_contrast");check(LauncherPrefs.appearanceMode(c).equals("day"));
                System.out.println("PASS production preference pair recovery/atomic save/canonical export");} }''',
        }
        with tempfile.TemporaryDirectory() as directory:
            tmp = pathlib.Path(directory)
            paths = []
            for name, content in stubs.items():
                target = tmp / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content)
                paths.append(str(target))
            sources = [ROOT / 'launcher/src/main/java/com/cbkii/ts18launcher' / name
                       for name in ('LauncherPrefs.java', 'AppearanceSchedule.java', 'ConfigurationStore.java')]
            compiled = subprocess.run([java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(tmp / 'classes'), *paths, *map(str, sources)], capture_output=True, text=True)
            self.assertEqual(0, compiled.returncode, compiled.stderr)
            ran = subprocess.run([java, '-cp', str(tmp / 'classes'), 'com.cbkii.ts18launcher.PrefsHarness'], capture_output=True, text=True)
            self.assertEqual(0, ran.returncode, ran.stderr)

    def test_shared_foreground_owner_and_binary_boundaries(self):
        java = shutil.which('java')
        if not java:
            self.skipTest('Java required')
        stubs = {
            'android/annotation/SuppressLint.java': 'package android.annotation; public @interface SuppressLint {String[] value();}',
            'android/content/Context.java': '''package android.content;
                public class Context { public int registrations,removals; public BroadcastReceiver receiver;
                public Context getApplicationContext(){return this;}
                public Intent registerReceiver(BroadcastReceiver r,IntentFilter f){receiver=r;registrations++;return null;}
                public void unregisterReceiver(BroadcastReceiver r){removals++;receiver=null;} }''',
            'android/content/Intent.java': '''package android.content; public class Intent {
                public static final String ACTION_TIME_CHANGED="time",ACTION_TIMEZONE_CHANGED="zone",ACTION_SCREEN_ON="screen";}''',
            'android/content/IntentFilter.java': '''package android.content; public class IntentFilter {
                public IntentFilter(String a){} public void addAction(String a){} }''',
            'android/content/BroadcastReceiver.java': '''package android.content; public abstract class BroadcastReceiver {
                public abstract void onReceive(Context c,Intent i); }''',
            'android/content/SharedPreferences.java': '''package android.content;
                public interface SharedPreferences { interface OnSharedPreferenceChangeListener {
                void onSharedPreferenceChanged(SharedPreferences p,String k); }
                void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l);
                void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l); }''',
            'android/os/Looper.java': 'package android.os; public class Looper {public static Looper getMainLooper(){return null;}}',
            'android/os/Handler.java': '''package android.os; import java.util.*; public class Handler {
                public static final List<Runnable> tasks=new ArrayList<>(); public Handler(Looper l){}
                public void removeCallbacks(Runnable r){tasks.removeIf(t->t==r);}
                public boolean post(Runnable r){tasks.add(r);return true;}
                public boolean postDelayed(Runnable r,long d){tasks.add(r);return true;}
                public static void runNext(){tasks.remove(0).run();} }''',
            'com/cbkii/ts18launcher/LauncherPrefs.java': '''package com.cbkii.ts18launcher;
                import android.content.*; public class LauncherPrefs {
                public static final String APPEARANCE_AUTO="auto",APPEARANCE_DAY="day",APPEARANCE_NIGHT="night";
                public static final int DEFAULT_DAY_START_MINUTES=420,DEFAULT_NIGHT_START_MINUTES=1140;
                static String mode="auto"; static int reads; static final Prefs prefs=new Prefs();
                static class Prefs implements SharedPreferences { OnSharedPreferenceChangeListener listener;
                public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){if(listener!=null)throw new AssertionError();listener=l;}
                public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){listener=null;} }
                static Prefs prefs(Context c){return prefs;}
                static String appearanceMode(Context c){reads++;return mode;}
                static int appearanceDayStartMinutes(Context c){return -1;}
                static int appearanceNightStartMinutes(Context c){return -1;} }''',
            'com/cbkii/ts18launcher/Harness.java': '''package com.cbkii.ts18launcher;
                import android.content.*; import android.os.*; import java.util.*;
                public class Harness {static void check(boolean b){if(!b)throw new AssertionError();}
                public static void main(String[] args){
                check(AppearanceSchedule.valid(420,421)); check(!AppearanceSchedule.valid(-1,420));
                check(AppearanceSchedule.effectiveDayStart(420,420)==420);
                check(AppearanceSchedule.effectiveNightStart(420,420)==1140);
                check(AppearanceSchedule.effectiveNightStart(-1,420)==1140);
                check(!AppearanceSchedule.valid(1140,-1)); check(!AppearanceSchedule.valid(420,420));
                for(int m=0;m<1440;m++)check(AppearanceSchedule.resolve(m,-1,-1).equals(m>=420&&m<1140?"day":"night"));
                check(AppearanceSchedule.resolve(360,1200,360).equals("night"));
                check(AppearanceSchedule.resolve(359,1200,360).equals("day"));
                check(AppearanceSchedule.resolve(720,420,420).equals("day"));
                Context c=new Context(); List<String> a=new ArrayList<>(),b=new ArrayList<>();
                var first=new AppearanceController(c,a::add); var second=new AppearanceController(c,b::add);
                first.start(); first.start(); second.start(); check(c.registrations==1); check(Handler.tasks.size()==1);
                int reads=LauncherPrefs.reads;
                for(int i=0;i<100;i++)AppearanceController.resolvedMode(c); check(reads==LauncherPrefs.reads);
                LauncherPrefs.mode="night"; LauncherPrefs.prefs.listener.onSharedPreferenceChanged(LauncherPrefs.prefs,"ui.appearance.mode");
                Handler.runNext(); check(a.get(a.size()-1).equals("night"));check(b.get(b.size()-1).equals("night"));
                check(Handler.tasks.isEmpty()); int ac=a.size(),bc=b.size();
                c.receiver.onReceive(c,new Intent());check(a.size()==ac&&b.size()==bc);
                first.stop(); check(c.removals==0);
                LauncherPrefs.mode="day"; LauncherPrefs.prefs.listener.onSharedPreferenceChanged(LauncherPrefs.prefs,"ui.appearance.mode");
                Handler.runNext();check(a.size()==ac);check(b.get(b.size()-1).equals("day"));
                second.stop();second.stop();check(c.removals==1);check(Handler.tasks.isEmpty());check(LauncherPrefs.prefs.listener==null);
                LauncherPrefs.mode="night";first.start();check(a.get(a.size()-1).equals("night"));first.stop();
                System.out.println("PASS binary boundaries/shared-owner/cached-snapshot/stopped-observer/restart");
                } }''',
        }
        with tempfile.TemporaryDirectory() as directory:
            tmp = pathlib.Path(directory)
            paths = []
            for name, content in stubs.items():
                target = tmp / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content)
                paths.append(str(target))
            sources = [ROOT / 'launcher/src/main/java/com/cbkii/ts18launcher' / name for name in ('AppearanceController.java', 'AppearanceSchedule.java')]
            compiled = subprocess.run([java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(tmp / 'classes'), *paths, *map(str, sources)], capture_output=True, text=True)
            self.assertEqual(0, compiled.returncode, compiled.stderr)
            ran = subprocess.run([java, '-cp', str(tmp / 'classes'), 'com.cbkii.ts18launcher.Harness'], capture_output=True, text=True)
            self.assertEqual(0, ran.returncode, ran.stderr)


if __name__ == '__main__':
    unittest.main()
