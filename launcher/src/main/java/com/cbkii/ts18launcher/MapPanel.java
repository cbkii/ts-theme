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
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;

@SuppressLint({"SetJavaScriptEnabled", "SetTextI18n", "MissingPermission", "ViewConstructor"})
final class MapPanel extends FrameLayout implements LocationListener {
    private static final String MAP_URL = "file:///android_asset/map/map.html";
    private static final long HEALTH_CHECK_DELAY_MS = 900L;
    private static final int MAP_ACTION_PX = 84;
    private static final int MAP_ACTION_GAP_PX = 8;

    private final Activity activity;
    private WebView webView;
    private static final MapState MAP_STATE = new MapState();
    private final RendererRecovery recovery = new RendererRecovery();
    private static Location processLastLocation;
    private final Button retry;
    private boolean active;
    private boolean recoveryPending;
    private final Runnable loadTimeout = this::failUnavailable;
    private final LinearLayout statusChip;
    private final ImageView statusIcon;
    private final TextView status;
    private final LocationManager locationManager;
    private final TileBroker tileBroker;
    private final ImageButton zoomIn;
    private final ImageButton zoomOut;
    private final ImageButton recenter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable mapHealthCheck = this::checkMapHealth;
    private boolean started;
    private boolean pageReady;
    private boolean destroyed;
    private Location lastLocation = processLastLocation == null ? null : new Location(processLastLocation);

