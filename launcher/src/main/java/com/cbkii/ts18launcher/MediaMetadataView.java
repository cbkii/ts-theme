package com.cbkii.ts18launcher;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Two-level glanceable now-playing metadata.
 *
 * Renders source-owned metadata and the explicitly scoped offline NavRadio frequency fallback. Launcher command
 * status, readiness messages and app labels are diagnostics, not now-playing metadata, and therefore
 * never replace the ticker contents.
 */
final class MediaMetadataView extends LinearLayout {
    private final SlowMarqueeTextView primary;
    private final TextView secondary;
    private final MetadataFadeRow primaryRow;
    private final MetadataFadeRow secondaryRow;
    private final RadioMetadataResolver radioResolver;
    private MediaListenerService.Snapshot currentSnapshot;
    private String lastPrimary;
    private String lastSecondary;
    private String lastSourceIdentity;
    private String selectedSource = MediaSelection.MUSIC;

    MediaMetadataView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int gap = AutomotiveUi.dimen(context, R.dimen.ui_metadata_padding_horizontal);
        setPadding(gap, 0, gap, 0);

        primary = new SlowMarqueeTextView(context);
        primary.setTextColor(AutomotiveUi.color(context, R.color.ui_text));
        primary.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(R.dimen.ui_metadata_text));
        primary.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        primary.setGravity(Gravity.BOTTOM);
        primaryRow = new MetadataFadeRow(context, primary);
        addView(primaryRow, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.15f));

        secondary = new TextView(context);
        secondary.setTextColor(AutomotiveUi.color(context, R.color.ui_text_secondary));
        secondary.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                context.getResources().getDimension(R.dimen.ui_metadata_secondary_text));
        secondary.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        secondary.setSingleLine(true);
        secondary.setHorizontallyScrolling(true);
        secondary.setGravity(Gravity.TOP);
        secondaryRow = new MetadataFadeRow(context, secondary);
        addView(secondaryRow, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 0.85f));
        radioResolver = new RadioMetadataResolver(context, () -> renderSnapshot(currentSnapshot));
    }

    /** The Activity supplies the same reconciled source used by transport icons. */
    void setSnapshot(MediaListenerService.Snapshot snapshot, String selected) {
        currentSnapshot = snapshot;
        selectedSource = selected;
        updateRadioObservation();
        renderSnapshot(snapshot);
    }

    void applyAppearance() {
        primary.setTextColor(AutomotiveUi.color(getContext(), R.color.ui_text));
        secondary.setTextColor(AutomotiveUi.color(getContext(), R.color.ui_text_secondary));
        primaryRow.invalidate();
        secondaryRow.invalidate();
    }

    /** Command/readiness diagnostics must never replace now-playing metadata. */
    void setMetadata(String ignoredTitle, String ignoredContext) { }

    private void renderSnapshot(MediaListenerService.Snapshot snapshot) {
        String selected = selectedSource;
        String sourceIdentity = sourceIdentity(snapshot);
        String appLabel = snapshot == null || snapshot.packageName.isEmpty()
                ? "" : AppResolver.labelFor(getContext(), snapshot.packageName, "");
        MediaTickerPolicy.Display display = MediaTickerPolicy.resolve(
                snapshot == null ? "" : snapshot.title,
                snapshot == null ? "" : snapshot.artist,
                appLabel,
                selected,
                snapshot == null ? "" : snapshot.packageName,
                radioResolver.frequency(snapshot));

        primary.setGravity(MediaSelection.RADIO.equals(selected) ? Gravity.CENTER_VERTICAL : Gravity.BOTTOM);
        MediaMetadataPolicy.Change change = MediaMetadataPolicy.update(
                lastPrimary, lastSecondary, lastSourceIdentity, sourceIdentity,
                display.primary, display.secondary);
        boolean itemChanged = lastSourceIdentity != null && !lastSourceIdentity.equals(sourceIdentity);
        lastSourceIdentity = sourceIdentity;
        if (change.primaryChanged) {
            lastPrimary = change.primary;
            primary.setText(change.primary);
            if (itemChanged) primary.restartForItem();
        }
        if (change.secondaryChanged) {
            lastSecondary = change.secondary;
            secondary.setText(change.secondary);
            secondaryRow.setVisibility(change.secondary.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateRadioObservation();
    }

    @Override protected void onDetachedFromWindow() {
        radioResolver.stop();
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (radioResolver == null || !isAttachedToWindow()) return;
        if (visibility == View.VISIBLE) updateRadioObservation(); else radioResolver.stop();
    }

    private void updateRadioObservation() {
        if (isAttachedToWindow() && isShown() && MediaSelection.RADIO.equals(selectedSource)
                && currentSnapshot != null && RadioFrequency.needsDirectory(currentSnapshot.packageName,
                        currentSnapshot.title, currentSnapshot.artist)) {
            radioResolver.start();
        } else radioResolver.stop();
    }

    private String sourceIdentity(MediaListenerService.Snapshot snapshot) {
        String selected = selectedSource;
        String packageName = snapshot == null ? "" : snapshot.packageName;
        if (packageName.isEmpty()) {
            if (MediaSelection.RADIO.equals(selected)) {
                packageName = RadioProvider.resolvePackage(getContext());
            } else {
                packageName = LauncherPrefs.lastMusicPackage(getContext());
                if (packageName.isEmpty()) {
                    packageName = LauncherPrefs.packageFor(getContext(), LauncherPrefs.KEY_MUSIC);
                }
            }
        }
        return selected + ":" + packageName + ":" + (snapshot == null ? "" : snapshot.trackIdentity());
    }
}
