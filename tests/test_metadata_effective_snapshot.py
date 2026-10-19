"""Execute the actual ticker View using reconciled snapshots without a listener instance."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher"


class EffectiveTickerTest(unittest.TestCase):
    def test_process_snapshot_renders_and_diagnostics_cannot_erase_it(self):
        if not shutil.which("java"):
            self.skipTest("Java unavailable")
        stubs = {
            "android/content/Context.java": "package android.content;public class Context {public android.content.res.Resources getResources(){return new android.content.res.Resources();}}",
            "android/content/res/Resources.java": "package android.content.res;public class Resources{public float getDimension(int d){return 18;}}",
            "android/graphics/Typeface.java": "package android.graphics;public class Typeface{public static int NORMAL=0;public static Typeface create(String s,int n){return null;}}",
            "android/text/TextUtils.java": "package android.text;public class TextUtils{public enum TruncateAt{END}}",
            "android/util/TypedValue.java": "package android.util;public class TypedValue{public static int COMPLEX_UNIT_PX=0;}",
            "android/view/Gravity.java": "package android.view;public class Gravity{public static int CENTER_VERTICAL=1,BOTTOM=2,TOP=3;}",
            "android/view/View.java": "package android.view;public class View{public static int GONE=8,VISIBLE=0;public void setVisibility(int v){}protected void onAttachedToWindow(){}protected void onDetachedFromWindow(){}protected void onWindowVisibilityChanged(int v){}public boolean isAttachedToWindow(){return true;}public boolean isShown(){return true;}public void invalidate(){}public boolean post(Runnable r){r.run();return true;}}",
            "android/widget/TextView.java": '''package android.widget;public class TextView extends android.view.View {
public String text="";public TextView(android.content.Context c){}public void setText(String s){text=s;}public void setTextColor(int c){}
public void setTextSize(int u,float s){}public void setTypeface(android.graphics.Typeface t){}public void setSingleLine(boolean b){}
public void setHorizontallyScrolling(boolean b){}public void setEllipsize(android.text.TextUtils.TruncateAt t){}public void setGravity(int g){}}''',
            "android/widget/LinearLayout.java": '''package android.widget;public class LinearLayout extends android.view.View {
android.content.Context c;public LinearLayout(android.content.Context c){this.c=c;}public static int VERTICAL=1;
public android.content.Context getContext(){return c;}public void setOrientation(int o){}public void setGravity(int g){}
public void setPadding(int a,int b,int c,int d){}public void addView(android.view.View v,LayoutParams l){}
public static class LayoutParams{public static int MATCH_PARENT=-1;public LayoutParams(int a,int b,float f){}}}''',
            "com/cbkii/ts18launcher/Doubles.java": '''package com.cbkii.ts18launcher;
class MetadataFadeRow extends android.view.View {MetadataFadeRow(android.content.Context c,android.widget.TextView t){}}
class RadioMetadataResolver {RadioMetadataResolver(android.content.Context c,Runnable r){}void start(){}void stop(){}String frequency(MediaListenerService.Snapshot s){return "";}}
class SlowMarqueeTextView extends android.widget.TextView{SlowMarqueeTextView(android.content.Context c){super(c);}}
class R{static class dimen{static int ui_metadata_padding_horizontal,driver_gap,ui_metadata_text,ui_metadata_secondary_text;}static class color{static int ui_text,ui_text_secondary;}}
class AutomotiveUi{static int dimen(android.content.Context c,int n){return 1;}static int color(android.content.Context c,int n){return 1;}}
class MediaSelection{static String MUSIC="music",RADIO="radio";}
class MediaListenerService {interface Observer {void onMediaStateChanged(Snapshot m,Snapshot r);}static void addObserver(Observer o){}static void removeObserver(Observer o){}
static class Snapshot {String packageName,title,artist;Snapshot(String p,String t,String a){packageName=p;title=t;artist=a;}Snapshot(String p,String t,String a,boolean playing){this(p,t,a);}}}
class AppResolver{static String labelFor(android.content.Context c,String p,String f){return p.equals("com.tw.media")?"Auxio":"NavRadio+";}}
class RadioProvider{static String resolvePackage(android.content.Context c){return "com.navimods.radio";}}
class LauncherPrefs {static String lastSource(android.content.Context c){return "music";}static String KEY_MUSIC="music";static String lastMusicPackage(android.content.Context c){return "com.tw.media";}
static String packageFor(android.content.Context c,String k){return "com.tw.media";}}
''',
            "com/cbkii/ts18launcher/Harness.java": '''package com.cbkii.ts18launcher;
public class Harness{static String primary(MediaMetadataView v)throws Exception{java.lang.reflect.Field f=MediaMetadataView.class.getDeclaredField("primary");f.setAccessible(true);return ((android.widget.TextView)f.get(v)).text;}
static void check(boolean b){if(!b)throw new AssertionError("effective ticker pipeline");}
static void feed(MediaMetadataView v,MediaListenerService.Snapshot s,String source)throws Exception{
try{MediaMetadataView.class.getDeclaredMethod("setSnapshot",MediaListenerService.Snapshot.class,String.class).invoke(v,s,source);}
catch(NoSuchMethodException e){v.setMetadata(s.title,s.artist);}}
public static void main(String[] a)throws Exception{
MediaMetadataView v=new MediaMetadataView(new android.content.Context());
feed(v,new MediaListenerService.Snapshot("com.tw.media","Track","Artist"),"music");check(primary(v).equals("Track"));
v.setMetadata("Preparing Music…","readiness");check(primary(v).equals("Track"));
feed(v,new MediaListenerService.Snapshot("com.navimods.radio","Radio","FM"),"radio");check(primary(v).equals("FM"));
feed(v,new MediaListenerService.Snapshot("","",""),"radio");check(primary(v).isEmpty());
System.out.println("PASS process ticker/source switch/session removal");}}
''',
        }
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name, text in stubs.items():
                p = root / name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(text)
            for name in ("MediaMetadataView", "MediaTickerPolicy", "MediaMetadataPolicy", "RadioFrequency"):
                shutil.copyfile(SRC / (name + ".java"), root / "com/cbkii/ts18launcher" / (name + ".java"))
            subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-d", str(root),
                            *map(str, root.rglob("*.java"))], check=True)
            subprocess.run(["java", "-cp", str(root), "com.cbkii.ts18launcher.Harness"], check=True)
