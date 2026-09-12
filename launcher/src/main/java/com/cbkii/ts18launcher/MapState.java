package com.cbkii.ts18launcher;

/** One recoverable viewport in process memory only; never a location history. */
final class MapState {
    int zoom = 15;
    boolean follow = true;
    boolean hasCentre;
    double latitude;
    double longitude;

    boolean update(int zoom, boolean follow, double latitude, double longitude) {
        if (zoom < 2 || zoom > 19 || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) return false;
        this.zoom = zoom; this.follow = follow; this.latitude = latitude; this.longitude = longitude;
        hasCentre = true;
        return true;
    }
}
