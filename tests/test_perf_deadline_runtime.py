"""Production sampler with its wall-clock wait accelerated; main cleanup is deliberately stalled."""
from pathlib import Path
import subprocess
import tempfile
import unittest
from test_navigation_controller_recovery import has_java_compiler
ROOT=Path(__file__).resolve().parents[1]
@unittest.skipUnless(has_java_compiler(), 'JDK required')
class PerfDeadlineTest(unittest.TestCase):
    def test_report_does_not_wait_for_main_and_old_frame_cannot_join_a_new_sample(self):
        files={
            'android/os/Looper.java':'package android.os;public class Looper{static final Looper MAIN=new Looper();public static Looper getMainLooper(){return MAIN;}public static Looper myLooper(){return MAIN;}}',
            'android/os/Handler.java':'package android.os;public class Handler{public Handler(Looper l){}public boolean post(Runnable r){return true;}}',
            'android/os/Process.java':'package android.os;public class Process{public static long getElapsedCpuTime(){return 0;}}',
            'android/os/Debug.java':'package android.os;public class Debug{public static long getPss(){return 10;}}',
            'android/util/Log.java':'package android.util;public class Log{public static java.util.List<String> lines=new java.util.concurrent.CopyOnWriteArrayList<>();public static int i(String t,String s){lines.add(s);return 0;}}',
            'android/view/Choreographer.java':'''package android.view;public class Choreographer {
public interface FrameCallback{void doFrame(long n);}public static java.util.List<FrameCallback> callbacks=new java.util.ArrayList<>();
public static Choreographer getInstance(){return new Choreographer();}public void postFrameCallback(FrameCallback c){callbacks.add(c);}public void removeFrameCallback(FrameCallback c){callbacks.remove(c);}}''',
            'Harness.java':'''package com.cbkii.ts18launcher;
public class Harness{static void check(boolean b){if(!b)throw new AssertionError();}
static void report(int count)throws Exception{long until=System.nanoTime()+3_000_000_000L;
while(android.util.Log.lines.stream().filter(s->s.contains("window_ms=")).count()<count){if(System.nanoTime()>until)throw new AssertionError("report blocked behind main");Thread.sleep(1);}}
public static void main(String[] args)throws Exception{
check(PerfSampler.start());var old=android.view.Choreographer.callbacks.get(0);old.doFrame(0);old.doFrame(16_000_000);report(1);
check(PerfSampler.start());int before=android.view.Choreographer.callbacks.size();old.doFrame(32_000_000);check(android.view.Choreographer.callbacks.size()==before);report(2);
System.out.println("PASS independent sampler deadline/report and per-sample callback lifetime");}}''',
        }
        source=(ROOT/'launcher/src/main/java/com/cbkii/ts18launcher/PerfSampler.java').read_text()
        self.assertIn('Thread.sleep(10000L)',source)
        files['PerfSampler.java']=source.replace('Thread.sleep(10000L)','Thread.sleep(20L)')
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            for name,source in files.items():
                p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source)
            subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',str(root),*map(str,root.rglob('*.java'))],check=True,timeout=30)
            subprocess.run(['java','-cp',str(root),'com.cbkii.ts18launcher.Harness'],check=True,timeout=10)
