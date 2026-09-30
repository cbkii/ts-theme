package com.cbkii.ts18launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.speech.RecognizerIntent;

import java.util.ArrayList;

final class VoiceSearch {
    static final int REQUEST_CODE = 7301;

    private VoiceSearch() {}

    static Intent intent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Search apps");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        return intent;
    }

    static boolean available(Context context) {
        return context.getPackageManager().resolveActivity(intent(), PackageManager.MATCH_DEFAULT_ONLY) != null;
    }

    static String firstResult(Intent data) {
        if (data == null) return "";
        ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        return results == null || results.isEmpty() || results.get(0) == null ? "" : results.get(0);
    }
}
