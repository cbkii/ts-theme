package com.cbkii.ts18launcher.oem;

/**
 * One device capability as this launcher process can establish it. Status#AVAILABLE means
 * observed in this caller's own context; it never implies authority to change anything.
 */
public final class Capability {
    public enum Status {
        /** Observed working from this process. */
        AVAILABLE,
        /** Inspected in this process and not present. */
        UNAVAILABLE,
        /** Present, but this caller is refused or the use is deliberately not permitted. */
        BLOCKED,
        /** Not established: not inspected, inconclusive, or a binary the contract was not verified for. */
        UNKNOWN
    }

    public final String id;
    public final Status status;
    /** The step that decided the status, e.g. package, descriptor, reply. */
    public final String stage;
    public final String detail;

    public Capability(String id, Status status, String stage, String detail) {
        this.id = id;
        this.status = status;
        this.stage = stage == null ? "" : stage;
        this.detail = detail == null ? "" : detail;
    }

    @Override public String toString() {
        return id + "=" + status + (stage.isEmpty() ? "" : "@" + stage) + (detail.isEmpty() ? "" : " " + detail);
    }
}
