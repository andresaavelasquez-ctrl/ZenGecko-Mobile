package com.andres.zengecko.model;

import org.mozilla.geckoview.GeckoSession;

public final class BrowserTab {
    public final String id;
    public String workspaceId;
    public String title;
    public String url;
    public boolean pinned;
    public boolean essential;
    public boolean canGoBack;
    public boolean canGoForward;
    public boolean loading;
    public boolean showStartPage;
    public boolean desktopMode;
    public boolean hasValidPaint;
    public int progress;
    public int navigationSerial;
    public boolean popupWindow;
    public long createdAtElapsed;
    public GeckoSession session;

    public BrowserTab(String id, String workspaceId, String title, String url) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.title = title;
        this.url = url;
        this.showStartPage = url == null || "about:blank".equals(url);
        this.desktopMode = false;
        this.hasValidPaint = this.showStartPage;
        this.progress = 0;
        this.createdAtElapsed = android.os.SystemClock.elapsedRealtime();
    }
}
