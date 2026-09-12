package com.cbkii.ts18launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One shortcut card: launch authority (App) and visual appearance (Icon) stay independent. */
final class ShortcutEditorView extends LinearLayout {
    private static final class PreviewIcon {
        final Drawable drawable;
        final boolean tint;
        PreviewIcon(Drawable drawable, boolean tint) { this.drawable = drawable; this.tint = tint; }
    }

    private final Activity activity;
    private final boolean drawer;
    private final int index;
    private final Runnable refresh;

    ShortcutEditorView(Activity activity, boolean drawer, int index, Runnable refresh) {
        super(activity);
        this.activity = activity;
        this.drawer = drawer;
        this.index = index;
        this.refresh = refresh;
        build();
    }

    private void build() {
        String title = (drawer ? "Drawer " : "Quick ") + (index + 1);
        String packageKey = packageKey();
        String role = role();
        String appearance = drawer ? UiPersonalizationPrefs.drawerQuickIcon(activity, index)
                : UiPersonalizationPrefs.quickIcon(activity, index);

        setOrientation(VERTICAL);
        setBackground(AutomotiveUi.cardBackground(activity));
        int gap = AutomotiveUi.dimen(activity, R.dimen.driver_gap);
        setPadding(gap, gap / 2, gap, gap);
        TextView heading = text(title, R.dimen.ui_settings_section, R.color.ui_text);
        addView(heading, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 36));

        LinearLayout columns = new LinearLayout(activity);
        columns.setOrientation(HORIZONTAL);
        columns.setGravity(Gravity.CENTER_VERTICAL);
        View app = editorCell("App", targetLabel(packageKey, role), targetPreview(packageKey, role), v -> chooseTarget());
        PreviewIcon appearancePreview = SlotIconCatalog.AUTO.equals(appearance)
                ? targetPreview(packageKey, role) : resourcePreview(SlotIconCatalog.icon(appearance));
        View icon = editorCell("Icon", SlotIconCatalog.label(appearance), appearancePreview, v -> chooseIcon());

        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        first.rightMargin = gap / 2;
        columns.addView(app, first);
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        second.leftMargin = gap / 2;
        columns.addView(icon, second);
        addView(columns, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private View editorCell(String heading, String value, PreviewIcon preview, View.OnClickListener listener) {
        LinearLayout cell = new LinearLayout(activity);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        int gap = AutomotiveUi.dimen(activity, R.dimen.driver_gap);
        cell.setPadding(gap, 0, gap, 0);
        cell.setBackground(AutomotiveUi.interactiveBackground(activity, false));

        ImageView icon = new ImageView(activity);
        icon.setImageDrawable(preview.drawable);
        if (preview.tint) icon.setColorFilter(AutomotiveUi.color(activity, R.color.ui_icon));
        else icon.clearColorFilter();
        int pad = AutomotiveUi.dimen(activity, preview.tint ? R.dimen.driver_gap : R.dimen.ui_card_inset);
        icon.setPadding(pad, pad, pad, pad);
        cell.addView(icon, new LinearLayout.LayoutParams(56, 56));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        TextView caption = text(heading, R.dimen.ui_settings_value, R.color.ui_accent);
        TextView detail = text(value, R.dimen.ui_settings_value, R.color.ui_text);
        detail.setSingleLine(true);
        detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(caption);
        labels.addView(detail);
        cell.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        cell.setFocusable(true);
        cell.setOnClickListener(listener);
        AutomotiveUi.attachFeedback(cell);
        return cell;
    }

    private PreviewIcon targetPreview(String packageKey, String role) {
        String pkg = LauncherPrefs.packageFor(activity, packageKey);
        if (!pkg.isEmpty()) {
            try { return new PreviewIcon(activity.getPackageManager().getApplicationIcon(pkg), false); }
            catch (PackageManager.NameNotFoundException ignored) { /* Use semantic fallback below. */ }
        }
        return resourcePreview(RoleIconCatalog.icon(role));
    }

    private PreviewIcon resourcePreview(int resource) {
        Drawable drawable = activity.getDrawable(resource);
        if (drawable == null) drawable = activity.getDrawable(R.drawable.ic_shortcut);
        return new PreviewIcon(drawable, true);
    }

    private String targetLabel(String packageKey, String role) {
        String pkg = LauncherPrefs.packageFor(activity, packageKey);
        return pkg.isEmpty() ? "Role · " + RoleIconCatalog.label(role) : AppResolver.labelFor(activity, pkg, pkg);
    }

    private void chooseTarget() {
        new AlertDialog.Builder(activity).setTitle((drawer ? "Drawer " : "Quick ") + (index + 1) + " app")
                .setItems(new String[] {"Choose app", "Use role default", "Change role"}, (dialog, which) -> {
                    if (which == 0) {
                        Intent intent = new Intent(activity, AppDrawerActivity.class);
                        intent.putExtra(AppDrawerActivity.EXTRA_PICK_KEY, packageKey());
                        activity.startActivity(intent);
                    } else if (which == 1) {
                        LauncherPrefs.setPackage(activity, packageKey(), null);
                        MediaListenerService.refreshActiveSessions();
                        refresh.run();
                    } else chooseRole();
                }).setNegativeButton("Cancel", null).show();
    }

    private void chooseRole() {
        String current = role();
        new AlertDialog.Builder(activity).setTitle("Use role shortcut (clears app assignment)")
                .setSingleChoiceItems(RoleIconCatalog.LABELS, indexOf(RoleIconCatalog.VALUES, current), (dialog, which) -> {
                    if (drawer) ShortcutSlot.chooseRole(activity, LauncherPrefs.DRAWER_ROLE_KEYS[index],
                            LauncherPrefs.DRAWER_QUICK_KEYS[index], RoleIconCatalog.VALUES[which]);
                    else ShortcutSlot.chooseRole(activity, LauncherPrefs.QUICK_ROLE_KEYS[index],
                            LauncherPrefs.QUICK_KEYS[index], RoleIconCatalog.VALUES[which]);
                    dialog.dismiss();
                    refresh.run();
                }).setNegativeButton("Cancel", null).show();
    }

    private void chooseIcon() {
        String current = drawer ? UiPersonalizationPrefs.drawerQuickIcon(activity, index)
                : UiPersonalizationPrefs.quickIcon(activity, index);
        new AlertDialog.Builder(activity).setTitle("Icon appearance · visual only")
                .setSingleChoiceItems(SlotIconCatalog.LABELS, indexOf(SlotIconCatalog.VALUES, current), (dialog, which) -> {
                    if (drawer) UiPersonalizationPrefs.setDrawerQuickIcon(activity, index, SlotIconCatalog.VALUES[which]);
                    else UiPersonalizationPrefs.setQuickIcon(activity, index, SlotIconCatalog.VALUES[which]);
                    dialog.dismiss();
                    refresh.run();
                }).setNegativeButton("Cancel", null).show();
    }

    private String packageKey() {
        return drawer ? LauncherPrefs.DRAWER_QUICK_KEYS[index] : LauncherPrefs.QUICK_KEYS[index];
    }

    private String role() {
        return drawer ? LauncherPrefs.drawerQuickRole(activity, index) : LauncherPrefs.quickRole(activity, index);
    }

    private TextView text(String value, int dimenId, int colorId) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextColor(AutomotiveUi.color(activity, colorId));
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, activity.getResources().getDimension(dimenId));
        view.setPadding(0, 4, 0, 4);
        return view;
    }

    private static int indexOf(String[] values, String current) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) return i;
        return -1;
    }
}
