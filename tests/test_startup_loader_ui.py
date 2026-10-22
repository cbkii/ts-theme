"""Execute the production loader against a window/focus/permission boundary model."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class StartupLoaderUiTest(unittest.TestCase):
    def test_real_loader_geometry_permissions_dismissal_and_window_cleanup(self):
        if not shutil.which('java'):
            self.skipTest('JDK unavailable')
        files = {
            'android/os/Process.java': 'package android.os;public class Process {public static int myUid(){return 10211;}}',
            'android/view/KeyEvent.java': 'package android.view;public class KeyEvent {public static int KEYCODE_BACK=4,ACTION_DOWN=0,ACTION_UP=1;int action;public KeyEvent(int a){action=a;}public int getKeyCode(){return 4;}public int getAction(){return action;}public boolean isCanceled(){return false;}}',

            'android/content/Context.java': 'package android.content;public class Context {public static String WINDOW_SERVICE="window";public Context getApplicationContext(){return this;}public Object getSystemService(String s){return android.view.WindowManager.shared;}}',
            'android/app/Activity.java': 'package android.app;public class Activity extends android.content.Context {}',
            'android/graphics/PixelFormat.java': 'package android.graphics;public class PixelFormat {public static int TRANSLUCENT=-3,OPAQUE=-1;}',
            'android/graphics/Rect.java': '''package android.graphics;public class Rect {public int left,top,right,bottom;public Rect(){}public Rect(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}public int width(){return right-left;}public int height(){return bottom-top;}public boolean isEmpty(){return width()<=0||height()<=0;}public void set(Rect r){left=r.left;top=r.top;right=r.right;bottom=r.bottom;}public boolean equals(Object o){if(!(o instanceof Rect))return false;Rect r=(Rect)o;return left==r.left&&top==r.top&&right==r.right&&bottom==r.bottom;}}''',
            'android/provider/Settings.java': 'package android.provider;public class Settings {public static boolean allowed;public static boolean canDrawOverlays(android.content.Context c){return allowed;}}',
            'android/view/Gravity.java': 'package android.view;public class Gravity {public static int CENTER=1,TOP=2,LEFT=4;}',
            'android/view/View.java': '''package android.view;public class View {public static int GONE=8,VISIBLE=0;public int visibility=VISIBLE;public Object parent,layout;public interface Click{void onClick(View v);}public Click click;public void setOnClickListener(Click c){click=c;}public void performClick(){if(click!=null)click.onClick(this);}public void setVisibility(int v){visibility=v;}public void setFocusableInTouchMode(boolean b){}public void setClickable(boolean b){}public boolean requestFocus(){return true;}public boolean dispatchKeyEvent(KeyEvent e){return false;}public void setBackgroundColor(int c){}public void setPadding(int l,int t,int r,int b){}public void setLayoutParams(Object p){layout=p;}public Object getParent(){return parent;}public void getLocationOnScreen(int[] p){p[0]=20;p[1]=30;}public void bringToFront(){}}''',
            'android/view/WindowManager.java': '''package android.view;public class WindowManager {public static WindowManager shared=new WindowManager();public int adds,removes;public boolean deny;public View added;public LayoutParams params;
public static class LayoutParams {public static int TYPE_APPLICATION_OVERLAY=2038,FLAG_NOT_FOCUSABLE=8,FLAG_NOT_TOUCH_MODAL=32,FLAG_LAYOUT_IN_SCREEN=256;public int width,height,type,flags,gravity,x,y;public LayoutParams(int w,int h,int t,int f,int p){width=w;height=h;type=t;flags=f;}public void setTitle(String s){}}
public void addView(View v,LayoutParams p){adds++;if(deny)throw new SecurityException();if(added!=null)throw new AssertionError("duplicate overlay");added=v;params=p;}public void removeViewImmediate(View v){if(v!=added)throw new AssertionError("wrong overlay");removes++;added=null;}}''',
            'android/widget/FrameLayout.java': '''package android.widget;public class FrameLayout extends android.view.View {public java.util.List<android.view.View> children=new java.util.ArrayList<>();public FrameLayout(android.app.Activity c){}public static class LayoutParams {public int width,height,leftMargin,topMargin;public LayoutParams(int w,int h){width=w;height=h;}public LayoutParams(int w,int h,int g){this(w,h);}}public void addView(android.view.View v){children.add(v);v.parent=this;}public void addView(android.view.View v,LayoutParams p){addView(v);v.layout=p;}public void removeView(android.view.View v){children.remove(v);v.parent=null;}}''',
            'android/widget/LinearLayout.java': '''package android.widget;public class LinearLayout extends android.view.View {public static int VERTICAL=1;java.util.List<android.view.View> children=new java.util.ArrayList<>();public LinearLayout(android.app.Activity c){}public static class LayoutParams{public int topMargin,bottomMargin;public LayoutParams(int w,int h){}}public void setOrientation(int i){}public void setGravity(int i){}public void addView(android.view.View v){children.add(v);v.parent=this;}public void addView(android.view.View v,LayoutParams p){addView(v);}public android.view.View getChildAt(int i){return children.get(i);}}''',
            'android/widget/TextView.java': 'package android.widget;public class TextView extends android.view.View {CharSequence text="";public TextView(android.app.Activity c){}public void setText(int i){text="resource";}public void setText(CharSequence s){text=s;}public CharSequence getText(){return text;}public void setTextSize(int i){}public void setTextColor(int c){}public void setGravity(int g){}}',
            'android/widget/Button.java': 'package android.widget;public class Button extends TextView{public Button(android.app.Activity c){super(c);}}',
            'android/widget/ProgressBar.java': 'package android.widget;public class ProgressBar extends android.view.View{public ProgressBar(android.app.Activity c){}public void setIndeterminate(boolean b){}}',
            'com/cbkii/ts18launcher/Support.java': '''package com.cbkii.ts18launcher;class AutomotiveUi {static int color(Object c,int i){return i;}static int dimen(Object c,int i){return 16;}}class R{static class color{static int ui_black=1,ui_text=2;}static class dimen{static int driver_gap=1,driver_target_min=2;}static class string{static int startup_loader_title=1,startup_loader_dismiss=2;}}class MediaEventTrace{static void record(String a,String b,String c){}}''',
            'com/cbkii/ts18launcher/Harness.java': '''package com.cbkii.ts18launcher;
import android.app.*;import android.widget.*;import android.view.*;import android.provider.*;
public class Harness {static void check(boolean b,String s){if(!b)throw new AssertionError(s);}public static void main(String[] a){
Activity activity=new Activity();FrameLayout root=new FrameLayout(activity);int[] clicks={0};StartupReadinessLoader l=new StartupReadinessLoader(activity,root,()->clicks[0]++);
l.geometry(96,88,1000,600);l.show("Music: preparing",true);l.showMask();check(WindowManager.shared.adds==0&&!l.canMask(),"permission denial mutated WMS");
Settings.allowed=true;l.showMask();WindowManager.LayoutParams p=WindowManager.shared.params;
check(p.width==1000&&p.height==600&&p.x==116&&p.y==118,"mask escaped supplied safe rectangle");
check(p.type==2038&&(p.flags&8)==0&&(p.flags&32)!=0,"mask must receive BACK while preserving outside system areas");l.showMask();check(WindowManager.shared.adds==1,"duplicate mask");
WindowManager.shared.added.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN));check(clicks[0]==0,"BACK down dismissed twice");WindowManager.shared.added.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP));check(clicks[0]==1,"owning-window BACK not wired");
l.hide();check(WindowManager.shared.added==null&&WindowManager.shared.removes==1,"dismiss leaked window");
l.show("Music: ready",false);l.showMask();l.geometry(96,88,900,600);check(WindowManager.shared.added==null,"old geometry retained");
l.showMask();check(WindowManager.shared.params.width==900,"resize ignored");l.destroy();l.destroy();check(root.children.isEmpty()&&WindowManager.shared.added==null,"destroy leaked Activity/window");
StartupReadinessLoader denied=new StartupReadinessLoader(activity,root,()->{});denied.geometry(0,0,100,100);denied.show("preparing",true);WindowManager.shared.deny=true;int before=WindowManager.shared.adds;
denied.showMask();denied.showMask();check(WindowManager.shared.adds==before+1&&!denied.canMask(),"repeated failed WMS admission");denied.destroy();
System.out.println("PASS actual loader permission/focus/geometry/click/resize/destruction/fail-open");}}
'''
        }
        with tempfile.TemporaryDirectory() as temp:
            out = Path(temp)
            for name, text in files.items():
                p = out / name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(text)
            name = 'com/cbkii/ts18launcher/StartupReadinessLoader.java'
            shutil.copyfile(ROOT / 'launcher/src/main/java' / name, out / name)
            focus = 'com/cbkii/ts18launcher/StartupMaskFocus.java'
            shutil.copyfile(ROOT / 'launcher/src/main/java' / focus, out / focus)
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(out),
                            *map(str, out.rglob('*.java'))], check=True)
            subprocess.run(['java', '-cp', str(out), 'com.cbkii.ts18launcher.Harness'], check=True)
