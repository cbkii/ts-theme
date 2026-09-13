package com.cbkii.ts18launcher;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/** Selected-package/task authority for foreign HOME navigation experiments. */
final class NavigationWindowController {
    enum State { IDLE, STARTING, PRESENTING, WINDOWED, SUSPENDED, FULLSCREEN_HANDOFF, FAILED, DESTROYED }
    private static final String TAG="TS18Nav";
    private static final String ORGANIC_MAPS_INCAR="app.organicmaps.incar";
    private static final long POST_LAUNCH_RECONCILE_MS=350L;
    private final Activity activity; private final NativeNavigationPanel panel; private final Handler main=new Handler(Looper.getMainLooper());
    private NavigationSurfaceBackend backend; private String backendMode=""; private State state=State.IDLE;
    private NavigationWindowBounds bounds; private String activePackage=""; private int activeTaskId=-1; private int generation; private boolean homeVisible;

    NavigationWindowController(Activity activity,NativeNavigationPanel panel){this.activity=activity;this.panel=panel;panel.setBoundsListener(this::onBoundsChanged);}
    void onHomeVisible(){if(state==State.DESTROYED)return;homeVisible=true;NavigationWindowBounds current=panel.currentBounds();if(current!=null)bounds=current;reconcile(false);}
    void onHomeStopped(){homeVisible=false;suspend("HOME stopped");}
    void onLauncherOverlayOpened(){suspend("launcher overlay");}
    void onLauncherOverlayClosed(){if(state!=State.DESTROYED&&homeVisible)reconcile(false);}
    void suspendForExperimentalMap(){suspend("Leaflet active");}

    boolean openFullscreen(Location location){
        if(state==State.DESTROYED)return false;String pkg=selectedPackage();if(pkg.isEmpty())return false;
        String mode=HomeNavigationSurfacePolicy.mode(activity);
        if(HomeNavigationSurfacePolicy.FULLSCREEN.equals(mode)||activeTaskId<=0||backend==null)return NavigationProvider.open(activity,pkg,location);
        final int request=++generation,taskId=activeTaskId;state=State.FULLSCREEN_HANDOFF;Log.i(TAG,"fullscreen request mode="+mode+" package="+pkg+" task="+taskId);
        backend.fullscreen(pkg,taskId,result->{
            if(!isCurrent(request,pkg))return;
            if(validIdentity(result,pkg,taskId)&&result.success)activeTaskId=result.taskId;else Log.w(TAG,"fullscreen verification failed: "+result.code);
            if(!NavigationProvider.open(activity,pkg,location))panel.showUnavailable("Navigation app unavailable",null);
        });return true;
    }
    void retry(){if(state!=State.DESTROYED)reconcile(true);}
    void destroy(){state=State.DESTROYED;homeVisible=false;generation++;main.removeCallbacksAndMessages(null);destroyBackend();}
    private void suspend(String reason){if(state==State.DESTROYED)return;generation++;state=State.SUSPENDED;Log.i(TAG,"suspend: "+reason+" task="+activeTaskId+" package="+activePackage);}
    private void onBoundsChanged(NavigationWindowBounds changed){if(state==State.DESTROYED)return;boolean material=!changed.equals(bounds);bounds=changed;if(homeVisible&&material&&state!=State.FULLSCREEN_HANDOFF)reconcile(false);}

    private void reconcile(boolean force){
        if(!homeVisible||state==State.DESTROYED||state==State.FULLSCREEN_HANDOFF)return;
        String mode=HomeNavigationSurfacePolicy.mode(activity);
        if(HomeNavigationSurfacePolicy.LEAFLET.equals(mode)){suspend("Leaflet selected");return;}
        String pkg=selectedPackage();
        if(pkg.isEmpty()){resetAuthority();state=State.FAILED;panel.showUnavailable("Choose a Navigation app in Settings",null);return;}
        if(HomeNavigationSurfacePolicy.FULLSCREEN.equals(mode)){
            destroyBackend();backendMode=mode;if(!pkg.equals(activePackage)){activePackage=pkg;activeTaskId=-1;}state=State.IDLE;
            panel.showFullscreenOnly(AppResolver.labelFor(activity,pkg,"Navigation"),()->NavigationProvider.open(activity,pkg,null));return;
        }
        NavigationWindowBounds target=bounds;
        if(target==null){main.postDelayed(()->{if(state!=State.DESTROYED&&homeVisible){NavigationWindowBounds current=panel.currentBounds();if(current!=null){bounds=current;reconcile(force);}}},80L);return;}
        ensureBackend(mode);
        if(backend==null){panel.showUnavailable("Unsupported navigation surface mode",()->NavigationProvider.open(activity,pkg,null));return;}
        if(!pkg.equals(activePackage)){activePackage=pkg;activeTaskId=-1;generation++;}
        if(!force&&activeTaskId>0)verifyOrRepair(pkg,target,activeTaskId);else launchAndPresent(pkg,target);
    }

    private void verifyOrRepair(String pkg,NavigationWindowBounds target,int taskId){
        final int request=++generation;state=State.PRESENTING;
        backend.verify(pkg,target,taskId,result->{
            if(!isCurrent(request,pkg))return;
            if(acceptSurfaceResult(result,pkg,taskId,target)){markReady(result);return;}
            if("TASK_NOT_FOUND".equals(result.code)){activeTaskId=-1;launchAndPresent(pkg,target);return;}
            if("TASK_AMBIGUOUS".equals(result.code)){fail(result,pkg);return;}
            backend.present(pkg,target,taskId,repaired->{if(!isCurrent(request,pkg))return;if(acceptSurfaceResult(repaired,pkg,taskId,target))markReady(repaired);else fail(repaired,pkg);});
        });
    }

