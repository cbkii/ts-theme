"""Execute production listener reconnection: access, connection and safe wake are separate."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ListenerRecoveryTest(unittest.TestCase):
    def test_bounded_rebind_connection_revocation_and_unlock(self):
        files = {
            'android/os/Looper.java': 'package android.os;public class Looper {public static Looper getMainLooper(){return null;}}',
            'android/os/Handler.java': '''package android.os;import java.util.*;public class Handler {
public record Job(Runnable r,long at){}public static long now;public static List<Job> jobs=new ArrayList<>();public Handler(Looper l){}
public void post(Runnable r){postDelayed(r,0);}public void postDelayed(Runnable r,long d){jobs.add(new Job(r,now+d));}
public static void until(long t){int n=0;while(!jobs.isEmpty()){Job j=jobs.stream().min(Comparator.comparingLong(Job::at)).get();if(j.at()>t)break;if(++n>40)throw new AssertionError("unbounded retries");jobs.remove(j);now=j.at();j.r().run();}now=t;}}
''',
            'android/os/SystemClock.java': 'package android.os;public class SystemClock {public static long uptimeMillis(){return Handler.now;}}',
            'android/os/PowerManager.java': 'package android.os;public class PowerManager {public static boolean awake=true;public boolean isInteractive(){return awake;}}',
            'android/app/KeyguardManager.java': 'package android.app;public class KeyguardManager {public static boolean locked;public boolean isKeyguardLocked(){return locked;}}',
            'android/content/Context.java': '''package android.content;public class Context {public static String POWER_SERVICE="p",KEYGUARD_SERVICE="k";
public Context getApplicationContext(){return this;}public Object getSystemService(String s){return s.equals("p")?new android.os.PowerManager():new android.app.KeyguardManager();}}''',
            'android/content/ComponentName.java': 'package android.content;public class ComponentName {public ComponentName(Context c,Class<?> s){}public String flattenToString(){return "launcher/.Listener";}public String flattenToShortString(){return flattenToString();}}',
            'android/service/notification/NotificationListenerService.java': 'package android.service.notification;public class NotificationListenerService {public static int requests;public static void requestRebind(android.content.ComponentName c){requests++;}}',
            'com/cbkii/ts18launcher/Doubles.java': '''package com.cbkii.ts18launcher;
class MediaListenerService {static boolean granted=true,connected;static boolean hasNotificationAccess(android.content.Context c){return granted;}static boolean isConnected(){return connected;}static void refreshActiveSessions(){}}
class ProcessMediaSessionMonitor {static int refreshes;static void refresh(android.content.Context c){refreshes++;}}
class AndroidUserId {static int current(){return 0;}}
class MediaEventTrace {static void record(String a,String b,String... c){}}
class RootShell {static class Result {boolean completed;int exitCode;}static Result runMillis(String c,long t){throw new AssertionError("connection recovery attempted root grant");}}
''',
            'com/cbkii/ts18launcher/Harness.java': '''package com.cbkii.ts18launcher;
import android.os.*;import android.app.KeyguardManager;import android.service.notification.NotificationListenerService;
public class Harness {static void check(boolean b,String s){if(!b)throw new AssertionError(s);}public static void main(String[] a){
var c=new android.content.Context();MediaNotificationAccessBootstrapper.ensureConnected(c);MediaNotificationAccessBootstrapper.ensureConnected(c);
Handler.until(100000);check(NotificationListenerService.requests==5,"rebind not bounded/coalesced");
MediaNotificationAccessBootstrapper.ensureConnected(c);Handler.until(100000);check(NotificationListenerService.requests==6,"safe HOME did not re-arm");
MediaListenerService.connected=true;MediaNotificationAccessBootstrapper.connected();Handler.until(200000);check(NotificationListenerService.requests==6,"connected listener kept retrying");
MediaListenerService.connected=false;MediaNotificationAccessBootstrapper.ensureConnected(c);Handler.until(200000);int before=NotificationListenerService.requests;
MediaListenerService.granted=false;Handler.until(300000);check(NotificationListenerService.requests==before,"revoked access retried");
MediaListenerService.granted=true;KeyguardManager.locked=true;MediaNotificationAccessBootstrapper.ensureConnected(c);Handler.until(300000);check(NotificationListenerService.requests==before,"locked user retried");
KeyguardManager.locked=false;MediaNotificationAccessBootstrapper.ensureConnected(c);Handler.until(300000);check(NotificationListenerService.requests==before+1,"unlock did not retry");
PowerManager.awake=false;Handler.until(400000);check(NotificationListenerService.requests==before+1,"sleep retried");
System.out.println("PASS production listener granted/disconnected/bounded/connected/revoked/unlock/sleep");}}
'''
        }
        files['com/cbkii/ts18launcher/MediaNotificationAccessBootstrapper.java'] = (ROOT / 'launcher/src/main/java/com/cbkii/ts18launcher/MediaNotificationAccessBootstrapper.java').read_text()
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp)
            for name, text in files.items():
                p = out / name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(text)
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(out), *map(str, out.rglob('*.java'))], check=True, timeout=30)
            subprocess.run(['java', '-cp', str(out), 'com.cbkii.ts18launcher.Harness'], check=True, timeout=30)
