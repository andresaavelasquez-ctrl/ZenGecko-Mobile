package com.andres.zengecko;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebRequestError;
import com.andres.zengecko.model.BrowserTab;
import com.andres.zengecko.model.Workspace;

public final class BrowserRepository {
    public interface Observer { void onBrowserStateChanged(); }

    private static final String PREFS = "browser_state";
    private static final String KEY_STATE = "state_v1";
    private static BrowserRepository instance;

    private final Context context;
    private final SharedPreferences preferences;
    private final List<Workspace> workspaces = new ArrayList<>();
    private final List<BrowserTab> tabs = new ArrayList<>();
    private final List<Observer> observers = new ArrayList<>();
    private String activeWorkspaceId;
    private String activeTabId;

    private BrowserRepository(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
    }

    public static synchronized BrowserRepository get(Context context) {
        if (instance == null) instance = new BrowserRepository(context);
        return instance;
    }

    public void addObserver(Observer observer) {
        if (!observers.contains(observer)) observers.add(observer);
    }

    public void removeObserver(Observer observer) { observers.remove(observer); }

    public List<Workspace> getWorkspaces() { return Collections.unmodifiableList(workspaces); }
    public List<BrowserTab> getTabs() { return Collections.unmodifiableList(tabs); }
    public String getActiveWorkspaceId() { return activeWorkspaceId; }

    public BrowserTab getActiveTab() {
        for (BrowserTab tab : tabs) if (tab.id.equals(activeTabId)) return tab;
        return tabs.isEmpty() ? null : tabs.get(0);
    }

    public List<BrowserTab> getVisibleTabs() {
        List<BrowserTab> result = new ArrayList<>();
        for (BrowserTab tab : tabs) {
            if (tab.essential || tab.workspaceId.equals(activeWorkspaceId)) result.add(tab);
        }
        return result;
    }

    public BrowserTab addTab(String rawUrl, boolean select) {
        String url = normalizeInput(rawUrl);
        BrowserTab tab = new BrowserTab(UUID.randomUUID().toString(), activeWorkspaceId, "Nueva pestaña", url);
        tabs.add(tab);
        ensureSession(tab, true);
        if (select) activeTabId = tab.id;
        persistAndNotify();
        return tab;
    }

    public void selectTab(String tabId) {
        BrowserTab tab = findTab(tabId);
        if (tab == null) return;
        if (!tab.essential) activeWorkspaceId = tab.workspaceId;
        activeTabId = tab.id;
        ensureSession(tab, true);
        persistAndNotify();
    }

    public void closeTab(String tabId) {
        BrowserTab closing = findTab(tabId);
        if (closing == null) return;
        if (closing.session != null) closing.session.close();
        tabs.remove(closing);
        if (tabId.equals(activeTabId)) {
            BrowserTab replacement = firstVisibleTab();
            if (replacement == null) replacement = addTabInternal("about:blank", false, true);
            activeTabId = replacement.id;
            ensureSession(replacement, true);
        }
        persistAndNotify();
    }

    public void switchWorkspace(String workspaceId) {
        if (findWorkspace(workspaceId) == null) return;
        activeWorkspaceId = workspaceId;
        BrowserTab candidate = null;
        for (BrowserTab tab : tabs) {
            if (tab.workspaceId.equals(workspaceId) && !tab.essential) { candidate = tab; break; }
        }
        if (candidate == null) candidate = addTabInternal("about:blank", false, true);
        activeTabId = candidate.id;
        ensureSession(candidate, true);
        persistAndNotify();
    }

    public Workspace addWorkspace(String name) {
        Workspace workspace = new Workspace(UUID.randomUUID().toString(), name == null || name.trim().isEmpty() ? "Espacio" : name.trim());
        workspaces.add(workspace);
        activeWorkspaceId = workspace.id;
        BrowserTab tab = addTabInternal("about:blank", false, true);
        activeTabId = tab.id;
        persistAndNotify();
        return workspace;
    }

