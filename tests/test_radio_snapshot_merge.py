"""Compile the actual Activity merge method; reject stale NavRadio notification enrichment."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'launcher/src/main/java/com/cbkii/ts18launcher'


def method(text, signature):
    start = text.index(signature)
    brace = text.index('{', start)
    depth = 1
    end = brace + 1
    while depth:
        if text[end] == '{':
            depth += 1
        elif text[end] == '}':
            depth -= 1
        end += 1
    return text[start:end]


class RadioSnapshotMergeTest(unittest.TestCase):
    def test_actual_merge_preserves_station_frequency_association(self):
        activity = (SRC / 'LauncherActivity.java').read_text()
        listener = (SRC / 'MediaListenerService.java').read_text()
        merge = '\n'.join(method(activity, sig) for sig in (
            'private static MediaListenerService.Snapshot mergeSessionSnapshot(',
            'private static MediaListenerService.Snapshot normaliseSnapshot(',
            'private static MediaListenerService.Snapshot emptyMediaSnapshot('))
        station = '\n'.join(method(listener, sig) for sig in (
            'static String meaningfulRadioTitle(', 'private static boolean isRadioPlaceholder(',
            'private static String normalise('))
        with tempfile.TemporaryDirectory() as tmp:
            work = Path(tmp)
            pkg = work / 'com/cbkii/ts18launcher'
            pkg.mkdir(parents=True)
            shutil.copyfile(SRC / 'RadioFrequency.java', pkg / 'RadioFrequency.java')
            (pkg / 'Harness.java').write_text('''package com.cbkii.ts18launcher;
class MediaListenerService {
static class Snapshot {
String packageName,title,artist;int state;long actions;Object sessionIdentity;RadioFrequency radioFrequency;
Snapshot(String p,String t,String a,boolean b){this(p,t,a,0,0,null);}
Snapshot(String p,String t,String a,int s,long n,Object token){packageName=p;title=t;artist=a;state=s;actions=n;sessionIdentity=token;radioFrequency=RadioFrequency.observed(p,t,a);}}
''' + station + '''}
public class Harness {
''' + merge + '''
static MediaListenerService.Snapshot snap(String p,String t,String a){return new MediaListenerService.Snapshot(p,t,a,3,1,"same-token");}
static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
public static void main(String[] args){
String radio=RadioFrequency.NAVRADIO;
MediaListenerService.Snapshot current=snap(radio,"New station","");
check(mergeSessionSnapshot(current,snap(radio,"Old station","105.9 FM"),true)==current,"new station cannot inherit old frequency");
check(mergeSessionSnapshot(current,snap(radio,"","105.9 FM"),true)==current,"frequency needs station association");
check(mergeSessionSnapshot(current,snap(radio,"New station","106.3 FM"),true).radioFrequency.khz==106300,"same station enrichment");
current=snap(radio,"Station","106.3 FM");
check(mergeSessionSnapshot(current,snap(radio,"Station","105.9 FM"),true)==current,"authoritative frequency wins");
check(mergeSessionSnapshot(snap(radio,"NavRadio+",""),snap(radio,"2CA","1053 AM"),true).title.equals("2CA"),"placeholder can enrich");
check(mergeSessionSnapshot(snap("music","Track",""),snap("music","Track","Artist"),true).artist.equals("Artist"),"music unchanged");
check(mergeSessionSnapshot(snap(radio,"Station",""),snap("music","Track","Artist"),true).packageName.equals(radio),"source switch isolation");
System.out.println("PASS actual source merge / stale frequency / placeholders / music");}}
''')
            subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',str(work),*map(str,pkg.glob('*.java'))],check=True,timeout=30)
            subprocess.run(['java','-cp',str(work),'com.cbkii.ts18launcher.Harness'],check=True,timeout=30)
