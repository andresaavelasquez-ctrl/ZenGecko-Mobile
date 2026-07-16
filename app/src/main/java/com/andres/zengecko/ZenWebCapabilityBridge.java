package com.andres.zengecko;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;

/**
 * Bridges GeckoView prompts with Android without replacing or reloading the
 * originating GeckoSession. File requests survive a host Activity recreation
 * while the system picker is open and are completed against the original
 * FilePrompt.
 */
public final class ZenWebCapabilityBridge {
    private static final String TAG = "ZenGecko/WebCapabilities";
    private static final int REQUEST_FILE = 8230;
    private static final int REQUEST_ANDROID_PERMISSIONS = 8231;
    private static final long FILE_TIMEOUT_MS = 5L * 60L * 1000L;
    private static final long RECOVERY_GRACE_MS = 1350L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<Activity> activityRef = new WeakReference<>(null);
    private static PendingFileRequest pendingFileRequest;
    private static Uri pendingCaptureUri;
    private static File pendingCaptureFile;
    private static GeckoSession.PermissionDelegate.Callback pendingPermissionCallback;
    private static long suppressRecoveryUntilElapsed;
    private static int requestSequence;
    private static Runnable fileTimeout;

    private ZenWebCapabilityBridge() { }

    private static final class PendingFileRequest {
        final int id;
        final GeckoSession session;
        final GeckoSession.PromptDelegate.FilePrompt prompt;
        final GeckoResult<GeckoSession.PromptDelegate.PromptResponse> result;
        final long createdAt;

        PendingFileRequest(
                int id,
                GeckoSession session,
                GeckoSession.PromptDelegate.FilePrompt prompt,
                GeckoResult<GeckoSession.PromptDelegate.PromptResponse> result) {
            this.id = id;
            this.session = session;
            this.prompt = prompt;
            this.result = result;
            createdAt = SystemClock.elapsedRealtime();
        }
    }

    public static void attach(Activity activity) {
        activityRef = new WeakReference<>(activity);
        Log.i(TAG, "HOST_ATTACHED pending=" + (pendingFileRequest != null));
    }

    public static void detach(Activity activity) {
        Activity current = activityRef.get();
        if (current == activity) activityRef.clear();
        rejectPendingAndroidPermission();
        // Do not dismiss a file prompt merely because Android recreated the
        // host Activity while its document picker is in front.
        if (pendingFileRequest == null) clearCapture();
        Log.i(TAG, "HOST_DETACHED filePickerInFlight=" + (pendingFileRequest != null));
    }

