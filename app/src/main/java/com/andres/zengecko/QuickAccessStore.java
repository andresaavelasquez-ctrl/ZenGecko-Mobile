package com.andres.zengecko;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Persistent quick-access records, separated by workspace.
 *
 * The store deliberately keeps only compact metadata. Favicons are cached by
 * RemoteAssetLoader with a strict disk limit instead of being embedded in
 * SharedPreferences.
 */
public final class QuickAccessStore {
    public static final int MAX_ITEMS = 8;

    public static final class Item {
        public String id;
        public String workspaceId;
        public String name;
        public String url;
        public String iconUrl;
        public int position;
        public long updatedAt;

        public Item copy() {
            Item item = new Item();
            item.id = id;
            item.workspaceId = workspaceId;
            item.name = name;
            item.url = url;
            item.iconUrl = iconUrl;
            item.position = position;
            item.updatedAt = updatedAt;
            return item;
        }

        JSONObject toJson() {
            JSONObject value = new JSONObject();
            try {
                value.put("id", id)
                        .put("workspaceId", workspaceId)
                        .put("name", name)
                        .put("url", url)
                        .put("iconUrl", iconUrl)
                        .put("position", position)
                        .put("updatedAt", updatedAt);
            } catch (Exception ignored) { }
            return value;
        }

        static Item fromJson(JSONObject value, String workspaceId, int fallbackPosition) {
            Item item = new Item();
            item.id = value.optString("id", UUID.randomUUID().toString());
            item.workspaceId = value.optString("workspaceId", workspaceId);
            item.name = value.optString("name", "Acceso");
            item.url = normalizeUrl(value.optString("url", "https://example.com"));
            item.iconUrl = value.optString("iconUrl", "");
            item.position = value.optInt("position", fallbackPosition);
            item.updatedAt = value.optLong("updatedAt", System.currentTimeMillis());
            return item;
        }
    }

    private static final String PREFS = "zen_quick_access_v2";
    private static final String KEY_PREFIX = "workspace_";
    private static final Object LOCK = new Object();

    private QuickAccessStore() { }

    public static List<Item> list(Context context, String workspaceId) {
        Context app = context.getApplicationContext();
        String safeWorkspace = safeWorkspace(workspaceId);
        synchronized (LOCK) {
            List<Item> items = read(app, safeWorkspace);
            if (items.isEmpty()) {
                items = defaultItems(safeWorkspace);
                write(app, safeWorkspace, items);
            }
            List<Item> copy = new ArrayList<>();
            for (Item item : items) copy.add(item.copy());
            sortAndNormalize(copy);
            return copy;
        }
    }

    public static Item create(
            String workspaceId,
            String name,
            String url,
            String iconUrl) {
        Item item = new Item();
        item.id = UUID.randomUUID().toString();
        item.workspaceId = safeWorkspace(workspaceId);
        item.name = sanitizeName(name, url);
        item.url = normalizeUrl(url);
        item.iconUrl = iconUrl == null ? "" : iconUrl.trim();
        item.position = Integer.MAX_VALUE;
        item.updatedAt = System.currentTimeMillis();
        return item;
    }

    public static boolean save(Context context, Item item) {
        if (item == null) return false;
        Context app = context.getApplicationContext();
        String workspaceId = safeWorkspace(item.workspaceId);
        synchronized (LOCK) {
            List<Item> items = read(app, workspaceId);
            if (items.isEmpty()) items = defaultItems(workspaceId);

            Item normalized = item.copy();
            normalized.id = normalized.id == null || normalized.id.trim().isEmpty()
                    ? UUID.randomUUID().toString() : normalized.id;
            normalized.workspaceId = workspaceId;
            normalized.url = normalizeUrl(normalized.url);
            normalized.name = sanitizeName(normalized.name, normalized.url);
            normalized.iconUrl = normalized.iconUrl == null ? "" : normalized.iconUrl.trim();
            normalized.updatedAt = System.currentTimeMillis();

            int index = indexOf(items, normalized.id);
            if (index >= 0) {
                normalized.position = items.get(index).position;
                items.set(index, normalized);
            } else {
                if (items.size() >= MAX_ITEMS) return false;
                normalized.position = items.size();
                items.add(normalized);
            }
            sortAndNormalize(items);
            write(app, workspaceId, items);
            return true;
        }
    }

    public static void remove(Context context, String workspaceId, String itemId) {
        if (itemId == null) return;
        Context app = context.getApplicationContext();
        String safeWorkspace = safeWorkspace(workspaceId);
        synchronized (LOCK) {
            List<Item> items = read(app, safeWorkspace);
            items.removeIf(item -> itemId.equals(item.id));
            sortAndNormalize(items);
            write(app, safeWorkspace, items);
        }
    }

