package com.cbkii.ts18launcher;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Visible-HOME passive observation only: no permission prompt or active GPS request. */
final class RadioMetadataResolver implements LocationListener {
    private static volatile AustralianRadioDirectory directory;
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor();
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable changed;
    private final Runnable expireLocation = this::expireLocation;
    private String lastResolution = "";
    private Location location;
    private java.util.Map<String, Integer> regionRanks;
    private int locationGeneration;
    private boolean started;
    private int generation;

    RadioMetadataResolver(Context context, Runnable changed) {
        this.context = context.getApplicationContext(); this.changed = changed;
    }

    void start() {
        if (started) return;
        started = true;
        final int request = ++generation;
        if (directory == null) LOADER.execute(() -> {
            try (InputStreamReader stations = new InputStreamReader(context.getAssets().open("radio/au-stations.tsv"), StandardCharsets.UTF_8);
                 InputStreamReader borders = new InputStreamReader(new java.util.zip.GZIPInputStream(
                         context.getAssets().open("radio/au-state-rings.tsv.gz")), StandardCharsets.UTF_8)) {
                directory = new AustralianRadioDirectory(stations, borders);
                main.post(() -> { if (started && generation == request) updateRegions(); });
            } catch (java.io.IOException | RuntimeException ignored) { /* fail empty */ }
        });
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null || context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            location = null; regionRanks = null; changed.run(); return;
        }
        try {
            for (String provider : manager.getAllProviders()) {
                Location last = manager.getLastKnownLocation(provider);
                if (usable(last) && (location == null || last.getElapsedRealtimeNanos() > location.getElapsedRealtimeNanos())) location = new Location(last);
            }
            manager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 30000L, 1000f, this, Looper.getMainLooper());
        } catch (SecurityException | IllegalArgumentException ignored) { location = null; }
        updateRegions();
    }

    void stop() {
        if (!started) return;
        started = false; generation++; locationGeneration++;
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (manager != null) {
            try { manager.removeUpdates(this); } catch (SecurityException ignored) { }
        }
        main.removeCallbacksAndMessages(null);
        location = null; regionRanks = null;
    }

    String frequency(MediaListenerService.Snapshot snapshot) {
        if (snapshot == null || !RadioFrequency.NAVRADIO.equals(snapshot.packageName)) return "";
        if (snapshot.radioFrequency != null) return resolved(snapshot.radioFrequency.display(), "observed-text");
        // Conflicting explicit observations must not be replaced by a reference guess.
        if (RadioFrequency.containsExplicitFrequency(snapshot.packageName, snapshot.title)
                || RadioFrequency.containsExplicitFrequency(snapshot.packageName, snapshot.artist)) return "";
        AustralianRadioDirectory data = directory;
        if (data == null) return "";
        boolean fresh = usable(location);
        if (fresh && regionRanks == null) return "";
        AustralianRadioDirectory.Match match = data.match(snapshot.title, snapshot.artist,
                fresh ? regionRanks : data.regionRanks(null, null), !fresh);
        return match == null ? resolved("", "unresolved") : resolved(match.station.frequency.display(),
                "directory:" + match.confidence + ":" + match.station.state);
    }

    private String resolved(String value, String provenance) {
        String resolution = provenance + ":" + value;
        if (!resolution.equals(lastResolution)) {
            lastResolution = resolution;
            MediaEventTrace.record("metadata", "radio-frequency", resolution);
        }
        return value;
    }

    private static boolean usable(Location fix) {
        if (fix == null || !fix.hasAccuracy() || fix.getAccuracy() > 10000f) return false;
        long age = SystemClock.elapsedRealtimeNanos() - fix.getElapsedRealtimeNanos();
        return age >= 0 && age <= 15L * 60 * 1000000000L;
    }

    @Override public void onLocationChanged(Location fix) {
        if (!started || !usable(fix)) return;
        location = new Location(fix); updateRegions();
    }
    private void expireLocation() {
        location = null; regionRanks = null; locationGeneration++;
        if (started) changed.run();
    }

    private void updateRegions() {
        regionRanks = null;
        final int ticket = ++locationGeneration;
        final Location fix = usable(location) ? new Location(location) : null;
        main.removeCallbacks(expireLocation);
        if (fix != null) {
            long ageMs = (SystemClock.elapsedRealtimeNanos() - fix.getElapsedRealtimeNanos()) / 1000000L;
            main.postDelayed(expireLocation, Math.max(1L, 15L * 60 * 1000 - ageMs + 1L));
        }
        AustralianRadioDirectory data = directory;
        if (data == null) return;
        LOADER.execute(() -> {
            java.util.Map<String, Integer> ranks = data.regionRanks(
                    fix == null ? null : fix.getLatitude(), fix == null ? null : fix.getLongitude());
            main.post(() -> {
                if (!started || ticket != locationGeneration) return;
                regionRanks = ranks; changed.run();
            });
        });
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { location = null; if (started) updateRegions(); }
}
