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
import android.os.Bundle;
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
import java.util.Locale;

@SuppressLint({"SetJavaScriptEnabled", "SetTextI18n", "MissingPermission"})
final class MapPanel extends FrameLayout implements LocationListener {
    interface NavigationLauncher {
        void openNavigation();
    }

    private static final String MAP_URL = "file:///android_asset/map/map.html";

    private final Activity activity;
    private final NavigationLauncher navigationLauncher;
    private final WebView webView;
    private final TextView status;
    private final LocationManager locationManager;
    private boolean started;
    private boolean pageReady;
    private Location lastLocation;
    private int zoom = 15;

    MapPanel(Activity activity, NavigationLauncher navigationLauncher) {
        super(activity);
        this.activity = activity;
        this.navigationLauncher = navigationLauncher;
        setBackgroundColor(Color.BLACK);

        webView = new WebView(activity);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setDatabaseEnabled(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(true);
        settings.setGeolocationEnabled(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkLoads(false);
        settings.setSupportZoom(false);
        webView.setBackgroundColor(Color.BLACK);
        webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, false);
        webView.setWebViewClient(new RestrictedMapClient());
        addView(webView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        status = new TextView(activity);
        status.setTextColor(Color.WHITE);
        status.setBackgroundColor(0xAA000000);
        status.setTextSize(12f);
        status.setPadding(8, 4, 8, 4);
        status.setText("Map waiting for location");
        LayoutParams statusLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        statusLp.leftMargin = 8;
        statusLp.topMargin = 8;
        addView(status, statusLp);

        Button zoomIn = mapButton("+");
        Button zoomOut = mapButton("−");
        Button openNav = mapButton("OPEN NAV");
        zoomIn.setOnClickListener(v -> adjustZoom(1));
        zoomOut.setOnClickListener(v -> adjustZoom(-1));
        openNav.setOnClickListener(v -> navigationLauncher.openNavigation());

        LayoutParams inLp = new LayoutParams(52, 52);
        inLp.gravity = android.view.Gravity.TOP | android.view.Gravity.RIGHT;
        inLp.topMargin = 8;
        inLp.rightMargin = 8;
        addView(zoomIn, inLp);

        LayoutParams outLp = new LayoutParams(52, 52);
        outLp.gravity = android.view.Gravity.TOP | android.view.Gravity.RIGHT;
        outLp.topMargin = 64;
        outLp.rightMargin = 8;
        addView(zoomOut, outLp);

        LayoutParams navLp = new LayoutParams(LayoutParams.WRAP_CONTENT, 48);
        navLp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.LEFT;
        navLp.leftMargin = 8;
        navLp.bottomMargin = 8;
        addView(openNav, navLp);

        locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        webView.loadUrl(MAP_URL);
    }

    private Button mapButton(String text) {
        Button button = new Button(activity);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13f);
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
        Location best = null;
        try {
            best = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (best == null || (network != null && network.getTime() > best.getTime())) best = network;
        } catch (SecurityException ignored) {
            status.setText("Location permission unavailable");
            return;
        }
        if (best != null) onLocationChanged(best);
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, 2000L, 5f, this, Looper.getMainLooper());
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER, 5000L, 20f, this, Looper.getMainLooper());
            }
        } catch (SecurityException ignored) {
            status.setText("Location permission unavailable");
        } catch (IllegalArgumentException ignored) {
            status.setText("Location provider unavailable");
        }
    }

    void stop() {
        if (!started) return;
        started = false;
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(this);
            } catch (SecurityException ignored) {
                // No-op.
            }
        }
        webView.onPause();
    }

    void resumeWebView() {
        webView.onResume();
        if (LauncherPrefs.mapEnabled(activity)) start();
    }

    void destroy() {
        stop();
        removeView(webView);
        webView.destroy();
    }

    void onLocationPermissionResult() {
        started = false;
        start();
    }

    private void adjustZoom(int delta) {
        zoom = Math.max(2, Math.min(18, zoom + delta));
        if (pageReady) {
            webView.evaluateJavascript("adjustZoom(" + delta + ")", null);
        }
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
        String js = String.format(
                Locale.US, "setCenter(%.7f,%.7f,%d)",
                location.getLatitude(), location.getLongitude(), zoom);
        webView.evaluateJavascript(js, null);
        status.setText("GPS");
    }

    private void onMapPageReady(String url) {
        if (!MAP_URL.equals(url)) return;
        pageReady = true;
        if (lastLocation != null) {
            renderLocation(lastLocation);
        } else {
            status.setText("Map waiting for location");
        }
    }

    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) {
        status.setText("GPS unavailable");
    }
    @Override public void onStatusChanged(String provider, int statusValue, Bundle extras) {}

    private final class RestrictedMapClient extends WebViewClient {
        private static final String TILE_HOST = "tile.openstreetmap.org";
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
            String scheme = request.getUrl().getScheme();
            String host = request.getUrl().getHost();
            String url = request.getUrl().toString();
            if ("file".equals(scheme) && url.startsWith("file:///android_asset/map/")) return null;
            if ("https".equals(scheme) && TILE_HOST.equals(host)) return null;
            return new WebResourceResponse(
                    "text/plain", "utf-8", 403, "Blocked",
                    java.util.Collections.emptyMap(), new ByteArrayInputStream(blocked));
        }
    }
}
