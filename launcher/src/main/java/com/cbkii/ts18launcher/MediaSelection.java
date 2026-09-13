package com.cbkii.ts18launcher;

import java.util.List;

/** Selection policy only: never dispatches transports or owns playback. */
final class MediaSelection {
    static final String RADIO = "radio";
    static final String MUSIC = "music";
    private String explicit;
    private String displayed;
    private boolean observed;
    private boolean radioWasPlaying;
    private boolean musicWasPlaying;

    MediaSelection(String explicit) { select(explicit); }

    void select(String source) {
        explicit = RADIO.equals(source) ? RADIO : MUSIC;
        displayed = explicit;
    }

    String reconcile(boolean radioPlaying, boolean musicPlaying) {
        // An explicit tap takes effect immediately. Only a new activity transition may
        // auto-select a sole playing authority; repeated metadata/poll callbacks cannot undo it.
        if (radioPlaying == musicPlaying) displayed = explicit;
        else if (!observed || radioPlaying != radioWasPlaying || musicPlaying != musicWasPlaying)
            displayed = radioPlaying ? RADIO : MUSIC;
        observed = true;
        radioWasPlaying = radioPlaying;
        musicWasPlaying = musicPlaying;
        return displayed;
    }

    String displayed() { return displayed; }

    static final class Candidate {
        final String packageName;
        final boolean eligible;
        final boolean usable;
        final boolean active;
        final boolean pausedOrStopped;
        Candidate(String name, boolean eligible, boolean usable, boolean active, boolean pausedOrStopped) {
            this.packageName = name; this.eligible = eligible; this.usable = usable;
            this.active = active; this.pausedOrStopped = pausedOrStopped;
        }
    }

    static int pick(List<Candidate> sessions, String preferred, boolean prefer, String remembered) {
        if (prefer) {
            int index = exact(sessions, preferred, false);
            if (index >= 0) return index;
        }
        int rememberedIndex = exact(sessions, remembered, true);
        if (rememberedIndex >= 0) return rememberedIndex;
        int paused = -1, any = -1;
        for (int i = 0; i < sessions.size(); i++) {
            Candidate candidate = sessions.get(i);
            if (!candidate.eligible) continue;
            if (candidate.active) return i;
            if (paused < 0 && candidate.pausedOrStopped) paused = i;
            if (any < 0) any = i;
        }
        return paused >= 0 ? paused : any;
    }

    private static int exact(List<Candidate> sessions, String name, boolean requireUsable) {
        if (name == null || name.isEmpty()) return -1;
        int fallback = -1;
        for (int i = 0; i < sessions.size(); i++) {
            Candidate candidate = sessions.get(i);
            if (!candidate.eligible || !name.equals(candidate.packageName)
                    || (requireUsable && !candidate.usable)) continue;
            if (candidate.active) return i;
            if (fallback < 0) fallback = i;
        }
        return fallback;
    }
}
