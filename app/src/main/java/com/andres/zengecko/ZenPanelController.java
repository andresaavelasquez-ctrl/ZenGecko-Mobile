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
import android.os.Handler;
import android.os.Looper;
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
import android.widget.ProgressBar;
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
import org.mozilla.geckoview.StorageController;

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
    public static final String KEY_DESKTOP_DEFAULT = "desktop_mode_default";
    public static final String KEY_AUTO_CACHE = "auto_cache_maintenance";
    public static final String KEY_SURFACE_STYLE = ZenLiquidGlass.KEY_STYLE;
    public static final String KEY_GLASS_INTENSITY = ZenLiquidGlass.KEY_INTENSITY;
    public static final String KEY_GLASS_REDUCE = ZenLiquidGlass.KEY_REDUCE_EFFECTS;
    private static final String KEY_LAST_CACHE_CLEAR = "last_cache_clear";

    private static final String PREFS_PROFILES = "zen_profiles";
    private static final String KEY_ACTIVE_PROFILE = "active_profile";
    private static final String KEY_PROFILES = "profiles";

    private static PopupWindow activePopup;
    private static Runnable activeBackAction;
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static Runnable activeTicker;

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

    public static boolean desktopModeDefault(Context context) {
        return ui(context).getBoolean(KEY_DESKTOP_DEFAULT, false);
    }

    public static boolean liquidGlassEnabled(Context context) {
        return ZenLiquidGlass.isEnabled(context);
    }

    public static int liquidGlassIntensity(Context context) {
        return ZenLiquidGlass.intensity(context);
    }

    public static boolean reduceGlassEffects(Context context) {
        return ZenLiquidGlass.reduceEffects(context);
    }

    public static boolean automaticCacheMaintenance(Context context) {
        return ui(context).getBoolean(KEY_AUTO_CACHE, true);
    }

    public static void maybeTrimCache(Activity activity) {
        if (activity == null || !automaticCacheMaintenance(activity)) return;
        SharedPreferences preferences = ui(activity);
        long now = System.currentTimeMillis();
        long last = preferences.getLong(KEY_LAST_CACHE_CLEAR, 0L);
        if (now - last < 2L * 60L * 60L * 1000L) return;

        // Automatic maintenance only touches Zen's bounded custom caches.
        // Gecko's web cache remains intact unless the user explicitly clears it.
        RemoteAssetLoader.trimNow(activity);
        ContextMediaStore.trimNow(activity);
        preferences.edit().putLong(KEY_LAST_CACHE_CLEAR, now).apply();
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

        EditText search = new EditText(activity);
        search.setSingleLine(true);
        search.setHint("Buscar en Ajustes");
        search.setTextSize(14);
        search.setTextColor(activity.getColor(R.color.zen_text));
        search.setHintTextColor(activity.getColor(R.color.zen_muted));
        search.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
        search.setCompoundDrawablePadding(dp(activity, 9));
        search.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
        search.setBackgroundResource(R.drawable.bg_address_rounder);
        content.addView(search, rowParams(activity, 48));

        LinearLayout categories = new LinearLayout(activity);
        categories.setOrientation(LinearLayout.VERTICAL);
        content.addView(categories);

        List<View> cards = new ArrayList<>();
        cards.add(settingsCategory(activity, "general", R.drawable.ic_settings_general,
                "General", "Inicio, idioma, sesión y salida",
                () -> showSettingsCategory(activity, browser, returnToSidebar, "general")));
        cards.add(settingsCategory(activity, "appearance", R.drawable.ic_settings_appearance,
                "Apariencia", "AMOLED, animaciones, interfaz y fondo",
                () -> showSettingsCategory(activity, browser, returnToSidebar, "appearance")));
        cards.add(settingsCategory(activity, "search", R.drawable.ic_settings_search,
                "Búsqueda", "Motores, sugerencias y resultados",
                () -> showSettingsCategory(activity, browser, returnToSidebar, "search")));
        cards.add(settingsCategory(activity, "tabs", R.drawable.ic_settings_tabs,
                "Pestañas y espacios", "Restauración, gestos, escritorio y accesos",
                () -> showSettingsCategory(activity, browser, returnToSidebar, "tabs")));
        cards.add(settingsCategory(activity, "downloads", R.drawable.ic_settings_downloads,
                "Descargas", "Avisos, confirmaciones y uso de red",
                () -> showSettingsCategory(activity, browser, returnToSidebar, "downloads")));
        cards.add(settingsCategory(activity, "privacy", R.drawable.ic_settings_privacy,
                "Privacidad y datos", "Historial, favoritos y limpieza",
                () -> showSettingsCategory(activity, browser, returnToSidebar, "privacy")));
        cards.add(settingsCategory(activity, "performance", R.drawable.ic_settings_performance,
                "Rendimiento y almacenamiento", "Caché, renderizado y recuperación",
                () -> showSettingsCategory(activity, browser, returnToSidebar, "performance")));
        cards.add(settingsCategory(activity, "accessibility", R.drawable.ic_settings_accessibility,
                "Accesibilidad", "Movimiento, sonido, vibración y pantalla",
                () -> showSettingsCategory(activity, browser, returnToSidebar, "accessibility")));
        cards.add(settingsCategory(activity, "advanced", R.drawable.ic_settings_advanced,
                "Avanzado", "GeckoView, JavaScript y diagnósticos",
                () -> showSettingsCategory(activity, browser, returnToSidebar, "advanced")));

        for (View card : cards) categories.addView(card);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(
                    CharSequence value, int start, int count, int after) { }

            @Override public void onTextChanged(
                    CharSequence value, int start, int before, int count) {
                String query = value == null
                        ? "" : value.toString().trim().toLowerCase(Locale.ROOT);
                for (View card : cards) {
                    Object tag = card.getTag();
                    String keywords = tag == null ? "" : tag.toString();
                    card.setVisibility(
                            query.isEmpty() || keywords.contains(query)
                                    ? View.VISIBLE : View.GONE);
                }
            }

            @Override public void afterTextChanged(Editable value) { }
        });

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

    private static View settingsCategory(
            Activity activity,
            String id,
            int iconRes,
            String title,
            String summary,
            Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 11), dp(activity, 5),
                dp(activity, 8), dp(activity, 5));
        row.setBackgroundResource(R.drawable.bg_settings_category);
        row.setTag((id + " " + title + " " + summary).toLowerCase(Locale.ROOT));

        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        row.addView(icon, new LinearLayout.LayoutParams(
                dp(activity, 34), dp(activity, 34)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        TextView primary = text(activity, title, 14, R.color.zen_text);
        primary.setTypeface(Typeface.DEFAULT_BOLD);
        TextView secondary = text(activity, summary, 10, R.color.zen_muted);
        secondary.setSingleLine(true);
        secondary.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(primary);
        labels.addView(secondary);
        LinearLayout.LayoutParams labelsParams =
                new LinearLayout.LayoutParams(0, dp(activity, 52), 1f);
        labelsParams.setMargins(dp(activity, 11), 0, dp(activity, 6), 0);
        row.addView(labels, labelsParams);

        ImageView arrow = new ImageView(activity);
        arrow.setImageResource(R.drawable.ic_chevron_right);
        arrow.setImageTintList(ColorStateList.valueOf(
                activity.getColor(R.color.zen_muted)));
        row.addView(arrow, new LinearLayout.LayoutParams(
                dp(activity, 22), dp(activity, 22)));

        row.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = rowParams(activity, 64);
        params.setMargins(0, 0, 0, dp(activity, 7));
        row.setLayoutParams(params);
        return row;
    }

    private static void showSettingsCategory(
            Activity activity,
            BrowserRepository browser,
            Runnable returnToSidebar,
            String category) {
        LinearLayout content = column(activity);
        SharedPreferences preferences = ui(activity);
        BrowserTab activeTab = browser.getActiveTab();
        String title;
        int icon;

        switch (category) {
            case "appearance":
                title = "Apariencia";
                icon = R.drawable.ic_settings_appearance;
                content.addView(toggle(activity,
                        "Animaciones suaves",
                        "Transiciones del menú, pestañas y paneles.",
                        KEY_ANIMATIONS, true, preferences));
                content.addView(toggle(activity,
                        "Fondo bonsái en Inicio",
                        "Usa el fondo vertical u horizontal según la orientación.",
                        KEY_HOME_BACKGROUND, true, preferences));
                content.addView(toggle(activity,
                        "Movimiento ambiental del fondo",
                        "Acercamiento lento y discreto.",
                        KEY_HOME_MOTION, true, preferences));
                content.addView(toggle(activity,
                        "Mostrar dirección en páginas",
                        "Integra el dominio dentro de la barra superior.",
                        KEY_SHOW_ADDRESS, true, preferences));
                content.addView(toggle(activity,
                        "Progreso minimalista",
                        "Línea inferior y resplandor dentro de la dirección.",
                        KEY_SHOW_PROGRESS, true, preferences));
                break;

            case "search":
                title = "Búsqueda";
                icon = R.drawable.ic_settings_search;
                content.addView(dropdownRow(
                        activity,
                        "Motor de búsqueda",
                        browser.getSearchEngine().displayName,
                        () -> showEngineDropdown(activity, browser)));
                content.addView(toggle(activity,
                        "Sugerencias del historial",
                        "Incluye direcciones visitadas.",
                        KEY_SEARCH_SUGGESTIONS, true, preferences));
                content.addView(toggle(activity,
                        "Buscar en pestañas abiertas",
                        "Muestra pestañas actuales entre los resultados.",
                        KEY_SEARCH_TABS, true, preferences));
                break;

            case "tabs":
                title = "Pestañas y espacios";
                icon = R.drawable.ic_settings_tabs;
                content.addView(toggle(activity,
                        "Restaurar sesión al iniciar",
                        "Recupera pestañas y espacios.",
                        KEY_RESTORE_SESSION, true, preferences));
                content.addView(toggle(activity,
                        "Abrir accesos en pestaña nueva",
                        "Los accesos editables no reemplazan la página actual.",
                        KEY_QUICK_NEW_TAB, false, preferences));
                content.addView(toggle(activity,
                        "Deslizar para cerrar pestañas",
                        "Cierra una pestaña con gesto lateral.",
                        KEY_SWIPE_CLOSE, true, preferences));
                content.addView(toggle(activity,
                        "Mostrar botón cerrar",
                        "Muestra la X en las filas.",
                        KEY_TAB_CLOSE_BUTTON, true, preferences));
                if (activeTab != null) {
                    TextView desktop = actionRow(
                            activity,
                            "Sitio de escritorio para esta pestaña: "
                                    + (activeTab.desktopMode ? "Activado" : "Desactivado"),
                            R.color.zen_accent);
                    desktop.setCompoundDrawablesWithIntrinsicBounds(
                            R.drawable.ic_settings_tabs, 0, 0, 0);
                    desktop.setCompoundDrawablePadding(dp(activity, 9));
                    desktop.setOnClickListener(v -> {
                        browser.setDesktopMode(activeTab.id, !activeTab.desktopMode);
                        dismiss();
                        showSettingsCategory(
                                activity, browser, returnToSidebar, "tabs");
                    });
                    content.addView(desktop, rowParams(activity, 50));
                }
                content.addView(toggle(activity,
                        "Modo escritorio en pestañas nuevas",
                        "Usa agente y viewport de escritorio.",
                        KEY_DESKTOP_DEFAULT, false, preferences));
                break;

            case "downloads":
                title = "Descargas";
                icon = R.drawable.ic_settings_downloads;
                content.addView(toggle(activity,
                        "Confirmar antes de descargar",
                        "Pregunta antes de iniciar cada archivo.",
                        KEY_CONFIRM_DOWNLOAD, false, preferences));
                content.addView(toggle(activity,
                        "Aviso visual Zen",
                        "Tarjeta con velocidad, tamaño, porcentaje y progreso inferior.",
                        KEY_DOWNLOAD_NOTICE, true, preferences));
                content.addView(toggle(activity,
                        "Permitir datos móviles",
                        "Autoriza descargas en redes medidas.",
                        KEY_DOWNLOADS_METERED, true, preferences));
                TextView openDownloads = actionRow(
                        activity, "Abrir administrador de descargas", R.color.zen_accent);
                openDownloads.setOnClickListener(v ->
                        showDownloads(activity, browser,
                                () -> showSettingsCategory(
                                        activity, browser, returnToSidebar, "downloads")));
                content.addView(openDownloads, rowParams(activity, 50));
                break;

            case "privacy":
                title = "Privacidad y datos";
                icon = R.drawable.ic_settings_privacy;
                content.addView(toggle(activity,
                        "Guardar historial",
                        "Registra páginas cargadas correctamente.",
                        KEY_SAVE_HISTORY, true, preferences));
                content.addView(dropdownRow(
                        activity,
                        "Límite del historial",
                        historyLimit(activity) + " páginas",
                        () -> showHistoryLimitDropdown(activity)));
                TextView clearHistory = actionRow(
                        activity, "Borrar historial", R.color.zen_text);
                clearHistory.setOnClickListener(v ->
                        confirmClearHistory(activity, browser));
                content.addView(clearHistory, rowParams(activity, 48));
                TextView clearFavorites = actionRow(
                        activity, "Borrar todos los favoritos", R.color.zen_text);
                clearFavorites.setOnClickListener(v ->
                        new AlertDialog.Builder(activity)
                                .setTitle("Borrar favoritos")
                                .setMessage("Esta acción no se puede deshacer.")
                                .setNegativeButton("Cancelar", null)
                                .setPositiveButton("Borrar", (dialog, which) -> {
                                    FavoritesStore.clear(activity);
                                    Toast.makeText(
                                            activity,
                                            "Favoritos eliminados",
                                            Toast.LENGTH_SHORT).show();
                                })
                                .show());
                content.addView(clearFavorites, rowParams(activity, 48));
                break;

            case "performance":
                title = "Rendimiento y almacenamiento";
                icon = R.drawable.ic_settings_performance;
                content.addView(toggle(activity,
                        "Recuperación visual automática",
                        "Protege el contenido hasta la primera pintura válida.",
                        KEY_PAGE_RECOVERY, true, preferences));
                content.addView(toggle(activity,
                        "Modo ahorro de interfaz",
                        "Reduce actualizaciones visuales en dispositivos modestos.",
                        KEY_ECO_RENDER, false, preferences));
                content.addView(toggle(activity,
                        "Mantenimiento automático de caché",
                        "Limita iconos y archivos contextuales sin vaciar la caché web.",
                        KEY_AUTO_CACHE, true, preferences));
                TextView clearCache = actionRow(
                        activity, "Limpiar caché temporal ahora", R.color.zen_accent);
                clearCache.setOnClickListener(v ->
                        clearTemporaryCaches(activity, true));
                content.addView(clearCache, rowParams(activity, 50));
                TextView clearIcons = actionRow(
                        activity, "Limpiar iconos y vistas previas", R.color.zen_text);
                clearIcons.setOnClickListener(v -> {
                    RemoteAssetLoader.clear(activity);
                    ContextMediaStore.clear(activity);
                    Toast.makeText(
                            activity,
                            "Caché visual limpiada",
                            Toast.LENGTH_SHORT).show();
                });
                content.addView(clearIcons, rowParams(activity, 50));
                break;

            case "accessibility":
                title = "Accesibilidad";
                icon = R.drawable.ic_settings_accessibility;
                content.addView(toggle(activity,
                        "Sonido de tecla mecánica",
                        "Reproduce el clic de la tecla Z.",
                        KEY_KEY_SOUND, true, preferences));
                content.addView(toggle(activity,
                        "Respuesta háptica de la tecla",
                        "Vibración ligera al presionar.",
                        KEY_KEY_HAPTICS, true, preferences));
                content.addView(toggle(activity,
                        "Gesto desde el borde",
                        "Desliza desde la izquierda para abrir el menú.",
                        KEY_EDGE_SWIPE, true, preferences));
                content.addView(toggle(activity,
                        "Mantener pantalla encendida",
                        "Evita que Android apague la pantalla.",
                        KEY_KEEP_AWAKE, false, preferences));
                break;

            case "advanced":
                title = "Avanzado";
                icon = R.drawable.ic_settings_advanced;
                content.addView(toggle(activity,
                        "JavaScript",
                        "Se aplica al reiniciar Zen Browser.",
                        KEY_JAVASCRIPT, true, preferences));
                content.addView(toggle(activity,
                        "Reproducción automática",
                        "Permite audio y video cuando el sitio lo solicita.",
                        KEY_AUTOPLAY, true, preferences));
                content.addView(infoCard(activity,
                        "Motor: GeckoView\nVersión: " + BuildConfig.VERSION_NAME
                                + "\nAPI Android: " + android.os.Build.VERSION.SDK_INT));
                break;

            default:
                title = "General";
                icon = R.drawable.ic_settings_general;
                content.addView(homePageEditor(activity, preferences));
                content.addView(toggle(activity,
                        "Restaurar sesión al iniciar",
                        "Recupera pestañas y espacios después de cerrar.",
                        KEY_RESTORE_SESSION, true, preferences));
                content.addView(toggle(activity,
                        "Confirmar al cerrar",
                        "Evita salir accidentalmente.",
                        KEY_CONFIRM_EXIT, true, preferences));
                break;
        }

        TextView reset = actionRow(
                activity,
                "Restablecer esta categoría",
                R.color.zen_muted);
        reset.setOnClickListener(v -> confirmResetCategory(
                activity, browser, returnToSidebar, category));
        LinearLayout.LayoutParams resetParams = rowParams(activity, 48);
        resetParams.setMargins(0, dp(activity, 13), 0, dp(activity, 7));
        content.addView(reset, resetParams);

        Runnable backToSettings =
                () -> showSettings(activity, browser, returnToSidebar);
        showPanel(activity, title, icon, content, backToSettings);
    }

    private static void confirmResetCategory(
            Activity activity,
            BrowserRepository browser,
            Runnable returnToSidebar,
            String category) {
        new AlertDialog.Builder(activity)
                .setTitle("Restablecer categoría")
                .setMessage("Las opciones de esta sección volverán a sus valores predeterminados.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Restablecer", (dialog, which) -> {
                    SharedPreferences.Editor editor = ui(activity).edit();
                    for (String key : categoryKeys(category)) editor.remove(key);
                    editor.apply();
                    notifyChanged(activity);
                    dismiss();
                    showSettingsCategory(activity, browser, returnToSidebar, category);
                })
                .show();
    }

    private static String[] categoryKeys(String category) {
        switch (category) {
            case "appearance":
                return new String[]{
                        KEY_ANIMATIONS, KEY_HOME_BACKGROUND, KEY_HOME_MOTION,
                        KEY_SHOW_ADDRESS, KEY_SHOW_PROGRESS};
            case "search":
                return new String[]{KEY_SEARCH_SUGGESTIONS, KEY_SEARCH_TABS};
            case "tabs":
                return new String[]{
                        KEY_RESTORE_SESSION, KEY_QUICK_NEW_TAB, KEY_SWIPE_CLOSE,
                        KEY_TAB_CLOSE_BUTTON, KEY_DESKTOP_DEFAULT};
            case "downloads":
                return new String[]{
                        KEY_CONFIRM_DOWNLOAD, KEY_DOWNLOAD_NOTICE, KEY_DOWNLOADS_METERED};
            case "privacy":
                return new String[]{KEY_SAVE_HISTORY, KEY_HISTORY_LIMIT};
            case "performance":
                return new String[]{KEY_PAGE_RECOVERY, KEY_ECO_RENDER, KEY_AUTO_CACHE};
            case "accessibility":
                return new String[]{
                        KEY_KEY_SOUND, KEY_KEY_HAPTICS, KEY_EDGE_SWIPE, KEY_KEEP_AWAKE};
            case "advanced":
                return new String[]{KEY_JAVASCRIPT, KEY_AUTOPLAY};
            default:
                return new String[]{KEY_HOME_URL, KEY_RESTORE_SESSION, KEY_CONFIRM_EXIT};
        }
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

    private static final class DownloadCardBinding {
        String id;
        FrameLayout root;
        TextView state;
        TextView name;
        TextView metrics;
        TextView action;
        ProgressBar progress;
        String lastStatus;
    }

    public static void showDownloads(
            Activity activity, BrowserRepository browser, Runnable returnToSidebar) {
        LinearLayout content = column(activity);

        TextView androidDownloads = actionRow(
                activity,
                "Abrir carpeta de descargas",
                R.color.zen_accent);
        androidDownloads.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_downloads, 0, 0, 0);
        androidDownloads.setCompoundDrawablePadding(dp(activity, 9));
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

        TextView hint = text(
                activity,
                "Mantén pulsada una descarga para quitarla de la lista.",
                10,
                R.color.zen_muted);
        hint.setPadding(dp(activity, 4), 0, dp(activity, 4), dp(activity, 8));
        content.addView(hint);

        LinearLayout listHolder = new LinearLayout(activity);
        listHolder.setOrientation(LinearLayout.VERTICAL);
        content.addView(listHolder);

        Runnable render = () -> renderDownloadList(
                activity, browser, returnToSidebar, listHolder);
        render.run();
        showPanel(activity, "Descargas", R.drawable.ic_downloads, content, returnToSidebar);
        startTicker(render, 400L);
    }

    private static void renderDownloadList(
            Activity activity,
            BrowserRepository browser,
            Runnable returnToSidebar,
            LinearLayout holder) {
        List<DownloadStore.Record> records = DownloadStore.list(activity);

        if (records.isEmpty()) {
            if (holder.getChildCount() != 1
                    || !"downloads-empty".equals(holder.getChildAt(0).getTag())) {
                holder.removeAllViews();
                View empty = infoCard(activity, "Todavía no hay descargas.");
                empty.setTag("downloads-empty");
                holder.addView(empty);
            }
            return;
        }

        Set<String> currentIds = new HashSet<>();
        int targetIndex = 0;

        for (DownloadStore.Record record : records) {
            currentIds.add(record.id);
            DownloadCardBinding binding = findDownloadBinding(holder, record.id);
            if (binding == null) {
                binding = createDownloadCard(
                        activity, browser, returnToSidebar, record.id);
            }

            bindDownloadCard(activity, binding, record);

            int currentIndex = holder.indexOfChild(binding.root);
            if (currentIndex < 0) {
                holder.addView(binding.root, Math.min(targetIndex, holder.getChildCount()));
            } else if (currentIndex != targetIndex) {
                holder.removeView(binding.root);
                holder.addView(binding.root, Math.min(targetIndex, holder.getChildCount()));
            }
            targetIndex++;
        }

        for (int index = holder.getChildCount() - 1; index >= 0; index--) {
            View child = holder.getChildAt(index);
            Object tag = child.getTag();
            if (tag instanceof DownloadCardBinding) {
                DownloadCardBinding binding = (DownloadCardBinding) tag;
                if (!currentIds.contains(binding.id)) holder.removeViewAt(index);
            } else {
                holder.removeViewAt(index);
            }
        }
    }

    private static DownloadCardBinding findDownloadBinding(
            LinearLayout holder, String id) {
        for (int index = 0; index < holder.getChildCount(); index++) {
            Object tag = holder.getChildAt(index).getTag();
            if (tag instanceof DownloadCardBinding) {
                DownloadCardBinding binding = (DownloadCardBinding) tag;
                if (id.equals(binding.id)) return binding;
            }
        }
        return null;
    }

    private static DownloadCardBinding createDownloadCard(
            Activity activity,
            BrowserRepository browser,
            Runnable returnToSidebar,
            String id) {
        DownloadCardBinding binding = new DownloadCardBinding();
        binding.id = id;

        FrameLayout card = new FrameLayout(activity);
        card.setBackgroundResource(R.drawable.bg_download_card_edge);
        card.setClickable(true);
        card.setFocusable(true);
        card.setElevation(dp(activity, 2));
        binding.root = card;
        card.setTag(binding);

        LinearLayout body = new LinearLayout(activity);
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.setPadding(
                dp(activity, 8), dp(activity, 5),
                dp(activity, 6), dp(activity, 8));

        FrameLayout iconShell = new FrameLayout(activity);
        iconShell.setBackgroundResource(R.drawable.bg_download_icon_glow);
        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_downloads);
        icon.setImageTintList(ColorStateList.valueOf(
                activity.getColor(R.color.zen_accent)));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iconShell.addView(icon, new FrameLayout.LayoutParams(
                dp(activity, 22), dp(activity, 22), Gravity.CENTER));
        body.addView(iconShell, new LinearLayout.LayoutParams(
                dp(activity, 40), dp(activity, 40)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.setPadding(dp(activity, 8), 0, dp(activity, 5), 0);

        binding.state = text(activity, "Descargando", 12, R.color.zen_text);
        binding.state.setTypeface(Typeface.DEFAULT_BOLD);
        binding.state.setSingleLine(true);

        binding.name = text(activity, "Archivo", 10, R.color.zen_muted);
        binding.name.setSingleLine(true);
        binding.name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);

        binding.metrics = text(
                activity, "Calculando velocidad", 9, R.color.zen_muted);
        binding.metrics.setSingleLine(true);
        binding.metrics.setEllipsize(android.text.TextUtils.TruncateAt.END);
        binding.metrics.setPadding(0, dp(activity, 2), 0, 0);

        labels.addView(binding.state);
        labels.addView(binding.name);
        labels.addView(binding.metrics);
        body.addView(labels, new LinearLayout.LayoutParams(
                0, dp(activity, 52), 1f));

        binding.action = text(activity, "VER", 10, R.color.zen_accent);
        binding.action.setTypeface(Typeface.DEFAULT_BOLD);
        binding.action.setGravity(Gravity.CENTER);
        binding.action.setPadding(dp(activity, 7), 0, dp(activity, 7), 0);
        binding.action.setBackgroundResource(R.drawable.bg_download_action);
        body.addView(binding.action, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 32)));

        card.addView(body, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        binding.progress = new ProgressBar(
                activity, null, android.R.attr.progressBarStyleHorizontal);
        binding.progress.setMax(100);
        binding.progress.setProgressDrawable(
                activity.getDrawable(R.drawable.progress_download_edge));
        binding.progress.setProgressBackgroundTintList(
                ColorStateList.valueOf(Color.TRANSPARENT));

        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, 3),
                Gravity.BOTTOM);
        progressParams.leftMargin = dp(activity, 3);
        progressParams.rightMargin = dp(activity, 3);
        progressParams.bottomMargin = dp(activity, 2);
        card.addView(binding.progress, progressParams);

        LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, 72));
        outer.setMargins(0, 0, 0, dp(activity, 6));
        card.setLayoutParams(outer);

        card.setOnLongClickListener(v -> {
            DownloadStore.Record record = DownloadStore.get(activity, binding.id);
            String name = record == null || record.name == null
                    ? "esta descarga" : record.name;
            new AlertDialog.Builder(activity)
                    .setTitle("Quitar descarga")
                    .setMessage("¿Quitar \"" + name + "\" de la lista?")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Quitar", (dialog, which) ->
                            DownloadStore.remove(activity, binding.id))
                    .show();
            return true;
        });
        return binding;
    }

    private static void bindDownloadCard(
            Activity activity,
            DownloadCardBinding binding,
            DownloadStore.Record record) {
        binding.name.setText(record.name == null || record.name.trim().isEmpty()
                ? "Archivo" : record.name);

        boolean statusChanged = binding.lastStatus == null
                || !binding.lastStatus.equals(record.status);
        boolean active = DownloadStore.DOWNLOADING.equals(record.status)
                || DownloadStore.QUEUED.equals(record.status);
        int percent = record.total > 0L
                ? (int) Math.min(100L, record.bytes * 100L / record.total)
                : 0;

        if (!DownloadStore.COMPLETE.equals(record.status)) {
            binding.progress.animate().cancel();
            binding.progress.setAlpha(1f);
        }
        binding.progress.setIndeterminate(active && record.total <= 0L);

        if (DownloadStore.COMPLETE.equals(record.status)) {
            binding.state.setText("Descarga completada");
            binding.metrics.setText(readableSize(record.bytes) + "   ·   100%");
            binding.action.setText("VER");
            binding.action.setOnClickListener(v -> openDownload(activity, record));
            binding.progress.setIndeterminate(false);
            binding.progress.setProgress(100, true);

            if (statusChanged) {
                binding.progress.setVisibility(View.VISIBLE);
                binding.root.animate().cancel();
                binding.root.setScaleX(1f);
                binding.root.setScaleY(1f);
                binding.root.animate()
                        .scaleX(1.012f)
                        .scaleY(1.012f)
                        .setDuration(115L)
                        .withEndAction(() -> binding.root.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(145L)
                                .start())
                        .start();
                binding.progress.animate()
                        .alpha(0f)
                        .setStartDelay(360L)
                        .setDuration(280L)
                        .withEndAction(() -> binding.progress.setVisibility(View.INVISIBLE))
                        .start();
            }
        } else if (DownloadStore.FAILED.equals(record.status)) {
            binding.state.setText("Error en la descarga");
            binding.metrics.setText(record.error == null || record.error.trim().isEmpty()
                    ? "No se pudo completar" : record.error);
            binding.progress.setVisibility(View.INVISIBLE);
            binding.action.setText("REINTENTAR");
            binding.action.setOnClickListener(v ->
                    DownloadStore.retry(activity, record.id));
        } else if (DownloadStore.CANCELLED.equals(record.status)) {
            binding.state.setText("Descarga cancelada");
            binding.metrics.setText("Mantén pulsado para quitarla");
            binding.progress.setVisibility(View.INVISIBLE);
            binding.action.setText("REINTENTAR");
            binding.action.setOnClickListener(v ->
                    DownloadStore.retry(activity, record.id));
        } else {
            binding.state.setText(DownloadStore.QUEUED.equals(record.status)
                    ? "Descarga en espera" : "Descargando");
            binding.metrics.setText(downloadMetrics(record));
            binding.progress.setVisibility(View.VISIBLE);
            if (!binding.progress.isIndeterminate()) {
                binding.progress.setProgress(percent, true);
            }
            binding.action.setText("CANCELAR");
            binding.action.setOnClickListener(v ->
                    DownloadStore.cancel(activity, record.id));
        }

        binding.lastStatus = record.status;
        binding.root.setContentDescription(
                binding.state.getText() + ". " + binding.name.getText()
                        + ". " + binding.metrics.getText());
    }

    private static String downloadMetrics(DownloadStore.Record record) {
        String rate = record.bytesPerSecond > 0L
                ? readableSize(record.bytesPerSecond) + "/s"
                : "Calculando velocidad";
        String amount = record.total > 0L
                ? readableSize(record.bytes) + " / " + readableSize(record.total)
                : readableSize(record.bytes);
        if (record.total <= 0L) return rate + "   ·   " + amount;
        int percent = (int) Math.min(
                100L, record.bytes * 100L / record.total);
        return rate + "   ·   " + amount + "   ·   " + percent + "%";
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
        scrim.setBackgroundColor(ZenTheme.panelScrim(activity));
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
        stopTicker();
        PopupWindow popup = activePopup;
        activePopup = null;
        activeBackAction = null;
        if (popup != null && popup.isShowing()) popup.dismiss();
    }

    private static void startTicker(Runnable action, long delayMs) {
        stopTicker();
        activeTicker = new Runnable() {
            @Override public void run() {
                if (activePopup == null || !activePopup.isShowing()) return;
                action.run();
                UI.postDelayed(this, delayMs);
            }
        };
        UI.postDelayed(activeTicker, delayMs);
    }

    private static void stopTicker() {
        if (activeTicker != null) UI.removeCallbacks(activeTicker);
        activeTicker = null;
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
            return "Completada · " + readableSize(record.bytes) + " · 100%";
        }
        if (DownloadStore.CANCELLED.equals(record.status)) return "Cancelada";
        if (DownloadStore.FAILED.equals(record.status)) {
            return "Falló · " + (record.error == null ? "" : record.error);
        }
        String rate = record.bytesPerSecond > 0L
                ? readableSize(record.bytesPerSecond) + "/s"
                : "Calculando velocidad";
        if (record.total > 0) {
            int progress = (int) Math.min(100L, record.bytes * 100L / record.total);
            return rate + "   ·   " + readableSize(record.bytes) + " / "
                    + readableSize(record.total) + "   ·   " + progress + "%";
        }
        return rate + "   ·   " + readableSize(record.bytes);
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

    private static void clearTemporaryCaches(Activity activity, boolean showMessage) {
        try {
            ZenGeckoApplication.runtime(activity)
                    .getStorageController()
                    .clearData(StorageController.ClearFlags.ALL_CACHES);
            RemoteAssetLoader.clear(activity);
            ContextMediaStore.clear(activity);
            deleteChildren(activity.getCacheDir());
            ui(activity).edit()
                    .putLong(KEY_LAST_CACHE_CLEAR, System.currentTimeMillis())
                    .apply();
            if (showMessage) {
                Toast.makeText(activity, "Caché temporal limpiada", Toast.LENGTH_SHORT).show();
            }
        } catch (RuntimeException error) {
            if (showMessage) {
                Toast.makeText(activity, "No se pudo limpiar la caché", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static long directorySize(java.io.File directory) {
        if (directory == null || !directory.exists()) return 0L;
        if (directory.isFile()) return directory.length();
        long total = 0L;
        java.io.File[] files = directory.listFiles();
        if (files == null) return 0L;
        for (java.io.File file : files) total += directorySize(file);
        return total;
    }

    private static void deleteChildren(java.io.File directory) {
        if (directory == null || !directory.isDirectory()) return;
        java.io.File[] files = directory.listFiles();
        if (files == null) return;
        for (java.io.File file : files) {
            if (file.isDirectory()) deleteChildren(file);
            try { file.delete(); } catch (SecurityException ignored) { }
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private interface ChoiceListener {
        void onChoice(String value);
    }
}
