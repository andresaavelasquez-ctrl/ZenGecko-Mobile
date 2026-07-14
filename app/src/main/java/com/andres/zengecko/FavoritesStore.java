package com.andres.zengecko;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import com.andres.zengecko.model.BrowserTab;

public final class FavoritesStore {
    public static final class Record {
        public final String title;
        public final String url;
        public final long createdAt;

        Record(String title, String url, long createdAt) {
            this.title = title;
            this.url = url;
            this.createdAt = createdAt;
        }

        JSONObject toJson() {
            JSONObject value = new JSONObject();
            try {
                value.put("title", title);
                value.put("url", url);
                value.put("createdAt", createdAt);
            } catch (Exception ignored) { }
            return value;
        }

        static Record fromJson(JSONObject value) {
            return new Record(
                    value.optString("title", ""),
                    value.optString("url", ""),
                    value.optLong("createdAt", System.currentTimeMillis()));
        }
    }

    private static final String PREFS = "zen_favorites_v1";
    private static final String KEY = "entries";
    private static final String MIGRATED = "migrated_essentials";
    private static final Object LOCK = new Object();

    private FavoritesStore() { }

    public static void migrateEssentials(Context context, List<BrowserTab> tabs) {
        Context app = context.getApplicationContext();
        SharedPreferences preferences = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (preferences.getBoolean(MIGRATED, false)) return;
        for (BrowserTab tab : tabs) {
            if (!tab.essential || tab.url == null || tab.url.startsWith("about:")) continue;
            add(app, tab.title, tab.url);
        }
        preferences.edit().putBoolean(MIGRATED, true).apply();
    }

    public static void add(Context context, String title, String url) {
        if (url == null || url.trim().isEmpty() || url.startsWith("about:")) return;
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            List<Record> records = read(app);
            String normalized = url.trim();
            records.removeIf(item -> normalized.equals(item.url));
            records.add(0, new Record(
                    title == null || title.trim().isEmpty() ? normalized : title.trim(),
                    normalized,
                    System.currentTimeMillis()));
            while (records.size() > 120) records.remove(records.size() - 1);
            write(app, records);
        }
    }

    public static boolean contains(Context context, String url) {
        if (url == null) return false;
        for (Record record : list(context)) if (url.equals(record.url)) return true;
        return false;
    }

    public static List<Record> list(Context context) {
        synchronized (LOCK) {
            return new ArrayList<>(read(context.getApplicationContext()));
        }
    }

    public static void remove(Context context, String url) {
        if (url == null) return;
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            List<Record> records = read(app);
            records.removeIf(item -> url.equals(item.url));
            write(app, records);
        }
    }

    public static void clear(Context context) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY).apply();
    }

    private static List<Record> read(Context context) {
        List<Record> records = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .getString(KEY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                Record record = Record.fromJson(array.getJSONObject(i));
                if (record.url != null && !record.url.trim().isEmpty()) records.add(record);
            }
        } catch (Exception ignored) { }
        return records;
    }

    private static void write(Context context, List<Record> records) {
        JSONArray array = new JSONArray();
        for (Record record : records) array.put(record.toJson());
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, array.toString()).apply();
    }
}