    MapPanel(Activity activity) {
        super(activity);
        this.activity = activity;
        setBackgroundColor(Color.BLACK);

        tileBroker = new TileBroker(activity.getCacheDir(), mapUserAgent(), this::definitelyOffline);

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
        statusChip.addView(status, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        LayoutParams statusLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        statusLp.leftMargin = MAP_ACTION_GAP_PX;
        statusLp.topMargin = MAP_ACTION_GAP_PX;
        addView(statusChip, statusLp);
        showStatus("Locating…", R.drawable.ic_my_location);

        zoomIn = mapButton(R.drawable.ic_zoom_in, "Zoom in");
        zoomOut = mapButton(R.drawable.ic_zoom_out, "Zoom out");
        recenter = mapButton(R.drawable.ic_my_location, "Follow location");
        recenter.setBackground(AutomotiveUi.followModeBackground(activity));
        recenter.setImageTintList(AutomotiveUi.followTint(activity));
        zoomIn.setOnClickListener(v -> adjustZoom(1));
        zoomOut.setOnClickListener(v -> adjustZoom(-1));
        recenter.setOnClickListener(v -> recenterMap());
        addView(zoomIn); addView(zoomOut); addView(recenter);
        AutomotiveUi.linkVertical(java.util.Arrays.asList(zoomIn, zoomOut, recenter));
        applyPreferences();

        retry = new Button(activity);
        retry.setText("Retry map");
        retry.setContentDescription("Retry unavailable map");
        retry.setOnClickListener(v -> {
            if (destroyed || webView != null) return;
            recovery.retry();
            createWebView();
            if (active) start();
        });
        LayoutParams retryLp = new LayoutParams(240, 88, Gravity.CENTER);
        addView(retry, retryLp);
        retry.setVisibility(View.GONE);
        locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        if (recovery.blocked()) showUnavailable(); else createWebView();
    }

    private boolean definitelyOffline() {
        try {
            ConnectivityManager manager = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return false;
            Network network = manager.getActiveNetwork();
            if (network == null) return true;
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            return capabilities != null && !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (RuntimeException ignored) { return false; }
    }

    private void createWebView() {
        if (destroyed || webView != null || recovery.blocked()) return;
        try {
            webView = new TrackingWebView(activity);
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
            settings.setUserAgentString(mapUserAgent());
            webView.setBackgroundColor(Color.BLACK);
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, false);
            webView.setWebViewClient(new RestrictedMapClient());
            addView(webView, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            retry.setVisibility(View.GONE);
            pageReady = false;
            showStatus("Map loading", R.drawable.ic_my_location);
            webView.loadUrl(MAP_URL);
            if (!active) webView.onPause();
            mainHandler.removeCallbacks(loadTimeout);
            mainHandler.postDelayed(loadTimeout, 10_000L);
        } catch (RuntimeException ignored) { failUnavailable(); }
    }

    private void detachWebView() {
        mainHandler.removeCallbacks(loadTimeout);
        mainHandler.removeCallbacks(mapHealthCheck);
        pageReady = false;
        WebView dead = webView;
        webView = null;
        if (dead != null) { removeView(dead); dead.destroy(); }
    }

    private void failUnavailable() {
        if (destroyed) return;
        recovery.creationFailed();
        detachWebView();
        showUnavailable();
    }

    private void showUnavailable() {
        showStatus("Map unavailable", R.drawable.ic_my_location);
        retry.setVisibility(View.VISIBLE);
        if (started && locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (SecurityException ignored) {}
        }
        started = false;
    }

    void applyAppearance() {
        statusChip.setBackground(AutomotiveUi.chipBackground(activity));
        statusIcon.setColorFilter(AutomotiveUi.color(activity, R.color.ui_icon));
        status.setTextColor(AutomotiveUi.color(activity, R.color.ui_text));
        AutomotiveUi.styleMapButton(activity, zoomIn, false);
        AutomotiveUi.styleMapButton(activity, zoomOut, false);
        recenter.setBackground(AutomotiveUi.followModeBackground(activity));
        recenter.setImageTintList(AutomotiveUi.followTint(activity));
        applyMapAppearance();
    }

    private String mapUserAgent() {
        String version = "unknown";
        try {
            String installed = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
            if (installed != null && !installed.isEmpty()) version = installed;
        } catch (PackageManager.NameNotFoundException ignored) {}
        return "TS18Launcher/" + version + " (+https://github.com/cbkii/ts-theme)";
    }

    private ImageButton mapButton(int icon, String description) {
        ImageButton button = new ImageButton(activity);
        button.setImageResource(icon);
        button.setContentDescription(description);
        AutomotiveUi.styleMapButton(activity, button, false);
        return button;
    }

    void applyPreferences() {
        boolean controls = LauncherPrefs.mapControlsEnabled(activity);
        int visibility = controls ? View.VISIBLE : View.GONE;
        zoomIn.setVisibility(visibility); zoomOut.setVisibility(visibility); recenter.setVisibility(visibility);

        // Remaining map controls stay opposite the permanent side rail to avoid adjacent glyph columns.
        boolean railRight = LauncherPrefs.railOnRight(activity);
        boolean controlsRight = !railRight;
        placeAction(zoomIn, 0, controlsRight);
        placeAction(zoomOut, 1, controlsRight);
        placeAction(recenter, 2, controlsRight);

        LayoutParams statusLp = (LayoutParams) statusChip.getLayoutParams();
        statusLp.gravity = (railRight ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP;
        statusLp.leftMargin = railRight ? 0 : MAP_ACTION_GAP_PX;
        statusLp.rightMargin = railRight ? MAP_ACTION_GAP_PX : 0;
        statusLp.topMargin = MAP_ACTION_GAP_PX;
        statusChip.setLayoutParams(statusLp);
        applyMapAppearance();
    }

    private void placeAction(View view, int row, boolean right) {
        LayoutParams lp = new LayoutParams(MAP_ACTION_PX, MAP_ACTION_PX);
        lp.gravity = (right ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP;
        if (right) lp.rightMargin = MAP_ACTION_GAP_PX; else lp.leftMargin = MAP_ACTION_GAP_PX;
        lp.topMargin = MAP_ACTION_GAP_PX + row * (MAP_ACTION_PX + MAP_ACTION_GAP_PX);
        view.setLayoutParams(lp);
    }

    private void applyMapAppearance() {
        if (!pageReady || webView == null) return;
        String mode = AppearanceController.resolvedMode(activity);
        String accent = AccentPalette.css(activity);
        webView.evaluateJavascript("setMapAppearance('" + mode + "','" + accent + "')", null);
    }

    void start() {
        if (started || !active || webView == null) return;
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
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 5f, this, Looper.getMainLooper());
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
        active = false;
        captureMapState();
        mainHandler.removeCallbacks(mapHealthCheck);
        if (webView != null) webView.onPause();
    }

    void resumeWebView() {
        if (destroyed) return;
        active = true;
        if (recoveryPending) { recoveryPending = false; createWebView(); }
        if (webView != null) webView.onResume();
        applyPreferences();
        if (ExperimentalMapPolicy.enabled(activity)) start();
    }

    void destroy() {
        destroyed = true; stop(); mainHandler.removeCallbacksAndMessages(null);
        detachWebView();
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
        processLastLocation = new Location(location);
        if (pageReady) renderLocation(lastLocation);
        else if (webView != null) showStatus("Map loading", R.drawable.ic_my_location);
    }

    private void renderLocation(Location location) {
        double accuracy = location.hasAccuracy() ? location.getAccuracy() : 0.0;
        double bearing = location.hasBearing() ? location.getBearing() : 0.0;
        String js = String.format(Locale.US, "setLocation(%.7f,%.7f,%.1f,%.1f,%s)",
                location.getLatitude(), location.getLongitude(), accuracy, bearing,
                location.hasBearing() ? "true" : "false");
        webView.evaluateJavascript(js, null);
        scheduleMapHealthCheck();
    }

    private void scheduleMapHealthCheck() {
        if (destroyed || !active || !pageReady || webView == null) return;
        mainHandler.removeCallbacks(mapHealthCheck);
        mainHandler.postDelayed(mapHealthCheck, HEALTH_CHECK_DELAY_MS);
    }

    private void checkMapHealth() {
        if (destroyed || !active || !pageReady || webView == null) return;
        WebView current = webView;
        current.evaluateJavascript("mapSnapshot()", value -> {
            if (destroyed || current != webView || !active || value == null) return;
            try {
                JSONObject snapshot = decodeSnapshot(value);
                updateMapState(snapshot);
                String health = snapshot.optString("health");
                recenter.setSelected(MAP_STATE.follow);
                TileState.Snapshot tiles = tileBroker.recentState();
                long now = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
                if (health.startsWith("ok:")) hideStatus();
                else if (health.startsWith("error:") && tiles != null && tiles.recentFailure(now))
                    showStatus("Map offline", R.drawable.ic_my_location);
                else if (lastLocation == null) showStatus("Locating…", R.drawable.ic_my_location);
                else if (health.startsWith("loading:")) showStatus("Map loading", R.drawable.ic_my_location);
                else hideStatus();
            } catch (JSONException ignored) { failUnavailable(); }
        });
    }

    private void captureMapState() {
        if (!pageReady || webView == null) return;
        WebView current = webView;
        current.evaluateJavascript("mapSnapshot()", value -> {
            if (destroyed || current != webView || value == null) return;
            try { updateMapState(decodeSnapshot(value)); }
            catch (JSONException ignored) { /* Retain last valid viewport. */ }
        });
    }

    private void updateMapState(JSONObject snapshot) throws JSONException {
        if (snapshot.optBoolean("hasCentre")) MAP_STATE.update(snapshot.getInt("zoom"),
                snapshot.getBoolean("follow"), snapshot.getDouble("latitude"), snapshot.getDouble("longitude"));
    }

    private static JSONObject decodeSnapshot(String value) throws JSONException {
        if (value == null) throw new JSONException("missing map snapshot");
        String json = value.trim();
        if (json.startsWith("\"")) {
            Object unwrapped = new org.json.JSONTokener(json).nextValue();
            if (unwrapped instanceof String) json = ((String) unwrapped).trim();
        }
        return new JSONObject(json);
    }

    private void showStatus(String text, int icon) {
        statusChip.animate().cancel();
        status.setText(text);
        statusIcon.setImageResource(icon);
        if (statusChip.getVisibility() != View.VISIBLE) {
            statusChip.setAlpha(0f);
            statusChip.setVisibility(View.VISIBLE);
            statusChip.animate().alpha(1f).setDuration(AutomotiveUi.PANEL_REVEAL_MS).start();
        } else statusChip.setAlpha(1f);
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
        WebView current = webView;
        if (current == null) return;
        current.evaluateJavascript("typeof mapSnapshot === 'function'", value -> {
            if (destroyed || current != webView) return;
            if (!"true".equals(value)) { failUnavailable(); return; }
            mainHandler.removeCallbacks(loadTimeout);
            pageReady = true;
            applyMapAppearance();
            if (lastLocation != null) renderLocation(lastLocation);
            if (MAP_STATE.hasCentre) current.evaluateJavascript(String.format(Locale.US,
                    "restoreMapState(%d,%s,%.7f,%.7f)", MAP_STATE.zoom, MAP_STATE.follow,
                    MAP_STATE.latitude, MAP_STATE.longitude), null);
            recenter.setSelected(MAP_STATE.follow);
            scheduleMapHealthCheck();
        });
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
        @Override public void onPageFinished(WebView view, String url) {
            if (view == webView) onMapPageReady(url);
        }
        @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            if (view != webView) { removeView(view); view.destroy(); return true; }
            detachWebView();
            if (!destroyed && recovery.failed()) {
                if (active) mainHandler.post(MapPanel.this::createWebView);
                else recoveryPending = true;
            } else if (!destroyed) showUnavailable();
            return true;
        }
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

    private final class TrackingWebView extends WebView {
        TrackingWebView(Context context) { super(context); }
        @Override public boolean performClick() { return super.performClick(); }
        @Override public boolean onTouchEvent(MotionEvent event) {
            boolean handled = super.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                performClick();
                scheduleMapHealthCheck();
            }
            return handled;
        }
    }
}
