package com.cbkii.ts18launcher;

/**
 * Decides whether the launcher may use root to enable its own notification listener.
 *
 * Root is never used implicitly. The ordinary Android "Notification access" screen is the default
 * path; the root convenience needs an explicit, persisted opt-in. If access disappears after a
 * root grant that this launcher applied, that is treated as the owner revoking it: consent is
 * withdrawn and only a fresh opt-in can grant again.
 */
final class MediaListenerAccessPolicy {
    enum Action {
        /** Access exists; nothing to do. */
        ALREADY_GRANTED,
        /** No consent for root: leave it to Android Settings. */
        USE_SETTINGS,
        /** Consent given and no earlier grant was removed: one bounded root grant. */
        ROOT_GRANT,
        /** A previously applied grant is gone: respect the revocation and drop the consent. */
        WITHDRAW_CONSENT
    }

    private MediaListenerAccessPolicy() {}

    /**
     * @param hasAccess        the listener is currently enabled
     * @param rootConsent      the owner opted in to the root convenience
     * @param grantApplied     an earlier root grant by this launcher was verified
     * @param freshConsent     this call is the owner turning the option on right now
     */
    static Action decide(boolean hasAccess, boolean rootConsent, boolean grantApplied,
                         boolean freshConsent) {
        if (hasAccess) return Action.ALREADY_GRANTED;
        if (!rootConsent) return Action.USE_SETTINGS;
        if (grantApplied && !freshConsent) return Action.WITHDRAW_CONSENT;
        return Action.ROOT_GRANT;
    }
}
