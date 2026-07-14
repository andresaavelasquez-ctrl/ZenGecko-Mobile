package com.andres.zengecko;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.andres.zengecko.model.BrowserTab;

public final class ZenPanelController {
    private static final String PREFS_UI = "zen_ui_prefs";

    public static final String KEY_ANIMATIONS = "animations";
    public static final String KEY_QUICK_NEW_TAB = "quick_new_tab";
    public static final String KEY_DOWNLOADS_METERED = "downloads_metered";
    public static final String KEY_CONFIRM_DOWNLOAD = "confirm_download";
    public static final String KEY_DOWNLOAD_NOTICE = "download_notice";
    public static final String KEY_CONFIRM_EXIT = "confirm_exit";
    public static final String KEY_RESTORE_SESSION = "restore_session";
    public static final String KEY_SHOW_ADDRESS = "show_address";
    public static final String KEY_SHOW_PROGRESS = "show_progress";
    public static final String KEY_KEEP_AWAKE = "keep_awake";
    public static final String KEY_EDGE_SWIPE = "edge_swipe";
    public static final String KEY_SEARCH_SUGGESTIONS = "search_suggestions";
    public static final String KEY_SEARCH_TABS = "search_tabs";
    public static final String KEY_SAVE_HISTORY = "save_history";
    public static final String KEY_HISTORY_LIMIT = "history_limit";
    public static final String KEY_JAVASCRIPT = "javascript";
    public static final String KEY_AUTOPLAY = "autoplay";
    public static final String KEY_SWIPE_CLOSE = "swipe_close";
    public static final String KEY_TAB_CLOSE_BUTTON = "tab_close_button";
    public static final String KEY_PAGE_RECOVERY = "page_recovery";
    public static final String KEY_ECO_RENDER = "eco_render";
    public static final String KEY_HOME_URL = "home_url";
    public static final String KEY_HOME_BACKGROUND = "home_background";
    public static final String KEY_HOME_MOTION = "home_motion";
    public static final String KEY_KEY_SOUND = "mechanical_key_sound";
    public static final String KEY_KEY_HAPTICS = "mechanical_key_haptics";

    private static final String PREFS_PROFILES = "zen_profiles";
    private static final String KEY_ACTIVE_PROFILE = "active_profile";
    private static final String KEY_PROFILES = "profiles";

    private static PopupWindow activePopup;
    private static Runnable activeBackAction;

    private ZenPanelController() { }

