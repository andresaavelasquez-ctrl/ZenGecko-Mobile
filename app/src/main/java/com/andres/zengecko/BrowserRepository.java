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
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebRequestError;
import org.mozilla.geckoview.WebResponse;
import com.andres.zengecko.model.BrowserTab;
import com.andres.zengecko.model.Workspace;

public final class BrowserRepository {
    public interface Observer {
        void onBrowserStateChanged();
        default void onFullScreenChanged(GeckoSession session, boolean fullScreen) { }
        default void onDownloadRequested(WebResponse response) { }
    }

    private static final String PREFS = "browser_state";
    private static final String KEY_STATE = "state_v1";
    private static final String KEY_SEARCH_ENGINE = "search_engine";
    private static final int MAX_RECENT_URLS = 14;
    private static BrowserRepository instance;

    private static final class ClosedTab {
        final int index;
        final String workspaceId;
        final String title;
        final String url;
        final boolean pinned;
        final boolean essential;

        ClosedTab(int index, BrowserTab tab) {
            this.index = index;
            this.workspaceId = tab.workspaceId;
            this.title = tab.title;
            this.url = tab.url;
            this.pinned = tab.pinned;
            this.essential = tab.essential;
        }
    }

    private final Context context;
    private final SharedPreferences preferences;
    private final List<Workspace> workspaces = new ArrayList<>();
    private final List<BrowserTab> tabs = new ArrayList<>();
    private final List<String> recentUrls = new ArrayList<>();
    private final List<Observer> observers = new ArrayList<>();
    private String activeWorkspaceId;
    private String activeTabId;
    private SearchEngine searchEngine = SearchEngine.DUCKDUCKGO;
    private ClosedTab lastClosedTab;

    private BrowserRepository(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.searchEngine = SearchEngine.fromId(
                this.preferences.getString(KEY_SEARCH_ENGINE, SearchEngine.DUCKDUCKGO.id));
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
    public List<String> getRecentUrls() { return Collections.unmodifiableList(recentUrls); }
    public String getActiveWorkspaceId() { return activeWorkspaceId; }
    public String getActiveTabId() { return activeTabId; }
    public SearchEngine getSearchEngine() { return searchEngine; }
    public boolean canRestoreLastClosedTab() { return lastClosedTab != null; }

    public void setSearchEngine(SearchEngine engine) {
        SearchEngine next = engine == null ? SearchEngine.DUCKDUCKGO : engine;
        if (next == searchEngine) return;
        searchEngine = next;
        preferences.edit().putString(KEY_SEARCH_ENGINE, next.id).apply();
        notifyObservers();
    }

    public BrowserTab getActiveTab() {
        for (BrowserTab tab : tabs) if (tab.id.equals(activeTabId)) return tab;
        return tabs.isEmpty() ? null : tabs.get(0);
    }

    public List<BrowserTab> getVisibleTabs() { return new ArrayList<>(tabs); }

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

        int closingIndex = tabs.indexOf(closing);
        lastClosedTab = new ClosedTab(closingIndex, closing);
        boolean closingActive = tabId.equals(activeTabId);

        if (closing.session != null) closing.session.close();
        tabs.remove(closing);

        if (tabs.isEmpty()) {
            BrowserTab replacement = addTabInternal("about:blank", false, true);
            activeTabId = replacement.id;
        } else if (closingActive) {
            BrowserTab replacement = nearestVisibleTab(closingIndex);
            if (replacement == null) replacement = firstVisibleTab();
            if (replacement == null) replacement = tabs.get(Math.min(closingIndex, tabs.size() - 1));
            activeTabId = replacement.id;
            if (!replacement.essential) activeWorkspaceId = replacement.workspaceId;
            ensureSession(replacement, true);
        }
        persistAndNotify();
    }

    public BrowserTab restoreLastClosedTab() {
        ClosedTab closed = lastClosedTab;
        if (closed == null) return null;
        lastClosedTab = null;

        if (tabs.size() == 1 && isDisposableBlank(tabs.get(0))) {
            BrowserTab blank = tabs.remove(0);
            if (blank.session != null) blank.session.close();
        }

        String workspaceId = activeWorkspaceId;
        BrowserTab restored = new BrowserTab(UUID.randomUUID().toString(), workspaceId,
                closed.title == null ? "Nueva pestaña" : closed.title,
                closed.url == null ? "about:blank" : closed.url);
        restored.pinned = closed.pinned;
        restored.essential = closed.essential;

        int index = Math.max(0, Math.min(closed.index, tabs.size()));
        tabs.add(index, restored);
        if (!restored.essential) activeWorkspaceId = restored.workspaceId;
        activeTabId = restored.id;
        ensureSession(restored, true);
        persistAndNotify();
        return restored;
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
        Workspace workspace = new Workspace(UUID.randomUUID().toString(),
                name == null || name.trim().isEmpty() ? "Espacio" : name.trim());
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
        tab.workspaceId = activeWorkspaceId;
        persistAndNotify();
    }

