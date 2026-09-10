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
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;

@SuppressLint({"SetJavaScriptEnabled", "SetTextI18n", "MissingPermission", "ViewConstructor"})
final class MapPanel extends FrameLayout implements LocationListener {
    interface NavigationLauncher {
        void openNavigation(Location location);
    }

    private static final String MAP_URL = "file:///android_asset/map/map.html";
    private static final long HEALTH_CHECK_DELAY_MS = 1400L;

    private final Activity activity;
    private final NavigationLauncher navigationLauncher;
    private final WebView webView;
    private final TextView status;
    private final LocationManager locationManager;
    private final TileBroker tileBroker;
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

        status = new TextView(activity);
        status.setTextColor(Color.WHITE);
        status.setBackgroundColor(0xAA000000);
        status.setTextSize(12f);
        status.setPadding(8, 4, 8, 4);
        status.setText("Map loading");
        LayoutParams statusLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        statusLp.leftMargin = 8;
        statusLp.topMargin = 8;
        addView(status, statusLp);

        Button zoomIn = mapButton("+");
        Button zoomOut = mapButton("−");
        Button recenter = mapButton("⌖");
        Button openNav = mapButton("OPEN NAV");
        zoomIn.setOnClickListener(v -> adjustZoom(1));
        zoomOut.setOnClickListener(v -> adjustZoom(-1));
        recenter.setOnClickListener(v -> recenterMap());
        openNav.setOnClickListener(v -> navigationLauncher.openNavigation(
                lastLocation == null ? null : new Location(lastLocation)));

        LayoutParams inLp = new LayoutParams(56, 56);
        inLp.gravity = android.view.Gravity.TOP | android.view.Gravity.RIGHT;
        inLp.topMargin = 8;
        inLp.rightMargin = 8;
        addView(zoomIn, inLp);

        LayoutParams outLp = new LayoutParams(56, 56);
        outLp.gravity = android.view.Gravity.TOP | android.view.Gravity.RIGHT;
        outLp.topMargin = 68;
        outLp.rightMargin = 8;
        addView(zoomOut, outLp);

        LayoutParams recenterLp = new LayoutParams(56, 56);
        recenterLp.gravity = android.view.Gravity.TOP | android.view.Gravity.RIGHT;
        recenterLp.topMargin = 128;
        recenterLp.rightMargin = 8;
        addView(recenter, recenterLp);

        LayoutParams navLp = new LayoutParams(LayoutParams.WRAP_CONTENT, 52);
        navLp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.LEFT;
        navLp.leftMargin = 8;
        navLp.bottomMargin = 8;
        addView(openNav, navLp);

        locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        webView.loadUrl(MAP_URL);
    }

    private String mapUserAgent() {
        String version = "unknown";
        try {
            String installed = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0).versionName;
            if (installed != null && !installed.isEmpty()) version = installed;
        } catch (PackageManager.NameNotFoundException ignored) {
            // The running package should always resolve; retain a deterministic fallback.
        }
        return "TS18Launcher/" + version + " (+https://github.com/cbkii/ts-theme)";
    }

    private Button mapButton(String text) {
        Button button = new Button(activity);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15f);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setBackgroundColor(0xCC3A1C16);
        button.setPadding(8, 0, 8, 0);
        return button;
    }

    void start() {
        if (started) return;
        started = true;
        if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            status.setText("Location permission required");
            return;
        }
        if (locationManager == null) {
            status.setText("Location service unavailable");
            return;
        }
        try {
            Location gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (gps != null) onLocationChanged(gps);
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, 2000L, 5f, this, Looper.getMainLooper());
            } else {
                status.setText("GPS unavailable");
            }
        } catch (SecurityException ignored) {
            status.setText("Location permission unavailable");
        } catch (IllegalArgumentException ignored) {
            status.setText("GPS provider unavailable");
        }
    }

    void stop() {
        if (started && locationManager != null) {
            try {
                locationManager.removeUpdates(this);
            } catch (SecurityException ignored) {
                // No-op.
            }
        }
        started = false;
        mainHandler.removeCallbacks(mapHealthCheck);
        webView.onPause();
    }

    void resumeWebView() {
        webView.onResume();
        if (LauncherPrefs.mapEnabled(activity)) start();
    }

    void destroy() {
        destroyed = true;
        stop();
        mainHandler.removeCallbacksAndMessages(null);
        removeView(webView);
        webView.destroy();
    }

    void onLocationPermissionResult() {
        started = false;
        start();
    }

    private void adjustZoom(int delta) {
        if (pageReady) {
            webView.evaluateJavascript("adjustZoom(" + delta + ")", null);
            scheduleMapHealthCheck();
        }
    }

    private void recenterMap() {
        if (pageReady) webView.evaluateJavascript("recenterMap()", null);
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location == null) return;
        lastLocation = new Location(location);
        if (pageReady) {
            renderLocation(lastLocation);
        } else {
            status.setText("GPS · map loading");
        }
    }

    private void renderLocation(Location location) {
        double accuracy = location.hasAccuracy() ? location.getAccuracy() : 0.0;
        double bearing = location.hasBearing() ? location.getBearing() : 0.0;
        String js = String.format(
                Locale.US,
                "setLocation(%.7f,%.7f,%.1f,%.1f,%s)",
                location.getLatitude(),
                location.getLongitude(),
                accuracy,
                bearing,
                location.hasBearing() ? "true" : "false");
        webView.evaluateJavascript(js, null);
        status.setText("GPS · map loading");
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
            if (value.contains("ok:")) {
                status.setText(lastLocation == null ? "Map ready · waiting for GPS" : "GPS");
            } else if (value.contains("error:")) {
                String detail = tileBroker.lastFailure();
                if (detail.isEmpty()) detail = "tile render error";
                status.setText(lastLocation == null
                        ? "Map tiles · " + detail
                        : "GPS · map tiles · " + detail);
            } else if (value.contains("runtime-error")) {
                status.setText("Map runtime unavailable");
            } else if (lastLocation != null) {
                status.setText("GPS · map loading");
            }
        });
    }

    private void onMapPageReady(String url) {
        if (!MAP_URL.equals(url)) return;
        pageReady = true;
        if (lastLocation != null) {
            renderLocation(lastLocation);
        } else {
            status.setText("Map ready · waiting for GPS");
        }
    }

    @Override public void onProviderEnabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) status.setText("Map ready · waiting for GPS");
    }

    @Override public void onProviderDisabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) status.setText("GPS unavailable");
    }

    @Override public void onStatusChanged(String provider, int statusValue, Bundle extras) {}

    private final class RestrictedMapClient extends WebViewClient {
        private final byte[] blocked = "blocked".getBytes(StandardCharsets.UTF_8);

        @Override
        public void onPageFinished(WebView view, String url) {
            onMapPageReady(url);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            return !MAP_URL.equals(url);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String scheme = uri.getScheme();
            String url = uri.toString();
            if ("file".equals(scheme) && url.startsWith("file:///android_asset/map/")) return null;
            if (TileBroker.isTileUri(uri)) return tileBroker.intercept(uri);
            return new WebResourceResponse(
                    "text/plain", "utf-8", 403, "Blocked",
                    Collections.emptyMap(), new ByteArrayInputStream(blocked));
        }
    }
}