    public static void moveBefore(
            Context context,
            String workspaceId,
            String sourceId,
            String targetId) {
        if (sourceId == null || targetId == null || sourceId.equals(targetId)) return;
        Context app = context.getApplicationContext();
        String safeWorkspace = safeWorkspace(workspaceId);
        synchronized (LOCK) {
            List<Item> items = read(app, safeWorkspace);
            int source = indexOf(items, sourceId);
            int target = indexOf(items, targetId);
            if (source < 0 || target < 0) return;
            Item moving = items.remove(source);
            if (source < target) target--;
            items.add(Math.max(0, Math.min(target, items.size())), moving);
            sortAndNormalize(items);
            write(app, safeWorkspace, items);
        }
    }

    public static boolean updateIconForUrl(
            Context context,
            String workspaceId,
            String pageUrl,
            String iconUrl) {
        if (pageUrl == null || iconUrl == null || iconUrl.trim().isEmpty()) return false;
        Context app = context.getApplicationContext();
        String safeWorkspace = safeWorkspace(workspaceId);
        String pageHost = host(pageUrl);
        if (pageHost.isEmpty()) return false;

        synchronized (LOCK) {
            List<Item> items = read(app, safeWorkspace);
            boolean changed = false;
            for (Item item : items) {
                if (!pageHost.equals(host(item.url))) continue;
                if (iconUrl.equals(item.iconUrl)) continue;
                item.iconUrl = iconUrl;
                item.updatedAt = System.currentTimeMillis();
                changed = true;
            }
            if (changed) write(app, safeWorkspace, items);
            return changed;
        }
    }

    public static String automaticIconUrl(String rawUrl) {
        String original = rawUrl == null ? "" : rawUrl.trim();
        if (original.isEmpty()) return "";
        String url = normalizeUrl(original);
        try {
            Uri uri = Uri.parse(url);
            if (uri.getScheme() == null || uri.getHost() == null) return "";
            return uri.getScheme() + "://" + uri.getHost() + "/favicon.ico";
        } catch (Exception ignored) {
            return "";
        }
    }


    public static String host(String rawUrl) {
        if (rawUrl == null) return "";
        try {
            String host = Uri.parse(normalizeUrl(rawUrl)).getHost();
            if (host == null) return "";
            return host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        } catch (Exception ignored) {
            return "";
        }
    }

    public static String normalizeUrl(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) return "https://example.com";
        Uri parsed = Uri.parse(clean);
        if (parsed.getScheme() != null) return clean;
        return "https://" + clean;
    }

    private static String sanitizeName(String value, String url) {
        String clean = value == null ? "" : value.trim();
        if (!clean.isEmpty()) return clean;
        String host = host(url);
        return host.isEmpty() ? "Acceso" : host;
    }

    private static int indexOf(List<Item> items, String id) {
        for (int index = 0; index < items.size(); index++) {
            if (id.equals(items.get(index).id)) return index;
        }
        return -1;
    }

    private static void sortAndNormalize(List<Item> items) {
        Collections.sort(items, Comparator.comparingInt(item -> item.position));
        for (int index = 0; index < items.size(); index++) {
            items.get(index).position = index;
        }
    }

    private static List<Item> read(Context context, String workspaceId) {
        SharedPreferences preferences =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = preferences.getString(KEY_PREFIX + workspaceId, null);
        List<Item> items = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return items;
        try {
            JSONArray array = new JSONArray(raw);
            for (int index = 0; index < array.length(); index++) {
                items.add(Item.fromJson(
                        array.getJSONObject(index), workspaceId, index));
            }
            sortAndNormalize(items);
        } catch (Exception ignored) {
            items.clear();
        }
        return items;
    }

    private static void write(Context context, String workspaceId, List<Item> items) {
        JSONArray array = new JSONArray();
        int count = Math.min(MAX_ITEMS, items.size());
        for (int index = 0; index < count; index++) {
            Item item = items.get(index);
            item.position = index;
            array.put(item.toJson());
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PREFIX + workspaceId, array.toString())
                .apply();
    }

    private static List<Item> defaultItems(String workspaceId) {
        List<Item> items = new ArrayList<>();
        addDefault(items, workspaceId, "YouTube", "https://youtube.com");
        addDefault(items, workspaceId, "Discord", "https://discord.com/app");
        addDefault(items, workspaceId, "X", "https://x.com");
        addDefault(items, workspaceId, "Wikipedia", "https://wikipedia.org");
        addDefault(items, workspaceId, "Reddit", "https://reddit.com");
        addDefault(items, workspaceId, "GitHub", "https://github.com");
        addDefault(items, workspaceId, "Perplexity", "https://www.perplexity.ai");
        return items;
    }

    private static void addDefault(
            List<Item> items,
            String workspaceId,
            String name,
            String url) {
        Item item = create(workspaceId, name, url, automaticIconUrl(url));
        item.id = "default-" + name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-");
        item.position = items.size();
        items.add(item);
    }

    private static String safeWorkspace(String workspaceId) {
        return workspaceId == null || workspaceId.trim().isEmpty()
                ? "personal" : workspaceId.trim();
    }
}
