package com.cbkii.ts18launcher;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Visible HOME uses an already-observed in-process fix; no location-service IPC or subscription. */
final class RadioMetadataResolver {
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
        if (started) {
            Location observed = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED ? MapPanel.observedLocationSnapshot() : null;
            if (observed == null && location != null) { location = null; updateRegions(); }
            if (usable(observed) && (location == null
                    || observed.getElapsedRealtimeNanos() > location.getElapsedRealtimeNanos())) {
                location = observed; updateRegions();
            }
            return;
        }
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
        location = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED ? MapPanel.observedLocationSnapshot() : null;
        updateRegions();
    }

    void stop() {
        if (!started) return;
        started = false; generation++; locationGeneration++;
        main.removeCallbacksAndMessages(null);
        location = null; regionRanks = null;
    }

    String frequency(MediaListenerService.Snapshot snapshot) {
        if (snapshot == null || !RadioFrequency.NAVRADIO.equals(snapshot.packageName)) return "";
        if (snapshot.radioFrequency != null) return resolved(snapshot.radioFrequency.display(), "observed-text");
        // Conflicting explicit observations must not be replaced by a reference guess.
        if (RadioFrequency.containsExplicitFrequency(snapshot.packageName, snapshot.title)
                || RadioFrequency.containsExplicitFrequency(snapshot.packageName, snapshot.artist)) {
            return resolved("", "conflicting-observation");
        }
        AustralianRadioDirectory data = directory;
        if (data == null) return "";
        boolean fresh = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED && usable(location);
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
        if (fix == null || !fix.hasAccuracy() || !Float.isFinite(fix.getAccuracy())
                || fix.getAccuracy() < 0 || fix.getAccuracy() > 10000f
                || !Double.isFinite(fix.getLatitude()) || !Double.isFinite(fix.getLongitude())
                || Math.abs(fix.getLatitude()) > 90 || Math.abs(fix.getLongitude()) > 180) return false;
        long age = SystemClock.elapsedRealtimeNanos() - fix.getElapsedRealtimeNanos();
        return age >= 0 && age <= 15L * 60 * 1000000000L;
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

}
