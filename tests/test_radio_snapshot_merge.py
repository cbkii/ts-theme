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
            'static String meaningfulRadioTitle(', 'static Snapshot applyRadioFallback(', 'private static boolean isRadioPlaceholder(',
            'private static String normalise('))
        with tempfile.TemporaryDirectory() as tmp:
            work = Path(tmp)
            pkg = work / 'com/cbkii/ts18launcher'
            pkg.mkdir(parents=True)
            shutil.copyfile(SRC / 'RadioFrequency.java', pkg / 'RadioFrequency.java')
            shutil.copyfile(SRC / 'RadioStationAssociation.java', pkg / 'RadioStationAssociation.java')
            (pkg / 'Harness.java').write_text('''package com.cbkii.ts18launcher;
class MediaListenerService {
static class Snapshot {
Snapshot withMetadata(String t,String a){return new Snapshot(packageName,t,a,state,actions,sessionIdentity);}String packageName,title,artist;int state;long actions;Object sessionIdentity;RadioFrequency radioFrequency;
Snapshot(String p,String t,String a,boolean b){this(p,t,a,0,0,null);}
Snapshot(String p,String t,String a,int s,long n,Object token){packageName=p;title=t;artist=a;state=s;actions=n;sessionIdentity=token;radioFrequency=RadioFrequency.observed(p,t,a);}}
''' + station + '''}
public class Harness {
''' + merge + '''
static MediaListenerService.Snapshot snap(String p,String t,String a){return new MediaListenerService.Snapshot(p,t,a,3,1,"same-token");}
static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
public static void main(String[] args){
RadioStationAssociation association=new RadioStationAssociation();
association.observe("token","Station A");association.notification("Station A");check(association.current(),"fresh association missing");
association.observe("token","");check(!association.current(),"placeholder retained old station association");
association.notification("Station A");check(!association.current(),"delayed old notification crossed placeholder epoch");
association.observe("token","Station B");check(!association.current(),"new station inherited old notification");
association.notification("Station B");check(association.current(),"new matching notification refused");
association.observe("another-token","Station B");check(!association.current(),"token replacement retained association");
association.observe("direct-token","Station A");association.notification("Station A");
association.observe("direct-token","Station B");check(!association.current(),"direct A-to-B retained old notification generation");
association.notification("Station B");check(association.current(),"direct station notification not admitted");
var fresh=snap("music","Track B","Artist B");var stale=snap("music","Track A","Artist A");
var merged=mergeSessionSnapshot(fresh,stale,true);
check(merged.title.equals("Track B")&&merged.artist.equals("Artist B"),"fresh Track B overwritten by stale Track A");
check(mergeSessionSnapshot(snap("music","","New artist"),stale,true).title.isEmpty(),"unknown track inherited stale title");
var paused=new MediaListenerService.Snapshot("music","Track B","Artist B",2,7,"same-token");
check(mergeSessionSnapshot(paused,stale,true).state==2,"external PAUSE overwritten");
String radio=RadioFrequency.NAVRADIO;
var frequencyOnly=MediaListenerService.applyRadioFallback(snap(radio,"NavRadio+","105.9 FM"),"","");
check(frequencyOnly.title.equals("NavRadio+")&&frequencyOnly.artist.equals("105.9 FM"),"listener promoted frequency into station row");
MediaListenerService.Snapshot current=snap(radio,"New station","");
check(mergeSessionSnapshot(current,snap(radio,"Old station","105.9 FM"),true)==current,"new station cannot inherit old frequency");
check(mergeSessionSnapshot(current,snap(radio,"","105.9 FM"),true)==current,"frequency needs station association");
check(mergeSessionSnapshot(current,snap(radio,"New station","106.3 FM"),true).radioFrequency.khz==106300,"same station enrichment");
check(mergeSessionSnapshot(snap(radio,"Station","Pop Music"),snap(radio,"Station","105.9 FM"),true).artist.equals("Pop Music 105.9 FM"),"valid frequency lost behind current RDS");
current=snap(radio,"Station","106.3 FM");
check(mergeSessionSnapshot(current,snap(radio,"Station","105.9 FM"),true)==current,"authoritative frequency wins");
check(mergeSessionSnapshot(snap(radio,"NavRadio+",""),snap(radio,"2CA","1053 AM"),true).title.equals("2CA"),"placeholder can enrich");
check(mergeSessionSnapshot(snap("music","Track",""),snap("music","Track","Artist"),true).artist.isEmpty(),"music has one publisher; title equality is not track identity");
check(mergeSessionSnapshot(snap(radio,"Station",""),snap("music","Track","Artist"),true).packageName.equals(radio),"source switch isolation");
System.out.println("PASS actual source merge / stale frequency / placeholders / music");}}
''')
            subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',str(work),*map(str,pkg.glob('*.java'))],check=True,timeout=30)
            subprocess.run(['java','-cp',str(work),'com.cbkii.ts18launcher.Harness'],check=True,timeout=30)
