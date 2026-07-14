package com.andres.zengecko;

import android.app.DownloadManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;
import org.mozilla.geckoview.GeckoWebExecutor;
import org.mozilla.geckoview.WebRequest;
import org.mozilla.geckoview.WebResponse;

public final class DownloadStore {
    public static final String QUEUED = "queued";
    public static final String DOWNLOADING = "downloading";
    public static final String COMPLETE = "complete";
    public static final String FAILED = "failed";
    public static final String CANCELLED = "cancelled";

    public static final class Record {
        public String id;
        public String name;
        public String source;
        public String referer;
        public String mime;
        public String contentUri;
        public String status;
        public String error;
        public long bytes;
        public long total;
        public long bytesPerSecond;
        public long updatedAt;
        public long systemId;
        public long createdAt;

        JSONObject toJson() {
            JSONObject item = new JSONObject();
            try {
                item.put("id", id).put("name", name).put("source", source)
                        .put("referer", referer)
                        .put("mime", mime).put("contentUri", contentUri)
                        .put("status", status).put("error", error)
                        .put("bytes", bytes).put("total", total)
                        .put("bytesPerSecond", bytesPerSecond).put("updatedAt", updatedAt)
                        .put("systemId", systemId).put("createdAt", createdAt);
            } catch (Exception ignored) { }
            return item;
        }

        static Record fromJson(JSONObject item) {
            Record record = new Record();
            record.id = item.optString("id", UUID.randomUUID().toString());
            record.name = item.optString("name", "archivo");
            record.source = item.optString("source", "");
            record.referer = item.optString("referer", "");
            record.mime = item.optString("mime", "application/octet-stream");
            record.contentUri = item.optString("contentUri", "");
            record.status = item.optString("status", FAILED);
            record.error = item.optString("error", "");
            record.bytes = item.optLong("bytes", 0L);
            record.total = item.optLong("total", -1L);
            record.bytesPerSecond = item.optLong("bytesPerSecond", 0L);
            record.systemId = item.optLong("systemId", -1L);
            record.createdAt = item.optLong("createdAt", System.currentTimeMillis());
            record.updatedAt = item.optLong("updatedAt", record.createdAt);
            return record;
        }
    }

    private static final String PREFS = "zen_downloads";
    private static final String KEY = "records";
    private static final Object LOCK = new Object();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Map<String, AtomicBoolean> CANCEL_FLAGS = new ConcurrentHashMap<>();

    private DownloadStore() { }

