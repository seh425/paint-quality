package com.paintshop.quality.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.paintshop.quality.model.InspectionDraft;

public class DraftStore {
    private static final String PREFS = "inspection_drafts";
    private final SharedPreferences preferences;
    private final Gson gson = new Gson();

    public DraftStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private String key(String userKey, String date, int round, int line) {
        return userKey + "|" + date + "|" + round + "|" + line;
    }

    public synchronized InspectionDraft load(String userKey, String date, int round, int line) {
        String value = preferences.getString(key(userKey, date, round, line), "");
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            InspectionDraft draft = gson.fromJson(value, InspectionDraft.class);
            return draft != null && draft.isValidFor(date, round, line) ? draft : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public synchronized void save(String userKey, InspectionDraft draft) {
        preferences.edit().putString(
                key(userKey, draft.workDate, draft.roundNumber, draft.lineNumber),
                gson.toJson(draft)).apply();
    }

    public synchronized void clear(String userKey, String date, int round, int line) {
        preferences.edit().remove(key(userKey, date, round, line)).apply();
    }
}