    public void toggleEssential(String tabId) {
        BrowserTab tab = findTab(tabId);
        if (tab == null) return;
        tab.essential = !tab.essential;
        if (!tab.essential) tab.workspaceId = activeWorkspaceId;
        persistAndNotify();
    }

    public void loadInActiveTab(String input) {
        BrowserTab tab = getActiveTab();
        if (tab == null) return;
        ensureSession(tab, false);
        String url = normalizeInput(input);
        tab.url = url;
        tab.session.loadUri(url);
        persistAndNotify();
    }

    public String normalizeInput(String input) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) return "about:blank";
        if (value.startsWith("about:") || value.startsWith("view-source:") || value.startsWith("data:")) return value;
        Uri parsed = Uri.parse(value);
        if (parsed.getScheme() != null) return value;
        if (value.contains(".") && !value.contains(" ")) return "https://" + value;
        return "https://duckduckgo.com/?q=" + Uri.encode(value);
    }

    private BrowserTab addTabInternal(String url, boolean select, boolean load) {
        BrowserTab tab = new BrowserTab(UUID.randomUUID().toString(), activeWorkspaceId, "Nueva pestaña", url);
        tabs.add(tab);
        ensureSession(tab, load);
        if (select) activeTabId = tab.id;
        return tab;
    }

    private void ensureSession(BrowserTab tab, boolean loadIfNew) {
        if (tab.session != null && tab.session.isOpen()) return;
        GeckoSession session = new GeckoSession();
        tab.session = session;

        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override public void onTitleChange(GeckoSession ignored, String title) {
                tab.title = title == null || title.trim().isEmpty() ? fallbackTitle(tab.url) : title;
                persistAndNotify();
            }
            @Override public void onCloseRequest(GeckoSession ignored) { closeTab(tab.id); }
            @Override public void onCrash(GeckoSession ignored) {
                tab.title = "La pestaña falló";
                tab.loading = false;
                notifyObservers();
            }
        });

        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override public void onLocationChange(GeckoSession ignored, String url,
                    List<GeckoSession.PermissionDelegate.ContentPermission> permissions,
                    Boolean hasUserGesture) {
                if (url != null) tab.url = url;
                persistAndNotify();
            }
            @Override public void onCanGoBack(GeckoSession ignored, boolean canGoBack) {
                tab.canGoBack = canGoBack;
                notifyObservers();
            }
            @Override public void onCanGoForward(GeckoSession ignored, boolean canGoForward) {
                tab.canGoForward = canGoForward;
                notifyObservers();
            }
            @Override public GeckoResult<GeckoSession> onNewSession(GeckoSession ignored, String uri) {
                BrowserTab newTab = addTabInternal(uri, true, false);
                persistAndNotify();
                return GeckoResult.fromValue(newTab.session);
            }
            @Override public GeckoResult<String> onLoadError(GeckoSession ignored, String uri, WebRequestError error) {
                tab.title = "No se pudo cargar";
                notifyObservers();
                return null;
            }
        });

        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override public void onPageStart(GeckoSession ignored, String url) {
                tab.loading = true;
                tab.progress = 5;
                tab.url = url;
                notifyObservers();
            }
            @Override public void onProgressChange(GeckoSession ignored, int progress) {
                tab.progress = progress;
                notifyObservers();
            }
            @Override public void onPageStop(GeckoSession ignored, boolean success) {
                tab.loading = false;
                tab.progress = 100;
                persistAndNotify();
            }
        });

        session.setPermissionDelegate(new GeckoSession.PermissionDelegate() {
            @Override public GeckoResult<Integer> onContentPermissionRequest(
                    GeckoSession ignored, GeckoSession.PermissionDelegate.ContentPermission permission) {
                int type = permission.permission;
                boolean safeDefault = type == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE
                        || type == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE
                        || type == GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE;
                return GeckoResult.fromValue(safeDefault
                        ? GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                        : GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY);
            }
        });

        session.open(ZenGeckoApplication.runtime(context));
        if (loadIfNew) session.loadUri(tab.url == null ? "about:blank" : tab.url);
    }

    private BrowserTab firstVisibleTab() {
        for (BrowserTab tab : tabs) if (tab.essential || tab.workspaceId.equals(activeWorkspaceId)) return tab;
        return null;
    }

    private BrowserTab findTab(String id) {
        for (BrowserTab tab : tabs) if (tab.id.equals(id)) return tab;
        return null;
    }

    private Workspace findWorkspace(String id) {
        for (Workspace workspace : workspaces) if (workspace.id.equals(id)) return workspace;
        return null;
    }

    private static String fallbackTitle(String url) {
        if (url == null || url.equals("about:blank")) return "Nueva pestaña";
        try {
            String host = Uri.parse(url).getHost();
            return host == null ? url : host;
        } catch (Exception ignored) { return url; }
    }

    private void load() {
        String raw = preferences.getString(KEY_STATE, null);
        if (raw != null) {
            try {
                JSONObject state = new JSONObject(raw);
                JSONArray ws = state.optJSONArray("workspaces");
                if (ws != null) for (int i = 0; i < ws.length(); i++) {
                    JSONObject item = ws.getJSONObject(i);
                    workspaces.add(new Workspace(item.getString("id"), item.getString("name")));
                }
                JSONArray savedTabs = state.optJSONArray("tabs");
                if (savedTabs != null) for (int i = 0; i < savedTabs.length(); i++) {
                    JSONObject item = savedTabs.getJSONObject(i);
                    BrowserTab tab = new BrowserTab(item.getString("id"), item.getString("workspaceId"),
                            item.optString("title", "Nueva pestaña"), item.optString("url", "about:blank"));
                    tab.pinned = item.optBoolean("pinned", false);
                    tab.essential = item.optBoolean("essential", false);
                    tabs.add(tab);
                }
                activeWorkspaceId = state.optString("activeWorkspaceId", null);
                activeTabId = state.optString("activeTabId", null);
            } catch (JSONException ignored) {
                workspaces.clear(); tabs.clear();
            }
        }
        if (workspaces.isEmpty()) {
            workspaces.add(new Workspace("personal", "Personal"));
            workspaces.add(new Workspace("work", "Trabajo"));
            workspaces.add(new Workspace("study", "Estudio"));
        }
        if (activeWorkspaceId == null || findWorkspace(activeWorkspaceId) == null) activeWorkspaceId = workspaces.get(0).id;
        if (tabs.isEmpty()) {
            BrowserTab start = new BrowserTab(UUID.randomUUID().toString(), activeWorkspaceId, "Nueva pestaña", "about:blank");
            tabs.add(start); activeTabId = start.id;
        }
        BrowserTab active = findTab(activeTabId);
        if (active == null) { active = firstVisibleTab(); activeTabId = active == null ? tabs.get(0).id : active.id; }
        ensureSession(getActiveTab(), true);
        persist();
    }

    private void persistAndNotify() { persist(); notifyObservers(); }

    private void persist() {
        try {
            JSONObject state = new JSONObject();
            JSONArray ws = new JSONArray();
            for (Workspace workspace : workspaces) ws.put(new JSONObject().put("id", workspace.id).put("name", workspace.name));
            JSONArray savedTabs = new JSONArray();
            for (BrowserTab tab : tabs) {
                savedTabs.put(new JSONObject()
                        .put("id", tab.id).put("workspaceId", tab.workspaceId)
                        .put("title", tab.title).put("url", tab.url)
                        .put("pinned", tab.pinned).put("essential", tab.essential));
            }
            state.put("workspaces", ws).put("tabs", savedTabs)
                    .put("activeWorkspaceId", activeWorkspaceId).put("activeTabId", activeTabId);
            preferences.edit().putString(KEY_STATE, state.toString()).apply();
        } catch (JSONException ignored) { }
    }

    private void notifyObservers() {
        List<Observer> copy = new ArrayList<>(observers);
        for (Observer observer : copy) observer.onBrowserStateChanged();
    }
}
