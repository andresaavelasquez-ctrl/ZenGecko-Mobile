package com.andres.zengecko;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;

/**
 * Connects GeckoView file/media permission callbacks with Android UI.
 * BrowserRepository owns GeckoSession delegates; MainActivity owns activity results.
 */
public final class ZenWebCapabilityBridge {
    private static final String TAG = "ZenGecko/WebCapabilities";
    private static final int REQUEST_FILE = 8230;
    private static final int REQUEST_ANDROID_PERMISSIONS = 8231;

    private static WeakReference<Activity> activityRef = new WeakReference<>(null);
    private static GeckoSession.PromptDelegate.FilePrompt pendingFilePrompt;
    private static GeckoResult<GeckoSession.PromptDelegate.PromptResponse> pendingFileResult;
    private static Uri pendingCaptureUri;
    private static File pendingCaptureFile;
    private static GeckoSession.PermissionDelegate.Callback pendingPermissionCallback;

    private ZenWebCapabilityBridge() { }

    public static void attach(Activity activity) {
        activityRef = new WeakReference<>(activity);
    }

    public static void detach(Activity activity) {
        Activity current = activityRef.get();
        if (current == activity) activityRef.clear();
        rejectPendingAndroidPermission();
        dismissPendingFile();
    }

    private static Activity activity() {
        Activity activity = activityRef.get();
        if (activity == null || activity.isFinishing()) return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                && activity.isDestroyed()) return null;
        return activity;
    }

    public static GeckoSession.PromptDelegate promptDelegate(Context appContext) {
        return new GeckoSession.PromptDelegate() {
            @Override public GeckoResult<PromptResponse> onFilePrompt(
                    GeckoSession session, FilePrompt prompt) {
                Activity activity = activity();
                if (activity == null || prompt == null) {
                    return GeckoResult.fromValue(prompt == null ? null : prompt.dismiss());
                }
                dismissPendingFile();
                pendingFilePrompt = prompt;
                pendingFileResult = new GeckoResult<>();
                launchFileChooser(activity, prompt);
                return pendingFileResult;
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
            GeckoSession.PromptDelegate.FilePrompt prompt) {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        String[] mimeTypes = normalizedMimeTypes(prompt.mimeTypes);
        picker.setType(mimeTypes.length == 1 ? mimeTypes[0] : "*/*");
        if (mimeTypes.length > 1) picker.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
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
            activity.startActivityForResult(launch, REQUEST_FILE);
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to open Android file chooser", error);
            Toast.makeText(activity, "No se pudo abrir el selector de archivos",
                    Toast.LENGTH_SHORT).show();
            dismissPendingFile();
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
            return camera.resolveActivity(activity.getPackageManager()) == null ? null : camera;
        } catch (IOException | RuntimeException error) {
            Log.w(TAG, "Camera capture intent unavailable", error);
            pendingCaptureFile = null;
            pendingCaptureUri = null;
            return null;
        }
    }

    private static boolean requestsImage(GeckoSession.PromptDelegate.FilePrompt prompt) {
        if (prompt == null || prompt.mimeTypes == null || prompt.mimeTypes.length == 0) {
            return false;
        }
        for (String type : prompt.mimeTypes) {
            if (type != null && type.toLowerCase().startsWith("image/")) return true;
        }
        return false;
    }

    private static String[] normalizedMimeTypes(String[] values) {
        Set<String> result = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (value == null) continue;
                String clean = value.trim();
                if (!clean.isEmpty()) result.add(clean);
            }
        }
        if (result.isEmpty()) result.add("*/*");
        return result.toArray(new String[0]);
    }

    public static boolean onActivityResult(
            Activity activity,
            int requestCode,
            int resultCode,
            Intent data) {
        if (requestCode != REQUEST_FILE) return false;
        GeckoSession.PromptDelegate.FilePrompt prompt = pendingFilePrompt;
        GeckoResult<GeckoSession.PromptDelegate.PromptResponse> result = pendingFileResult;
        pendingFilePrompt = null;
        pendingFileResult = null;

        if (prompt == null || result == null) {
            clearCapture();
            return true;
        }

        if (resultCode != Activity.RESULT_OK) {
            result.complete(prompt.dismiss());
            clearCapture();
            return true;
        }

        List<Uri> uris = new ArrayList<>();
        if (data != null) {
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int index = 0; index < clip.getItemCount(); index++) {
                    Uri uri = clip.getItemAt(index).getUri();
                    if (uri != null && !uris.contains(uri)) uris.add(uri);
                }
            }
            Uri single = data.getData();
            if (single != null && !uris.contains(single)) uris.add(single);
        }
        if (uris.isEmpty() && pendingCaptureUri != null
                && pendingCaptureFile != null && pendingCaptureFile.length() > 0L) {
            uris.add(pendingCaptureUri);
        }

        if (uris.isEmpty()) {
            result.complete(prompt.dismiss());
            clearCapture();
            return true;
        }

        for (Uri uri : uris) {
            try {
                activity.getContentResolver().takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) { }
        }
        try {
            if (uris.size() == 1) {
                result.complete(prompt.confirm(activity.getApplicationContext(), uris.get(0)));
            } else {
                result.complete(prompt.confirm(
                        activity.getApplicationContext(), uris.toArray(new Uri[0])));
            }
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to return selected files to GeckoView", error);
            result.complete(prompt.dismiss());
        }
        clearCapture();
        return true;
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

    private static void rejectPendingAndroidPermission() {
        GeckoSession.PermissionDelegate.Callback callback = pendingPermissionCallback;
        pendingPermissionCallback = null;
        if (callback != null) {
            try { callback.reject(); } catch (RuntimeException ignored) { }
        }
    }

    private static void dismissPendingFile() {
        GeckoSession.PromptDelegate.FilePrompt prompt = pendingFilePrompt;
        GeckoResult<GeckoSession.PromptDelegate.PromptResponse> result = pendingFileResult;
        pendingFilePrompt = null;
        pendingFileResult = null;
        if (prompt != null && result != null) {
            try { result.complete(prompt.dismiss()); } catch (RuntimeException ignored) { }
        }
        clearCapture();
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
