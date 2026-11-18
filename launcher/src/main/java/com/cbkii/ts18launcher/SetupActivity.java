package com.cbkii.ts18launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.cbkii.ts18launcher.navigation.NavigationFeatureFacade;

/**
 * Short, skippable, repeatable setup. One screen of steps, each with its current state and one
 * action that opens the normal Android or launcher screen for it. Nothing is granted or taken
 * silently: HOME goes through the system role/Home screen, notification access through Android
 * Settings, and the optional root listener grant stays an informed switch in Settings. Every step
 * may be skipped; HOME works without any of them.
 */
public final class SetupActivity extends Activity {
    static final String PREFS = "setup";
    static final String KEY_OFFERED = "offered_v1";

    private LinearLayout steps;

    /** First run only: offered once, recorded before showing so a crash can never loop it. */
    @android.annotation.SuppressLint("ApplySharedPref") // Must be durable before the screen opens.
    static boolean offerOnce(Activity home) {
        android.content.SharedPreferences prefs = home.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_OFFERED, false)) return false;
        prefs.edit().putBoolean(KEY_OFFERED, true).commit();
        try {
            home.startActivity(new Intent(home, SetupActivity.class));
            return true;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    @Override protected void onCreate(Bundle state) {
        setTheme(AutomotiveUi.activityTheme(this));
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 24, 32, 24);
        TextView title = text(getString(R.string.setup_title), 24f);
        root.addView(title);
        root.addView(text(getString(R.string.setup_intro), 16f));
        steps = new LinearLayout(this);
        steps.setOrientation(LinearLayout.VERTICAL);
        root.addView(steps);
        Button done = new Button(this);
        done.setText(R.string.setup_done);
        done.setMinHeight(104);
        done.setOnClickListener(v -> finish());
        root.addView(done);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        steps.removeAllViews();
        boolean home = HomeMode.isDefaultHome(this);
        step(getString(R.string.setup_home), home ? getString(R.string.setup_home_done) : getString(R.string.setup_home_todo),
                home ? null : getString(R.string.setup_choose), v -> HomeMode.requestHomeRole(this));
        app(getString(R.string.setup_music), LauncherPrefs.KEY_MUSIC);
        app(getString(R.string.setup_radio), LauncherPrefs.KEY_RADIO);
        app(getString(R.string.setup_navigation), LauncherPrefs.KEY_NAV);
        boolean access = MediaListenerService.hasNotificationAccess(this);
        step(getString(R.string.setup_access), access ? getString(R.string.setup_access_done)
                        : getString(R.string.setup_access_todo),
                access ? null : getString(R.string.setup_open_android_settings),
                v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        step(getString(R.string.setup_root_listener),
                UiPersonalizationPrefs.mediaListenerRootGrant(this) ? getString(R.string.setup_root_on)
                        : getString(R.string.setup_root_off),
                getString(R.string.setup_review_in_settings),
                v -> startActivity(new Intent(this, SettingsActivity.class)));
        boolean n3 = NavigationFeatureFacade.get(this).n3Enabled();
        boolean m2 = !Ts18LauncherApplication.legacyMediaEngineOwns(this, false)
                || !Ts18LauncherApplication.legacyMediaEngineOwns(this, true);
        step(getString(R.string.setup_experimental),
                getString(n3 ? R.string.setup_n3_on : R.string.setup_n3_off) + " · "
                        + getString(m2 ? R.string.setup_m2_on : R.string.setup_m2_off),
                getString(R.string.setup_review_in_settings),
                v -> startActivity(new Intent(this, SettingsActivity.class)));
        step(getString(R.string.setup_rollback), getString(R.string.setup_rollback_text), null, null);
    }

    private void app(String label, String key) {
        String pkg = LauncherPrefs.packageFor(this, key);
        String value = pkg.isEmpty() ? getString(R.string.setup_not_chosen)
                : AppResolver.labelFor(this, pkg, pkg);
        step(label, value, getString(pkg.isEmpty() ? R.string.setup_choose : R.string.setup_change), v -> {
            Intent intent = new Intent(this, AppDrawerActivity.class);
            intent.putExtra(AppDrawerActivity.EXTRA_PICK_KEY, key);
            startActivity(intent);
        });
    }

    private void step(String label, String value, String action, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 16, 0, 16);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(label, 18f));
        texts.addView(text(value, 15f));
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (action != null && listener != null) {
            Button button = new Button(this);
            button.setText(action);
            button.setMinHeight(96);
            button.setContentDescription(action + " · " + label);
            button.setOnClickListener(listener);
            row.addView(button);
        }
        steps.addView(row);
    }

    private TextView text(String value, float size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        return view;
    }
}
