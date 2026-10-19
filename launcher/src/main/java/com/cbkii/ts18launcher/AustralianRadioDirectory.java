package com.cbkii.ts18launcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Offline reference inference, separate from observed tuner/session authority. */
final class AustralianRadioDirectory {
    private static final List<String> PRIORITY = Arrays.asList("ACT", "NSW", "VIC", "QLD", "SA", "WA", "TAS", "NT");
    static final class Station {
        final String state, locality, callsign, branding;
        final RadioFrequency frequency;
        Station(String[] row) {
            state = row[0]; locality = row[1]; callsign = row[2]; branding = row[5];
            frequency = new RadioFrequency(Integer.parseInt(row[3]), row[4]);
        }
    }
    static final class Match {
        final Station station;
        final String confidence;
        Match(Station station, String confidence) { this.station = station; this.confidence = confidence; }
    }
    private final Map<String, List<Station>> aliases = new HashMap<>();
    private final Map<String, List<double[][]>> rings = new HashMap<>();

    AustralianRadioDirectory(Reader stations, Reader boundaries) throws IOException {
        BufferedReader reader = new BufferedReader(stations);
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("#") || line.isEmpty()) continue;
            String[] row = line.split("\t", -1);
            if (row.length != 6) throw new IOException("Invalid station row");
            Station station = new Station(row);
            add(station.callsign, station); add(station.branding, station);
        }
        reader = new BufferedReader(boundaries);
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("#") || line.isEmpty()) continue;
            String[] row = line.split("\t", -1), points = row[1].split(";");
            double[][] ring = new double[points.length][2];
            for (int i = 0; i < points.length; i++) {
                String[] xy = points[i].split(",");
                ring[i][0] = Double.parseDouble(xy[0]); ring[i][1] = Double.parseDouble(xy[1]);
            }
            rings.computeIfAbsent(row[0], key -> new ArrayList<>()).add(ring);
        }
    }

    private void add(String alias, Station station) {
        String key = normalise(alias);
        if (!key.isEmpty()) aliases.computeIfAbsent(key, ignored -> new ArrayList<>()).add(station);
    }

    /** Rank identity first, then containing state, then <=99 km border neighbours.
     * No substring/edit-distance guessing. Conflicting equal-rank frequencies abstain. */
    Match match(String title, String subtitle, Double latitude, Double longitude) {
        return match(title, subtitle, regionRanks(latitude, longitude), latitude == null || longitude == null);
    }

    Match match(String title, String subtitle, Map<String, Integer> geography, boolean defaultRegion) {
        int best = Integer.MAX_VALUE;
        Station winner = null;
        boolean ambiguous = false;
        for (String value : new String[]{title, subtitle}) {
            String key = normalise(value);
            List<Station> candidates = aliases.get(key);
            if (candidates == null) continue;
            for (Station station : candidates) {
                Integer geo = geography.get(station.state);
                if (geo == null) continue;
                int identity = station.callsign.matches("[1-8][A-Z0-9]{2,5}")
                        && key.equals(normalise(station.callsign)) ? 0 : 1;
                int rank = identity * 100 + geo;
                if (rank < best) { best = rank; winner = station; ambiguous = false; }
                else if (rank == best && winner != null
                        && !winner.frequency.display().equals(station.frequency.display())) ambiguous = true;
            }
        }
        return winner == null || ambiguous ? null : new Match(winner,
                defaultRegion ? "default-region" : "location-region");
    }

    Map<String, Integer> regionRanks(Double lat, Double lon) {
        Map<String, Integer> result = new HashMap<>();
        if (lat == null || lon == null || !Double.isFinite(lat) || !Double.isFinite(lon)) {
            for (int i = 0; i < 3; i++) result.put(PRIORITY.get(i), i);
            return result;
        }
        for (String state : PRIORITY) {
            boolean inside = false;
            double distance = Double.POSITIVE_INFINITY;
            List<double[][]> stateRings = rings.get(state);
            if (stateRings == null) continue;
            for (double[][] ring : stateRings) {
                boolean inRing = false;
                for (int i = 1; i < ring.length; i++) {
                    double[] a = ring[i - 1], b = ring[i];
                    if ((a[1] > lat) != (b[1] > lat)
                            && lon < (b[0] - a[0]) * (lat - a[1]) / (b[1] - a[1]) + a[0]) inRing = !inRing;
                    distance = Math.min(distance, distanceKm(lat, lon, a, b));
                }
                if (inRing) inside = !inside; // holes remain holes
            }
            if (inside) result.put(state, 0);
            else if (distance <= 99.0) result.put(state, 10 + PRIORITY.indexOf(state));
        }
        return result;
    }

    private static double distanceKm(double lat, double lon, double[] a, double[] b) {
        double scale = Math.cos(Math.toRadians(lat));
        double ax = (a[0] - lon) * scale * 111.195, ay = (a[1] - lat) * 111.195;
        double bx = (b[0] - lon) * scale * 111.195, by = (b[1] - lat) * 111.195;
        double dx = bx - ax, dy = by - ay, square = dx * dx + dy * dy;
        double t = square == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / square));
        return Math.hypot(ax + t * dx, ay + t * dy);
    }

    private static String normalise(String text) {
        if (text == null || text.length() > 2048) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
