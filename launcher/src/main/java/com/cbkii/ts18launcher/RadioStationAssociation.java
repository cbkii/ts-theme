package com.cbkii.ts18launcher;

/** A notification observed before a station-to-placeholder transition cannot enrich it later. */
final class RadioStationAssociation {
    private Object token;
    private String station = "";
    private String retiredStation = "";
    private int generation;
    private int notificationGeneration = -1;
    void observe(Object identity, String meaningfulStation) {
        if (!java.util.Objects.equals(token, identity) || !station.isEmpty() && meaningfulStation.isEmpty()) {
            retiredStation = station;
            generation++;
        }
        token = identity;
        station = meaningfulStation;
    }
    void notification(String associatedStation) {
        // A delayed/reposted old-station notification is still stale during the identity gap.
        if (station.isEmpty() && !retiredStation.isEmpty()
                && (associatedStation.isEmpty() || retiredStation.equalsIgnoreCase(associatedStation))) {
            notificationGeneration = -1; return;
        }
        notificationGeneration = generation;
    }
    boolean current() { return notificationGeneration == generation; }
}
