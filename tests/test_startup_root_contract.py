"""Execute the actual root adapter and normal-start classification, including installed identity gates."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'launcher/src/main/java/com/cbkii/ts18launcher'


class StartupRootContractTests(unittest.TestCase):
    def test_only_current_exported_entries_and_definite_refusals_are_admitted(self):
        if not shutil.which('java'): self.skipTest('JDK unavailable')
        files={
'android/content/ComponentName.java': '''package android.content;public class ComponentName {String p,c;public ComponentName(String p,String c){this.p=p;this.c=c;}public String getPackageName(){return p;}public String flattenToShortString(){return p+"/"+c;}}''',
'android/content/Intent.java': '''package android.content;public class Intent {public static int FLAG_ACTIVITY_NEW_TASK=1;public static String ACTION_MAIN="main",CATEGORY_LAUNCHER="launcher";
public ComponentName component;public Intent(){}public Intent(String s){}public Intent addFlags(int f){return this;}public Intent setClassName(String p,String c){component=new ComponentName(p,c);return this;}public Intent addCategory(String c){return this;}public ComponentName getComponent(){return component;}}''',
'android/content/ActivityNotFoundException.java': 'package android.content;public class ActivityNotFoundException extends RuntimeException {}',
'android/content/Context.java': '''package android.content;public class Context {public int calls;public RuntimeException error;public Context getApplicationContext(){return this;}public String getPackageName(){return "com.cbkii.ts18launcher";}public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();}public void startActivity(Intent i){calls++;if(error!=null)throw error;}}''',
'android/content/pm/ApplicationInfo.java':'package android.content.pm;public class ApplicationInfo {public static int FLAG_SYSTEM=1,FLAG_UPDATED_SYSTEM_APP=128;public int uid=10179,flags;public boolean enabled=true;}',
'android/content/pm/ActivityInfo.java':'package android.content.pm;public class ActivityInfo {public boolean enabled=true,exported=true;public String packageName="com.tw.media";public ApplicationInfo applicationInfo=new ApplicationInfo();}',
'android/content/pm/PackageManager.java': '''package android.content.pm;public class PackageManager {public static ActivityInfo info=new ActivityInfo();public static String pkg="com.tw.media",entry="com.tw.music.MusicActivity";public static class NameNotFoundException extends Exception{}
public android.content.Intent getLaunchIntentForPackage(String p){android.content.Intent i=new android.content.Intent();i.setClassName(pkg,entry);return i;}
public ActivityInfo getActivityInfo(android.content.ComponentName n,int f)throws NameNotFoundException{return info;}
public ApplicationInfo getApplicationInfo(String p,int f)throws NameNotFoundException{return info.applicationInfo;}public CharSequence getApplicationLabel(ApplicationInfo i){return "app";}}''',
'android/os/SystemClock.java':'package android.os;public class SystemClock {public static long elapsedRealtime(){return 100;}}',
'com/cbkii/ts18launcher/Doubles.java':'''package com.cbkii.ts18launcher;
class AuxioPreparationContract{static boolean capability=true;static String COMPONENT="com.tw.media/org.oxycblt.auxio.headunit.LauncherPreparationActivity";static boolean supported(android.content.Context c){return capability;}static String canonicalEntry(android.content.Context c,android.content.ComponentName n){return "com.tw.media/org.oxycblt.auxio.MainActivity";}}
class RootTaskSession{static RootTaskSession get(android.content.Context c){return new RootTaskSession();}NavigationHelperResult runCurrent(long d,String... f){return null;}}
class AndroidUserId{static int UID_RANGE_PER_USER=100000;static int current(){return 0;}static int fromUid(int u){return u/100000;}}
class MediaSourceAdapter{static String AUXIO_PACKAGE="com.tw.media",NAVRADIO_PACKAGE="com.navimods.radio";}
class NavigationHelperResult{String code;NavigationHelperResult(String c){code=c;}static NavigationHelperResult failure(String c,String r){return new NavigationHelperResult(c);}}
class NavigationRootHelper{static int calls;static String[] args;NavigationRootHelper(android.content.Context c){}void setDeadline(long d){}NavigationHelperResult run(String a,String... s){calls++;args=s;return new NavigationHelperResult(a);}}
class MediaListenerService{static void noteExplicitLaunch(android.content.Context c,String p){}}
''',
'com/cbkii/ts18launcher/Harness.java':'''package com.cbkii.ts18launcher;import android.content.*;import android.content.pm.*;
public class Harness {static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
public static void main(String[] x){Context c=new Context();StartupRootFallback f=new StartupRootFallback(c);
f.launch("com.tw.media","com.tw.media/com.tw.music.MusicActivity",10,7000L);check(NavigationRootHelper.calls==1,"current Auxio entry rejected");
check(NavigationRootHelper.args[1].equals(AuxioPreparationContract.COMPONENT),"silent preparation entry was not used");
f.launch("com.tw.radio","com.tw.radio/.RadioActivity",10,7000L);f.launch("com.tw.media","com.tw.media/.Other",10,7000L);f.restoreHome("com.tw.media","com.tw.media/com.tw.music.MusicActivity",10,10);
check(NavigationRootHelper.calls==1,"protected/arbitrary/shared identity admitted");
PackageManager.info.exported=false;f.launch("com.tw.media","com.tw.media/com.tw.music.MusicActivity",10,7000L);check(NavigationRootHelper.calls==1,"non-exported entry admitted");PackageManager.info.exported=true;
PackageManager.info.applicationInfo.enabled=false;f.launch("com.tw.media","com.tw.media/com.tw.music.MusicActivity",10,7000L);check(NavigationRootHelper.calls==1,"disabled app admitted");PackageManager.info.applicationInfo.enabled=true;
PackageManager.info.applicationInfo.flags=1;f.launch("com.tw.media","com.tw.media/com.tw.music.MusicActivity",10,7000L);check(NavigationRootHelper.calls==1,"system app admitted");PackageManager.info.applicationInfo.flags=0;
PackageManager.info.applicationInfo.uid=110179;f.launch("com.tw.media","com.tw.media/com.tw.music.MusicActivity",10,7000L);check(NavigationRootHelper.calls==1,"other Android user admitted");PackageManager.info.applicationInfo.uid=10179;
check(StartupRootFallback.definitelyNotStarted(new NavigationHelperResult("STARTUP_SOURCE_REJECTED")),"explicit rejection unknown");
check(StartupRootFallback.definitelyNotStarted(new NavigationHelperResult("STARTUP_FOCUS_CHANGED")),"pre-dispatch focus refusal was not classified");
for(String code:new String[]{"STARTUP_SOURCE_UNCERTAIN","PHASE_TIMEOUT","HELPER_EXIT_1"}) check(!StartupRootFallback.definitelyNotStarted(new NavigationHelperResult(code)),"uncertainty permits replay");
check(AppResolver.launchForStartup(c,"com.tw.media")==AppResolver.LaunchOutcome.ACCEPTED,"acceptance lost");
c.error=new SecurityException();check(AppResolver.launchForStartup(c,"com.tw.media")==AppResolver.LaunchOutcome.REJECTED,"permission refusal uncertain");
c.error=new ActivityNotFoundException();check(AppResolver.launchForStartup(c,"com.tw.media")==AppResolver.LaunchOutcome.REJECTED,"missing Activity uncertain");
c.error=new IllegalStateException();check(AppResolver.launchForStartup(c,"com.tw.media")==AppResolver.LaunchOutcome.UNCERTAIN,"runtime error called definitely rejected");
System.out.println("PASS root package/export/entry/claim gates and normal outcome classification");}}
'''}
        with tempfile.TemporaryDirectory() as tmp:
            d=Path(tmp)
            for name,text in files.items():
                p=d/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)
            for name in ('StartupRootFallback','AppResolver'):
                shutil.copyfile(SRC/(name+'.java'),d/'com/cbkii/ts18launcher'/(name+'.java'))
            subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',tmp,*map(str,d.rglob('*.java'))],check=True)
            subprocess.run(['java','-cp',tmp,'com.cbkii.ts18launcher.Harness'],check=True,timeout=30)
