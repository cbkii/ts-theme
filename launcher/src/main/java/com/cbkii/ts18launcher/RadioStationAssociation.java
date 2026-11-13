package com.cbkii.ts18launcher;

/** A notification observed before a station-to-placeholder transition cannot enrich it later. */
final class RadioStationAssociation {
    private Object token;
    private String station = "";
    /** Every station retired in this session, not only the latest: an older notification must
     * never become current again after several station-to-placeholder transitions. */
    private final java.util.LinkedHashSet<String> retiredStations = new java.util.LinkedHashSet<>();
    private int generation;
    private int notificationGeneration = -1;
    /** The station the latest accepted notification described; revalidated if the session
     * catches up to it after the notification arrived first. */
    private String notificationStation = "";
    void observe(Object identity, String meaningfulStation) {
        boolean tokenChanged = !java.util.Objects.equals(token, identity);
        if (tokenChanged || !station.isEmpty() && !station.equalsIgnoreCase(meaningfulStation)) {
            if (!station.isEmpty()) {
                retiredStations.add(station);
                while (retiredStations.size() > 8) {
                    java.util.Iterator<String> it = retiredStations.iterator();
                    it.next(); it.remove();
                }
            }
            generation++;
        }
        token = identity;
        station = meaningfulStation;
        // A new-station notification can arrive before the session update. When the SAME session
        // now shows that station, revalidate its notification instead of discarding it. A token
        // replacement never inherits any earlier notification: a new session re-proves its own.
        if (!tokenChanged && !station.isEmpty() && station.equalsIgnoreCase(notificationStation)) {
            notificationGeneration = generation;
        } else if (!notificationStation.isEmpty() && retiredContains(notificationStation)) {
            notificationGeneration = -1;
        }
    }
    void notification(String associatedStation) {
        notificationStation = associatedStation == null ? "" : associatedStation;
        // A delayed/reposted old-station notification is still stale during the identity gap.
        if (station.isEmpty() && !retiredStations.isEmpty()
                && (notificationStation.isEmpty() || retiredContains(notificationStation))) {
            notificationGeneration = -1; return;
        }
        notificationGeneration = generation;
    }
    private boolean retiredContains(String value) {
        for (String retired : retiredStations) if (retired.equalsIgnoreCase(value)) return true;
        return false;
    }
    boolean current() { return notificationGeneration == generation; }
}
