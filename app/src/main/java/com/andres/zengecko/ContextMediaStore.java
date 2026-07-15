package com.andres.zengecko;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.MimeTypeMap;
import androidx.core.content.FileProvider;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.mozilla.geckoview.GeckoWebExecutor;
import org.mozilla.geckoview.WebRequest;
import org.mozilla.geckoview.WebResponse;

/** Cookie-aware temporary fetches for copy/share actions from web context menus. */
public final class ContextMediaStore {
    public interface Callback {
        void onReady(File file, String mime);
        void onError(Throwable error);
    }

    private static final long MAX_BYTES = 32L * 1024L * 1024L;
    private static final long CACHE_LIMIT = 24L * 1024L * 1024L;
    private static final long MAX_AGE_MS = 12L * 60L * 60L * 1000L;
    private static final long RELEASE_DELAY_MS = 15L * 60L * 1000L;
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ContextMediaStore() { }

    public static void fetch(
            Context context,
            String url,
            String referrer,
            Callback callback) {
        if (url == null || url.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException("Dirección vacía"));
            return;
        }
        Context app = context.getApplicationContext();
        File directory = new File(app.getCacheDir(), "context-media");
        directory.mkdirs();

        try {
            WebRequest.Builder builder = new WebRequest.Builder(url.trim())
                    .header("Accept", "image/avif,image/webp,image/apng,image/*,video/*,audio/*,*/*;q=0.6");
            if (referrer != null && !referrer.trim().isEmpty()) builder.referrer(referrer);
            GeckoWebExecutor executor =
                    new GeckoWebExecutor(ZenGeckoApplication.runtime(app));
            executor.fetch(builder.build(), GeckoWebExecutor.FETCH_FLAGS_NONE)
                    .withHandler(MAIN)
                    .accept(response -> IO.execute(() ->
                                    consume(app, url, response, directory, callback)),
                            callback::onError);
        } catch (Throwable error) {
            callback.onError(error);
        }
    }

    public static void share(Activity activity, File file, String mime) {
        Uri uri = uri(activity, file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(safeMime(mime));
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(Intent.createChooser(intent, "Compartir"));
        releaseLater(file);
    }

    public static void copy(Activity activity, File file, String mime) {
        Uri uri = uri(activity, file);
        ClipboardManager clipboard =
                (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) throw new IllegalStateException("Portapapeles no disponible");
        ClipData clip = ClipData.newUri(activity.getContentResolver(), "Imagen", uri);
        clipboard.setPrimaryClip(clip);
        activity.grantUriPermission(
                activity.getPackageName(), uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        releaseLater(file);
    }

    public static void trimNow(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        IO.execute(() -> trim(new File(app.getCacheDir(), "context-media")));
    }

    public static void clear(Context context) {
        if (context == null) return;
        File directory = new File(context.getApplicationContext().getCacheDir(), "context-media");
        IO.execute(() -> deleteRecursively(directory));
    }

    private static void releaseLater(File file) {
        if (file == null) return;
        MAIN.postDelayed(() -> {
            try { if (file.exists()) file.delete(); }
            catch (SecurityException ignored) { }
        }, RELEASE_DELAY_MS);
    }

    public static Uri uri(Context context, File file) {
        return FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".files",
                file);
    }

    private static void consume(
            Context context,
            String originalUrl,
            WebResponse response,
            File directory,
            Callback callback) {
        if (response == null || response.body == null) {
            postError(callback, new IllegalStateException("El servidor no devolvió contenido"));
            return;
        }

        String mime = header(response, "content-type");
        if (mime.contains(";")) mime = mime.substring(0, mime.indexOf(';')).trim();
        if (mime.isEmpty()) mime = guessMime(originalUrl);
        String extension = extension(mime, originalUrl);
        File target = new File(directory, digest(originalUrl) + extension);
        File temporary = new File(directory, target.getName() + ".part");

        try {
            long total = 0L;
            try (InputStream input = new BufferedInputStream(response.body);
                    FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_BYTES) throw new IllegalStateException("Archivo demasiado grande");
                    output.write(buffer, 0, read);
                }
                output.flush();
            }
            if (target.exists()) target.delete();
            if (!temporary.renameTo(target)) {
                throw new IllegalStateException("No se pudo preparar el archivo temporal");
            }
            trim(directory);
            String finalMime = mime;
            MAIN.post(() -> callback.onReady(target, finalMime));
        } catch (Throwable error) {
            temporary.delete();
            postError(callback, error);
        }
    }

    private static String header(WebResponse response, String name) {
        if (response.headers == null) return "";
        String value = response.headers.get(name);
        return value == null ? "" : value;
    }

    private static String guessMime(String url) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return mime == null ? "application/octet-stream" : mime;
    }

    private static String extension(String mime, String url) {
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        if (extension == null || extension.isEmpty()) {
            extension = MimeTypeMap.getFileExtensionFromUrl(url);
        }
        return extension == null || extension.isEmpty() ? ".bin" : "." + extension;
    }

    private static String safeMime(String mime) {
        return mime == null || mime.trim().isEmpty()
                ? "application/octet-stream" : mime;
    }

    private static void postError(Callback callback, Throwable error) {
        MAIN.post(() -> callback.onError(error));
    }

    private static void trim(File directory) {
        if (directory == null || !directory.isDirectory()) return;
        File[] all = directory.listFiles();
        if (all == null) return;
        long now = System.currentTimeMillis();
        for (File file : all) {
            if (!file.isFile()) continue;
            boolean stalePart = file.getName().endsWith(".part")
                    && now - file.lastModified() > 15L * 60L * 1000L;
            boolean staleAsset = !file.getName().endsWith(".part")
                    && now - file.lastModified() > MAX_AGE_MS;
            if (stalePart || staleAsset) {
                try { file.delete(); } catch (SecurityException ignored) { }
            }
        }

        File[] files = directory.listFiles(file ->
                file.isFile() && !file.getName().endsWith(".part"));
        if (files == null) return;
        long total = 0L;
        for (File file : files) total += file.length();
        if (total <= CACHE_LIMIT) return;
        java.util.Arrays.sort(files, (left, right) ->
                Long.compare(left.lastModified(), right.lastModified()));
        for (File file : files) {
            if (total <= CACHE_LIMIT) break;
            long length = file.length();
            if (file.delete()) total -= length;
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        try { file.delete(); } catch (SecurityException ignored) { }
    }

    private static String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] result = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : result) builder.append(String.format(Locale.ROOT, "%02x", item));
            return builder.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
