"""Exercise the real client against blocked Binder and lifecycle boundaries."""
from pathlib import Path
import subprocess
import tempfile
import unittest
from test_navigation_controller_recovery import has_java_compiler

ROOT = Path(__file__).resolve().parents[1]

@unittest.skipUnless(has_java_compiler(), 'Java compiler required')
class CompactClientTests(unittest.TestCase):
    def test_timeout_replacement_saturation_auth_and_idempotent_close(self):
        files = {
            'android/os/Looper.java': 'package android.os; public class Looper {public static Looper getMainLooper(){return new Looper();}}',
            'android/os/SystemClock.java': 'package android.os; public class SystemClock {public static long now;public static long elapsedRealtime(){return now;}}',
            'android/os/Handler.java': '''package android.os; public class Handler {
record Job(Handler h,Runnable r,long due){}static java.util.List<Job> jobs=new java.util.ArrayList<>();
public Handler(Looper l){} public void handleMessage(Message m){}
public boolean post(Runnable r){return postDelayed(r,0);}public boolean postDelayed(Runnable r,long d){synchronized(jobs){jobs.add(new Job(this,r,SystemClock.now+d));}return true;}
public void removeCallbacks(Runnable r){synchronized(jobs){jobs.removeIf(j->j.h()==this&&j.r()==r);}}
public void removeCallbacksAndMessages(Object o){synchronized(jobs){jobs.removeIf(j->j.h()==this);}}
public static void until(long time){for(int n=0;;n++){Job next;synchronized(jobs){next=jobs.stream().filter(j->j.due()<=time).min(java.util.Comparator.comparingLong(Job::due)).orElse(null);if(next==null)break;jobs.remove(next);}if(n>100)throw new AssertionError("unbounded jobs");SystemClock.now=Math.max(SystemClock.now,next.due());next.r().run();}SystemClock.now=time;}}
''',
            'android/os/Message.java': 'package android.os; public class Message {public int what,sendingUid;}',
            'android/os/IBinder.java': 'package android.os; public interface IBinder {}',
            'android/os/Messenger.java': 'package android.os; public class Messenger {public static Handler handler;public Messenger(Handler h){handler=h;}public IBinder getBinder(){return null;}}',
            'android/os/Bundle.java': 'package android.os; public class Bundle {public void putInt(String k,int v){}public void putBinder(String k,IBinder b){}}',
            'android/os/CancellationSignal.java': 'package android.os; public class CancellationSignal {volatile boolean stopped;public void cancel(){stopped=true;}public boolean isCanceled(){return stopped;}}',
            'android/os/Build.java': 'package android.os;public class Build{public static class VERSION{public static int SDK_INT=29;}}',
            'android/os/PowerManager.java': 'package android.os;public class PowerManager{public boolean isInteractive(){return true;}}',
            'android/os/UserManager.java': 'package android.os;public class UserManager{public boolean isUserUnlocked(){return true;}}',
            'android/app/KeyguardManager.java': 'package android.app;public class KeyguardManager{public boolean isKeyguardLocked(){return false;}}',
            'android/media/AudioManager.java': 'package android.media;public class AudioManager{public static int MODE_NORMAL=0;public int getMode(){return 0;}}',
            'android/provider/Settings.java': 'package android.provider;public class Settings{public static class Global{public static String BOOT_COUNT="boot";public static int getInt(Object r,String k,int d){return 7;}}}',
            'android/net/Uri.java': 'package android.net;public class Uri{public static Uri parse(String s){return new Uri();}public Uri buildUpon(){return this;}public Uri appendQueryParameter(String k,String v){return this;}public Uri build(){return this;}}',
            'android/database/Cursor.java': 'package android.database;public interface Cursor extends AutoCloseable{boolean moveToFirst();int getInt(int c);String getString(int c);int getColumnIndexOrThrow(String k);void close();}',
            'android/content/pm/ApplicationInfo.java': 'package android.content.pm;public class ApplicationInfo{public int uid=24000;}',
            'android/content/pm/PackageManager.java': 'package android.content.pm;public class PackageManager{public static class NameNotFoundException extends Exception{}public ApplicationInfo getApplicationInfo(String p,int f)throws NameNotFoundException{return new ApplicationInfo();}}',
            'android/content/SharedPreferences.java': 'package android.content;public class SharedPreferences{public int getInt(String k,int d){return d;}public SharedPreferences edit(){return this;}public SharedPreferences putInt(String k,int v){return this;}public void apply(){}}',
            'android/content/Context.java': '''package android.content;public class Context{public static String POWER_SERVICE="p",KEYGUARD_SERVICE="k",AUDIO_SERVICE="a",USER_SERVICE="u";public static int MODE_PRIVATE=0;public Context getApplicationContext(){return this;}public Object getSystemService(String k){return null;}public SharedPreferences getSharedPreferences(String k,int f){return new SharedPreferences();}public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();}public ContentResolver getContentResolver(){return new ContentResolver();}}''',
            'android/content/ContentResolver.java': '''package android.content;public class ContentResolver{
public static final java.util.concurrent.atomic.AtomicInteger observes=new java.util.concurrent.atomic.AtomicInteger();public static volatile boolean block;public static java.util.concurrent.CountDownLatch release=new java.util.concurrent.CountDownLatch(1);
public Object call(android.net.Uri u,String m,String a,android.os.Bundle b){if(m.equals("observe")){observes.incrementAndGet();if(block)try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}}return null;}
public android.database.Cursor query(android.net.Uri u,String[] p,String s,String[] a,String o,android.os.CancellationSignal c){return null;}}
''',
            'Doubles.java': '''package com.cbkii.ts18launcher;
class CompactStartupPeer{static String PACKAGE="app.organicmaps.incar";static java.util.concurrent.atomic.AtomicInteger auth=new java.util.concurrent.atomic.AtomicInteger();static boolean trusted(android.content.Context c,int uid){if(Thread.currentThread().getName().equals("main"))throw new AssertionError("PM on main");auth.incrementAndGet();return uid==24000;}static boolean ownsProvider(android.content.Context c,String a){return true;}}
class AndroidUserId{static int current(){return 0;}}class HomeMode{static boolean isDefaultHome(android.content.Context c){return true;}}class MediaEventTrace{static void record(String a,String b,String... c){}}
''',
            'Harness.java': '''package com.cbkii.ts18launcher;
import android.os.*;import android.content.*;import java.util.concurrent.*;
public class Harness{
static void check(boolean b){if(!b)throw new AssertionError();}
static void waitFor(java.util.function.BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(!condition.getAsBoolean()){if(System.nanoTime()>end)throw new AssertionError("worker did not run");Thread.sleep(1);}}
public static void main(String[] args)throws Exception{
int[] callbacks={0};CompactStartupClient c=new CompactStartupClient(new Context(),s->{check(!s.compatible);callbacks[0]++;});
ContentResolver.block=true;c.watch();waitFor(()->ContentResolver.observes.get()==1);
Handler.until(2000);check(callbacks[0]==1);Handler.until(2500);waitFor(()->ContentResolver.observes.get()==2);
Handler.until(4500);Handler.until(5500);check(callbacks[0]==3);check(ContentResolver.observes.get()==2);
// Both timed-out calls may recover after several failures. Observation must resume
// inside the boot window rather than stopping permanently after the third refusal.
ContentResolver.block=false;ContentResolver.release.countDown();
var field=CompactStartupClient.class.getDeclaredField("worker");field.setAccessible(true);
var pool=(java.util.concurrent.ThreadPoolExecutor)field.get(c);waitFor(()->pool.getActiveCount()==0);
// getActiveCount()==0 does not prove SynchronousQueue workers have begun waiting.
// A transient rejection is permitted; require recovery inside the bounded window.
var busy=CompactStartupClient.class.getDeclaredField("busy");busy.setAccessible(true);
for(int retry=0;retry<5&&ContentResolver.observes.get()<3;retry++){
 Handler.until(7000+retry*3000);
 waitFor(()->{try{return ContentResolver.observes.get()>=3||!busy.getBoolean(c);}catch(Exception e){throw new AssertionError(e);}});
}
check(ContentResolver.observes.get()==3);waitFor(()->pool.getActiveCount()==0);Handler.until(SystemClock.now);check(callbacks[0]>=4);
int completedCallbacks=callbacks[0];long checkpoint=SystemClock.now+500;
// Saturated cleanup is harmless, as is canonical HOME redirect followed by onDestroy.
c.close();c.close();ContentResolver.release.countDown();Thread.sleep(20);Handler.until(checkpoint);check(callbacks[0]==completedCallbacks);
// Invalidation authentication must leave the main looper and ignore untrusted UIDs.
ContentResolver.block=false;int initial=CompactStartupPeer.auth.get();CompactStartupClient d=new CompactStartupClient(new Context(),s->{});
Message m=new Message();m.what=1;m.sendingUid=123;Messenger.handler.handleMessage(m);waitFor(()->CompactStartupPeer.auth.get()>initial);
Handler.until(checkpoint);check(ContentResolver.observes.get()==3);
// A timer scheduled just before expiry cannot initiate a query after the window.
var until=CompactStartupClient.class.getDeclaredField("until");until.setAccessible(true);until.setLong(d,checkpoint+2500);
var schedule=CompactStartupClient.class.getDeclaredMethod("scheduleNext",CompactStartupClient.Snapshot.class);schedule.setAccessible(true);
SystemClock.now=checkpoint+2499;schedule.invoke(d,CompactStartupClient.Snapshot.unknown());Handler.until(checkpoint+3500);
Thread.sleep(10);check(ContentResolver.observes.get()==3);
// Genuine authenticated events remain observable after the periodic window expires.
m=new Message();m.what=1;m.sendingUid=24000;Messenger.handler.handleMessage(m);
var dpool=(java.util.concurrent.ThreadPoolExecutor)field.get(d);waitFor(()->dpool.getActiveCount()==0);Handler.until(checkpoint+3500);
waitFor(()->ContentResolver.observes.get()==4);waitFor(()->dpool.getActiveCount()==0);Handler.until(checkpoint+3500);d.close();d.close();
System.out.println("PASS actual client timeout/replacement/saturation/off-main-auth/double-close");}}
''',
        }
        files['CompactStartupClient.java'] = (ROOT / 'launcher/src/main/java/com/cbkii/ts18launcher/CompactStartupClient.java').read_text()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name, source in files.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(source)
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(root), *map(str, root.rglob('*.java'))], check=True, timeout=30)
            subprocess.run(['java', '-cp', str(root), 'com.cbkii.ts18launcher.Harness'], check=True, timeout=15)