    private void launchAndPresent(String pkg,NavigationWindowBounds target){
        Intent launch=activity.getPackageManager().getLaunchIntentForPackage(pkg);
        if(launch==null){state=State.FAILED;panel.showUnavailable("Navigation app unavailable",null);return;}
        activePackage=pkg;state=State.STARTING;panel.showStarting(AppResolver.labelFor(activity,pkg,"Navigation"),backend.label());final int request=++generation;
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try{
            if(HomeNavigationSurfacePolicy.RAW_FREEFORM.equals(backendMode)){ActivityOptions options=ActivityOptions.makeBasic();options.setLaunchBounds(target.asRect());activity.startActivity(launch,options.toBundle());}
            else activity.startActivity(launch);
        }catch(RuntimeException error){state=State.FAILED;panel.showUnavailable("Navigation launch failed",()->NavigationProvider.open(activity,pkg,null));return;}
        main.postDelayed(()->{if(!isCurrent(request,pkg))return;backend.present(pkg,target,0,result->{if(!isCurrent(request,pkg))return;if(acceptSurfaceResult(result,pkg,-1,target))markReady(result);else fail(result,pkg);});},POST_LAUNCH_RECONCILE_MS);
    }

    private boolean acceptSurfaceResult(NavigationHelperResult result,String pkg,int expectedTask,NavigationWindowBounds target){
        if(!result.success||result.taskId<=0||!pkg.equals(result.packageName))return false;
        if(expectedTask>0&&result.taskId!=expectedTask)return false;
        if(result.displayId!=0||!result.component.startsWith(pkg+"/"))return false;
        if(!target.toString().equals(result.bounds))return false;
        int expectedMode=HomeNavigationSurfacePolicy.ANDROID_PIP.equals(backendMode)?2:5;
        return result.windowingMode==expectedMode;
    }
    private boolean validIdentity(NavigationHelperResult result,String pkg,int expectedTask){return result!=null&&pkg.equals(result.packageName)&&result.taskId==expectedTask&&result.component.startsWith(pkg+"/")&&result.displayId==0;}
    private void markReady(NavigationHelperResult result){activeTaskId=result.taskId;state=State.WINDOWED;String detail=HomeNavigationSurfacePolicy.ANDROID_PIP.equals(backendMode)?"Android PiP active · physical interaction still unqualified":"Raw freeform active · OEM HOME composition still unqualified";panel.showReady(detail);Log.i(TAG,"surface state accepted mode="+backendMode+" package="+activePackage+" task="+activeTaskId+" windowingMode="+result.windowingMode+" bounds="+result.bounds);}
    private void fail(NavigationHelperResult result,String pkg){state=State.FAILED;Log.w(TAG,"surface failed mode="+backendMode+" package="+pkg+" code="+result.code+" raw="+result.raw);panel.showUnavailable(failureText(result),()->NavigationProvider.open(activity,pkg,null));}
    private void ensureBackend(String mode){if(mode.equals(backendMode)&&backend!=null)return;destroyBackend();backendMode=mode;if(HomeNavigationSurfacePolicy.RAW_FREEFORM.equals(mode))backend=new RawFreeformTaskBackend(activity);else if(HomeNavigationSurfacePolicy.ANDROID_PIP.equals(mode))backend=new AndroidPipBackend(activity);activeTaskId=-1;generation++;}
    private void destroyBackend(){if(backend!=null)backend.destroy();backend=null;}
    private void resetAuthority(){activePackage="";activeTaskId=-1;generation++;}
    private boolean isCurrent(int request,String pkg){return state!=State.DESTROYED&&request==generation&&pkg.equals(activePackage)&&homeVisible;}
    private String selectedPackage(){String configured=LauncherPrefs.packageFor(activity,LauncherPrefs.KEY_NAV);if(!configured.isEmpty())return configured;return packageInstalled(ORGANIC_MAPS_INCAR)?ORGANIC_MAPS_INCAR:"";}
    private boolean packageInstalled(String pkg){try{activity.getPackageManager().getApplicationInfo(pkg,0);return true;}catch(PackageManager.NameNotFoundException ignored){return false;}}
    private static String failureText(NavigationHelperResult r){
        switch(r.code){case"ROOT_REQUIRED":case"ROOT_UNAVAILABLE":return"Experimental task surface needs Magisk root";case"TASK_NOT_FOUND":return"Navigation task not found";case"TASK_AMBIGUOUS":return"Multiple navigation tasks found · close duplicates";case"PIP_FEATURE_MISSING":return"Android PiP is not supported by this system";case"PIP_UNSUPPORTED":return"Configured map Activity does not support Android PiP";case"PIP_SUPPORT_UNKNOWN":return"Cannot prove configured map Activity supports PiP";case"PIP_OCCUPIED_BY_OTHER_APP":return"Android PiP is already owned by another app";case"PIP_RESIZE_FAILED":case"PIP_MODE_REJECTED":return"Android PiP could not occupy the HOME map rectangle";case"WINDOWING_MODE_MISMATCH":return"Navigation task entered the wrong window mode";case"BOUNDS_MISMATCH":return"Navigation task rejected the HOME map rectangle";case"DISPLAY_MISMATCH":return"Navigation task moved to the wrong display";case"TIMEOUT":case"INSTALL_TIMEOUT":return"Navigation experiment timed out";default:return"Navigation surface unavailable · "+r.code;}
    }
}
