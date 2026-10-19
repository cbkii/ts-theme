"""Execute production parser, geography and ticker contracts with the shipped reference data."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'launcher/src/main/java/com/cbkii/ts18launcher'

class RadioDirectoryTest(unittest.TestCase):
    def test_parser_geography_ambiguity_and_music_isolation(self):
        with tempfile.TemporaryDirectory() as tmp:
            work = Path(tmp)
            pkg = work / 'com/cbkii/ts18launcher'
            pkg.mkdir(parents=True)
            for name in ('RadioFrequency', 'AustralianRadioDirectory', 'MediaTickerPolicy'):
                shutil.copyfile(SRC / (name + '.java'), pkg / (name + '.java'))
            (pkg / 'Harness.java').write_text(r'''package com.cbkii.ts18launcher;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;
class MediaSelection{static final String RADIO="radio",MUSIC="music";}
public class Harness{
static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
static String freq(String... s){RadioFrequency f=RadioFrequency.observed(RadioFrequency.NAVRADIO,s);return f==null?"":f.display();}
public static void main(String[] a)throws Exception{
 check(freq("105.9 FM").equals("105.9 FM"),"FM suffix");
 check(freq("FM 105.900").equals("105.9 FM"),"FM prefix");
 check(freq("1053 kHz").equals("1053 AM"),"AM units");
 check(freq("105.9 MHz").equals("105.9 FM"),"MHz units");
 check(freq("105900","Track 1053","1 FM","108.1 FM").isEmpty(),"reject untyped/out of range");
 check(freq("105.9 FM","106.3 FM").isEmpty(),"conflicting observations");
 check(RadioFrequency.containsExplicitFrequency(RadioFrequency.NAVRADIO,"105.9 FM / 106.3 FM"),"conflict cannot fall back");
 check(RadioFrequency.observed("com.tw.media","105.9 FM")==null,"music isolation");
 check(!RadioFrequency.needsDirectory(RadioFrequency.NAVRADIO,"Station","105.9 FM"),"observed frequency needs no directory/location");
 check(!RadioFrequency.needsDirectory(RadioFrequency.NAVRADIO,"105.9 FM / 106.3 FM",""),"conflict needs no directory/location");
 check(RadioFrequency.needsDirectory(RadioFrequency.NAVRADIO,"2CA",""),"station-only uses fallback");
 AustralianRadioDirectory d;
 try(Reader s=new InputStreamReader(new FileInputStream(a[0]+"/au-stations.tsv"),StandardCharsets.UTF_8);
 Reader b=new InputStreamReader(new java.util.zip.GZIPInputStream(new FileInputStream(a[0]+"/au-state-rings.tsv.gz")),StandardCharsets.UTF_8)){d=new AustralianRadioDirectory(s,b);}
 check(d.match("2CA","",null,null).station.frequency.display().equals("1053 AM"),"ACT default/callsign");
 check(d.match("2XX","",-35.28,149.13).station.frequency.display().equals("98.3 FM"),"ACT branding");
 check(d.match("ABC Classic","",-35.28,149.13)==null,"same-state transmitter ambiguity");
 check(d.match("Totally Unknown Station","",null,null)==null,"unknown abstains");
 check(d.match("2CA news","",null,null)==null,"no substring guessing");
 check(d.regionRanks(-35.28,149.13).get("ACT")==0,"Canberra contains ACT");
 check(d.regionRanks(-35.28,149.13).containsKey("NSW"),"ACT near NSW border");
 check(d.regionRanks(-33.87,151.21).get("NSW")==0,"Sydney NSW");
 check(!d.regionRanks(-33.87,151.21).containsKey("ACT"),"outside 99km");
 check(d.regionRanks(-37.81,144.96).get("VIC")==0,"Melbourne VIC");
 check(d.regionRanks(-36.08,146.91).containsKey("NSW") && d.regionRanks(-36.08,146.91).containsKey("VIC"),"Albury border fuzz");
 check(d.regionRanks(-27.47,153.02).get("QLD")==0,"Brisbane QLD");
 check(d.regionRanks(-31.95,115.86).get("WA")==0,"Perth WA");
 check(d.regionRanks(-34.93,138.60).get("SA")==0,"Adelaide SA");
 check(d.regionRanks(-42.88,147.33).get("TAS")==0,"Hobart TAS");
 check(d.regionRanks(-12.46,130.84).get("NT")==0,"Darwin NT");
 check(d.regionRanks(51.5,-0.1).isEmpty(),"outside Australia");
 AustralianRadioDirectory border=new AustralianRadioDirectory(new StringReader(""),new StringReader("NSW\t0,0;1,0;1,1;0,1;0,0\n"));
 check(border.regionRanks(0.5,1.89).containsKey("NSW"),"within 99km fuzz");
 check(!border.regionRanks(0.5,1.90).containsKey("NSW"),"beyond 99km fuzz");
 String rows="ACT\tA\t2AAA\t101500\tFM\tShared\nNSW\tB\t2BBB\t102500\tFM\tShared\nVIC\tC\t3CCC\t103500\tFM\tShared\n";
 AustralianRadioDirectory fixture=new AustralianRadioDirectory(new StringReader(rows),new StringReader(""));
 check(fixture.match("Shared","",null,null).station.state.equals("ACT"),"ACT > NSW > VIC");
 check(fixture.match("Shared","",Collections.singletonMap("NSW",0),false).station.state.equals("NSW"),"current state wins");
 check(fixture.match("2BBB","Shared",null,null).station.state.equals("NSW"),"callsign outranks brand");
 check(MediaTickerPolicy.resolve("Station","DJ","NavRadio+","radio",RadioFrequency.NAVRADIO,"105.9 FM").secondary.equals("105.9 FM"),"frequency second line");
 check(MediaTickerPolicy.resolve("Track","Artist","Auxio","music","com.tw.media","105.9 FM").secondary.equals("Artist"),"music preserved");
 check(MediaTickerPolicy.resolve("Station","DJ","Radio","radio","com.tw.radio","105.9 FM").secondary.equals("DJ"),"other radios preserved");
 System.out.println("PASS radio frequency / all eight regions / ambiguity / source isolation");}}
''')
            subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',str(work),*map(str,pkg.glob('*.java'))],check=True,timeout=30)
            subprocess.run(['java','-cp',str(work),'com.cbkii.ts18launcher.Harness',str(ROOT/'launcher/src/main/assets/radio')],check=True,timeout=30)