    private static SharedPreferences ui(Context context) {
        return context.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE);
    }

    public static boolean animationsEnabled(Context context) {
        return ui(context).getBoolean(KEY_ANIMATIONS, true);
    }

    public static boolean quickAccessOpensNewTab(Context context) {
        return ui(context).getBoolean(KEY_QUICK_NEW_TAB, false);
    }

    public static boolean downloadsAllowedOnMetered(Context context) {
        return ui(context).getBoolean(KEY_DOWNLOADS_METERED, true);
    }

    public static boolean confirmDownloads(Context context) {
        return ui(context).getBoolean(KEY_CONFIRM_DOWNLOAD, false);
    }

    public static boolean downloadNotificationsEnabled(Context context) {
        return ui(context).getBoolean(KEY_DOWNLOAD_NOTICE, true);
    }

    public static boolean confirmExitEnabled(Context context) {
        return ui(context).getBoolean(KEY_CONFIRM_EXIT, true);
    }

    public static boolean restoreSessionEnabled(Context context) {
        return ui(context).getBoolean(KEY_RESTORE_SESSION, true);
    }

    public static boolean showAddressBar(Context context) {
        return ui(context).getBoolean(KEY_SHOW_ADDRESS, true);
    }

    public static boolean showProgressBar(Context context) {
        return ui(context).getBoolean(KEY_SHOW_PROGRESS, true);
    }

    public static boolean keepScreenAwake(Context context) {
        return ui(context).getBoolean(KEY_KEEP_AWAKE, false);
    }

    public static boolean edgeSwipeEnabled(Context context) {
        return ui(context).getBoolean(KEY_EDGE_SWIPE, true);
    }

    public static boolean searchSuggestionsEnabled(Context context) {
        return ui(context).getBoolean(KEY_SEARCH_SUGGESTIONS, true);
    }

    public static boolean searchOpenTabsEnabled(Context context) {
        return ui(context).getBoolean(KEY_SEARCH_TABS, true);
    }

    public static boolean saveHistoryEnabled(Context context) {
        return ui(context).getBoolean(KEY_SAVE_HISTORY, true);
    }

    public static int historyLimit(Context context) {
        return Math.max(50, ui(context).getInt(KEY_HISTORY_LIMIT, 250));
    }

    public static boolean javaScriptEnabled(Context context) {
        return ui(context).getBoolean(KEY_JAVASCRIPT, true);
    }

    public static boolean autoplayEnabled(Context context) {
        return ui(context).getBoolean(KEY_AUTOPLAY, true);
    }

    public static boolean swipeCloseTabs(Context context) {
        return ui(context).getBoolean(KEY_SWIPE_CLOSE, true);
    }

    public static boolean showTabCloseButtons(Context context) {
        return ui(context).getBoolean(KEY_TAB_CLOSE_BUTTON, true);
    }

    public static boolean pageRecoveryEnabled(Context context) {
        return ui(context).getBoolean(KEY_PAGE_RECOVERY, true);
    }

    public static long renderDelayMs(Context context) {
        return ui(context).getBoolean(KEY_ECO_RENDER, false) ? 32L : 16L;
    }

    public static String homeUrl(Context context) {
        String value = ui(context).getString(KEY_HOME_URL, "about:blank");
        return value == null || value.trim().isEmpty() ? "about:blank" : value.trim();
    }

    public static boolean homeBackgroundEnabled(Context context) {
        return ui(context).getBoolean(KEY_HOME_BACKGROUND, true);
    }

    public static boolean homeMotionEnabled(Context context) {
        return ui(context).getBoolean(KEY_HOME_MOTION, true);
    }

    public static boolean keySoundEnabled(Context context) {
        return ui(context).getBoolean(KEY_KEY_SOUND, true);
    }

    public static boolean keyHapticsEnabled(Context context) {
        return ui(context).getBoolean(KEY_KEY_HAPTICS, true);
    }

    public static String activeProfile(Context context) {
        return context.getSharedPreferences(PREFS_PROFILES, Context.MODE_PRIVATE)
                .getString(KEY_ACTIVE_PROFILE, "Personal");
    }

    public static boolean handleBack() {
        PopupWindow popup = activePopup;
        if (popup == null || !popup.isShowing()) return false;
        Runnable back = activeBackAction;
        dismiss();
        if (back != null) back.run();
        return true;
    }

    public static void showSettings(
            Activity activity, BrowserRepository browser, Runnable returnToSidebar) {
        LinearLayout content = column(activity);
        SharedPreferences preferences = ui(activity);

        content.addView(section(activity, "BÚSQUEDA"));
        content.addView(dropdownRow(
                activity,
                "Motor de búsqueda",
                browser.getSearchEngine().displayName,
                () -> showEngineDropdown(activity, browser)));
        content.addView(toggle(activity,
                "Sugerencias del historial",
                "Muestra direcciones visitadas dentro del buscador.",
                KEY_SEARCH_SUGGESTIONS,
                true,
                preferences));
        content.addView(toggle(activity,
                "Buscar en pestañas abiertas",
                "Incluye las pestañas actuales entre los resultados.",
                KEY_SEARCH_TABS,
                true,
                preferences));

        content.addView(section(activity, "INICIO Y PESTAÑAS"));
        content.addView(homePageEditor(activity, preferences));
        content.addView(toggle(activity,
                "Restaurar sesión al iniciar",
                "Recupera pestañas y espacios después de cerrar la aplicación.",
                KEY_RESTORE_SESSION,
                true,
                preferences));
        content.addView(toggle(activity,
                "Accesos rápidos en pestaña nueva",
                "Los accesos no reemplazan la página que estés usando.",
                KEY_QUICK_NEW_TAB,
                false,
                preferences));
        content.addView(toggle(activity,
                "Fondo bonsái en Inicio",
                "Usa la imagen zen como fondo de las pestañas nuevas.",
                KEY_HOME_BACKGROUND,
                true,
                preferences));
        content.addView(toggle(activity,
                "Movimiento ambiental del fondo",
                "Aplica un acercamiento muy lento y discreto.",
                KEY_HOME_MOTION,
                true,
                preferences));
        content.addView(toggle(activity,
                "Sonido de tecla mecánica",
                "La tecla Z reproduce un clic corto respetando el modo silencioso.",
                KEY_KEY_SOUND,
                true,
                preferences));
        content.addView(toggle(activity,
                "Respuesta háptica de la tecla",
                "Añade una vibración ligera al presionar la Z.",
                KEY_KEY_HAPTICS,
                true,
                preferences));
        content.addView(toggle(activity,
                "Deslizar para cerrar pestañas",
                "Permite cerrar una pestaña con un gesto lateral.",
                KEY_SWIPE_CLOSE,
                true,
                preferences));
        content.addView(toggle(activity,
                "Mostrar botón cerrar",
                "Muestra la X en cada pestaña del menú.",
                KEY_TAB_CLOSE_BUTTON,
                true,
                preferences));

        content.addView(section(activity, "INTERFAZ"));
        content.addView(toggle(activity,
                "Animaciones suaves",
                "Transiciones del menú, pestañas y paneles.",
                KEY_ANIMATIONS,
                true,
                preferences));
        content.addView(toggle(activity,
                "Mostrar dirección en páginas",
                "La URL aparece arriba cuando una página está abierta.",
                KEY_SHOW_ADDRESS,
                true,
                preferences));
        content.addView(toggle(activity,
                "Barra de progreso",
                "Muestra el progreso de carga con el estilo Zen.",
                KEY_SHOW_PROGRESS,
                true,
                preferences));
        content.addView(toggle(activity,
                "Gesto desde el borde",
                "Desliza desde la izquierda para abrir el menú.",
                KEY_EDGE_SWIPE,
                true,
                preferences));
        content.addView(toggle(activity,
                "Mantener pantalla encendida",
                "Evita que Android apague la pantalla mientras navegas.",
                KEY_KEEP_AWAKE,
                false,
                preferences));

        content.addView(section(activity, "RENDIMIENTO Y ESTABILIDAD"));
        content.addView(toggle(activity,
                "Recuperación visual automática",
                "Revalida la superficie de GeckoView si una página queda negra.",
                KEY_PAGE_RECOVERY,
                true,
                preferences));
        content.addView(toggle(activity,
                "Modo ahorro de interfaz",
                "Reduce la frecuencia de actualizaciones visuales.",
                KEY_ECO_RENDER,
                false,
                preferences));

        content.addView(section(activity, "CONTENIDO"));
        content.addView(toggle(activity,
                "JavaScript",
                "Se aplica al reiniciar Zen Browser.",
                KEY_JAVASCRIPT,
                true,
                preferences));
        content.addView(toggle(activity,
                "Reproducción automática",
                "Permite audio y video automático cuando el sitio lo solicita.",
                KEY_AUTOPLAY,
                true,
                preferences));

        content.addView(section(activity, "HISTORIAL"));
        content.addView(toggle(activity,
                "Guardar historial",
                "Registra páginas cargadas correctamente.",
                KEY_SAVE_HISTORY,
                true,
                preferences));
        content.addView(dropdownRow(
                activity,
                "Límite del historial",
                historyLimit(activity) + " páginas",
                () -> showHistoryLimitDropdown(activity)));

        content.addView(section(activity, "DESCARGAS"));
        content.addView(toggle(activity,
                "Confirmar antes de descargar",
                "Pregunta antes de iniciar cada archivo.",
                KEY_CONFIRM_DOWNLOAD,
                false,
                preferences));
        content.addView(toggle(activity,
                "Aviso visual de descarga",
                "Muestra una tarjeta al iniciar la descarga.",
                KEY_DOWNLOAD_NOTICE,
                true,
                preferences));
        content.addView(toggle(activity,
                "Permitir datos móviles",
                "Autoriza descargas en redes medidas.",
                KEY_DOWNLOADS_METERED,
                true,
                preferences));

        content.addView(section(activity, "SEGURIDAD Y SALIDA"));
        content.addView(toggle(activity,
                "Confirmar al cerrar",
                "Evita cerrar Zen Browser accidentalmente.",
                KEY_CONFIRM_EXIT,
                true,
                preferences));

        content.addView(section(activity, "DATOS"));
        TextView clearHistory = actionRow(activity, "Borrar historial", R.color.zen_text);
        clearHistory.setOnClickListener(v -> confirmClearHistory(activity, browser));
        content.addView(clearHistory, rowParams(activity, 48));

        TextView clearFavorites = actionRow(activity, "Borrar todos los favoritos", R.color.zen_text);
        clearFavorites.setOnClickListener(v -> new AlertDialog.Builder(activity)
                .setTitle("Borrar favoritos")
                .setMessage("Esta acción no se puede deshacer.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Borrar", (dialog, which) -> {
                    FavoritesStore.clear(activity);
                    Toast.makeText(activity, "Favoritos eliminados", Toast.LENGTH_SHORT).show();
                })
                .show());
        content.addView(clearFavorites, rowParams(activity, 48));

        content.addView(section(activity, "VERSIÓN"));
        content.addView(infoCard(activity,
                "Zen Browser " + BuildConfig.VERSION_NAME
                        + "\nGeckoView · Android · interfaz AMOLED"));

        showPanel(
                activity,
                "Configuración",
                R.drawable.ic_settings,
                content,
                returnToSidebar);
    }

    private static View homePageEditor(Activity activity, SharedPreferences preferences) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 12), dp(activity, 9), dp(activity, 12), dp(activity, 10));
        card.setBackgroundResource(R.drawable.bg_panel_card);

        TextView title = text(activity, "Página de inicio", 14, R.color.zen_text);
        card.addView(title);

        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setText(homeUrl(activity));
        input.setHint("about:blank o https://...");
        input.setTextColor(activity.getColor(R.color.zen_text));
        input.setHintTextColor(activity.getColor(R.color.zen_muted));
        input.setTextSize(13);
        input.setBackgroundResource(R.drawable.bg_address_rounder);
        input.setPadding(dp(activity, 13), 0, dp(activity, 13), 0);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 44));
        inputParams.setMargins(0, dp(activity, 7), 0, dp(activity, 6));
        card.addView(input, inputParams);

        TextView save = smallAction(activity, "GUARDAR", v -> {
            String value = input.getText().toString().trim();
            preferences.edit().putString(
                    KEY_HOME_URL,
                    value.isEmpty() ? "about:blank" : value).apply();
            notifyChanged(activity);
            Toast.makeText(activity, "Página de inicio guardada", Toast.LENGTH_SHORT).show();
        });
        save.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        card.addView(save, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 36)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(activity, 7));
        card.setLayoutParams(params);
        return card;
    }

    private static void showEngineDropdown(Activity activity, BrowserRepository browser) {
        List<String> labels = new ArrayList<>();
        for (SearchEngine engine : SearchEngine.values()) labels.add(engine.displayName);
        showChoicePopup(
                activity,
                "Motor de búsqueda",
                labels,
                browser.getSearchEngine().displayName,
                selected -> {
                    Runnable back = activeBackAction;
                    for (SearchEngine engine : SearchEngine.values()) {
                        if (engine.displayName.equals(selected)) {
                            browser.setSearchEngine(engine);
                            Toast.makeText(activity,
                                    "Motor: " + engine.displayName,
                                    Toast.LENGTH_SHORT).show();
                            break;
                        }
                    }
                    dismiss();
                    showSettings(activity, browser, back);
                });
    }

    private static void showHistoryLimitDropdown(Activity activity) {
        List<String> options = new ArrayList<>();
        options.add("100 páginas");
        options.add("250 páginas");
        options.add("500 páginas");
        showChoicePopup(
                activity,
                "Límite del historial",
                options,
                historyLimit(activity) + " páginas",
                selected -> {
                    Runnable back = activeBackAction;
                    int value = selected.startsWith("100") ? 100
                            : selected.startsWith("500") ? 500 : 250;
                    ui(activity).edit().putInt(KEY_HISTORY_LIMIT, value).apply();
                    notifyChanged(activity);
                    dismiss();
                    showSettings(activity, BrowserRepository.get(activity), back);
                });
    }

    public static void showProfiles(Activity activity, Runnable returnToSidebar) {
        LinearLayout content = column(activity);
        SharedPreferences prefs =
                activity.getSharedPreferences(PREFS_PROFILES, Context.MODE_PRIVATE);
        Set<String> stored = new HashSet<>(
                prefs.getStringSet(KEY_PROFILES, new HashSet<>()));
        if (stored.isEmpty()) {
            stored.add("Personal");
            stored.add("Invitado");
            prefs.edit().putStringSet(KEY_PROFILES, stored).apply();
        }
        String active = activeProfile(activity);

        content.addView(infoCard(activity,
                "Los perfiles son identidades locales. Los espacios Personal, Trabajo "
                        + "y Educación se seleccionan desde el menú principal."));

        List<String> ordered = new ArrayList<>(stored);
        ordered.sort(String.CASE_INSENSITIVE_ORDER);
        for (String name : ordered) {
            TextView row = actionRow(
                    activity,
                    (name.equals(active) ? "●  " : "○  ") + name,
                    name.equals(active) ? R.color.zen_text : R.color.zen_muted);
            row.setOnClickListener(v -> {
                prefs.edit().putString(KEY_ACTIVE_PROFILE, name).apply();
                Toast.makeText(activity, "Perfil activo: " + name, Toast.LENGTH_SHORT).show();
                notifyChanged(activity);
            });
            content.addView(row, rowParams(activity, 48));
        }

        TextView add = actionRow(activity, "＋  Crear perfil local", R.color.zen_accent);
        add.setOnClickListener(v -> promptNewProfile(activity, stored));
        content.addView(add, rowParams(activity, 48));

        showPanel(activity, "Perfiles", R.drawable.ic_profile, content, returnToSidebar);
    }

    private static void promptNewProfile(Activity activity, Set<String> current) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint("Nombre del perfil");
        input.setTextColor(activity.getColor(R.color.zen_text));
        input.setHintTextColor(activity.getColor(R.color.zen_muted));
        input.setBackgroundResource(R.drawable.bg_address_rounder);
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
                    Toast.makeText(activity, "Perfil creado: " + name, Toast.LENGTH_SHORT).show();
                    notifyChanged(activity);
                })
                .show();
    }

    public static void showDownloads(
            Activity activity, BrowserRepository browser, Runnable returnToSidebar) {
        LinearLayout content = column(activity);
        List<DownloadStore.Record> records = DownloadStore.list(activity);

        TextView androidDownloads = actionRow(
                activity,
                "Abrir descargas de Android",
                R.color.zen_accent);
        androidDownloads.setOnClickListener(v -> {
            try {
                activity.startActivity(new Intent("android.intent.action.VIEW_DOWNLOADS"));
            } catch (Exception error) {
                Toast.makeText(
                        activity,
                        "Android no expuso la carpeta de descargas",
                        Toast.LENGTH_SHORT).show();
            }
        });
        content.addView(androidDownloads, rowParams(activity, 48));

        if (records.isEmpty()) {
            content.addView(infoCard(activity, "Todavía no hay descargas."));
        } else {
            for (DownloadStore.Record record : records) {
                content.addView(downloadRow(activity, browser, returnToSidebar, record));
            }
        }

        showPanel(activity, "Descargas", R.drawable.ic_downloads, content, returnToSidebar);
    }

    private static View downloadRow(
            Activity activity,
            BrowserRepository browser,
            Runnable returnToSidebar,
            DownloadStore.Record record) {
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
                showDownloads(activity, browser, returnToSidebar);
            }));
        } else {
            actions.addView(smallAction(activity, "Reintentar", v -> {
                DownloadStore.retry(activity, record.id);
                dismiss();
                showDownloads(activity, browser, returnToSidebar);
            }));
        }
        actions.addView(smallAction(activity, "Quitar", v -> {
            DownloadStore.remove(activity, record.id);
            dismiss();
            showDownloads(activity, browser, returnToSidebar);
        }));
        card.addView(actions);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(activity, 7));
        card.setLayoutParams(params);
        return card;
    }

    public static void showHistory(
            Activity activity, BrowserRepository browser, Runnable returnToSidebar) {
        LinearLayout content = column(activity);

        EditText search = new EditText(activity);
        search.setSingleLine(true);
        search.setHint("Buscar en el historial");
        search.setTextSize(14);
        search.setTextColor(activity.getColor(R.color.zen_text));
        search.setHintTextColor(activity.getColor(R.color.zen_muted));
        search.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
        search.setCompoundDrawablePadding(dp(activity, 9));
        search.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
        search.setBackgroundResource(R.drawable.bg_history_search);
        content.addView(search, rowParams(activity, 48));

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);

        Runnable render = () -> renderHistoryRows(
                activity,
                browser,
                list,
                search.getText() == null ? "" : search.getText().toString());

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(
                    CharSequence value, int start, int count, int after) { }

            @Override public void onTextChanged(
                    CharSequence value, int start, int before, int count) {
                render.run();
            }

            @Override public void afterTextChanged(Editable value) { }
        });
        render.run();

        ImageButton trash = new ImageButton(activity);
        trash.setImageResource(R.drawable.ic_trash);
        trash.setImageTintList(ColorStateList.valueOf(activity.getColor(R.color.zen_danger)));
        trash.setBackgroundResource(R.drawable.bg_button);
        trash.setContentDescription("Borrar historial");
        trash.setOnClickListener(v -> confirmClearHistory(activity, browser));
        LinearLayout.LayoutParams trashParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 50));
        trashParams.setMargins(0, dp(activity, 12), 0, dp(activity, 4));
        content.addView(trash, trashParams);

        showPanel(activity, "Historial", R.drawable.ic_history, content, returnToSidebar);
    }

    private static void renderHistoryRows(
            Activity activity,
            BrowserRepository browser,
            LinearLayout holder,
            String query) {
        holder.removeAllViews();
        List<HistoryStore.Record> records = HistoryStore.search(activity, query);
        if (records.isEmpty()) {
            holder.addView(infoCard(activity, "No hay páginas que coincidan."));
            return;
        }

        DateFormat formatter = DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT,
                Locale.getDefault());

        int shown = 0;
        for (HistoryStore.Record record : records) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(activity, 13), dp(activity, 9),
                    dp(activity, 13), dp(activity, 9));
            row.setBackgroundResource(R.drawable.bg_panel_card);

            TextView title = text(
                    activity,
                    record.title == null || record.title.trim().isEmpty()
                            ? record.url : record.title,
                    14,
                    R.color.zen_text);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(title);

            TextView url = text(activity, record.url, 11, R.color.zen_muted);
            url.setSingleLine(true);
            url.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            row.addView(url);

            TextView date = text(
                    activity,
                    formatter.format(new Date(record.visitedAt)),
                    10,
                    R.color.zen_muted);
            row.addView(date);

            row.setOnClickListener(v -> {
                browser.loadInActiveTab(record.url);
                dismiss();
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, dp(activity, 6));
            holder.addView(row, params);

            if (++shown >= 200) break;
        }
    }

    private static void confirmClearHistory(Activity activity, BrowserRepository browser) {
        new AlertDialog.Builder(activity)
                .setTitle("Borrar historial")
                .setMessage("Se eliminarán todas las páginas visitadas y las sugerencias recientes.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Borrar", (dialog, which) -> {
                    HistoryStore.clear(activity);
                    browser.clearRecentUrls();
                    Toast.makeText(activity, "Historial eliminado", Toast.LENGTH_SHORT).show();
                    notifyChanged(activity);
                })
                .show();
    }

    public static void showFavorites(
            Activity activity, BrowserRepository browser, Runnable returnToSidebar) {
        FavoritesStore.migrateEssentials(activity, browser.getTabs());
        LinearLayout content = column(activity);
        BrowserTab active = browser.getActiveTab();

        if (active != null && active.url != null && !active.url.startsWith("about:")) {
            boolean favorite = FavoritesStore.contains(activity, active.url);
            TextView current = actionRow(
                    activity,
                    favorite ? "★  Quitar página actual de favoritos"
                            : "☆  Añadir página actual a favoritos",
                    R.color.zen_accent);
            current.setOnClickListener(v -> {
                if (FavoritesStore.contains(activity, active.url)) {
                    FavoritesStore.remove(activity, active.url);
                    Toast.makeText(activity, "Favorito eliminado", Toast.LENGTH_SHORT).show();
                } else {
                    FavoritesStore.add(activity, active.title, active.url);
                    Toast.makeText(activity, "Página añadida a favoritos", Toast.LENGTH_SHORT).show();
                }
                dismiss();
                showFavorites(activity, browser, returnToSidebar);
            });
            content.addView(current, rowParams(activity, 48));
        }

        List<FavoritesStore.Record> favorites = FavoritesStore.list(activity);
        if (favorites.isEmpty()) {
            content.addView(infoCard(activity,
                    "Todavía no hay sitios favoritos. Abre una página y añádela desde aquí."));
        } else {
            for (FavoritesStore.Record favorite : favorites) {
                LinearLayout row = new LinearLayout(activity);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(activity, 11), dp(activity, 5),
                        dp(activity, 5), dp(activity, 5));
                row.setBackgroundResource(R.drawable.bg_panel_card);

                ImageView star = new ImageView(activity);
                star.setImageResource(R.drawable.ic_favorite_filled);
                row.addView(star, new LinearLayout.LayoutParams(
                        dp(activity, 28), dp(activity, 28)));

                LinearLayout labels = new LinearLayout(activity);
                labels.setOrientation(LinearLayout.VERTICAL);
                TextView title = text(activity, favorite.title, 14, R.color.zen_text);
                title.setSingleLine(true);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                TextView url = text(activity, favorite.url, 10, R.color.zen_muted);
                url.setSingleLine(true);
                url.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                labels.addView(title);
                labels.addView(url);
                LinearLayout.LayoutParams labelParams =
                        new LinearLayout.LayoutParams(0, dp(activity, 48), 1f);
                labelParams.setMargins(dp(activity, 9), 0, dp(activity, 5), 0);
                row.addView(labels, labelParams);

                TextView remove = smallAction(activity, "Quitar", v -> {
                    FavoritesStore.remove(activity, favorite.url);
                    dismiss();
                    showFavorites(activity, browser, returnToSidebar);
                });
                row.addView(remove, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 40)));

                row.setOnClickListener(v -> {
                    browser.loadInActiveTab(favorite.url);
                    dismiss();
                });

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 58));
                params.setMargins(0, 0, 0, dp(activity, 6));
                content.addView(row, params);
            }
        }

        showPanel(activity, "Favoritos", R.drawable.ic_star, content, returnToSidebar);
    }

    private static void showChoicePopup(
            Activity activity,
            String title,
            List<String> options,
            String selected,
            ChoiceListener listener) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 9), dp(activity, 9), dp(activity, 9), dp(activity, 9));
        panel.setBackgroundResource(R.drawable.bg_sidebar_panel);
        panel.setElevation(dp(activity, 20));

        TextView heading = text(activity, title, 12, R.color.zen_muted);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setPadding(dp(activity, 8), dp(activity, 5), dp(activity, 8), dp(activity, 8));
        panel.addView(heading);

        PopupWindow choicePopup = new PopupWindow(
                panel,
                Math.min(dp(activity, 330),
                        activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 36)),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        choicePopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        choicePopup.setOutsideTouchable(true);
        choicePopup.setElevation(dp(activity, 22));

        for (String option : options) {
            TextView row = actionRow(
                    activity,
                    (option.equals(selected) ? "✓  " : "    ") + option,
                    option.equals(selected) ? R.color.zen_text : R.color.zen_muted);
            row.setOnClickListener(v -> {
                listener.onChoice(option);
                choicePopup.dismiss();
            });
            panel.addView(row, rowParams(activity, 46));
        }

        choicePopup.showAtLocation(
                activity.getWindow().getDecorView(),
                Gravity.CENTER,
                0,
                0);
    }

    private static View dropdownRow(
            Activity activity,
            String title,
            String value,
            Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 13), 0, dp(activity, 9), 0);
        row.setBackgroundResource(R.drawable.bg_dropdown);

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView primary = text(activity, title, 14, R.color.zen_text);
        TextView secondary = text(activity, value, 11, R.color.zen_accent);
        labels.addView(primary);
        labels.addView(secondary);
        row.addView(labels, new LinearLayout.LayoutParams(0, dp(activity, 50), 1f));

        ImageView arrow = new ImageView(activity);
        arrow.setImageResource(R.drawable.ic_chevron_down);
        arrow.setImageTintList(ColorStateList.valueOf(activity.getColor(R.color.zen_muted)));
        row.addView(arrow, new LinearLayout.LayoutParams(dp(activity, 22), dp(activity, 22)));
        row.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams params = rowParams(activity, 54);
        row.setLayoutParams(params);
        return row;
    }

    private static void showPanel(
            Activity activity,
            String title,
            int iconRes,
            View content,
            Runnable returnToSidebar) {
        dismiss();
        activeBackAction = returnToSidebar;

        FrameLayout overlay = new FrameLayout(activity);
        overlay.setBackgroundColor(Color.TRANSPARENT);

        View scrim = new View(activity);
        scrim.setBackgroundColor(0xB8000000);
        overlay.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 14), dp(activity, 10),
                dp(activity, 14), dp(activity, 13));
        panel.setBackgroundResource(R.drawable.bg_sidebar_panel);
        panel.setElevation(dp(activity, 18));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageButton back = new ImageButton(activity);
        back.setImageResource(R.drawable.ic_back_panel);
        back.setImageTintList(ColorStateList.valueOf(activity.getColor(R.color.zen_text)));
        back.setBackgroundResource(R.drawable.bg_button);
        back.setContentDescription("Volver al menú");
        back.setOnClickListener(v -> {
            Runnable action = activeBackAction;
            dismiss();
            if (action != null) action.run();
        });
        header.addView(back, new LinearLayout.LayoutParams(dp(activity, 40), dp(activity, 40)));

        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(activity.getColor(R.color.zen_text)));
        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(dp(activity, 24), dp(activity, 24));
        iconParams.setMargins(dp(activity, 7), 0, 0, 0);
        header.addView(icon, iconParams);

        TextView heading = text(activity, title, 20, R.color.zen_text);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams headingParams =
                new LinearLayout.LayoutParams(0, dp(activity, 44), 1f);
        headingParams.setMargins(dp(activity, 10), 0, 0, 0);
        header.addView(heading, headingParams);
        panel.addView(header);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.addView(content);
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        int width = activity.getResources().getDisplayMetrics().widthPixels;
        int panelWidth = Math.min(dp(activity, 450), (int) (width * .95f));
        overlay.addView(panel, new FrameLayout.LayoutParams(
                panelWidth,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.START));

        scrim.setOnClickListener(v -> dismiss());

        activePopup = new PopupWindow(
                overlay,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                true);
        activePopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        activePopup.setOutsideTouchable(true);
        activePopup.setClippingEnabled(false);
        activePopup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        activePopup.showAtLocation(
                activity.getWindow().getDecorView(),
                Gravity.START | Gravity.TOP,
                0,
                0);

        if (animationsEnabled(activity)) {
            scrim.setAlpha(0f);
            scrim.animate().alpha(1f).setDuration(110L).start();
            panel.setTranslationX(-panelWidth);
            panel.animate()
                    .translationX(0f)
                    .setDuration(165L)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
    }

    public static void dismiss() {
        PopupWindow popup = activePopup;
        activePopup = null;
        activeBackAction = null;
        if (popup != null && popup.isShowing()) popup.dismiss();
    }

    private static CheckBox toggle(
            Activity activity,
            String title,
            String description,
            String key,
            boolean defaultValue,
            SharedPreferences preferences) {
        CheckBox box = new CheckBox(activity);
        box.setText(title + "\n" + description);
        box.setTextColor(activity.getColor(R.color.zen_text));
        box.setTextSize(13);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(activity, 12), dp(activity, 7),
                dp(activity, 12), dp(activity, 7));
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
        box.setChecked(preferences.getBoolean(key, defaultValue));
        box.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean(key, checked).apply();
            notifyChanged(activity);
        });
        LinearLayout.LayoutParams params = rowParams(activity, 67);
        params.setMargins(0, 0, 0, dp(activity, 6));
        box.setLayoutParams(params);
        return box;
    }

    private static void notifyChanged(Activity activity) {
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).applySettingsNow();
        }
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
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
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

    private static TextView smallAction(
            Activity activity,
            String value,
            View.OnClickListener listener) {
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
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, height));
        params.setMargins(0, 0, 0, dp(activity, 6));
        return params;
    }

    private static TextView text(
            Activity activity,
            String value,
            float size,
            int colorRes) {
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
                Toast.makeText(
                        activity,
                        "No se encontró una aplicación compatible",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private interface ChoiceListener {
        void onChoice(String value);
    }
}
