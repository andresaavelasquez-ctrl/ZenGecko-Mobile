package com.andres.zengecko;

import android.net.Uri;

/** Search providers exposed by ZenGecko. */
public enum SearchEngine {
    DUCKDUCKGO("duckduckgo", "DuckDuckGo", "D", "https://duckduckgo.com/?q="),
    PERPLEXITY("perplexity", "Perplexity", "P", "https://www.perplexity.ai/search?q="),
    BRAVE("brave", "Brave Search", "B", "https://search.brave.com/search?q="),
    GOOGLE("google", "Google", "G", "https://www.google.com/search?q=");

    public final String id;
    public final String displayName;
    public final String mark;
    private final String searchBaseUrl;

    SearchEngine(String id, String displayName, String mark, String searchBaseUrl) {
        this.id = id;
        this.displayName = displayName;
        this.mark = mark;
        this.searchBaseUrl = searchBaseUrl;
    }

    public String buildSearchUrl(String query) {
        return searchBaseUrl + Uri.encode(query == null ? "" : query.trim());
    }

    public static SearchEngine fromId(String id) {
        if (id != null) {
            for (SearchEngine engine : values()) {
                if (engine.id.equalsIgnoreCase(id)) return engine;
            }
        }
        return DUCKDUCKGO;
    }
}
