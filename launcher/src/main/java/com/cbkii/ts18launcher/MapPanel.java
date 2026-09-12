package com.cbkii.ts18launcher;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;

@SuppressLint({"SetJavaScriptEnabled", "SetTextI18n", "MissingPermission", "ViewConstructor"})
final class MapPanel extends FrameLayout implements LocationListener {
    interface NavigationLauncher { void openNavigation(Location location); }

    private static final String MAP_URL = "file:///android_asset/map/map.html";
    private static final long HEALTH_CHECK_DELAY_MS = 900L;
    private static final int MAP_ACTION_PX = 84;
    private static final int MAP_ACTION_GAP_PX = 8;

    private final Activity activity;
    private final NavigationLauncher navigationLauncher;
    private final WebView webView;
    private final LinearLayout statusChip;
    private final ImageView statusIcon;
    private final TextView status;
    private final LocationManager locationManager;
    private final TileBroker tileBroker;
    private final ImageButton zoomIn;
    private final ImageButton zoomOut;
    private final ImageButton recenter;
    private final ImageButton openNav;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable mapHealthCheck = this::checkMapHealth;
    private boolean started;
    private boolean pageReady;
    private boolean destroyed;
    private Location lastLocation;

    MapPanel(Activity activity, NavigationLauncher navigationLauncher) {
        super(activity);
        this.activity = activity;
        this.navigationLauncher = navigationLauncher;
        setBackgroundColor(Color.BLACK);

        String userAgent = mapUserAgent();
        tileBroker = new TileBroker(activity.getCacheDir(), userAgent);
        webView = new WebView(activity);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setDatabaseEnabled(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(true);
        settings.setGeolocationEnabled(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkLoads(false);
        settings.setSupportZoom(false);
        settings.setUserAgentString(userAgent);
        webView.setBackgroundColor(Color.BLACK);
        webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, false);
        webView.setWebViewClient(new RestrictedMapClient());
        addView(webView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        statusChip = new LinearLayout(activity);
        statusChip.setGravity(Gravity.CENTER_VERTICAL);
        statusChip.setBackground(AutomotiveUi.chipBackground(activity));
        int statusPad = AutomotiveUi.dimen(activity, R.dimen.driver_gap);
        statusChip.setPadding(statusPad, statusPad / 2, statusPad, statusPad / 2);
        statusIcon = new ImageView(activity);
        statusIcon.setColorFilter(AutomotiveUi.color(activity, R.color.ui_icon));
        statusChip.addView(statusIcon, new LinearLayout.LayoutParams(28, 28));
        status = new TextView(activity);
        status.setTextColor(AutomotiveUi.color(activity, R.color.ui_text));
        status.setTextSize(14f);
        status.setPadding(statusPad / 2, 0, 0, 0);
        statusChip.addView(status, new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        LayoutParams statusLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        statusLp.leftMargin = MAP_ACTION_GAP_PX;
        statusLp.topMargin = MAP_ACTION_GAP_PX;
        addView(statusChip, statusLp);
        showStatus("Locating…", R.drawable.ic_my_location);

        zoomIn = mapButton(R.drawable.ic_zoom_in, "Zoom in", false);
        zoomOut = mapButton(R.drawable.ic_zoom_out, "Zoom out", false);
        recenter = mapButton(R.drawable.ic_my_location, "Follow location", false);
        recenter.setBackground(AutomotiveUi.followModeBackground(activity));
        recenter.setImageTintList(AutomotiveUi.followTint(activity));
        openNav = mapButton(R.drawable.ic_navigation, "Open navigation", true);
        zoomIn.setOnClickListener(v -> adjustZoom(1));
        zoomOut.setOnClickListener(v -> adjustZoom(-1));
        recenter.setOnClickListener(v -> recenterMap());
        openNav.setOnClickListener(v -> navigationLauncher.openNavigation(
                lastLocation == null ? null : new Location(lastLocation)));
        addView(zoomIn); addView(zoomOut); addView(recenter); addView(openNav);
        AutomotiveUi.linkVertical(java.util.Arrays.asList(zoomIn, zoomOut, recenter));
        applyPreferences();

        locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        webView.loadUrl(MAP_URL);
    }

    private String mapUserAgent() {
        String version = "unknown";
        try {
            String installed = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
            if (installed != null && !installed.isEmpty()) version = installed;
        } catch (PackageManager.NameNotFoundException ignored) {}
        return "TS18Launcher/" + version + " (+https://github.com/cbkii/ts-theme)";
    }

    private ImageButton mapButton(int icon, String description, boolean primary) {
        ImageButton button = new ImageButton(activity);
        button.setImageResource(icon);
        button.setContentDescription(description);
        AutomotiveUi.styleMapButton(activity, button, primary);
        return button;
    }

    void applyPreferences() {
        boolean controls = LauncherPrefs.mapControlsEnabled(activity);
        int visibility = controls ? View.VISIBLE : View.GONE;
        zoomIn.setVisibility(visibility); zoomOut.setVisibility(visibility);
        recenter.setVisibility(visibility); openNav.setVisibility(visibility);
        boolean right = LauncherPrefs.railOnRight(activity);
        placeAction(zoomIn, 0, right, true);
        placeAction(zoomOut, 1, right, true);
        placeAction(recenter, 2, right, true);
        placeAction(openNav, 0, right, false);
        LayoutParams statusLp = (LayoutParams) statusChip.getLayoutParams();
        statusLp.gravity = (right ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP;
        statusLp.leftMargin = right ? MAP_ACTION_GAP_PX : 0;
        statusLp.rightMargin = right ? 0 : MAP_ACTION_GAP_PX;
        statusLp.topMargin = MAP_ACTION_GAP_PX;
        statusChip.setLayoutParams(statusLp);
        applyMapAppearance();
    }

    private void placeAction(View view, int row, boolean right, boolean top) {
        LayoutParams lp = new LayoutParams(MAP_ACTION_PX, MAP_ACTION_PX);
        lp.gravity = (right ? Gravity.RIGHT : Gravity.LEFT) | (top ? Gravity.TOP : Gravity.BOTTOM);
        if (right) lp.rightMargin = MAP_ACTION_GAP_PX; else lp.leftMargin = MAP_ACTION_GAP_PX;
        if (top) lp.topMargin = MAP_ACTION_GAP_PX + row * (MAP_ACTION_PX + MAP_ACTION_GAP_PX);
        else lp.bottomMargin = MAP_ACTION_GAP_PX;
        view.setLayoutParams(lp);
    }

    private void applyMapAppearance() {
        if (!pageReady) return;
        String mode = AppearanceController.resolvedMode(activity);
        webView.evaluateJavascript("setMapAppearance('" + mode + "')", null);
    }

    void start() {
        if (started) return;
        started = true;
        if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            showStatus("Location denied", R.drawable.ic_my_location); return;
        }
        if (locationManager == null) { showStatus("GPS unavailable", R.drawable.ic_my_location); return; }
        try {
            Location gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (gps != null) onLocationChanged(gps);
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 5f, this,
                        Looper.getMainLooper());
            } else showStatus("GPS unavailable", R.drawable.ic_my_location);
        } catch (SecurityException ignored) {
            showStatus("Location denied", R.drawable.ic_my_location);
        } catch (IllegalArgumentException ignored) {
            showStatus("GPS unavailable", R.drawable.ic_my_location);
        }
    }

    void stop() {
        if (started && locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (SecurityException ignored) {}
        }
        started = false;
        mainHandler.removeCallbacks(mapHealthCheck);
        webView.onPause();
    }

    void resumeWebView() {
        webView.onResume();
        applyPreferences();
        if (LauncherPrefs.mapEnabled(activity)) start();
    }

    void destroy() {
        destroyed = true; stop(); mainHandler.removeCallbacksAndMessages(null);
        removeView(webView); webView.destroy();
    }

    void onLocationPermissionResult() { started = false; start(); }

    private void adjustZoom(int delta) {
        if (pageReady) { webView.evaluateJavascript("adjustZoom(" + delta + ")", null); scheduleMapHealthCheck(); }
    }

    private void recenterMap() {
        if (pageReady) {
            webView.evaluateJavascript("recenterMap()", null);
            recenter.setSelected(true);
            scheduleMapHealthCheck();
        }
    }

    @Override public void onLocationChanged(Location location) {
        if (location == null) return;
        lastLocation = new Location(location);
        if (pageReady) renderLocation(lastLocation); else showStatus("Map loading", R.drawable.ic_my_location);
    }

    private void renderLocation(Location location) {
        double accuracy = location.hasAccuracy() ? location.getAccuracy() : 0.0;
        double bearing = location.hasBearing() ? location.getBearing() : 0.0;
        String js = String.format(Locale.US, "setLocation(%.7f,%.7f,%.1f,%.1f,%s)",
                location.getLatitude(), location.getLongitude(), accuracy, bearing,
                location.hasBearing() ? "true" : "false");
        webView.evaluateJavascript(js, null);
        showStatus("Map loading", R.drawable.ic_my_location);
        scheduleMapHealthCheck();
    }

    private void scheduleMapHealthCheck() {
        if (destroyed || !pageReady) return;
        mainHandler.removeCallbacks(mapHealthCheck);
        mainHandler.postDelayed(mapHealthCheck, HEALTH_CHECK_DELAY_MS);
    }

    private void checkMapHealth() {
        if (destroyed || !pageReady) return;
        webView.evaluateJavascript("mapHealth()", value -> {
            if (destroyed || value == null) return;
            recenter.setSelected(value.contains(":follow"));
            if (value.contains("ok:")) {
                if (!tileBroker.lastFailure().isEmpty()) showStatus("Map offline", R.drawable.ic_my_location);
                else hideStatus();
            } else if (value.contains("error:")) {
                showStatus("Map offline", R.drawable.ic_my_location);
            } else if (value.contains("runtime-error")) {
                showStatus("Map unavailable", R.drawable.ic_my_location);
            } else if (lastLocation == null) showStatus("Locating…", R.drawable.ic_my_location);
            else showStatus("Map loading", R.drawable.ic_my_location);
        });
    }

    private void showStatus(String text, int icon) {
        statusChip.animate().cancel();
        status.setText(text);
        statusIcon.setImageResource(icon);
        if (statusChip.getVisibility() != View.VISIBLE) {
            statusChip.setAlpha(0f);
            statusChip.setVisibility(View.VISIBLE);
            statusChip.animate().alpha(1f).setDuration(AutomotiveUi.PANEL_REVEAL_MS).start();
        } else {
            statusChip.setAlpha(1f);
        }
    }

    private void hideStatus() {
        if (statusChip.getVisibility() != View.VISIBLE) return;
        statusChip.animate().cancel();
        statusChip.animate().alpha(0f).setDuration(AutomotiveUi.STATE_CROSSFADE_MS).withEndAction(() -> {
            statusChip.setVisibility(View.GONE);
            statusChip.setAlpha(1f);
        }).start();
    }

    private void onMapPageReady(String url) {
        if (!MAP_URL.equals(url)) return;
        pageReady = true;
        applyMapAppearance();
        if (lastLocation != null) renderLocation(lastLocation); else showStatus("Locating…", R.drawable.ic_my_location);
    }

    @Override public void onProviderEnabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) showStatus("Locating…", R.drawable.ic_my_location);
    }
    @Override public void onProviderDisabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) showStatus("GPS unavailable", R.drawable.ic_my_location);
    }
    @Override public void onStatusChanged(String provider, int statusValue, Bundle extras) {}

    private final class RestrictedMapClient extends WebViewClient {
        private final byte[] blocked = "blocked".getBytes(StandardCharsets.UTF_8);
        @Override public void onPageFinished(WebView view, String url) { onMapPageReady(url); }
        @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return !MAP_URL.equals(request.getUrl().toString());
        }
        @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String scheme = uri.getScheme();
            String url = uri.toString();
            if ("file".equals(scheme) && url.startsWith("file:///android_asset/map/")) return null;
            if (TileBroker.isTileUri(uri)) return tileBroker.intercept(uri);
            return new WebResourceResponse("text/plain", "utf-8", 403, "Blocked",
                    Collections.emptyMap(), new ByteArrayInputStream(blocked));
        }
    }
}
