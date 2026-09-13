package com.cbkii.ts18launcher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Launcher-owned geometry/status only; it never claims a foreign map is rendered inside this View. */
@SuppressLint({"SetTextI18n","ViewConstructor"})
final class NativeNavigationPanel extends FrameLayout {
    interface BoundsListener{void onBoundsChanged(NavigationWindowBounds bounds);}
    private final TextView status; private final Button action; private final TextView provenance;
    private BoundsListener boundsListener; private NavigationWindowBounds lastBounds; private Runnable actionRunnable;
    NativeNavigationPanel(Activity activity){
        super(activity);setBackgroundColor(Color.BLACK);setFocusable(false);setClickable(false);
        LinearLayout message=new LinearLayout(activity);message.setOrientation(LinearLayout.VERTICAL);message.setGravity(Gravity.CENTER);
        status=new TextView(activity);status.setTextColor(AutomotiveUi.color(activity,R.color.ui_text));status.setTextSize(18f);status.setGravity(Gravity.CENTER);status.setText("Navigation surface not started");
        int pad=AutomotiveUi.dimen(activity,R.dimen.driver_gap);status.setPadding(pad,pad,pad,pad);message.addView(status,new LinearLayout.LayoutParams(-2,-2));
        provenance=new TextView(activity);provenance.setText(BuildIdentity.summary(activity));provenance.setTextColor(AutomotiveUi.color(activity,R.color.ui_text_secondary));provenance.setTextSize(12f);provenance.setGravity(Gravity.CENTER);message.addView(provenance,new LinearLayout.LayoutParams(-2,-2));
        action=new Button(activity);action.setText("Open navigation");action.setVisibility(View.VISIBLE);action.setOnClickListener(v->{if(actionRunnable!=null)actionRunnable.run();});message.addView(action,new LinearLayout.LayoutParams(300,88));
        addView(message,new LayoutParams(-2,-2,Gravity.CENTER));
    }
    void setBoundsListener(BoundsListener listener){boundsListener=listener;}
    void setAction(String label,Runnable runnable){actionRunnable=runnable;action.setText(label==null||label.isEmpty()?"Open navigation":label);action.setVisibility(runnable==null?View.GONE:View.VISIBLE);}
    void showStarting(String label,String backend){String app=label==null||label.isEmpty()?"navigation":label;status.setText("Starting "+app+"…\n"+backend);action.setVisibility(View.GONE);}
    void showReady(String detail){status.setText(detail==null||detail.isEmpty()?"Navigation surface active":detail);action.setVisibility(View.GONE);}
    void showUnavailable(String detail,Runnable openFullscreen){status.setText(detail==null||detail.isEmpty()?"Navigation surface unavailable":detail);setAction("Open fullscreen",openFullscreen);}
    void showFullscreenOnly(String label,Runnable openFullscreen){String app=label==null||label.isEmpty()?"Navigation":label;status.setText(app+"\nFullscreen-only mode");setAction("Open navigation",openFullscreen);}
    void applyAppearance(Activity activity){setBackgroundColor(AutomotiveUi.color(activity,R.color.ui_black));status.setTextColor(AutomotiveUi.color(activity,R.color.ui_text));provenance.setTextColor(AutomotiveUi.color(activity,R.color.ui_text_secondary));}
    NavigationWindowBounds currentBounds(){if(getWidth()<=0||getHeight()<=0)return null;try{return NavigationWindowBounds.fromView(this);}catch(IllegalArgumentException ignored){return null;}}
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){super.onLayout(changed,left,top,right,bottom);NavigationWindowBounds b=currentBounds();if(b==null||b.equals(lastBounds))return;lastBounds=b;if(boundsListener!=null)boundsListener.onBoundsChanged(b);}
}
