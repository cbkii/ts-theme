package com.cbkii.ts18launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cbkii.ts18launcher.navigation.NavigationFeatureFacade;
import com.cbkii.ts18launcher.navigation.n3.N3NavigationBackend;
import com.cbkii.ts18launcher.navigation.n3.N3PresenterView;
import com.cbkii.ts18launcher.navigation.n3.N3Snapshot;
import com.cbkii.ts18launcher.navigation.n3.N3State;

/**
 * Experimental N3 screen, reachable only after N3 is explicitly enabled in Settings. It shows the
 * selected navigator on the N3 display, its truthful status, and the three recovery actions:
 * Retry N3, Open navigation normally (an ordinary launch on the main screen) and Use legacy
 * navigation (ends N3 and turns it off). Expanding is the same embedded display shown larger,
 * not a hand-off of the task to the main screen.
 */
public final class N3Activity extends Activity implements N3NavigationBackend.Listener {
    private N3NavigationBackend backend;
    private TextView status;
    private Button primary;
    private LinearLayout controls;
    private boolean expanded;

    @Override protected void onCreate(Bundle state) {
        setTheme(AutomotiveUi.activityTheme(this));
        super.onCreate(state);
        NavigationFeatureFacade facade = NavigationFeatureFacade.get(this);
        backend = facade.n3();
        if (backend == null) { finish(); return; }
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        // A landscape display matching the head unit's own density, sized for a map.
        int width = 1024, height = 600;
        backend.setGeometry(width, height, metrics.densityDpi);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        status = new TextView(this);
        status.setTextSize(18f);
        status.setPadding(24, 12, 24, 12);
        status.setContentDescription("N3 status");
        root.addView(status);

        FrameLayout stage = new FrameLayout(this);
        N3PresenterView presenter = new N3PresenterView(this, backend, width, height);
        stage.addView(presenter, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        root.addView(stage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        primary = button(R.string.n3_start, v -> startOrRetry());
        controls.addView(primary);
        controls.addView(button(R.string.n3_back, v -> backend.back()));
        controls.addView(button(R.string.n3_expand, v -> toggleExpanded((Button) v)));
        controls.addView(button(R.string.n3_open_normally, v -> openNormally()));
        controls.addView(button(R.string.n3_use_legacy, v -> { facade.useLegacy(); finish(); }));
        root.addView(controls);
        setContentView(root);
        backend.addListener(this);
    }

    private Button button(int label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setMinHeight(96);
        button.setOnClickListener(listener);
        return button;
    }

    /** Exact launcher component of the configured navigator ("package/class"), or "" if none. */
    private String target() {
        String pkg = LauncherPrefs.packageFor(this, LauncherPrefs.KEY_NAV);
        if (pkg.isEmpty()) return "";
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
            return launch == null || launch.getComponent() == null ? "" : launch.getComponent().flattenToString();
        } catch (RuntimeException unavailable) {
            return "";
        }
    }

    private void startOrRetry() {
        if (backend.snapshot().state == N3State.FAILED) backend.retry();
        else backend.start(target());
    }

    private void toggleExpanded(Button button) {
        expanded = !expanded;
        status.setVisibility(expanded ? View.GONE : View.VISIBLE);
        button.setText(expanded ? R.string.n3_compact : R.string.n3_expand);
        backend.setExpanded(expanded);
    }

    /** N0: an ordinary launch on the main screen. The N3 session is ended first; never both. */
    private void openNormally() {
        backend.shutdown();
        ComponentName component = ComponentName.unflattenFromString(target());
        if (component == null) return;
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException unavailable) {
            status.setText(R.string.n3_open_failed);
        }
    }

    @Override public void onN3(N3Snapshot snapshot) {
        StringBuilder text = new StringBuilder("N3 · ").append(snapshot.classification());
        if (!snapshot.detail.isEmpty()) text.append(" · ").append(snapshot.detail);
        if (snapshot.state == N3State.INTERACTIVE && !snapshot.inputUnavailable.isEmpty()) {
            text.append(" · view only: ").append(snapshot.inputUnavailable);
        }
        if (!snapshot.authority.isEmpty()) text.append(" · ").append(snapshot.authority);
        status.setText(text);
        primary.setText(snapshot.state == N3State.FAILED ? R.string.n3_retry : R.string.n3_start);
        primary.setEnabled(!snapshot.state.holdsDisplay());
    }

    @Override public void onBackPressed() {
        // Back goes to the map on the N3 display first; only when it is not interactive does it
        // leave this screen.
        if (!backend.back()) super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (backend != null) {
            backend.removeListener(this);
            if (isFinishing()) backend.shutdown();
        }
        super.onDestroy();
    }
}