    private static Activity activity() {
        Activity activity = activityRef.get();
        if (activity == null || activity.isFinishing()) return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                && activity.isDestroyed()) return null;
        return activity;
    }

    public static boolean isExternalUiInFlight() {
        return pendingFileRequest != null;
    }

    public static boolean shouldSuppressVisualRecovery() {
        return pendingFileRequest != null
                || SystemClock.elapsedRealtime() < suppressRecoveryUntilElapsed;
    }

    private static void beginExternalUiGrace() {
        suppressRecoveryUntilElapsed = Math.max(
                suppressRecoveryUntilElapsed,
                SystemClock.elapsedRealtime() + RECOVERY_GRACE_MS);
    }

    public static GeckoSession.PromptDelegate promptDelegate(Context appContext) {
        return new GeckoSession.PromptDelegate() {
            @Override public GeckoResult<PromptResponse> onFilePrompt(
                    GeckoSession session, FilePrompt prompt) {
                Activity activity = activity();
                if (activity == null || prompt == null) {
                    return GeckoResult.fromValue(prompt == null ? null : prompt.dismiss());
                }

                dismissPendingFile("replaced-by-new-request");
                GeckoResult<PromptResponse> result = new GeckoResult<>();
                PendingFileRequest request = new PendingFileRequest(
                        ++requestSequence, session, prompt, result);
                pendingFileRequest = request;
                beginExternalUiGrace();
                Log.i(TAG, "FILE_PROMPT_CREATED id=" + request.id
                        + " session=" + sessionIdentity(session)
                        + " multiple=" + (prompt.type == FilePrompt.Type.MULTIPLE));
                launchFileChooser(activity, request);
                scheduleFileTimeout(request.id);
                return result;
            }
        };
    }

    public static GeckoSession.PermissionDelegate permissionDelegate(Context appContext) {
        return new GeckoSession.PermissionDelegate() {
            @Override public void onAndroidPermissionsRequest(
                    GeckoSession session,
                    String[] permissions,
                    Callback callback) {
                Activity activity = activity();
                if (callback == null) return;
                if (activity == null) {
                    callback.reject();
                    return;
                }
                if (permissions == null || permissions.length == 0) {
                    callback.grant();
                    return;
                }

                List<String> missing = new ArrayList<>();
                for (String permission : permissions) {
                    if (permission == null || permission.trim().isEmpty()) continue;
                    if (ContextCompat.checkSelfPermission(activity, permission)
                            != PackageManager.PERMISSION_GRANTED) {
                        missing.add(permission);
                    }
                }
                if (missing.isEmpty()) {
                    callback.grant();
                    return;
                }

                rejectPendingAndroidPermission();
                pendingPermissionCallback = callback;
                try {
                    activity.requestPermissions(
                            missing.toArray(new String[0]),
                            REQUEST_ANDROID_PERMISSIONS);
                } catch (RuntimeException error) {
                    Log.e(TAG, "Unable to request Android permissions", error);
                    rejectPendingAndroidPermission();
                }
            }

            @Override public GeckoResult<Integer> onContentPermissionRequest(
                    GeckoSession session,
                    ContentPermission permission) {
                if (permission == null) {
                    return GeckoResult.fromValue(ContentPermission.VALUE_DENY);
                }
                int type = permission.permission;
                boolean autoplay = ZenPanelController.autoplayEnabled(appContext)
                        && (type == PERMISSION_AUTOPLAY_AUDIBLE
                        || type == PERMISSION_AUTOPLAY_INAUDIBLE);
                if (autoplay || type == PERMISSION_PERSISTENT_STORAGE) {
                    return GeckoResult.fromValue(ContentPermission.VALUE_ALLOW);
                }

                Activity activity = activity();
                if (activity == null) {
                    return GeckoResult.fromValue(ContentPermission.VALUE_DENY);
                }
                GeckoResult<Integer> result = new GeckoResult<>();
                String capability = type == PERMISSION_GEOLOCATION
                        ? "tu ubicación"
                        : type == PERMISSION_DESKTOP_NOTIFICATION
                        ? "enviar notificaciones"
                        : "esta función del navegador";
                try {
                    new AlertDialog.Builder(activity)
                            .setTitle("Permiso del sitio")
                            .setMessage("¿Permitir que esta página use " + capability + "?")
                            .setNegativeButton("Bloquear", (dialog, which) ->
                                    result.complete(ContentPermission.VALUE_DENY))
                            .setPositiveButton("Permitir", (dialog, which) ->
                                    result.complete(ContentPermission.VALUE_ALLOW))
                            .setOnCancelListener(dialog ->
                                    result.complete(ContentPermission.VALUE_DENY))
                            .show();
                } catch (RuntimeException error) {
                    result.complete(ContentPermission.VALUE_DENY);
                }
                return result;
            }

            @Override public void onMediaPermissionRequest(
                    GeckoSession session,
                    String uri,
                    MediaSource[] video,
                    MediaSource[] audio,
                    MediaCallback callback) {
                Activity activity = activity();
                if (activity == null || callback == null) {
                    if (callback != null) callback.reject();
                    return;
                }
                MediaSource selectedVideo = firstUsable(video);
                MediaSource selectedAudio = firstUsable(audio);
                if (selectedVideo == null && selectedAudio == null) {
                    callback.reject();
                    return;
                }

                StringBuilder request = new StringBuilder();
                if (selectedVideo != null) request.append("cámara");
                if (selectedAudio != null) {
                    if (request.length() > 0) request.append(" y ");
                    request.append("micrófono");
                }
                String site = host(uri);
                try {
                    new AlertDialog.Builder(activity)
                            .setTitle("Acceso multimedia")
                            .setMessage((site.isEmpty() ? "Esta página" : site)
                                    + " solicita acceso a " + request + ".")
                            .setNegativeButton("Bloquear", (dialog, which) -> callback.reject())
                            .setPositiveButton("Permitir una vez", (dialog, which) ->
                                    callback.grant(selectedVideo, selectedAudio))
                            .setOnCancelListener(dialog -> callback.reject())
                            .show();
                } catch (RuntimeException error) {
                    callback.reject();
                }
            }
        };
    }

    private static GeckoSession.PermissionDelegate.MediaSource firstUsable(
            GeckoSession.PermissionDelegate.MediaSource[] sources) {
        if (sources == null || sources.length == 0) return null;
        for (GeckoSession.PermissionDelegate.MediaSource source : sources) {
            if (source != null) return source;
        }
        return null;
    }

    private static String host(String value) {
        if (value == null) return "";
        try {
            String host = Uri.parse(value).getHost();
            return host == null ? "" : host;
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static void launchFileChooser(
            Activity activity,
            PendingFileRequest request) {
        GeckoSession.PromptDelegate.FilePrompt prompt = request.prompt;
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        String[] mimeTypes = normalizedMimeTypes(prompt.mimeTypes);
        String primaryType = primaryMimeType(mimeTypes);
        picker.setType(primaryType);
        if (mimeTypes.length > 1 && !containsWildcard(mimeTypes)) {
            picker.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        }
        picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE);

        Intent launch = picker;
        if (requestsImage(prompt) && prompt.capture
                != GeckoSession.PromptDelegate.FilePrompt.Capture.NONE) {
            Intent camera = createCameraIntent(activity);
            if (camera != null) {
                launch = Intent.createChooser(picker, "Seleccionar archivo");
                launch.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] { camera });
            }
        }

        try {
            Log.i(TAG, "FILE_PICKER_OPENED id=" + request.id
                    + " type=" + primaryType
                    + " accepted=" + java.util.Arrays.toString(mimeTypes));
            activity.startActivityForResult(launch, REQUEST_FILE);
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to open Android file chooser", error);
            Toast.makeText(activity, "No se pudo abrir el selector de archivos",
                    Toast.LENGTH_SHORT).show();
            dismissPendingFile("picker-launch-failed");
        }
    }

    private static Intent createCameraIntent(Activity activity) {
        try {
            File directory = new File(activity.getCacheDir(), "web-capture");
            if (!directory.exists() && !directory.mkdirs()) return null;
            pendingCaptureFile = File.createTempFile("zen-capture-", ".jpg", directory);
            pendingCaptureUri = FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".files",
                    pendingCaptureFile);
            Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            camera.putExtra(MediaStore.EXTRA_OUTPUT, pendingCaptureUri);
            camera.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            return camera.resolveActivity(activity.getPackageManager()) == null
                    ? null : camera;
        } catch (IOException | RuntimeException error) {
            Log.w(TAG, "Camera capture intent unavailable", error);
            pendingCaptureFile = null;
            pendingCaptureUri = null;
            return null;
        }
    }

    private static boolean requestsImage(
            GeckoSession.PromptDelegate.FilePrompt prompt) {
        if (prompt == null || prompt.mimeTypes == null) return false;
        for (String raw : prompt.mimeTypes) {
            if (raw == null) continue;
            String value = raw.trim().toLowerCase(Locale.ROOT);
            if (value.startsWith("image/")
                    || value.endsWith(".jpg")
                    || value.endsWith(".jpeg")
                    || value.endsWith(".png")
                    || value.endsWith(".webp")) {
                return true;
            }
        }
        return false;
    }

    private static String[] normalizedMimeTypes(String[] values) {
        Set<String> result = new LinkedHashSet<>();
        if (values != null) {
            for (String raw : values) {
                if (raw == null) continue;
                for (String token : raw.split(",")) {
                    String clean = token.trim().toLowerCase(Locale.ROOT);
                    if (clean.isEmpty()) continue;
                    String mime = normalizeMimeToken(clean);
                    if (mime != null && !mime.isEmpty()) result.add(mime);
                }
            }
        }
        if (result.isEmpty()) result.add("*/*");
        if (result.contains("*/*")) return new String[] { "*/*" };
        return result.toArray(new String[0]);
    }

    private static String normalizeMimeToken(String value) {
        if ("*".equals(value) || "*/*".equals(value)) return "*/*";
        if (value.startsWith(".")) {
            String extension = value.substring(1);
            String mime = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(extension);
            return mime == null ? "*/*" : mime;
        }
        if (value.indexOf('/') > 0) return value;
        int dot = value.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < value.length()) {
            String mime = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(value.substring(dot + 1));
            return mime == null ? "*/*" : mime;
        }
        return "*/*";
    }

    private static String primaryMimeType(String[] values) {
        if (values == null || values.length == 0) return "*/*";
        if (values.length == 1) return values[0];
        String family = null;
        for (String value : values) {
            if (value == null || "*/*".equals(value)) return "*/*";
            int slash = value.indexOf('/');
            if (slash <= 0) return "*/*";
            String current = value.substring(0, slash);
            if (family == null) family = current;
            else if (!family.equals(current)) return "*/*";
        }
        return family == null ? "*/*" : family + "/*";
    }

    private static boolean containsWildcard(String[] values) {
        if (values == null) return true;
        for (String value : values) {
            if (value == null || value.contains("*")) return true;
        }
        return false;
    }

    public static boolean onActivityResult(
            Activity activity,
            int requestCode,
            int resultCode,
            Intent data) {
        if (requestCode != REQUEST_FILE) return false;
        PendingFileRequest request = pendingFileRequest;
        pendingFileRequest = null;
        cancelFileTimeout();
        beginExternalUiGrace();

        if (request == null) {
            Log.w(TAG, "FILE_RESULT_WITHOUT_REQUEST");
            clearCapture();
            return true;
        }

        Log.i(TAG, "FILE_RESULT_RECEIVED id=" + request.id
                + " resultCode=" + resultCode
                + " ageMs=" + (SystemClock.elapsedRealtime() - request.createdAt)
                + " session=" + sessionIdentity(request.session));

        if (resultCode != Activity.RESULT_OK) {
            completeDismiss(request, "cancelled");
            clearCapture();
            return true;
        }

        List<Uri> uris = collectUris(data);
        if (uris.isEmpty() && pendingCaptureUri != null
                && pendingCaptureFile != null && pendingCaptureFile.length() > 0L) {
            uris.add(pendingCaptureUri);
        }
        if (uris.isEmpty()) {
            completeDismiss(request, "empty-result");
            clearCapture();
            return true;
        }

        int grantFlags = data == null ? Intent.FLAG_GRANT_READ_URI_PERMISSION
                : data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        for (Uri uri : uris) {
            try {
                activity.getContentResolver().takePersistableUriPermission(
                        uri,
                        grantFlags == 0
                                ? Intent.FLAG_GRANT_READ_URI_PERMISSION
                                : grantFlags);
            } catch (SecurityException ignored) {
                // Some providers grant only a temporary URI permission.
            }
        }

        try {
            GeckoSession.PromptDelegate.PromptResponse response = uris.size() == 1
                    ? request.prompt.confirm(
                            activity.getApplicationContext(), uris.get(0))
                    : request.prompt.confirm(
                            activity.getApplicationContext(), uris.toArray(new Uri[0]));
            request.result.complete(response);
            Log.i(TAG, "FILE_RESULT_DELIVERED id=" + request.id
                    + " count=" + uris.size()
                    + " sessionOpen=" + isSessionOpen(request.session));
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to return selected files to GeckoView", error);
            completeDismiss(request, "delivery-failed");
        }
        clearCapture();
        return true;
    }

    private static List<Uri> collectUris(Intent data) {
        List<Uri> uris = new ArrayList<>();
        if (data == null) return uris;
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int index = 0; index < clip.getItemCount(); index++) {
                Uri uri = clip.getItemAt(index).getUri();
                if (uri != null && !uris.contains(uri)) uris.add(uri);
            }
        }
        Uri single = data.getData();
        if (single != null && !uris.contains(single)) uris.add(single);
        return uris;
    }

    public static boolean onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {
        if (requestCode != REQUEST_ANDROID_PERMISSIONS) return false;
        GeckoSession.PermissionDelegate.Callback callback = pendingPermissionCallback;
        pendingPermissionCallback = null;
        if (callback == null) return true;
        boolean granted = grantResults != null && grantResults.length > 0;
        if (granted) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
        }
        if (granted) callback.grant(); else callback.reject();
        return true;
    }

    private static void scheduleFileTimeout(int requestId) {
        cancelFileTimeout();
        fileTimeout = () -> {
            PendingFileRequest request = pendingFileRequest;
            if (request == null || request.id != requestId) return;
            Log.w(TAG, "FILE_PROMPT_TIMEOUT id=" + request.id);
            dismissPendingFile("timeout");
        };
        MAIN.postDelayed(fileTimeout, FILE_TIMEOUT_MS);
    }

    private static void cancelFileTimeout() {
        if (fileTimeout != null) MAIN.removeCallbacks(fileTimeout);
        fileTimeout = null;
    }

    private static void rejectPendingAndroidPermission() {
        GeckoSession.PermissionDelegate.Callback callback = pendingPermissionCallback;
        pendingPermissionCallback = null;
        if (callback != null) {
            try { callback.reject(); } catch (RuntimeException ignored) { }
        }
    }

    private static void dismissPendingFile(String reason) {
        PendingFileRequest request = pendingFileRequest;
        pendingFileRequest = null;
        cancelFileTimeout();
        if (request != null) completeDismiss(request, reason);
        clearCapture();
    }

    private static void completeDismiss(PendingFileRequest request, String reason) {
        if (request == null) return;
        try {
            request.result.complete(request.prompt.dismiss());
        } catch (RuntimeException ignored) { }
        Log.i(TAG, "FILE_PROMPT_CANCELLED id=" + request.id + " reason=" + reason);
    }

    private static boolean isSessionOpen(GeckoSession session) {
        if (session == null) return false;
        try { return session.isOpen(); }
        catch (RuntimeException ignored) { return false; }
    }

    private static String sessionIdentity(GeckoSession session) {
        return session == null ? "null"
                : Integer.toHexString(System.identityHashCode(session));
    }

    private static void clearCapture() {
        pendingCaptureUri = null;
        File file = pendingCaptureFile;
        pendingCaptureFile = null;
        if (file != null && file.exists() && file.length() == 0L) {
            try { file.delete(); } catch (RuntimeException ignored) { }
        }
    }
}
