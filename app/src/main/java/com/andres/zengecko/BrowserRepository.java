package com.andres.zengecko;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.net.URI;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.WebRequestError;
import org.mozilla.geckoview.WebResponse;
import com.andres.zengecko.model.BrowserTab;
import com.andres.zengecko.model.Workspace;

public final class BrowserRepository {
    public interface Observer {
        void onBrowserStateChanged();
        default void onFullScreenChanged(GeckoSession session, boolean fullScreen) { }
        default void onDownloadRequested(WebResponse response) { }
        default void onPageStarted(
                GeckoSession session,
                String url,
                boolean hadValidPaint) { }
        default void onPageFinished(GeckoSession session, boolean success) { }
        default void onPaintStatusReset(GeckoSession session) { }
        default void onFirstComposite(GeckoSession session) { }
        default void onFirstContentfulPaint(GeckoSession session) { }
        default void onContextMenu(
                GeckoSession session,
                int screenX,
                int screenY,
                GeckoSession.ContentDelegate.ContextElement element) { }
        default void onPageIcon(GeckoSession session, String iconUrl) { }
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
        final boolean desktopMode;

        ClosedTab(int index, BrowserTab tab) {
            this.index = index;
            this.workspaceId = tab.workspaceId;
            this.title = tab.title;
            this.url = tab.url;
            this.pinned = tab.pinned;
            this.essential = tab.essential;
            this.desktopMode = tab.desktopMode;
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
    private boolean appForeground = true;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int sessionActivityGeneration;

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

    public void setAppForeground(boolean foreground) {
        appForeground = foreground;
        updateSessionActivity();
    }

    public boolean setDesktopMode(String tabId, boolean enabled) {
        BrowserTab tab = findTab(tabId);
        if (tab == null || tab.desktopMode == enabled) return false;
        tab.desktopMode = enabled;
        applySessionSettings(tab);
        if (tab.session != null && tab.session.isOpen()
                && tab.url != null && !"about:blank".equals(tab.url)) {
            tab.showStartPage = false;
            tab.hasValidPaint = false;
            tab.session.reload();
        }
        persistAndNotify();
        return true;
    }

    public void clearRecentUrls() {
        recentUrls.clear();
        persistAndNotify();
    }

    public void setSearchEngine(SearchEngine engine) {
        SearchEngine next = engine == null ? SearchEngine.DUCKDUCKGO : engine;
        if (next == searchEngine) return;
        searchEngine = next;
        preferences.edit().putString(KEY_SEARCH_ENGINE, next.id).apply();
        notifyObservers();
    }

    public String getActiveWorkspaceName() {
        Workspace workspace = findWorkspace(activeWorkspaceId);
        return workspace == null ? "Personal" : workspace.name;
    }

    public boolean selectPreviousTab() {
        List<BrowserTab> visible = getVisibleTabs();
        if (visible.size() <= 1) return false;
        int index = -1;
        for (int i = 0; i < visible.size(); i++) {
            if (visible.get(i).id.equals(activeTabId)) {
                index = i;
                break;
            }
        }
        int previous = index <= 0 ? visible.size() - 1 : index - 1;
        BrowserTab target = visible.get(previous);
        activeTabId = target.id;
        ensureSession(target, true);
        updateSessionActivity();
        persistAndNotify();
        return true;
    }

    public BrowserTab getActiveTab() {
        for (BrowserTab tab : tabs) if (tab.id.equals(activeTabId)) return tab;
        return tabs.isEmpty() ? null : tabs.get(0);
    }

    public List<BrowserTab> getVisibleTabs() {
        List<BrowserTab> visible = new ArrayList<>();
        for (BrowserTab tab : tabs) {
            if (tab.workspaceId.equals(activeWorkspaceId)) visible.add(tab);
        }
        return visible;
    }

    public BrowserTab addTab(String rawUrl, boolean select) {
        String url = normalizeInput(rawUrl);
        BrowserTab tab = new BrowserTab(UUID.randomUUID().toString(), activeWorkspaceId, "Nueva pestaña", url);
        tab.desktopMode = ZenPanelController.desktopModeDefault(context);
        tabs.add(tab);
        if (select) activeTabId = tab.id;
        ensureSession(tab, true);
        updateSessionActivity();
        persistAndNotify();
        return tab;
    }

    public void selectTab(String tabId) {
        BrowserTab tab = findTab(tabId);
        if (tab == null) return;
        activeWorkspaceId = tab.workspaceId;
        activeTabId = tab.id;
        ensureSession(tab, true);
        updateSessionActivity();
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

        if (closingActive) {
            BrowserTab replacement = nearestVisibleTab(closingIndex);
            if (replacement == null) replacement = firstVisibleTab();
            if (replacement == null) {
                replacement = addTabInternal("about:blank", false, true);
            }
            activeTabId = replacement.id;
            ensureSession(replacement, true);
        }
        updateSessionActivity();
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
        restored.desktopMode = closed.desktopMode;

        int index = Math.max(0, Math.min(closed.index, tabs.size()));
        tabs.add(index, restored);
        if (!restored.essential) activeWorkspaceId = restored.workspaceId;
        activeTabId = restored.id;
        ensureSession(restored, true);
        updateSessionActivity();
        persistAndNotify();
        return restored;
    }

    public void switchWorkspace(String workspaceId) {
        if (findWorkspace(workspaceId) == null) return;
        activeWorkspaceId = workspaceId;
        BrowserTab candidate = firstVisibleTab();
        if (candidate == null) {
            candidate = addTabInternal("about:blank", false, true);
        }
        activeTabId = candidate.id;
        ensureSession(candidate, true);
        updateSessionActivity();
        persistAndNotify();
    }

    public Workspace addWorkspace(String name) {
        Workspace workspace = new Workspace(UUID.randomUUID().toString(),
                name == null || name.trim().isEmpty() ? "Espacio" : name.trim());
        workspaces.add(workspace);
        activeWorkspaceId = workspace.id;
        BrowserTab tab = addTabInternal("about:blank", false, true);
        activeTabId = tab.id;
        updateSessionActivity();
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
        tab.showStartPage = "about:blank".equals(url);
        tab.hasValidPaint = tab.showStartPage;
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
        tab.desktopMode = ZenPanelController.desktopModeDefault(context);
        tabs.add(tab);
        if (select) activeTabId = tab.id;
        ensureSession(tab, load);
        updateSessionActivity();
        return tab;
    }

    private void ensureSession(BrowserTab tab, boolean loadIfNew) {
        if (tab.session != null && tab.session.isOpen()) {
            applySessionSettings(tab);
            return;
        }
        GeckoSession session = new GeckoSession();
        tab.session = session;
        applySessionSettings(tab);

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
            @Override public void onPaintStatusReset(GeckoSession source) {
                tab.hasValidPaint = false;
                if (tab.id.equals(activeTabId)) notifyPaintStatusReset(source);
            }
            @Override public void onFirstComposite(GeckoSession source) {
                if (tab.id.equals(activeTabId)) notifyFirstComposite(source);
            }
            @Override public void onFirstContentfulPaint(GeckoSession source) {
                tab.hasValidPaint = true;
                if (tab.id.equals(activeTabId)) notifyFirstContentfulPaint(source);
            }

            @Override public void onContextMenu(
                    GeckoSession source,
                    int screenX,
                    int screenY,
                    GeckoSession.ContentDelegate.ContextElement element) {
                if (tab.id.equals(activeTabId)) {
                    notifyContextMenu(source, screenX, screenY, element);
                }
            }

            @Override public void onWebAppManifest(
                    GeckoSession source,
                    JSONObject manifest) {
                String iconUrl = bestManifestIcon(tab.url, manifest);
                if (!iconUrl.isEmpty() && tab.id.equals(activeTabId)) {
                    notifyPageIcon(source, iconUrl);
                }
            }

            @Override public void onCrash(GeckoSession ignored) {
                recoverCrashedTab(tab);
            }
        });

        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override public void onLocationChange(GeckoSession ignored, String url,
                    List<GeckoSession.PermissionDelegate.ContentPermission> permissions,
                    Boolean hasUserGesture) {
                if (url != null) {
                    tab.url = url;
                    if (!"about:blank".equals(url)) tab.showStartPage = false;
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
            @Override public GeckoResult<String> onLoadError(
                    GeckoSession ignored, String uri, WebRequestError error) {
                tab.title = "No se pudo cargar";
                tab.loading = false;
                notifyObservers();
                return null;
            }
        });

        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override public void onPageStart(GeckoSession source, String url) {
                boolean hadValidPaint = tab.hasValidPaint;
                tab.navigationSerial++;
                tab.loading = true;
                tab.progress = 5;
                tab.url = url;
                tab.showStartPage = url == null || "about:blank".equals(url);
                tab.hasValidPaint = tab.showStartPage;
                notifyPageStarted(source, url, hadValidPaint);
                notifyObservers();
            }
            @Override public void onProgressChange(GeckoSession ignored, int progress) {
                int previous = tab.progress;
                tab.progress = progress;
                if (progress >= 100 || progress <= 5 || Math.abs(progress - previous) >= 4) {
                    notifyObservers();
                }
            }
            @Override public void onPageStop(GeckoSession source, boolean success) {
                tab.loading = false;
                tab.progress = 100;
                recordRecentUrl(tab.url);
                if (success) HistoryStore.record(context, tab.title, tab.url);
                persistAndNotify();
                notifyPageFinished(source, success);
            }
        });

        session.setPermissionDelegate(new GeckoSession.PermissionDelegate() {
            @Override public GeckoResult<Integer> onContentPermissionRequest(
                    GeckoSession ignored, GeckoSession.PermissionDelegate.ContentPermission permission) {
                int type = permission.permission;
                boolean autoplay = ZenPanelController.autoplayEnabled(context)
                        && (type == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE
                        || type == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE);
                boolean safeDefault = autoplay
                        || type == GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE;
                return GeckoResult.fromValue(safeDefault
                        ? GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                        : GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY);
            }
        });

        session.open(ZenGeckoApplication.runtime(context));
        session.setActive(appForeground && tab.id.equals(activeTabId));
        if (loadIfNew) session.loadUri(tab.url == null ? "about:blank" : tab.url);
    }

    private void applySessionSettings(BrowserTab tab) {
        if (tab == null || tab.session == null) return;
        GeckoSessionSettings settings = tab.session.getSettings();
        settings.setAllowJavascript(ZenPanelController.javaScriptEnabled(context));
        settings.setSuspendMediaWhenInactive(true);
        settings.setUserAgentMode(tab.desktopMode
                ? GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                : GeckoSessionSettings.USER_AGENT_MODE_MOBILE);
        settings.setViewportMode(tab.desktopMode
                ? GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                : GeckoSessionSettings.VIEWPORT_MODE_MOBILE);
    }

    private void updateSessionActivity() {
        final int generation = ++sessionActivityGeneration;
        final String selectedId = activeTabId;
        for (BrowserTab item : tabs) {
            if (item.session == null || !item.session.isOpen()) continue;
            if (item.id.equals(selectedId)) {
                try {
                    item.session.setActive(appForeground);
                } catch (RuntimeException ignored) { }
            }
        }

        mainHandler.postDelayed(() -> {
            if (generation != sessionActivityGeneration) return;
            for (BrowserTab item : tabs) {
                if (item.session == null || !item.session.isOpen()) continue;
                boolean active = appForeground && item.id.equals(activeTabId);
                try {
                    item.session.setActive(active);
                } catch (RuntimeException ignored) { }
            }
        }, 280L);
    }


    private void recoverCrashedTab(BrowserTab tab) {
        if (tab == null) return;
        tab.title = "Recuperando pestaña…";
        tab.loading = true;
        tab.hasValidPaint = false;
        tab.session = null;
        ensureSession(tab, true);
        updateSessionActivity();
        notifyObservers();
    }



    private static String bestManifestIcon(String pageUrl, JSONObject manifest) {
        if (manifest == null) return "";
        JSONArray icons = manifest.optJSONArray("icons");
        if (icons == null || icons.length() == 0) return "";
        String selected = "";
        int bestScore = -1;
        for (int index = 0; index < icons.length(); index++) {
            JSONObject icon = icons.optJSONObject(index);
            if (icon == null) continue;
            String src = icon.optString("src", "");
            if (src.isEmpty()) continue;
            String sizes = icon.optString("sizes", "");
            int score = sizes.contains("512") ? 512
                    : sizes.contains("256") ? 256
                    : sizes.contains("192") ? 192
                    : sizes.contains("128") ? 128 : 64;
            if (score > bestScore) {
                selected = src;
                bestScore = score;
            }
        }
        if (selected.isEmpty()) return "";
        try {
            return new URI(pageUrl).resolve(selected).toString();
        } catch (Exception ignored) {
            return selected;
        }
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
                if (candidate.workspaceId.equals(activeWorkspaceId)) return candidate;
            }
            int right = formerIndex - distance;
            if (right >= 0 && right < tabs.size()) {
                BrowserTab candidate = tabs.get(right);
                if (candidate.workspaceId.equals(activeWorkspaceId)) return candidate;
            }
        }
        return null;
    }

    private BrowserTab firstVisibleTab() {
        for (BrowserTab tab : tabs) {
            if (tab.workspaceId.equals(activeWorkspaceId)) return tab;
        }
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
        if (raw != null && ZenPanelController.restoreSessionEnabled(context)) {
            try {
                JSONObject state = new JSONObject(raw);
                JSONArray ws = state.optJSONArray("workspaces");
                if (ws != null) {
                    for (int i = 0; i < ws.length(); i++) {
                        JSONObject item = ws.getJSONObject(i);
                        workspaces.add(new Workspace(
                                item.getString("id"),
                                item.getString("name")));
                    }
                }
                JSONArray savedTabs = state.optJSONArray("tabs");
                if (savedTabs != null) {
                    for (int i = 0; i < savedTabs.length(); i++) {
                        JSONObject item = savedTabs.getJSONObject(i);
                        BrowserTab tab = new BrowserTab(
                                item.getString("id"),
                                item.optString("workspaceId", "personal"),
                                item.optString("title", "Nueva pestaña"),
                                item.optString("url", "about:blank"));
                        tab.pinned = item.optBoolean("pinned", false);
                        tab.essential = item.optBoolean("essential", false);
                        tab.desktopMode = item.optBoolean("desktopMode", false);
                        tab.showStartPage = "about:blank".equals(tab.url);
                        tab.hasValidPaint = tab.showStartPage;
                        tabs.add(tab);
                    }
                }
                JSONArray recent = state.optJSONArray("recentUrls");
                if (recent != null) {
                    for (int i = 0;
                            i < recent.length() && i < MAX_RECENT_URLS;
                            i++) {
                        String url = recent.optString(i, null);
                        if (url != null && !url.trim().isEmpty()) recentUrls.add(url);
                    }
                }
                activeWorkspaceId = state.optString("activeWorkspaceId", "personal");
                activeTabId = state.optString("activeTabId", null);
            } catch (JSONException ignored) {
                workspaces.clear();
                tabs.clear();
                recentUrls.clear();
            }
        }

        ensureDefaultWorkspace("personal", "Personal");
        ensureDefaultWorkspace("work", "Trabajo");
        ensureDefaultWorkspace("education", "Educación");

        if (findWorkspace(activeWorkspaceId) == null) activeWorkspaceId = "personal";
        for (BrowserTab tab : tabs) {
            if (findWorkspace(tab.workspaceId) == null) tab.workspaceId = "personal";
        }

        if (tabs.isEmpty()) {
            BrowserTab start = new BrowserTab(
                    UUID.randomUUID().toString(),
                    "personal",
                    "Nueva pestaña",
                    "about:blank");
            tabs.add(start);
            activeWorkspaceId = "personal";
            activeTabId = start.id;
        }

        BrowserTab active = findTab(activeTabId);
        if (active == null || !active.workspaceId.equals(activeWorkspaceId)) {
            active = firstVisibleTab();
        }
        if (active == null) {
            active = addTabInternal("about:blank", false, false);
        }
        activeTabId = active.id;
        ensureSession(active, true);
        updateSessionActivity();
        persist();
    }

    private void ensureDefaultWorkspace(String id, String name) {
        if (findWorkspace(id) == null) workspaces.add(new Workspace(id, name));
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
                        .put("pinned", tab.pinned).put("essential", tab.essential)
                        .put("desktopMode", tab.desktopMode));
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



    private void notifyContextMenu(
            GeckoSession session,
            int screenX,
            int screenY,
            GeckoSession.ContentDelegate.ContextElement element) {
        List<Observer> copy = new ArrayList<>(observers);
        for (Observer observer : copy) {
            observer.onContextMenu(session, screenX, screenY, element);
        }
    }

    private void notifyPageIcon(GeckoSession session, String iconUrl) {
        List<Observer> copy = new ArrayList<>(observers);
        for (Observer observer : copy) observer.onPageIcon(session, iconUrl);
    }

    private void notifyPageStarted(
            GeckoSession session,
            String url,
            boolean hadValidPaint) {
        List<Observer> copy = new ArrayList<>(observers);
        for (Observer observer : copy) {
            observer.onPageStarted(session, url, hadValidPaint);
        }
    }

    private void notifyPageFinished(GeckoSession session, boolean success) {
        List<Observer> copy = new ArrayList<>(observers);
        for (Observer observer : copy) observer.onPageFinished(session, success);
    }

    private void notifyPaintStatusReset(GeckoSession session) {
        List<Observer> copy = new ArrayList<>(observers);
        for (Observer observer : copy) observer.onPaintStatusReset(session);
    }

    private void notifyFirstComposite(GeckoSession session) {
        List<Observer> copy = new ArrayList<>(observers);
        for (Observer observer : copy) observer.onFirstComposite(session);
    }

    private void notifyFirstContentfulPaint(GeckoSession session) {
        List<Observer> copy = new ArrayList<>(observers);
        for (Observer observer : copy) observer.onFirstContentfulPaint(session);
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