    public static Record enqueue(Context context, WebResponse response) {
        Context app = context.getApplicationContext();
        Record record = new Record();
        record.id = UUID.randomUUID().toString();
        record.source = response.uri == null ? "" : response.uri;
        record.referer = "";
        record.mime = header(response.headers, "content-type");
        if (record.mime.isEmpty()) record.mime = guessMime(record.source);
        record.total = parseLong(header(response.headers, "content-length"));
        record.name = uniqueDisplayName(app, deriveName(response));
        record.status = QUEUED;
        record.error = "";
        record.systemId = -1L;
        record.createdAt = System.currentTimeMillis();
        record.updatedAt = record.createdAt;
        record.bytesPerSecond = 0L;
        upsert(app, record);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && response.body != null) {
            AtomicBoolean cancelled = new AtomicBoolean(false);
            CANCEL_FLAGS.put(record.id, cancelled);
            InputStream body = response.body;
            EXECUTOR.execute(() -> streamToMediaStore(app, record, body, cancelled));
        } else {
            if (response.body != null) {
                try { response.body.close(); } catch (Exception ignored) { }
            }
            enqueueSystem(app, record);
        }
        return record;
    }


    public static Record enqueueUrl(
            Context context,
            String url,
            String referer,
            String nameHint,
            String mimeHint) {
        if (url == null || url.trim().isEmpty()) return null;
        Context app = context.getApplicationContext();
        Record record = new Record();
        record.id = UUID.randomUUID().toString();
        record.source = url.trim();
        record.referer = referer == null ? "" : referer.trim();
        record.mime = mimeHint == null || mimeHint.trim().isEmpty()
                ? guessMime(record.source) : mimeHint.trim();
        String requestedName = nameHint == null || nameHint.trim().isEmpty()
                ? nameFromUrl(record.source) : nameHint.trim();
        record.name = uniqueDisplayName(app, ensureExtension(requestedName, record.mime));
        record.total = -1L;
        record.status = QUEUED;
        record.error = "";
        record.systemId = -1L;
        record.createdAt = System.currentTimeMillis();
        record.updatedAt = record.createdAt;
        record.bytesPerSecond = 0L;
        upsert(app, record);
        startUrlFetch(app, record);
        return record;
    }

    private static void startUrlFetch(Context app, Record record) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            enqueueSystem(app, record);
            return;
        }
        try {
            WebRequest.Builder builder = new WebRequest.Builder(record.source)
                    .header("Accept", "*/*");
            if (record.referer != null && !record.referer.isEmpty()) {
                builder.referrer(record.referer);
            }
            GeckoWebExecutor executor =
                    new GeckoWebExecutor(ZenGeckoApplication.runtime(app));
            executor.fetch(builder.build(), GeckoWebExecutor.FETCH_FLAGS_NONE)
                    .accept(response -> {
                        if (response == null || response.body == null) {
                            record.status = FAILED;
                            record.error = "El servidor no devolvió contenido";
                            record.updatedAt = System.currentTimeMillis();
                            upsert(app, record);
                            return;
                        }
                        String responseMime = header(response.headers, "content-type");
                        if (responseMime.contains(";")) {
                            responseMime = responseMime.substring(
                                    0, responseMime.indexOf(';')).trim();
                        }
                        if (!responseMime.isEmpty()) record.mime = responseMime;
                        long responseTotal = parseLong(
                                header(response.headers, "content-length"));
                        if (responseTotal > 0L) record.total = responseTotal;
                        AtomicBoolean cancelled = new AtomicBoolean(false);
                        CANCEL_FLAGS.put(record.id, cancelled);
                        EXECUTOR.execute(() ->
                                streamToMediaStore(app, record, response.body, cancelled));
                    }, error -> {
                        record.status = FAILED;
                        record.error = error == null || error.getMessage() == null
                                ? "No se pudo iniciar"
                                : error.getMessage();
                        record.updatedAt = System.currentTimeMillis();
                        upsert(app, record);
                    });
        } catch (Throwable error) {
            record.status = FAILED;
            record.error = error.getMessage() == null
                    ? "No se pudo iniciar" : error.getMessage();
            record.updatedAt = System.currentTimeMillis();
            upsert(app, record);
        }
    }

    public static List<Record> list(Context context) {
        refreshSystem(context.getApplicationContext());
        synchronized (LOCK) {
            return read(context.getApplicationContext());
        }
    }

    public static Record get(Context context, String id) {
        Context app = context.getApplicationContext();
        refreshSystem(app);
        return find(app, id);
    }

    public static void cancel(Context context, String id) {
        Context app = context.getApplicationContext();
        AtomicBoolean flag = CANCEL_FLAGS.get(id);
        if (flag != null) flag.set(true);
        Record record = find(app, id);
        if (record == null) return;
        if (record.systemId >= 0) {
            DownloadManager manager = (DownloadManager) app.getSystemService(Context.DOWNLOAD_SERVICE);
            manager.remove(record.systemId);
        }
        record.status = CANCELLED;
        record.error = "Cancelada";
        upsert(app, record);
    }

    public static void retry(Context context, String id) {
        Context app = context.getApplicationContext();
        Record old = find(app, id);
        if (old == null || old.source == null || old.source.isEmpty()) return;
        old.status = QUEUED;
        old.error = "";
        old.bytes = 0;
        old.total = -1L;
        old.bytesPerSecond = 0L;
        old.updatedAt = System.currentTimeMillis();
        old.systemId = -1L;
        upsert(app, old);
        startUrlFetch(app, old);
    }

    public static void remove(Context context, String id) {
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            List<Record> records = read(app);
            records.removeIf(item -> id.equals(item.id));
            write(app, records);
        }
    }

    private static void streamToMediaStore(Context app, Record record, InputStream input,
            AtomicBoolean cancelled) {
        Uri target = null;
        try (InputStream source = input) {
            record.status = DOWNLOADING;
            record.updatedAt = System.currentTimeMillis();
            record.bytesPerSecond = 0L;
            upsert(app, record);
            ContentResolver resolver = app.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, record.name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, record.mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/ZenBrowser");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (target == null) throw new IllegalStateException("Android no creó el archivo");

            try (OutputStream output = resolver.openOutputStream(target, "w")) {
                if (output == null) throw new IllegalStateException("No se pudo abrir la descarga");
                byte[] buffer = new byte[32 * 1024];
                long lastUpdate = 0L;
                long previousBytes = record.bytes;
                int read;
                while ((read = source.read(buffer)) != -1) {
                    if (cancelled.get()) throw new InterruptedException("Cancelada");
                    output.write(buffer, 0, read);
                    record.bytes += read;
                    long now = System.currentTimeMillis();
                    if (now - lastUpdate > 450L) {
                        long elapsed = Math.max(1L, now - record.updatedAt);
                        long delta = Math.max(0L, record.bytes - previousBytes);
                        record.bytesPerSecond = smoothRate(
                                record.bytesPerSecond,
                                delta * 1000L / elapsed);
                        record.updatedAt = now;
                        previousBytes = record.bytes;
                        upsert(app, record);
                        lastUpdate = now;
                    }
                }
                output.flush();
            }
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(target, ready, null, null);
            record.contentUri = target.toString();
            record.status = COMPLETE;
            record.error = "";
            record.bytesPerSecond = 0L;
            record.updatedAt = System.currentTimeMillis();
            upsert(app, record);
        } catch (InterruptedException cancelledError) {
            if (target != null) app.getContentResolver().delete(target, null, null);
            record.status = CANCELLED;
            record.error = "Cancelada";
            record.bytesPerSecond = 0L;
            record.updatedAt = System.currentTimeMillis();
            upsert(app, record);
        } catch (Exception error) {
            if (target != null) app.getContentResolver().delete(target, null, null);
            record.status = FAILED;
            record.error = error.getMessage() == null ? "Error de descarga" : error.getMessage();
            record.bytesPerSecond = 0L;
            record.updatedAt = System.currentTimeMillis();
            upsert(app, record);
        } finally {
            CANCEL_FLAGS.remove(record.id);
        }
    }

    private static void enqueueSystem(Context app, Record record) {
        try {
            DownloadManager manager = (DownloadManager) app.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(record.source));
            request.setTitle(record.name);
            if (record.referer != null && !record.referer.isEmpty()) {
                request.addRequestHeader("Referer", record.referer);
            }
            request.setDescription("Descarga de Zen Browser");
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(ZenPanelController.downloadsAllowedOnMetered(app));
            request.setAllowedOverRoaming(false);
            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS, "ZenBrowser/" + record.name);
            if (record.mime != null && !record.mime.isEmpty()) request.setMimeType(record.mime);
            record.systemId = manager.enqueue(request);
            record.status = DOWNLOADING;
            record.updatedAt = System.currentTimeMillis();
            record.bytesPerSecond = 0L;
            upsert(app, record);
        } catch (Exception error) {
            record.status = FAILED;
            record.error = error.getMessage() == null ? "No se pudo iniciar" : error.getMessage();
            upsert(app, record);
        }
    }

    private static void refreshSystem(Context app) {
        DownloadManager manager = (DownloadManager) app.getSystemService(Context.DOWNLOAD_SERVICE);
        List<Record> records;
        synchronized (LOCK) { records = read(app); }
        boolean changed = false;
        for (Record record : records) {
            if (record.systemId < 0 || COMPLETE.equals(record.status)
                    || FAILED.equals(record.status) || CANCELLED.equals(record.status)) continue;
            try (Cursor cursor = manager.query(new DownloadManager.Query().setFilterById(record.systemId))) {
                if (cursor == null || !cursor.moveToFirst()) continue;
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                long now = System.currentTimeMillis();
                long previousBytes = record.bytes;
                long previousAt = record.updatedAt > 0L ? record.updatedAt : now;
                record.bytes = cursor.getLong(cursor.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                record.total = cursor.getLong(cursor.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                long elapsed = Math.max(1L, now - previousAt);
                long delta = Math.max(0L, record.bytes - previousBytes);
                record.bytesPerSecond = smoothRate(
                                record.bytesPerSecond,
                                delta * 1000L / elapsed);
                record.updatedAt = now;
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    Uri uri = manager.getUriForDownloadedFile(record.systemId);
                    record.contentUri = uri == null ? "" : uri.toString();
                    record.status = COMPLETE;
                    record.bytesPerSecond = 0L;
                } else if (status == DownloadManager.STATUS_FAILED) {
                    record.status = FAILED;
                    record.bytesPerSecond = 0L;
                    record.error = "Descarga rechazada por Android";
                } else if (status == DownloadManager.STATUS_PAUSED) {
                    record.status = QUEUED;
                } else {
                    record.status = DOWNLOADING;
                }
                changed = true;
            } catch (Exception ignored) { }
        }
        if (changed) synchronized (LOCK) { write(app, records); }
    }


    private static long smoothRate(long previous, long instant) {
        if (instant <= 0L) return previous;
        if (previous <= 0L) return instant;
        return (previous * 3L + instant * 2L) / 5L;
    }

    private static String nameFromUrl(String url) {
        try {
            String segment = Uri.parse(url).getLastPathSegment();
            if (segment != null && !segment.trim().isEmpty()) {
                return sanitize(Uri.decode(segment));
            }
        } catch (Exception ignored) { }
        return "descarga-" + System.currentTimeMillis();
    }

    private static String ensureExtension(String name, String mime) {
        String clean = sanitize(name);
        if (clean.contains(".")) return clean;
        String extension = MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(mime);
        return extension == null || extension.isEmpty()
                ? clean : clean + "." + extension;
    }

    private static String deriveName(WebResponse response) {
        String disposition = header(response.headers, "content-disposition");
        String fromHeader = filenameFromDisposition(disposition);
        if (!fromHeader.isEmpty()) return sanitize(fromHeader);
        try {
            String segment = Uri.parse(response.uri).getLastPathSegment();
            if (segment != null && !segment.trim().isEmpty()) return sanitize(Uri.decode(segment));
        } catch (Exception ignored) { }
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(
                header(response.headers, "content-type"));
        return "descarga-" + System.currentTimeMillis() + (extension == null ? "" : "." + extension);
    }

    private static String uniqueDisplayName(Context app, String requested) {
        String clean = sanitize(requested);
        List<Record> records = read(app);
        String candidate = clean;
        int dot = clean.lastIndexOf('.');
        String base = dot > 0 ? clean.substring(0, dot) : clean;
        String ext = dot > 0 ? clean.substring(dot) : "";
        int index = 2;
        boolean exists = true;
        while (exists) {
            exists = false;
            for (Record record : records) {
                if (candidate.equalsIgnoreCase(record.name)) { exists = true; break; }
            }
            if (exists) candidate = base + " (" + index++ + ")" + ext;
        }
        return candidate;
    }

    private static String filenameFromDisposition(String value) {
        if (value == null) return "";
        for (String part : value.split(";")) {
            String item = part.trim();
            if (item.toLowerCase(Locale.ROOT).startsWith("filename=")) {
                return item.substring(item.indexOf('=') + 1).replace("\"", "").trim();
            }
        }
        return "";
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers == null) return "";
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) return entry.getValue() == null ? "" : entry.getValue();
        }
        return "";
    }

    private static String guessMime(String source) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(source == null ? "" : source);
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return mime == null ? "application/octet-stream" : mime;
    }

    private static String sanitize(String value) {
        String clean = value == null ? "archivo" : value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return clean.isEmpty() ? "archivo" : clean;
    }

    private static long parseLong(String value) {
        try { return Long.parseLong(value); } catch (Exception ignored) { return -1L; }
    }

    private static Record find(Context app, String id) {
        synchronized (LOCK) {
            for (Record record : read(app)) if (id.equals(record.id)) return record;
        }
        return null;
    }

    private static void upsert(Context app, Record record) {
        synchronized (LOCK) {
            List<Record> records = read(app);
            records.removeIf(item -> record.id.equals(item.id));
            records.add(0, record);
            while (records.size() > 40) records.remove(records.size() - 1);
            write(app, records);
        }
    }

    private static List<Record> read(Context app) {
        List<Record> records = new ArrayList<>();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) records.add(Record.fromJson(array.getJSONObject(i)));
        } catch (Exception ignored) { }
        return records;
    }

    private static void write(Context app, List<Record> records) {
        JSONArray array = new JSONArray();
        for (Record record : records) array.put(record.toJson());
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, array.toString()).apply();
    }
}
