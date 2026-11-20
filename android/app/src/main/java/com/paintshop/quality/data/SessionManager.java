package com.paintshop.quality.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.paintshop.quality.BuildConfig;
import com.paintshop.quality.model.User;

public class SessionManager {
    private static final String PREFS = "session";
    private static final String TOKEN = "token";
    private static final String USER = "user";
    private static final String BASE_URL = "base_url";

    private final SharedPreferences preferences;
    private final Gson gson = new Gson();

    public SessionManager(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String normalizeBaseUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            normalized = BuildConfig.API_BASE_URL;
        }
        if (!normalized.regionMatches(true, 0, "http://", 0, 7)
                && !normalized.regionMatches(true, 0, "https://", 0, 8)) {
            normalized = (isLocalAddress(normalized) ? "http://" : "https://") + normalized;
        }
        return normalized.replaceAll("/+$", "") + "/";
    }

    private static boolean isLocalAddress(String value) {
        String host = value;
        int slash = host.indexOf('/');
        if (slash >= 0) {
            host = host.substring(0, slash);
        }
        int colon = host.indexOf(':');
        if (colon >= 0) {
            host = host.substring(0, colon);
        }
        if (host.equalsIgnoreCase("localhost")) {
            return true;
        }
        return host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    public synchronized String getBaseUrl() {
        return normalizeBaseUrl(preferences.getString(BASE_URL, BuildConfig.API_BASE_URL));
    }

    public synchronized void saveBaseUrl(String baseUrl) {
        preferences.edit().putString(BASE_URL, normalizeBaseUrl(baseUrl)).apply();
    }

    public synchronized void saveAuth(String token, User user) {
        preferences.edit().putString(TOKEN, token).putString(USER, gson.toJson(user)).apply();
    }

    public synchronized void updateUser(User user) {
        preferences.edit().putString(USER, gson.toJson(user)).apply();
    }

    public synchronized String getToken() {
        return preferences.getString(TOKEN, "");
    }

    public synchronized User getUser() {
        String value = preferences.getString(USER, "");
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return gson.fromJson(value, User.class);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public synchronized boolean hasSession() {
        return !getToken().isEmpty() && getUser() != null;
    }

    public synchronized String getUserKey() {
        User user = getUser();
        if (user == null) {
            return "anonymous";
        }
        return user.id > 0 ? String.valueOf(user.id) : user.inspectorName + "@" + user.area;
    }

    public synchronized void clear() {
        preferences.edit().remove(TOKEN).remove(USER).apply();
    }
}