    public void loadInActiveTab(String input) {
        BrowserTab tab = getActiveTab();
        if (tab == null) return;
        ensureSession(tab, false);
        String url = normalizeInput(input);
        tab.url = url;
        recordRecentUrl(url);
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
        return searchEngine.buildSearchUrl(value);
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
            @Override public void onFullScreen(GeckoSession source, boolean fullScreen) {
                if (tab.id.equals(activeTabId)) notifyFullScreen(source, fullScreen);
            }
            @Override public void onExternalResponse(GeckoSession source, WebResponse response) {
                if (tab.id.equals(activeTabId)) notifyDownload(response);
            }
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
                if (url != null) {
                    tab.url = url;
                    recordRecentUrl(url);
                }
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
                tab.navigationSerial++;
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
                recordRecentUrl(tab.url);
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


    private boolean isDisposableBlank(BrowserTab tab) {
        return tab != null && !tab.essential && !tab.pinned
                && (tab.url == null || "about:blank".equals(tab.url))
                && (tab.title == null || "Nueva pestaña".equals(tab.title));
    }

    private BrowserTab nearestVisibleTab(int formerIndex) {
        if (tabs.isEmpty()) return null;
        for (int distance = 0; distance < tabs.size(); distance++) {
            int left = formerIndex - 1 - distance;
            if (left >= 0) {
                BrowserTab candidate = tabs.get(left);
                return candidate;
            }
            int right = formerIndex - distance;
            if (right >= 0 && right < tabs.size()) {
                BrowserTab candidate = tabs.get(right);
                if (candidate.essential || candidate.workspaceId.equals(activeWorkspaceId)) return candidate;
            }
        }
        return null;
    }

    private BrowserTab firstVisibleTab() {
        for (BrowserTab tab : tabs) return tab;
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

    private void recordRecentUrl(String url) {
        if (url == null || url.startsWith("about:") || url.startsWith("data:") || url.startsWith("view-source:")) return;
        recentUrls.remove(url);
        recentUrls.add(0, url);
        while (recentUrls.size() > MAX_RECENT_URLS) recentUrls.remove(recentUrls.size() - 1);
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
                JSONArray recent = state.optJSONArray("recentUrls");
                if (recent != null) for (int i = 0; i < recent.length() && i < MAX_RECENT_URLS; i++) {
                    String url = recent.optString(i, null);
                    if (url != null && !url.trim().isEmpty()) recentUrls.add(url);
                }
                activeWorkspaceId = state.optString("activeWorkspaceId", null);
                activeTabId = state.optString("activeTabId", null);
            } catch (JSONException ignored) {
                workspaces.clear(); tabs.clear(); recentUrls.clear();
            }
        }
        workspaces.clear();
        workspaces.add(new Workspace("personal", "Personal"));
        activeWorkspaceId = "personal";
        for (BrowserTab tab : tabs) tab.workspaceId = activeWorkspaceId;
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
            JSONArray recent = new JSONArray();
            for (String url : recentUrls) recent.put(url);
            state.put("workspaces", ws).put("tabs", savedTabs).put("recentUrls", recent)
                    .put("activeWorkspaceId", activeWorkspaceId).put("activeTabId", activeTabId);
            preferences.edit().putString(KEY_STATE, state.toString()).apply();
        } catch (JSONException ignored) { }
    }

    private void notifyObservers() {
        List<Observer> copy = new ArrayList<>(observers);
        for (Observer observer : copy) observer.onBrowserStateChanged();
    }


    private void notifyDownload(WebResponse response) {
        List<Observer> copy = new ArrayList<>(observers);
        for (Observer observer : copy) observer.onDownloadRequested(response);
    }

    private void notifyFullScreen(GeckoSession session, boolean fullScreen) {
        List<Observer> copy = new ArrayList<>(observers);
        for (Observer observer : copy) observer.onFullScreenChanged(session, fullScreen);
    }
}
