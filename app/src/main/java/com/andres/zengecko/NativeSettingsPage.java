package com.andres.zengecko;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Native, offline settings page rendered as an internal browser tab. */
public final class NativeSettingsPage {
    private static final String PREFS_UI = "zen_ui_prefs";

    private final Activity activity;
    private final BrowserRepository browser;
    private final Runnable onChanged;
    private final SharedPreferences preferences;
    private FrameLayout contentHost;

    private NativeSettingsPage(
            Activity activity,
            BrowserRepository browser,
            Runnable onChanged) {
        this.activity = activity;
        this.browser = browser;
        this.onChanged = onChanged;
        this.preferences = activity.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE);
    }

    public static View create(
            Activity activity,
            BrowserRepository browser,
            Runnable onChanged) {
        return new NativeSettingsPage(activity, browser, onChanged).build();
    }

    private View build() {
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundResource(R.drawable.bg_settings_page);

        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(12), dp(10), dp(12), dp(12));
        root.addView(shell, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(7), dp(12), dp(7));
        header.setBackgroundResource(R.drawable.bg_settings_section);

        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_settings);
        icon.setImageTintList(ColorStateList.valueOf(
                activity.getColor(R.color.zen_accent)));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        header.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(10), 0, 0, 0);
        TextView title = text("Configuración", 18, R.color.zen_text);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView subtitle = text(
                "Preferencias internas de Zen Browser", 10, R.color.zen_muted);
        labels.addView(title);
        labels.addView(subtitle);
        header.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView version = text("v" + BuildConfig.VERSION_NAME, 10, R.color.zen_muted);
        version.setGravity(Gravity.CENTER);
        version.setPadding(dp(8), 0, dp(4), 0);
        header.addView(version, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));

        shell.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        contentHost = new FrameLayout(activity);
        LinearLayout.LayoutParams hostParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        hostParams.setMargins(0, dp(8), 0, 0);
        shell.addView(contentHost, hostParams);

        showHome();
        return root;
    }

    private void showHome() {
        ScrollView scroll = scroll();
        LinearLayout page = page();
        scroll.addView(page);

        EditText search = new EditText(activity);
        search.setSingleLine(true);
        search.setHint("Buscar en Configuración");
        search.setTextSize(14);
        search.setTextColor(activity.getColor(R.color.zen_text));
        search.setHintTextColor(activity.getColor(R.color.zen_muted));
        search.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_search, 0, 0, 0);
        search.setCompoundDrawablePadding(dp(9));
        search.setPadding(dp(14), 0, dp(14), 0);
        search.setBackgroundResource(R.drawable.bg_address_rounder);
        page.addView(search, rowParams(48));

        List<View> cards = new ArrayList<>();
        cards.add(categoryCard(
                "general inicio sesión salida",
                R.drawable.ic_settings_general,
                "General",
                "Inicio, restauración y comportamiento al salir",
                "general"));
        cards.add(categoryCard(
                "apariencia amoled animaciones fondo interfaz",
                R.drawable.ic_settings_appearance,
                "Apariencia",
                "Interfaz, movimiento y elementos visibles",
                "appearance"));
        cards.add(categoryCard(
                "búsqueda motor sugerencias resultados",
                R.drawable.ic_settings_search,
                "Búsqueda",
                "Motor, sugerencias y pestañas abiertas",
                "search"));
        cards.add(categoryCard(
                "pestañas espacios escritorio gestos accesos",
                R.drawable.ic_settings_tabs,
                "Pestañas y espacios",
                "Escritorio, cierres, gestos y accesos rápidos",
                "tabs"));
        cards.add(categoryCard(
                "descargas avisos confirmación red",
                R.drawable.ic_settings_downloads,
                "Descargas",
                "Notificaciones, confirmación y uso de datos",
                "downloads"));
        cards.add(categoryCard(
                "privacidad datos historial favoritos limpiar",
                R.drawable.ic_settings_privacy,
                "Privacidad y datos",
                "Historial, favoritos y datos locales",
                "privacy"));
        cards.add(categoryCard(
                "rendimiento almacenamiento caché render recuperación",
                R.drawable.ic_settings_performance,
                "Rendimiento y almacenamiento",
                "Recuperación visual y cachés personalizadas",
                "performance"));
        cards.add(categoryCard(
                "accesibilidad sonido vibración borde pantalla",
                R.drawable.ic_settings_accessibility,
                "Accesibilidad",
                "Sonido, respuesta háptica y navegación",
                "accessibility"));
        cards.add(categoryCard(
                "avanzado gecko javascript autoplay diagnóstico",
                R.drawable.ic_settings_advanced,
                "Avanzado",
                "GeckoView, JavaScript y diagnóstico",
                "advanced"));

        for (View card : cards) page.addView(card);

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

        setContent(scroll);
    }

    private View categoryCard(
            String keywords,
            int iconRes,
            String title,
            String summary,
            String category) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(11), dp(7), dp(9), dp(7));
        row.setBackgroundResource(R.drawable.bg_settings_section);
        row.setTag((keywords + " " + title + " " + summary).toLowerCase(Locale.ROOT));

        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.setPadding(dp(10), 0, dp(8), 0);
        TextView primary = text(title, 14, R.color.zen_text);
        primary.setTypeface(Typeface.DEFAULT_BOLD);
        TextView secondary = text(summary, 10, R.color.zen_muted);
        secondary.setSingleLine(true);
        secondary.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(primary);
        labels.addView(secondary);
        row.addView(labels, new LinearLayout.LayoutParams(
                0, dp(50), 1f));

        TextView arrow = text("›", 24, R.color.zen_muted);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(40)));

        row.setOnClickListener(v -> showCategory(category));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        params.setMargins(0, dp(7), 0, 0);
        row.setLayoutParams(params);
        return row;
    }

    private void showCategory(String category) {
        ScrollView scroll = scroll();
        LinearLayout page = page();
        scroll.addView(page);

        LinearLayout back = new LinearLayout(activity);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setPadding(dp(8), 0, dp(10), 0);
        back.setBackgroundResource(R.drawable.bg_settings_section);
        ImageView backIcon = new ImageView(activity);
        backIcon.setImageResource(R.drawable.ic_back);
        backIcon.setImageTintList(ColorStateList.valueOf(
                activity.getColor(R.color.zen_text)));
        back.addView(backIcon, new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView backText = text("Todas las categorías", 13, R.color.zen_text);
        backText.setTypeface(Typeface.DEFAULT_BOLD);
        backText.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams backTextParams = new LinearLayout.LayoutParams(
                0, dp(44), 1f);
        backTextParams.setMargins(dp(8), 0, 0, 0);
        back.addView(backText, backTextParams);
        back.setOnClickListener(v -> showHome());
        page.addView(back, rowParams(46));

        switch (category) {
            case "general":
                addTitle(page, "General", "Inicio, sesión y salida");
                addHomeUrl(page);
                page.addView(toggle(
                        "Restaurar sesión",
                        "Conserva pestañas y espacios al iniciar.",
                        ZenPanelController.KEY_RESTORE_SESSION,
                        true));
                page.addView(toggle(
                        "Confirmar antes de salir",
                        "Pregunta antes de cerrar completamente Zen Browser.",
                        ZenPanelController.KEY_CONFIRM_EXIT,
                        true));
                break;

            case "appearance":
                addTitle(page, "Apariencia", "Interfaz AMOLED y movimiento");
                page.addView(toggle(
                        "Animaciones",
                        "Activa transiciones breves y discretas.",
                        ZenPanelController.KEY_ANIMATIONS,
                        true));
                page.addView(toggle(
                        "Mostrar dirección",
                        "Muestra el dominio de la página activa.",
                        ZenPanelController.KEY_SHOW_ADDRESS,
                        true));
                page.addView(toggle(
                        "Mostrar progreso",
                        "Línea de progreso bajo la barra superior.",
                        ZenPanelController.KEY_SHOW_PROGRESS,
                        true));
                page.addView(toggle(
                        "Fondo de Inicio",
                        "Muestra el fondo de bonsái en pestañas nuevas.",
                        ZenPanelController.KEY_HOME_BACKGROUND,
                        true));
                page.addView(toggle(
                        "Movimiento del fondo",
                        "Aplica un movimiento lento al fondo de Inicio.",
                        ZenPanelController.KEY_HOME_MOTION,
                        true));
                break;

            case "search":
                addTitle(page, "Búsqueda", "Motor y resultados");
                addSearchEngines(page);
                page.addView(toggle(
                        "Sugerencias",
                        "Muestra sugerencias mientras escribes.",
                        ZenPanelController.KEY_SEARCH_SUGGESTIONS,
                        true));
                page.addView(toggle(
                        "Buscar en pestañas abiertas",
                        "Incluye pestañas actuales entre los resultados.",
                        ZenPanelController.KEY_SEARCH_TABS,
                        true));
                break;

            case "tabs":
                addTitle(page, "Pestañas y espacios", "Comportamiento y accesos");
                page.addView(toggle(
                        "Accesos rápidos en pestaña nueva",
                        "Abre los accesos en una pestaña independiente.",
                        ZenPanelController.KEY_QUICK_NEW_TAB,
                        false));
                page.addView(toggle(
                        "Deslizar para cerrar",
                        "Cierra una pestaña al desplazar su fila.",
                        ZenPanelController.KEY_SWIPE_CLOSE,
                        true));
                page.addView(toggle(
                        "Mostrar botón de cierre",
                        "Muestra la X en cada fila de pestaña.",
                        ZenPanelController.KEY_TAB_CLOSE_BUTTON,
                        true));
                page.addView(toggle(
                        "Modo escritorio predeterminado",
                        "Las pestañas nuevas solicitarán sitios de escritorio.",
                        ZenPanelController.KEY_DESKTOP_DEFAULT,
                        false));
                break;

            case "downloads":
                addTitle(page, "Descargas", "Avisos y uso de red");
                page.addView(toggle(
                        "Notificación flotante",
                        "Muestra el progreso compacto de la descarga.",
                        ZenPanelController.KEY_DOWNLOAD_NOTICE,
                        true));
                page.addView(toggle(
                        "Confirmar descargas",
                        "Solicita confirmación antes de iniciar.",
                        ZenPanelController.KEY_CONFIRM_DOWNLOAD,
                        false));
                page.addView(toggle(
                        "Permitir datos móviles",
                        "Autoriza descargas mediante redes medidas.",
                        ZenPanelController.KEY_DOWNLOADS_METERED,
                        true));
                page.addView(action(
                        R.drawable.ic_downloads,
                        "Abrir panel de Descargas",
                        "Muestra las descargas activas y anteriores.",
                        () -> ZenPanelController.showDownloads(
                                activity, browser, null)));
                break;

            case "privacy":
                addTitle(page, "Privacidad y datos", "Historial y datos locales");
                page.addView(toggle(
                        "Guardar historial",
                        "Registra páginas visitadas en el dispositivo.",
                        ZenPanelController.KEY_SAVE_HISTORY,
                        true));
                page.addView(action(
                        R.drawable.ic_history,
                        "Borrar historial",
                        "Elimina todos los registros locales.",
                        () -> confirm(
                                "Borrar historial",
                                "Esta acción no se puede deshacer.",
                                () -> {
                                    HistoryStore.clear(activity);
                                    toast("Historial eliminado");
                                })));
                page.addView(action(
                        R.drawable.ic_star,
                        "Borrar favoritos",
                        "Elimina todos los favoritos guardados.",
                        () -> confirm(
                                "Borrar favoritos",
                                "Esta acción no se puede deshacer.",
                                () -> {
                                    FavoritesStore.clear(activity);
                                    toast("Favoritos eliminados");
                                })));
                break;

            case "performance":
                addTitle(page, "Rendimiento y almacenamiento",
                        "Renderizado y cachés personalizadas");
                page.addView(toggle(
                        "Recuperación visual automática",
                        "Repara superficies de GeckoView que dejan de pintar.",
                        ZenPanelController.KEY_PAGE_RECOVERY,
                        true));
                page.addView(toggle(
                        "Modo ahorro de interfaz",
                        "Reduce actualizaciones visuales en dispositivos modestos.",
                        ZenPanelController.KEY_ECO_RENDER,
                        false));
                page.addView(toggle(
                        "Mantenimiento automático",
                        "Limita iconos y archivos contextuales sin borrar la caché web.",
                        ZenPanelController.KEY_AUTO_CACHE,
                        true));
                page.addView(action(
                        R.drawable.ic_settings_performance,
                        "Optimizar cachés de Zen",
                        "Limpia iconos y temporales contextuales.",
                        () -> {
                            RemoteAssetLoader.clear(activity);
                            ContextMediaStore.clear(activity);
                            toast("Cachés personalizadas limpiadas");
                        }));
                break;

            case "accessibility":
                addTitle(page, "Accesibilidad", "Sonido, tacto y navegación");
                page.addView(toggle(
                        "Sonido de tecla mecánica",
                        "Reproduce el clic de la tecla Z.",
                        ZenPanelController.KEY_KEY_SOUND,
                        true));
                page.addView(toggle(
                        "Respuesta háptica",
                        "Vibración ligera al presionar la tecla.",
                        ZenPanelController.KEY_KEY_HAPTICS,
                        true));
                page.addView(toggle(
                        "Gesto desde el borde",
                        "Desliza desde la izquierda para abrir el panel.",
                        ZenPanelController.KEY_EDGE_SWIPE,
                        true));
                page.addView(toggle(
                        "Mantener pantalla encendida",
                        "Evita que Android apague la pantalla durante la navegación.",
                        ZenPanelController.KEY_KEEP_AWAKE,
                        false));
                break;

            default:
                addTitle(page, "Avanzado", "GeckoView y diagnóstico");
                page.addView(toggle(
                        "JavaScript",
                        "Permite JavaScript en páginas web.",
                        ZenPanelController.KEY_JAVASCRIPT,
                        true));
                page.addView(toggle(
                        "Reproducción automática",
                        "Permite autoplay cuando el sitio lo solicita.",
                        ZenPanelController.KEY_AUTOPLAY,
                        true));
                page.addView(info(
                        "Zen Browser " + BuildConfig.VERSION_NAME
                                + "\nGeckoView 152 · Android"
                                + "\nPágina interna: "
                                + BrowserRepository.INTERNAL_SETTINGS_URL));
                page.addView(info(
                        "Los cambios se guardan localmente. Las opciones de GeckoView "
                                + "se aplican a las sesiones activas sin abrir una página externa."));
                break;
        }

        setContent(scroll);
    }

    private void addHomeUrl(LinearLayout page) {
        TextView label = text("PÁGINA DE INICIO", 10, R.color.zen_muted);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setLetterSpacing(.10f);
        label.setPadding(dp(5), dp(14), 0, dp(5));
        page.addView(label);

        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setText(preferences.getString(
                ZenPanelController.KEY_HOME_URL, "about:blank"));
        input.setHint("about:blank o https://...");
        input.setTextSize(14);
        input.setTextColor(activity.getColor(R.color.zen_text));
        input.setHintTextColor(activity.getColor(R.color.zen_muted));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackgroundResource(R.drawable.bg_address_rounder);
        page.addView(input, rowParams(48));

        Runnable save = () -> {
            String value = input.getText().toString().trim();
            if (value.isEmpty()) value = "about:blank";
            preferences.edit()
                    .putString(ZenPanelController.KEY_HOME_URL, value)
                    .apply();
            changed();
            toast("Página de inicio guardada");
        };
        input.setOnEditorActionListener((view, actionId, event) -> {
            save.run();
            return true;
        });
        page.addView(action(
                R.drawable.ic_home,
                "Guardar página de inicio",
                "Aplica la dirección escrita arriba.",
                save));
    }

    private void addSearchEngines(LinearLayout page) {
        TextView section = text("MOTOR DE BÚSQUEDA", 10, R.color.zen_muted);
        section.setTypeface(Typeface.DEFAULT_BOLD);
        section.setLetterSpacing(.10f);
        section.setPadding(dp(5), dp(14), 0, dp(5));
        page.addView(section);

        SearchEngine selected = browser.getSearchEngine();
        for (SearchEngine engine : SearchEngine.values()) {
            String prefix = engine == selected ? "✓  " : "";
            page.addView(action(
                    R.drawable.ic_search,
                    prefix + engine.displayName,
                    engine == selected ? "Motor seleccionado" : "Seleccionar este motor",
                    () -> {
                        browser.setSearchEngine(engine);
                        changed();
                        showCategory("search");
                    }));
        }
    }

    private View toggle(
            String title,
            String summary,
            String key,
            boolean defaultValue) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(6), dp(7), dp(6));
        row.setBackgroundResource(R.drawable.bg_settings_section);

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        TextView primary = text(title, 13, R.color.zen_text);
        primary.setTypeface(Typeface.DEFAULT_BOLD);
        TextView secondary = text(summary, 10, R.color.zen_muted);
        secondary.setMaxLines(2);
        secondary.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(primary);
        labels.addView(secondary);
        row.addView(labels, new LinearLayout.LayoutParams(
                0, dp(52), 1f));

        CheckBox check = new CheckBox(activity);
        check.setButtonTintList(new ColorStateList(
                new int[][] {
                        new int[] {android.R.attr.state_checked},
                        new int[] {}
                },
                new int[] {
                        activity.getColor(R.color.zen_accent),
                        activity.getColor(R.color.zen_muted)
                }));
        check.setChecked(preferences.getBoolean(key, defaultValue));
        check.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean(key, checked).apply();
            changed();
        });
        row.addView(check, new LinearLayout.LayoutParams(dp(48), dp(48)));
        row.setOnClickListener(v -> check.setChecked(!check.isChecked()));

        LinearLayout.LayoutParams params = rowParams(66);
        params.setMargins(0, dp(7), 0, 0);
        row.setLayoutParams(params);
        return row;
    }

    private View action(
            int iconRes,
            String title,
            String summary,
            Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(11), dp(6), dp(9), dp(6));
        row.setBackgroundResource(R.drawable.bg_settings_section);

        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(
                activity.getColor(R.color.zen_text)));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        row.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.setPadding(dp(10), 0, 0, 0);
        TextView primary = text(title, 13, R.color.zen_text);
        primary.setTypeface(Typeface.DEFAULT_BOLD);
        TextView secondary = text(summary, 10, R.color.zen_muted);
        secondary.setSingleLine(true);
        secondary.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(primary);
        labels.addView(secondary);
        row.addView(labels, new LinearLayout.LayoutParams(
                0, dp(50), 1f));

        row.setOnClickListener(v -> {
            if (action == null) return;
            v.setEnabled(false);
            try {
                action.run();
            } finally {
                v.postDelayed(() -> {
                    if (v.isAttachedToWindow()) v.setEnabled(true);
                }, 280L);
            }
        });

        LinearLayout.LayoutParams params = rowParams(62);
        params.setMargins(0, dp(7), 0, 0);
        row.setLayoutParams(params);
        return row;
    }

    private View info(String value) {
        TextView info = text(value, 11, R.color.zen_muted);
        info.setPadding(dp(13), dp(11), dp(13), dp(11));
        info.setBackgroundResource(R.drawable.bg_settings_section);
        info.setLineSpacing(0f, 1.15f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(7), 0, 0);
        info.setLayoutParams(params);
        return info;
    }

    private void addTitle(LinearLayout page, String title, String summary) {
        TextView primary = text(title, 19, R.color.zen_text);
        primary.setTypeface(Typeface.DEFAULT_BOLD);
        primary.setPadding(dp(5), dp(16), dp(5), 0);
        page.addView(primary);
        TextView secondary = text(summary, 11, R.color.zen_muted);
        secondary.setPadding(dp(5), dp(2), dp(5), dp(4));
        page.addView(secondary);
    }

    private ScrollView scroll() {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        return scroll;
    }

    private LinearLayout page() {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(2), dp(2), dp(2), dp(18));
        return page;
    }

    private void setContent(View content) {
        contentHost.removeAllViews();
        contentHost.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void confirm(String title, String message, Runnable action) {
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Continuar", (dialog, which) -> {
                    if (action != null) action.run();
                })
                .show();
    }

    private void changed() {
        if (onChanged != null) onChanged.run();
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    private TextView text(String value, float sp, int colorRes) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(activity.getColor(colorRes));
        view.setFontFeatureSettings("kern");
        return view;
    }

    private LinearLayout.LayoutParams rowParams(int heightDp) {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp));
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
