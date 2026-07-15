package com.andres.zengecko;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.mozilla.geckoview.GeckoWebExecutor;
import org.mozilla.geckoview.WebRequest;
import org.mozilla.geckoview.WebResponse;

/** Small, bounded image loader for favicons and contextual previews. */
public final class RemoteAssetLoader {
    public interface Callback {
        void onLoaded(Bitmap bitmap);
        default void onError(Throwable error) { }
    }

    private static final int MEMORY_ITEMS = 16;
    private static final long DISK_LIMIT_BYTES = 6L * 1024L * 1024L;
    private static final long DISK_MAX_AGE_MS = 14L * 24L * 60L * 60L * 1000L;
    private static final long MAX_RESPONSE_BYTES = 8L * 1024L * 1024L;
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Bitmap> MEMORY =
            new LinkedHashMap<String, Bitmap>(MEMORY_ITEMS, .75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
                    return size() > MEMORY_ITEMS;
                }
            };

    private RemoteAssetLoader() { }

    public static void loadInto(
            Context context,
            String url,
            int maxPixels,
            ImageView target,
            Callback callback) {
        if (target == null || url == null || url.trim().isEmpty()) {
            if (callback != null) callback.onError(new IllegalArgumentException("URL vacía"));
            return;
        }
        String clean = url.trim();
        String key = digest(clean);
        try {
            target.setTag(R.id.zen_remote_asset_tag, key);
        } catch (RuntimeException error) {
            if (callback != null) callback.onError(error);
            return;
        }

        synchronized (MEMORY) {
            Bitmap cached = MEMORY.get(key);
            if (cached != null && !cached.isRecycled()) {
                target.setImageBitmap(cached);
                if (callback != null) callback.onLoaded(cached);
                return;
            }
        }

        Context app = context.getApplicationContext();
        File file = assetFile(app, key);
        IO.execute(() -> {
            Bitmap disk = decodeScaled(file, maxPixels);
            if (disk != null) {
                remember(key, disk);
                deliver(target, key, disk, callback);
                return;
            }
            fetch(app, clean, key, maxPixels, target, callback);
        });
    }

    public static void cancel(ImageView target) {
        if (target == null) return;
        try {
            target.setTag(R.id.zen_remote_asset_tag, null);
        } catch (RuntimeException ignored) { }
    }

    public static void clear(Context context) {
        synchronized (MEMORY) {
            MEMORY.clear();
        }
        File directory = new File(context.getCacheDir(), "zen-assets");
        IO.execute(() -> deleteRecursively(directory));
    }

    public static void trimNow(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        IO.execute(() -> trimDiskCache(new File(app.getCacheDir(), "zen-assets")));
    }

    private static void fetch(
            Context context,
            String url,
            String key,
            int maxPixels,
            ImageView target,
            Callback callback) {
        try {
            GeckoWebExecutor executor =
                    new GeckoWebExecutor(ZenGeckoApplication.runtime(context));
            WebRequest request = new WebRequest.Builder(url)
                    .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    .build();
            executor.fetch(request, GeckoWebExecutor.FETCH_FLAGS_ANONYMOUS)
                    .withHandler(MAIN)
                    .accept(response -> IO.execute(() ->
                                    consumeResponse(context, response, key, maxPixels, target, callback)),
                            error -> deliverError(callback, error));
        } catch (Throwable error) {
            deliverError(callback, error);
        }
    }

    private static void consumeResponse(
            Context context,
            WebResponse response,
            String key,
            int maxPixels,
            ImageView target,
            Callback callback) {
        if (response == null || response.body == null) {
            deliverError(callback, new IllegalStateException("La imagen no devolvió contenido"));
            return;
        }
        File file = assetFile(context, key);
        File temporary = new File(file.getParentFile(), key + ".part");
        try {
            file.getParentFile().mkdirs();
            long total = 0L;
            try (InputStream input = new BufferedInputStream(response.body);
                    FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_RESPONSE_BYTES) {
                        throw new IllegalStateException("Imagen demasiado grande");
                    }
                    output.write(buffer, 0, read);
                }
                output.flush();
            }
            if (!temporary.renameTo(file)) {
                copyFile(temporary, file);
                temporary.delete();
            }
            Bitmap bitmap = decodeScaled(file, maxPixels);
            if (bitmap == null) throw new IllegalStateException("Formato de imagen no compatible");
            remember(key, bitmap);
            trimDiskCache(file.getParentFile());
            deliver(target, key, bitmap, callback);
        } catch (Throwable error) {
            temporary.delete();
            file.delete();
            deliverError(callback, error);
        }
    }

    private static void deliver(
            ImageView target,
            String key,
            Bitmap bitmap,
            Callback callback) {
        MAIN.post(() -> {
            boolean valid = false;
            try {
                valid = target != null
                        && target.isAttachedToWindow()
                        && key.equals(target.getTag(R.id.zen_remote_asset_tag));
                if (valid) target.setImageBitmap(bitmap);
            } catch (RuntimeException error) {
                valid = false;
            }

            if (callback != null && valid) {
                try {
                    callback.onLoaded(bitmap);
                } catch (RuntimeException ignored) { }
            }
        });
    }


    private static void deliverError(Callback callback, Throwable error) {
        if (callback == null) return;
        MAIN.post(() -> callback.onError(error));
    }

    private static void remember(String key, Bitmap bitmap) {
        synchronized (MEMORY) {
            MEMORY.put(key, bitmap);
        }
    }

    private static Bitmap decodeScaled(File file, int maxPixels) {
        if (file == null || !file.isFile() || file.length() <= 0L) return null;
        int limit = Math.max(32, maxPixels);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int sample = 1;
        while (bounds.outWidth / sample > limit * 2
                || bounds.outHeight / sample > limit * 2) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap decoded = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (decoded == null) return null;
        int width = decoded.getWidth();
        int height = decoded.getHeight();
        if (width <= limit && height <= limit) return decoded;

        float scale = Math.min(limit / (float) width, limit / (float) height);
        Bitmap scaled = Bitmap.createScaledBitmap(
                decoded,
                Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)),
                true);
        if (scaled != decoded) decoded.recycle();
        return scaled;
    }

    private static File assetFile(Context context, String key) {
        File directory = new File(context.getCacheDir(), "zen-assets");
        return new File(directory, key + ".img");
    }

    private static void trimDiskCache(File directory) {
        if (directory == null || !directory.isDirectory()) return;
        File[] all = directory.listFiles();
        if (all == null) return;
        long now = System.currentTimeMillis();
        for (File file : all) {
            if (!file.isFile()) continue;
            boolean stalePart = file.getName().endsWith(".part")
                    && now - file.lastModified() > 15L * 60L * 1000L;
            boolean staleImage = file.getName().endsWith(".img")
                    && now - file.lastModified() > DISK_MAX_AGE_MS;
            if (stalePart || staleImage) {
                try { file.delete(); } catch (SecurityException ignored) { }
            }
        }

        File[] files = directory.listFiles(file ->
                file.isFile() && file.getName().endsWith(".img"));
        if (files == null) return;
        long total = 0L;
        for (File file : files) total += file.length();
        if (total <= DISK_LIMIT_BYTES) return;
        java.util.Arrays.sort(files, (left, right) ->
                Long.compare(left.lastModified(), right.lastModified()));
        for (File file : files) {
            if (total <= DISK_LIMIT_BYTES) break;
            long length = file.length();
            if (file.delete()) total -= length;
        }
    }

    private static void copyFile(File source, File destination) throws Exception {
        try (FileInputStream input = new FileInputStream(source);
                FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            output.flush();
        }
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

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }
}
