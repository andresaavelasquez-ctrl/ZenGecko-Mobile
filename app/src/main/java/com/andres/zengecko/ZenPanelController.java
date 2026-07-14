package com.andres.zengecko;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.andres.zengecko.model.BrowserTab;

public final class ZenPanelController {
    public static final String PREFS_UI = "zen_ui_prefs";
    public static final String KEY_ANIMATIONS = "animations";
    public static final String KEY_QUICK_NEW_TAB = "quick_new_tab";
    public static final String KEY_DOWNLOADS_METERED = "downloads_metered";
    public static final String PREFS_PROFILES = "zen_profiles";
    public static final String KEY_ACTIVE_PROFILE = "active_profile";
    public static final String KEY_PROFILES = "profiles";

    private static PopupWindow activePopup;

    private ZenPanelController() { }

    public static boolean animationsEnabled(Context context) {
        return context.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE)
                .getBoolean(KEY_ANIMATIONS, true);
    }

    public static boolean quickAccessOpensNewTab(Context context) {
        return context.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE)
                .getBoolean(KEY_QUICK_NEW_TAB, false);
    }

    public static boolean downloadsAllowedOnMetered(Context context) {
        return context.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE)
                .getBoolean(KEY_DOWNLOADS_METERED, true);
    }

    public static String activeProfile(Context context) {
        return context.getSharedPreferences(PREFS_PROFILES, Context.MODE_PRIVATE)
                .getString(KEY_ACTIVE_PROFILE, "Personal");
    }

    public static void showSettings(Activity activity, BrowserRepository browser) {
        LinearLayout content = column(activity);

        content.addView(section(activity, "MOTOR DE BÚSQUEDA"));
        for (SearchEngine engine : SearchEngine.values()) {
            boolean selected = engine == browser.getSearchEngine();
            TextView row = actionRow(activity,
                    (selected ? "●  " : "○  ") + engine.displayName,
                    selected ? R.color.zen_text : R.color.zen_muted);
            row.setOnClickListener(v -> {
                browser.setSearchEngine(engine);
                dismiss();
                showSettings(activity, browser);
            });
            content.addView(row, rowParams(activity, 48));
        }

        content.addView(section(activity, "INTERFAZ Y RENDIMIENTO"));
        SharedPreferences ui = activity.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE);
        content.addView(toggle(activity, "Animaciones suaves",
                "Desactívalas para priorizar la respuesta inmediata.",
                KEY_ANIMATIONS, true, ui));
        content.addView(toggle(activity, "Accesos rápidos en pestaña nueva",
                "Conserva la página actual al abrir un acceso del inicio o del panel.",
                KEY_QUICK_NEW_TAB, false, ui));

        content.addView(section(activity, "DESCARGAS"));
        content.addView(toggle(activity, "Permitir datos móviles",
                "Autoriza a Android DownloadManager a usar redes medidas.",
                KEY_DOWNLOADS_METERED, true, ui));

        content.addView(section(activity, "DATOS LOCALES"));
        TextView clearRecent = actionRow(activity, "Borrar direcciones recientes", R.color.zen_text);
        clearRecent.setOnClickListener(v -> {
            browser.clearRecentUrls();
            Toast.makeText(activity, "Direcciones recientes eliminadas", Toast.LENGTH_SHORT).show();
        });
        content.addView(clearRecent, rowParams(activity, 48));

        content.addView(section(activity, "VERSIÓN"));
        content.addView(infoCard(activity, "ZenGecko " + BuildConfig.VERSION_NAME
                + "\nGeckoView · interfaz AMOLED · colección única"));

        showPanel(activity, "Configuración", R.drawable.ic_settings, content);
    }

    public static void showProfiles(Activity activity) {
        LinearLayout content = column(activity);
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_PROFILES, Context.MODE_PRIVATE);
        Set<String> stored = new HashSet<>(prefs.getStringSet(KEY_PROFILES, new HashSet<>()));
        if (stored.isEmpty()) {
            stored.add("Personal");
            stored.add("Invitado");
            prefs.edit().putStringSet(KEY_PROFILES, stored).apply();
        }
        String active = activeProfile(activity);

        content.addView(infoCard(activity,
                "Los perfiles son locales y cambian la identidad visible del navegador. "
                + "Las pestañas todavía permanecen compartidas en esta etapa."));

        List<String> ordered = new ArrayList<>(stored);
        ordered.sort(String.CASE_INSENSITIVE_ORDER);
        for (String name : ordered) {
            TextView row = actionRow(activity,
                    (name.equals(active) ? "●  " : "○  ") + name,
                    name.equals(active) ? R.color.zen_text : R.color.zen_muted);
            row.setOnClickListener(v -> {
                prefs.edit().putString(KEY_ACTIVE_PROFILE, name).apply();
                dismiss();
                Toast.makeText(activity, "Perfil activo: " + name, Toast.LENGTH_SHORT).show();
            });
            content.addView(row, rowParams(activity, 48));
        }

        TextView add = actionRow(activity, "＋  Crear perfil local", R.color.zen_accent);
        add.setOnClickListener(v -> promptNewProfile(activity, stored));
        content.addView(add, rowParams(activity, 48));

        showPanel(activity, "Perfiles", R.drawable.ic_profile, content);
    }

    private static void promptNewProfile(Activity activity, Set<String> current) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint("Nombre del perfil");
        input.setTextColor(activity.getColor(R.color.zen_text));
        input.setHintTextColor(activity.getColor(R.color.zen_muted));
        input.setBackgroundResource(R.drawable.bg_address);
        input.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);

        LinearLayout wrapper = new LinearLayout(activity);
        wrapper.setPadding(dp(activity, 20), dp(activity, 8), dp(activity, 20), 0);
        wrapper.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 50)));

        new AlertDialog.Builder(activity)
                .setTitle("Nuevo perfil")
                .setView(wrapper)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Crear", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    Set<String> next = new HashSet<>(current);
                    next.add(name);
                    activity.getSharedPreferences(PREFS_PROFILES, Context.MODE_PRIVATE)
                            .edit()
                            .putStringSet(KEY_PROFILES, next)
                            .putString(KEY_ACTIVE_PROFILE, name)
                            .apply();
                    dismiss();
                    Toast.makeText(activity, "Perfil creado: " + name, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    public static void showDownloads(Activity activity) {
        LinearLayout content = column(activity);
        List<DownloadStore.Record> records = DownloadStore.list(activity);

        TextView androidDownloads = actionRow(activity,
                "Abrir carpeta de descargas de Android", R.color.zen_accent);
        androidDownloads.setOnClickListener(v -> {
            try {
                activity.startActivity(new Intent("android.intent.action.VIEW_DOWNLOADS"));
            } catch (Exception error) {
                Toast.makeText(activity, "Android no expuso la carpeta de descargas",
                        Toast.LENGTH_SHORT).show();
            }
        });
        content.addView(androidDownloads, rowParams(activity, 48));

        if (records.isEmpty()) {
            content.addView(infoCard(activity, "Todavía no hay descargas."));
        } else {
            for (DownloadStore.Record record : records) {
                content.addView(downloadRow(activity, record));
            }
        }
        showPanel(activity, "Descargas", R.drawable.ic_downloads, content);
    }

    private static View downloadRow(Activity activity, DownloadStore.Record record) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 12), dp(activity, 9), dp(activity, 12), dp(activity, 9));
        card.setBackgroundResource(R.drawable.bg_panel_card);

        TextView name = text(activity, record.name, 14, R.color.zen_text);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(name);

        TextView status = text(activity, downloadStatus(record), 11, R.color.zen_muted);
        status.setPadding(0, dp(activity, 4), 0, dp(activity, 5));
        card.addView(status);

        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.END);
        if (DownloadStore.COMPLETE.equals(record.status)) {
            actions.addView(smallAction(activity, "Abrir", v -> openDownload(activity, record)));
        } else if (DownloadStore.DOWNLOADING.equals(record.status)
                || DownloadStore.QUEUED.equals(record.status)) {
            actions.addView(smallAction(activity, "Cancelar", v -> {
                DownloadStore.cancel(activity, record.id);
                dismiss();
                showDownloads(activity);
            }));
        } else {
            actions.addView(smallAction(activity, "Reintentar", v -> {
                DownloadStore.retry(activity, record.id);
                dismiss();
                showDownloads(activity);
            }));
        }
        actions.addView(smallAction(activity, "Quitar", v -> {
            DownloadStore.remove(activity, record.id);
            dismiss();
            showDownloads(activity);
        }));
        card.addView(actions);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(activity, 7));
        card.setLayoutParams(params);
        return card;
    }

    public static void showPrivacy(Activity activity, BrowserRepository browser) {
        LinearLayout content = column(activity);
        content.addView(infoCard(activity,
                "Privacidad actual\n"
                + "• Solicitud de permisos por sitio.\n"
                + "• Modo AMOLED sin telemetría propia.\n"
                + "• Historial y perfiles guardados únicamente en el dispositivo."));

        TextView clearRecent = actionRow(activity,
                "Borrar direcciones recientes", R.color.zen_text);
        clearRecent.setOnClickListener(v -> {
            browser.clearRecentUrls();
            Toast.makeText(activity, "Direcciones recientes eliminadas", Toast.LENGTH_SHORT).show();
        });
        content.addView(clearRecent, rowParams(activity, 48));

        TextView note = infoCard(activity,
                "El bloqueador de anuncios y rastreadores se incorporará en una versión posterior, "
                + "después de estabilizar descargas, perfiles y navegación.");
        content.addView(note);

        showPanel(activity, "Privacidad", R.drawable.ic_shield, content);
    }

    public static void showBookmarks(Activity activity, BrowserRepository browser) {
        LinearLayout content = column(activity);
        List<BrowserTab> essentials = new ArrayList<>();
        for (BrowserTab tab : browser.getVisibleTabs()) {
            if (tab.essential) essentials.add(tab);
        }
        if (essentials.isEmpty()) {
            content.addView(infoCard(activity,
                    "No hay esenciales. Mantén pulsada una pestaña desde el menú lateral "
                    + "para convertirla en esencial."));
        } else {
            for (BrowserTab tab : essentials) {
                String title = tab.title == null || tab.title.trim().isEmpty()
                        ? "Nueva pestaña" : tab.title;
                TextView row = actionRow(activity, "★  " + title, R.color.zen_text);
                row.setOnClickListener(v -> {
                    browser.selectTab(tab.id);
                    dismiss();
                });
                content.addView(row, rowParams(activity, 48));
            }
        }
        showPanel(activity, "Esenciales", R.drawable.ic_star, content);
    }

    private static void showPanel(Activity activity, String title, int iconRes, View content) {
        dismiss();

        FrameLayout overlay = new FrameLayout(activity);
        overlay.setBackgroundColor(Color.TRANSPARENT);

        View scrim = new View(activity);
        scrim.setBackgroundColor(0xB8000000);
        overlay.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 14));
        panel.setBackgroundResource(R.drawable.bg_sidebar_panel);
        panel.setElevation(dp(activity, 18));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(activity.getColor(R.color.zen_text)));
        header.addView(icon, new LinearLayout.LayoutParams(dp(activity, 24), dp(activity, 24)));

        TextView heading = text(activity, title, 20, R.color.zen_text);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                0, dp(activity, 44), 1f);
        headingParams.setMargins(dp(activity, 10), 0, 0, 0);
        header.addView(heading, headingParams);

        TextView close = text(activity, "×", 27, R.color.zen_muted);
        close.setGravity(Gravity.CENTER);
        close.setBackgroundResource(R.drawable.bg_button);
        close.setOnClickListener(v -> dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 42)));
        panel.addView(header);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.addView(content);
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        int width = activity.getResources().getDisplayMetrics().widthPixels;
        int panelWidth = Math.min(dp(activity, 430), (int) (width * .94f));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                panelWidth, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
        overlay.addView(panel, panelParams);

        scrim.setOnClickListener(v -> dismiss());
        activePopup = new PopupWindow(overlay,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                true);
        activePopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        activePopup.setOutsideTouchable(true);
        activePopup.setClippingEnabled(false);
        activePopup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        activePopup.showAtLocation(activity.getWindow().getDecorView(),
                Gravity.START | Gravity.TOP, 0, 0);

        if (animationsEnabled(activity)) {
            scrim.setAlpha(0f);
            scrim.animate().alpha(1f).setDuration(120L).start();
            panel.setTranslationX(-panelWidth);
            panel.animate().translationX(0f).setDuration(170L)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
    }

    public static void dismiss() {
        PopupWindow popup = activePopup;
        activePopup = null;
        if (popup != null && popup.isShowing()) popup.dismiss();
    }

    private static CheckBox toggle(Activity activity, String title, String description,
            String key, boolean defaultValue, SharedPreferences prefs) {
        CheckBox box = new CheckBox(activity);
        box.setText(title + "\n" + description);
        box.setTextColor(activity.getColor(R.color.zen_text));
        box.setTextSize(13);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(activity, 12), dp(activity, 7), dp(activity, 12), dp(activity, 7));
        box.setButtonTintList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{
                        activity.getColor(R.color.zen_accent),
                        activity.getColor(R.color.zen_muted)
                }));
        box.setBackgroundResource(R.drawable.bg_panel_card);
        box.setChecked(prefs.getBoolean(key, defaultValue));
        box.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(key, checked).apply());
        LinearLayout.LayoutParams params = rowParams(activity, 64);
        params.setMargins(0, 0, 0, dp(activity, 6));
        box.setLayoutParams(params);
        return box;
    }

    private static TextView section(Activity activity, String value) {
        TextView view = text(activity, value, 10, R.color.zen_muted);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setLetterSpacing(.13f);
        view.setPadding(dp(activity, 4), dp(activity, 15), 0, dp(activity, 7));
        return view;
    }

    private static TextView infoCard(Activity activity, String value) {
        TextView view = text(activity, value, 13, R.color.zen_muted);
        view.setPadding(dp(activity, 13), dp(activity, 11),
                dp(activity, 13), dp(activity, 11));
        view.setBackgroundResource(R.drawable.bg_panel_card);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(activity, 7));
        view.setLayoutParams(params);
        return view;
    }

    private static TextView actionRow(Activity activity, String value, int color) {
        TextView view = text(activity, value, 14, color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setSingleLine(true);
        view.setPadding(dp(activity, 13), 0, dp(activity, 13), 0);
        view.setBackgroundResource(R.drawable.bg_panel_card);
        return view;
    }

    private static TextView smallAction(Activity activity, String value, View.OnClickListener listener) {
        TextView view = text(activity, value, 11, R.color.zen_accent);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(activity, 10), 0, dp(activity, 10), 0);
        view.setBackgroundResource(R.drawable.bg_button);
        view.setOnClickListener(listener);
        return view;
    }

    private static LinearLayout column(Activity activity) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(activity, 3), 0, dp(activity, 8));
        return content;
    }

    private static LinearLayout.LayoutParams rowParams(Activity activity, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, height));
        params.setMargins(0, 0, 0, dp(activity, 6));
        return params;
    }

    private static TextView text(Activity activity, String value, float size, int colorRes) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(activity.getColor(colorRes));
        view.setFontFeatureSettings("kern");
        return view;
    }

    private static String downloadStatus(DownloadStore.Record record) {
        if (DownloadStore.COMPLETE.equals(record.status)) {
            return "Completada · " + readableSize(record.bytes);
        }
        if (DownloadStore.CANCELLED.equals(record.status)) return "Cancelada";
        if (DownloadStore.FAILED.equals(record.status)) {
            return "Falló · " + (record.error == null ? "" : record.error);
        }
        if (record.total > 0) {
            int progress = (int) Math.min(100L, record.bytes * 100L / record.total);
            return "Descargando · " + progress + "% · "
                    + readableSize(record.bytes) + " / " + readableSize(record.total);
        }
        return "Descargando · " + readableSize(record.bytes);
    }

    private static String readableSize(long bytes) {
        if (bytes < 0) return "tamaño desconocido";
        if (bytes >= 1024L * 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f GB", bytes / 1073741824f);
        }
        if (bytes >= 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / 1048576f);
        }
        if (bytes >= 1024L) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024f);
        }
        return bytes + " B";
    }

    private static void openDownload(Activity activity, DownloadStore.Record record) {
        try {
            if (record.contentUri == null || record.contentUri.isEmpty()) return;
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(record.contentUri), record.mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, "Abrir descarga"));
        } catch (Exception error) {
            try {
                activity.startActivity(new Intent("android.intent.action.VIEW_DOWNLOADS"));
            } catch (Exception ignored) {
                Toast.makeText(activity, "No se encontró una aplicación compatible",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
