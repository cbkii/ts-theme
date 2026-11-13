package com.cbkii.ts18launcher;

/** A notification observed before a station-to-placeholder transition cannot enrich it later. */
final class RadioStationAssociation {
    private Object token;
    private String station = "";
    private boolean stationSeen;
    private int generation;
    private int notificationGeneration = -1;
    /** Retain a notification received before the same session publishes its new station. */
    private String notificationStation = "";

    void observe(Object identity, String meaningfulStation) {
        boolean tokenChanged = !java.util.Objects.equals(token, identity);
        if (tokenChanged || !station.isEmpty() && !station.equalsIgnoreCase(meaningfulStation))
            generation++;
        if (tokenChanged) {
            stationSeen = false;
            notificationStation = "";
            notificationGeneration = -1;
        }
        token = identity;
        station = meaningfulStation;
        stationSeen |= !station.isEmpty();
        if (!tokenChanged && !station.isEmpty() && station.equalsIgnoreCase(notificationStation))
            notificationGeneration = generation;
    }

    void notification(String associatedStation) {
        notificationStation = associatedStation == null ? "" : associatedStation;
        // After a known station disappears, a notification cannot independently prove the next
        // station. Keep it for same-token revalidation, but never fill the identity gap from a
        // finite retired-name cache: an evicted old station could otherwise become current.
        if (station.isEmpty() && stationSeen) {
            notificationGeneration = -1;
            return;
        }
        notificationGeneration = generation;
    }

    boolean current() { return notificationGeneration == generation; }
}
