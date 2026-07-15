package com.andres.zengecko;

import android.content.Context;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves quick-access favicons off the UI thread and saves them safely. */
public final class ZenFaviconResolver {
    private static final String TAG = "ZenFaviconResolver";
    private static final int CONNECT_TIMEOUT_MS = 4500;
    private static final int READ_TIMEOUT_MS = 5500;
    private static final int MAX_HTML_BYTES = 256 * 1024;
    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor();

    private static final Pattern ICON_REL_FIRST = Pattern.compile(
            "<link[^>]*rel\\s*=\\s*['\\\"][^'\\\"]*icon[^'\\\"]*['\\\"][^>]*"
                    + "href\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ICON_HREF_FIRST = Pattern.compile(
            "<link[^>]*href\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"][^>]*"
                    + "rel\\s*=\\s*['\\\"][^'\\\"]*icon[^'\\\"]*['\\\"]",
            Pattern.CASE_INSENSITIVE);

    private ZenFaviconResolver() { }

    public static void resolveAndSave(
            Context context, QuickAccessStore.Item source) {
        if (context == null || source == null) return;
        Context app = context.getApplicationContext();
        QuickAccessStore.Item item = source.copy();
        EXECUTOR.execute(() -> resolve(app, item));
    }

    private static void resolve(Context context, QuickAccessStore.Item item) {
        try {
            String pageUrl = QuickAccessStore.normalizeUrl(item.url);
            if (!isHttp(pageUrl)) return;
            String iconUrl = findIconInPage(pageUrl);
            if (iconUrl == null || iconUrl.trim().isEmpty()) {
                URI page = URI.create(pageUrl);
                iconUrl = new URI(
                        page.getScheme(), page.getAuthority(),
                        "/favicon.ico", null, null).toString();
            }
            if (!isHttp(iconUrl)) return;
            item.iconUrl = iconUrl;
            QuickAccessStore.save(context, item);
            Log.d(TAG, "Favicon resolved for " + pageUrl);
        } catch (Throwable error) {
            Log.w(TAG, "Favicon resolution failed", error);
        }
    }

    private static String findIconInPage(String pageUrl) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(pageUrl).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty(
                    "User-Agent", "Mozilla/5.0 (Android) ZenBrowser/0.1.18");
            connection.setRequestProperty(
                    "Accept", "text/html,application/xhtml+xml");
            connection.connect();
            int status = connection.getResponseCode();
            if (status < 200 || status >= 400) return null;
            String contentType = connection.getContentType();
            if (contentType != null
                    && !contentType.toLowerCase(Locale.ROOT).contains("html")) {
                return null;
            }
            byte[] htmlBytes = readLimited(connection, MAX_HTML_BYTES);
            String html = new String(htmlBytes, StandardCharsets.UTF_8);
            String href = match(ICON_REL_FIRST, html);
            if (href == null) href = match(ICON_HREF_FIRST, html);
            if (href == null || href.trim().isEmpty()) return null;
            return new URL(new URL(pageUrl), href.trim()).toString();
        } catch (Throwable error) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static byte[] readLimited(
            HttpURLConnection connection, int maximum) throws Exception {
        try (BufferedInputStream input =
                     new BufferedInputStream(connection.getInputStream());
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                int allowed = Math.min(count, maximum - total);
                if (allowed <= 0) break;
                output.write(buffer, 0, allowed);
                total += allowed;
            }
            return output.toByteArray();
        }
    }

    private static String match(Pattern pattern, String html) {
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static boolean isHttp(String value) {
        if (value == null) return false;
        String clean = value.trim().toLowerCase(Locale.ROOT);
        return clean.startsWith("https://") || clean.startsWith("http://");
    }
}
