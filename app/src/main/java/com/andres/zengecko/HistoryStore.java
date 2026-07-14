package com.andres.zengecko;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public final class HistoryStore {
    public static final class Record {
        public final String title;
        public final String url;
        public final long visitedAt;

        Record(String title, String url, long visitedAt) {
            this.title = title;
            this.url = url;
            this.visitedAt = visitedAt;
        }

        JSONObject toJson() {
            JSONObject value = new JSONObject();
            try {
                value.put("title", title);
                value.put("url", url);
                value.put("visitedAt", visitedAt);
            } catch (Exception ignored) { }
            return value;
        }

        static Record fromJson(JSONObject value) {
            return new Record(
                    value.optString("title", ""),
                    value.optString("url", ""),
                    value.optLong("visitedAt", System.currentTimeMillis()));
        }
    }

    private static final String PREFS = "zen_history_v2";
    private static final String KEY = "entries";
    private static final Object LOCK = new Object();

    private HistoryStore() { }

    public static void record(Context context, String title, String url) {
        if (!ZenPanelController.saveHistoryEnabled(context)) return;
        if (url == null || url.trim().isEmpty()
                || url.startsWith("about:")
                || url.startsWith("data:")
                || url.startsWith("view-source:")) return;

        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            List<Record> records = read(app);
            String normalized = url.trim();
            records.removeIf(item -> normalized.equals(item.url));
            String safeTitle = title == null || title.trim().isEmpty()
                    ? normalized : title.trim();
            records.add(0, new Record(safeTitle, normalized, System.currentTimeMillis()));
            int limit = ZenPanelController.historyLimit(app);
            while (records.size() > limit) records.remove(records.size() - 1);
            write(app, records);
        }
    }

    public static List<Record> list(Context context) {
        synchronized (LOCK) {
            return new ArrayList<>(read(context.getApplicationContext()));
        }
    }

    public static List<Record> search(Context context, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Record> result = new ArrayList<>();
        for (Record record : list(context)) {
            String haystack = (record.title + " " + record.url).toLowerCase(Locale.ROOT);
            if (needle.isEmpty() || haystack.contains(needle)) result.add(record);
        }
        return result;
    }

    public static void clear(Context context) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY).apply();
    }

    private static List<Record> read(Context context) {
        List<Record> records = new ArrayList<>();
        SharedPreferences preferences =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray array = new JSONArray(preferences.getString(KEY, "[]"));
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
