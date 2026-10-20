"""Execute the real fallback panel against a layout-request-counting Android boundary."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class NativePanelMeasurementTest(unittest.TestCase):
    def test_unchanged_measurements_do_not_request_another_layout(self):
        if not shutil.which('javac'):
            self.skipTest('Java compiler unavailable')
        files = {
            'android/annotation/SuppressLint.java': 'package android.annotation; public @interface SuppressLint {String[] value();}',
            'android/app/Activity.java': 'package android.app; public class Activity {}',
            'android/content/res/ColorStateList.java': 'package android.content.res; public class ColorStateList {public static ColorStateList valueOf(int c){return new ColorStateList();}}',
            'android/graphics/Color.java': 'package android.graphics; public class Color {public static int BLACK=0,WHITE=-1;}',
            'android/graphics/drawable/GradientDrawable.java': 'package android.graphics.drawable; public class GradientDrawable {public static int OVAL=1;public void setShape(int i){}public void setColor(int i){}}',
            'android/graphics/drawable/RippleDrawable.java': 'package android.graphics.drawable; public class RippleDrawable {public RippleDrawable(Object a,Object b,Object c){}}',
            'android/view/Gravity.java': 'package android.view; public class Gravity {public static int CENTER=1;}',
            'android/view/View.java': '''package android.view;
public class View {
 public static int GONE=8,VISIBLE=0; public int paddingRequests; int padding,w,h;
 Object params; public interface Click {void onClick(View v);}
 public static class MeasureSpec {public static int EXACTLY=0x40000000; public static int makeMeasureSpec(int s,int m){return s|m;}}
 public void setPadding(int l,int t,int r,int b){padding=l;paddingRequests++;}
 public int getPaddingLeft(){return padding;}public int getPaddingTop(){return padding;}
 public int getPaddingRight(){return padding;}public int getPaddingBottom(){return padding;}
 public void setFocusable(boolean b){}public void setClickable(boolean b){}public void setBackgroundColor(int c){}
 public void setBackground(Object b){}public void setContentDescription(String s){}public void setOnClickListener(Click c){}
 public void setVisibility(int i){}public Object getLayoutParams(){return params;}public void setLayoutParams(Object p){params=p;}
 public void measure(int x,int y){w=x&0x3fffffff;h=y&0x3fffffff;}
 public int getWidth(){return w;}public int getHeight(){return h;}public int getMeasuredWidth(){return w;}public int getMeasuredHeight(){return h;}
}''',
            'android/widget/FrameLayout.java': '''package android.widget; import android.view.View;
public class FrameLayout extends View {public FrameLayout(android.app.Activity a){}
 public static class LayoutParams {public int width,height; public LayoutParams(int w,int h,int g){width=w;height=h;}}
 public void addView(View v,LayoutParams p){v.setLayoutParams(p);}
 protected void onMeasure(int w,int h){measure(w,h);}
 protected void onLayout(boolean c,int l,int t,int r,int b){}
}''',
            'android/widget/ImageView.java': 'package android.widget; public class ImageView extends android.view.View {public enum ScaleType{FIT_CENTER}}',
            'android/widget/ImageButton.java': '''package android.widget; public class ImageButton extends ImageView {
 public ImageButton(android.app.Activity a){}public void setImageResource(int i){}public void setImageTintList(Object c){}
 public void setScaleType(ScaleType s){} }''',
            'com/cbkii/ts18launcher/Support.java': '''package com.cbkii.ts18launcher;
class R {static class drawable {static int ic_navigation_filled=1;} static class color {static int ui_black=2,ui_text=3;}}
class AutomotiveUi {static void attachFeedback(Object v){} static int color(Object c,int id){return id;}}
class NavigationWindowBounds {static NavigationWindowBounds fromView(Object v){return new NavigationWindowBounds();}}
''',
            'com/cbkii/ts18launcher/MeasureTest.java': '''package com.cbkii.ts18launcher;
public class MeasureTest {public static void main(String[] args)throws Exception {
 NativeNavigationPanel panel=new NativeNavigationPanel(new android.app.Activity());
 var field=NativeNavigationPanel.class.getDeclaredField("openNavigation");field.setAccessible(true);
 var child=(android.view.View)field.get(panel);
 panel.onMeasure(800,600);int first=child.paddingRequests;
 for(int i=0;i<1000;i++)panel.onMeasure(800,600);
 if(first!=1 || child.paddingRequests!=first || child.getMeasuredWidth()!=450)throw new AssertionError("layout loop");
 panel.onMeasure(800,400);
 if(child.paddingRequests!=first+1 || child.getMeasuredWidth()!=300)throw new AssertionError("resize");
 }}''',
        }
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name, content in files.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
            source = ROOT / 'launcher/src/main/java/com/cbkii/ts18launcher/NativeNavigationPanel.java'
            result = subprocess.run(['javac', '-d', tmp, *map(str, root.rglob('*.java')), str(source)],
                                    capture_output=True, text=True, timeout=30)
            self.assertEqual(0, result.returncode, result.stderr)
            subprocess.run(['java', '-cp', tmp, 'com.cbkii.ts18launcher.MeasureTest'], check=True, timeout=10)
