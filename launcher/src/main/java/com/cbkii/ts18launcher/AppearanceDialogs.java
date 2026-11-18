package com.cbkii.ts18launcher;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TimePicker;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** Framework dialogs use the same local policy without changing global Android night mode. */
final class AppearanceDialogs {
    private final Context context;
    private final List<WeakReference<Dialog>> dialogs = new ArrayList<>();
    private final Map<Dialog, Runnable> bindings = new HashMap<>();
    AppearanceDialogs(Context context) { this.context = context; }

    Context themedContext() {
        return new ContextThemeWrapper(context,
                LauncherPrefs.APPEARANCE_DAY.equals(AppearanceController.resolvedMode(context))
                        ? android.R.style.Theme_Material_Light_Dialog_Alert : android.R.style.Theme_Material_Dialog_Alert);
    }

    AlertDialog.Builder builder() {
        return new AlertDialog.Builder(themedContext()) {
            @Override public AlertDialog create() {
                AlertDialog dialog = super.create();
                dialog.setOnShowListener(ignored -> track(dialog));
                return dialog;
            }
        };
    }

    void bind(Dialog dialog, Runnable update) {
        bindings.put(dialog, update);
        dialog.setOnDismissListener(ignored -> bindings.remove(dialog));
    }

    void track(Dialog dialog) {
        dialogs.add(new WeakReference<>(dialog));
        apply(dialog);
    }

    void close() {
        for (WeakReference<Dialog> reference : dialogs) {
            Dialog dialog = reference.get();
            if (dialog != null && dialog.isShowing()) dialog.dismiss();
        }
        dialogs.clear();
        bindings.clear();
    }

    void refresh() {
        List<LiveTimePicker> reopen = new ArrayList<>();
        for (Iterator<WeakReference<Dialog>> it = dialogs.iterator(); it.hasNext();) {
            Dialog dialog = it.next().get();
            if (dialog == null || !dialog.isShowing()) it.remove();
            else if (dialog instanceof LiveTimePicker) {
                it.remove();
                dialog.dismiss();
                reopen.add((LiveTimePicker) dialog);
            } else apply(dialog);
        }
        for (LiveTimePicker previous : reopen)
            showTimePicker(previous.listener, previous.hour, previous.minute);
    }

    void showTimePicker(TimePickerDialog.OnTimeSetListener listener, int hour, int minute) {
        LiveTimePicker picker = new LiveTimePicker(listener, hour, minute);
        picker.show();
        track(picker);
    }

    private final class LiveTimePicker extends TimePickerDialog {
        final OnTimeSetListener listener;
        int hour;
        int minute;
        LiveTimePicker(OnTimeSetListener listener, int hour, int minute) {
            super(AppearanceDialogs.this.themedContext(), listener, hour, minute, true);
            this.listener = listener;
            this.hour = hour;
            this.minute = minute;
        }
        @Override public void onTimeChanged(TimePicker view, int hour, int minute) {
            super.onTimeChanged(view, hour, minute);
            this.hour = hour;
            this.minute = minute;
        }
    }

    private void apply(Dialog dialog) {
        if (dialog.getWindow() == null) return;
        dialog.getContext().getTheme().applyStyle(
                LauncherPrefs.APPEARANCE_DAY.equals(AppearanceController.resolvedMode(context))
                        ? android.R.style.Theme_Material_Light_Dialog_Alert : android.R.style.Theme_Material_Dialog_Alert, true);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(AutomotiveUi.color(context, R.color.ui_surface)));
        tint(dialog.getWindow().getDecorView());
        Runnable binding = bindings.get(dialog);
        if (binding != null) binding.run();
    }

    private void tint(View view) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            int colour = view instanceof Button ? AutomotiveUi.accentForeground(context)
                    : AutomotiveUi.color(context, R.color.ui_text);
            text.setTextColor(colour);
            if (view instanceof EditText)
                text.setHintTextColor(AutomotiveUi.color(context, R.color.ui_text_secondary));
        }
        if (view instanceof android.widget.CheckedTextView)
            ((android.widget.CheckedTextView) view).setCheckMarkTintList(
                    ColorStateList.valueOf(AutomotiveUi.accentForeground(context)));
        if (view instanceof android.widget.CompoundButton)
            ((android.widget.CompoundButton) view).setButtonTintList(
                    ColorStateList.valueOf(AutomotiveUi.accentForeground(context)));
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) tint(group.getChildAt(i));
        }
    }
}
