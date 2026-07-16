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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Clean native settings page rendered as an internal Zen Browser tab. */
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
        preferences = activity.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE);
    }

    public static View create(
            Activity activity,
            BrowserRepository browser,
            Runnable onChanged) {
        return new NativeSettingsPage(activity, browser, onChanged).build();
    }

    private View build() {
        FrameLayout root = new FrameLayout(activity);
        ZenLiquidGlass.applyGenericSurface(activity, root, R.drawable.bg_settings_page);

        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(18), dp(16), dp(18), dp(18));
        root.addView(shell, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(2), 0, dp(2), 0);

        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_settings);
        icon.setImageTintList(ColorStateList.valueOf(
                activity.getColor(R.color.zen_accent)));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        header.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(30)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(11), 0, 0, 0);
        TextView title = text("Configuración", 22, R.color.zen_text);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView subtitle = text(
                "Preferencias de Zen Browser", 11, R.color.zen_muted);
        labels.addView(title);
        labels.addView(subtitle);
        header.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView version = text("v" + BuildConfig.VERSION_NAME, 10, R.color.zen_muted);
        version.setGravity(Gravity.CENTER);
        header.addView(version, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        shell.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

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
        search.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
        search.setCompoundDrawablePadding(dp(9));
        search.setPadding(dp(15), 0, dp(15), 0);
        search.setBackgroundResource(R.drawable.bg_address_rounder);
        page.addView(search, rowParams(48));

        TextView section = sectionLabel("CATEGORÍAS");
        page.addView(section);

        List<View> rows = new ArrayList<>();
        rows.add(categoryRow("general inicio sesión salida", R.drawable.ic_settings_general,
                "General", "Inicio, restauración y salida", "general"));
        rows.add(categoryRow("apariencia tema noche día color interfaz", R.drawable.ic_settings_appearance,
                "Apariencia", "Tema, interfaz y movimiento", "appearance"));
        rows.add(categoryRow("búsqueda motor sugerencias resultados", R.drawable.ic_settings_search,
                "Búsqueda", "Motor, sugerencias y pestañas", "search"));
        rows.add(categoryRow("pestañas espacios escritorio gestos accesos", R.drawable.ic_settings_tabs,
                "Pestañas y espacios", "Escritorio, cierres y accesos", "tabs"));
        rows.add(categoryRow("descargas avisos confirmación red", R.drawable.ic_settings_downloads,
                "Descargas", "Notificaciones y uso de datos", "downloads"));
        rows.add(categoryRow("privacidad datos historial favoritos limpiar", R.drawable.ic_settings_privacy,
                "Privacidad y datos", "Historial, favoritos y datos locales", "privacy"));
        rows.add(categoryRow("rendimiento almacenamiento caché render recuperación", R.drawable.ic_settings_performance,
                "Rendimiento y almacenamiento", "Recuperación visual y cachés", "performance"));
        rows.add(categoryRow("accesibilidad sonido vibración borde pantalla", R.drawable.ic_settings_accessibility,
                "Accesibilidad", "Sonido, tacto y navegación", "accessibility"));
        rows.add(categoryRow("avanzado gecko javascript autoplay diagnóstico", R.drawable.ic_settings_advanced,
                "Avanzado", "GeckoView, JavaScript y diagnóstico", "advanced"));

        for (int index = 0; index < rows.size(); index++) {
            View row = rows.get(index);
            page.addView(row);
            if (index < rows.size() - 1) page.addView(divider());
        }

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(
                    CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(
                    CharSequence value, int start, int before, int count) {
                String query = value == null
                        ? "" : value.toString().trim().toLowerCase(Locale.ROOT);
                for (View row : rows) {
                    String keywords = String.valueOf(row.getTag());
                    row.setVisibility(query.isEmpty() || keywords.contains(query)
                            ? View.VISIBLE : View.GONE);
                }
            }
            @Override public void afterTextChanged(Editable value) { }
        });
        setContent(scroll);
    }

    private View categoryRow(
            String keywords,
            int iconRes,
            String title,
            String summary,
            String category) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(6), dp(6), dp(6));
        ZenLiquidGlass.applyGenericSurface(activity, row, R.drawable.bg_settings_row);
        row.setTag((keywords + " " + title + " " + summary).toLowerCase(Locale.ROOT));

        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(
                activity.getColor(R.color.zen_accent)));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        row.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(30)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.setPadding(dp(13), 0, dp(8), 0);
        TextView primary = text(title, 14, R.color.zen_text);
        primary.setTypeface(Typeface.DEFAULT_BOLD);
        TextView secondary = text(summary, 10, R.color.zen_muted);
        secondary.setSingleLine(true);
        secondary.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(primary);
        labels.addView(secondary);
        row.addView(labels, new LinearLayout.LayoutParams(0, dp(48), 1f));

        TextView arrow = text("›", 24, R.color.zen_muted);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(40)));
        row.setOnClickListener(v -> showCategory(category));
        row.setLayoutParams(rowParams(60));
        return row;
    }

    private void showCategory(String category) {
        ScrollView scroll = scroll();
        LinearLayout page = page();
        scroll.addView(page);

        TextView back = text("‹  Todas las categorías", 13, R.color.zen_accent);
        back.setTypeface(Typeface.DEFAULT_BOLD);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setPadding(dp(5), 0, dp(5), 0);
        back.setBackgroundResource(R.drawable.bg_settings_row);
        back.setOnClickListener(v -> showHome());
        page.addView(back, rowParams(42));

        switch (category) {
            case "general":
                addTitle(page, "General", "Inicio, sesión y salida");
                addHomeUrl(page);
                addToggle(page, "Restaurar sesión",
                        "Conserva pestañas y espacios al iniciar.",
                        ZenPanelController.KEY_RESTORE_SESSION, true);
                addToggle(page, "Confirmar antes de salir",
                        "Pregunta antes de cerrar completamente Zen Browser.",
                        ZenPanelController.KEY_CONFIRM_EXIT, true);
                addAction(page, R.drawable.ic_open_new,
                        "Navegador predeterminado",
                        "Abre la selección de navegador de Android.",
                        () -> BrowserRoleHelper.requestDefaultBrowser(activity));
                break;
            case "appearance":
                addTitle(page, "Apariencia", "Tema, colores e interfaz");
                page.addView(themeSelector());
                page.addView(surfaceStyleSelector());
                page.addView(glassIntensitySelector());
                addToggle(page, "Reducir efectos para ahorrar batería",
                        "Usa una variante más ligera del cristal y sus animaciones.",
                        ZenLiquidGlass.KEY_REDUCE_EFFECTS, false);
                addToggle(page, "Animaciones",
                        "Activa transiciones breves y discretas.",
                        ZenPanelController.KEY_ANIMATIONS, true);
                addToggle(page, "Mostrar dirección",
                        "Muestra el dominio de la página activa.",
                        ZenPanelController.KEY_SHOW_ADDRESS, true);
                addToggle(page, "Mostrar progreso",
                        "Línea de progreso bajo la barra superior.",
                        ZenPanelController.KEY_SHOW_PROGRESS, true);
                addToggle(page, "Fondo de Inicio",
                        "Muestra el bonsái en el tema Noche.",
                        ZenPanelController.KEY_HOME_BACKGROUND, true);
                addToggle(page, "Movimiento del fondo",
                        "Aplica un movimiento lento al fondo de Inicio.",
                        ZenPanelController.KEY_HOME_MOTION, true);
                break;
            case "search":
                addTitle(page, "Búsqueda", "Motor y resultados");
                addSearchEngines(page);
                addToggle(page, "Sugerencias",
                        "Muestra sugerencias mientras escribes.",
                        ZenPanelController.KEY_SEARCH_SUGGESTIONS, true);
                addToggle(page, "Buscar en pestañas abiertas",
                        "Incluye pestañas actuales entre los resultados.",
                        ZenPanelController.KEY_SEARCH_TABS, true);
                break;
            case "tabs":
                addTitle(page, "Pestañas y espacios", "Comportamiento y accesos");
                addToggle(page, "Accesos rápidos en pestaña nueva",
                        "Abre cada acceso en una pestaña independiente.",
                        ZenPanelController.KEY_QUICK_NEW_TAB, false);
                addToggle(page, "Deslizar para cerrar",
                        "Cierra una pestaña al desplazar su fila.",
                        ZenPanelController.KEY_SWIPE_CLOSE, true);
                addToggle(page, "Mostrar botón de cierre",
                        "Muestra la X en cada fila de pestaña.",
                        ZenPanelController.KEY_TAB_CLOSE_BUTTON, true);
                addToggle(page, "Modo escritorio predeterminado",
                        "Las pestañas nuevas solicitan sitios de escritorio.",
                        ZenPanelController.KEY_DESKTOP_DEFAULT, false);
                break;
            case "downloads":
                addTitle(page, "Descargas", "Avisos y uso de red");
                addToggle(page, "Notificación flotante",
                        "Muestra el progreso compacto de la descarga.",
                        ZenPanelController.KEY_DOWNLOAD_NOTICE, true);
                addToggle(page, "Confirmar descargas",
                        "Solicita confirmación antes de iniciar.",
                        ZenPanelController.KEY_CONFIRM_DOWNLOAD, false);
                addToggle(page, "Permitir datos móviles",
                        "Autoriza descargas mediante redes medidas.",
                        ZenPanelController.KEY_DOWNLOADS_METERED, true);
                addAction(page, R.drawable.ic_downloads, "Abrir Descargas",
                        "Muestra descargas activas y anteriores.",
                        () -> ZenPanelController.showDownloads(activity, browser, null));
                break;
            case "privacy":
                addTitle(page, "Privacidad y datos", "Historial y datos locales");
                addToggle(page, "Guardar historial",
                        "Registra páginas visitadas en el dispositivo.",
                        ZenPanelController.KEY_SAVE_HISTORY, true);
                addAction(page, R.drawable.ic_history, "Borrar historial",
                        "Elimina todos los registros locales.",
                        () -> confirm("Borrar historial",
                                "Esta acción no se puede deshacer.",
                                () -> { HistoryStore.clear(activity); toast("Historial eliminado"); }));
                addAction(page, R.drawable.ic_star, "Borrar favoritos",
                        "Elimina todos los favoritos guardados.",
                        () -> confirm("Borrar favoritos",
                                "Esta acción no se puede deshacer.",
                                () -> { FavoritesStore.clear(activity); toast("Favoritos eliminados"); }));
                break;
            case "performance":
                addTitle(page, "Rendimiento y almacenamiento", "Renderizado y cachés");
                addToggle(page, "Recuperación visual automática",
                        "Repara superficies de GeckoView que dejan de pintar.",
                        ZenPanelController.KEY_PAGE_RECOVERY, true);
                addToggle(page, "Modo ahorro de interfaz",
                        "Reduce actualizaciones visuales en dispositivos modestos.",
                        ZenPanelController.KEY_ECO_RENDER, false);
                addToggle(page, "Mantenimiento automático",
                        "Limita iconos y temporales sin borrar la caché web.",
                        ZenPanelController.KEY_AUTO_CACHE, true);
                addAction(page, R.drawable.ic_settings_performance,
                        "Optimizar cachés de Zen",
                        "Limpia iconos y temporales contextuales.",
                        () -> {
                            RemoteAssetLoader.clear(activity);
                            ContextMediaStore.clear(activity);
                            toast("Cachés personalizadas limpiadas");
                        });
                break;
            case "accessibility":
                addTitle(page, "Accesibilidad", "Sonido, tacto y navegación");
                addToggle(page, "Sonido de tecla mecánica",
                        "Reproduce el clic de la tecla Z.",
                        ZenPanelController.KEY_KEY_SOUND, true);
                addToggle(page, "Respuesta háptica",
                        "Vibración ligera al presionar la tecla.",
                        ZenPanelController.KEY_KEY_HAPTICS, true);
                addToggle(page, "Gesto desde el borde",
                        "Desliza desde la izquierda para abrir el panel.",
                        ZenPanelController.KEY_EDGE_SWIPE, true);
                addToggle(page, "Mantener pantalla encendida",
                        "Evita que Android apague la pantalla al navegar.",
                        ZenPanelController.KEY_KEEP_AWAKE, false);
                break;
            default:
                addTitle(page, "Avanzado", "GeckoView y diagnóstico");
                addToggle(page, "JavaScript", "Permite JavaScript en páginas web.",
                        ZenPanelController.KEY_JAVASCRIPT, true);
                addToggle(page, "Reproducción automática",
                        "Permite autoplay cuando el sitio lo solicita.",
                        ZenPanelController.KEY_AUTOPLAY, true);
                addInfo(page, "Zen Browser " + BuildConfig.VERSION_NAME
                        + "\nGeckoView 152 · Android"
                        + "\nPágina interna: " + BrowserRepository.INTERNAL_SETTINGS_URL);
                break;
        }
        setContent(scroll);
    }

    private View themeSelector() {
        LinearLayout section = new LinearLayout(activity);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(5), dp(10), dp(5), dp(10));
        TextView label = text("TEMA", 10, R.color.zen_muted);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setLetterSpacing(.11f);
        section.addView(label);

        LinearLayout choices = new LinearLayout(activity);
        choices.setGravity(Gravity.CENTER);
        choices.setPadding(dp(3), dp(3), dp(3), dp(3));
        choices.setBackgroundResource(R.drawable.bg_profile_selector);
        boolean day = ZenTheme.isDay(activity);
        TextView night = themeChoice("☾  Noche", !day, ZenTheme.MODE_NIGHT);
        TextView light = themeChoice("☀  Día", day, ZenTheme.MODE_DAY);
        choices.addView(night, new LinearLayout.LayoutParams(0, dp(42), 1f));
        choices.addView(light, new LinearLayout.LayoutParams(0, dp(42), 1f));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        params.setMargins(0, dp(7), 0, dp(5));
        section.addView(choices, params);
        return section;
    }

    private View surfaceStyleSelector() {
        LinearLayout section = new LinearLayout(activity);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(5), dp(10), dp(5), dp(10));

        TextView label = text("ESTILO DE SUPERFICIES", 10, R.color.zen_muted);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setLetterSpacing(.11f);
        section.addView(label);

        LinearLayout choices = new LinearLayout(activity);
        choices.setGravity(Gravity.CENTER);
        choices.setPadding(dp(3), dp(3), dp(3), dp(3));
        choices.setBackgroundResource(R.drawable.bg_profile_selector);
        boolean glass = ZenLiquidGlass.isEnabled(activity);
        choices.addView(surfaceStyleChoice("Sólido", !glass,
                ZenLiquidGlass.STYLE_SOLID), new LinearLayout.LayoutParams(0, dp(42), 1f));
        choices.addView(surfaceStyleChoice("Cristal líquido · Beta", glass,
                ZenLiquidGlass.STYLE_LIQUID_GLASS),
                new LinearLayout.LayoutParams(0, dp(42), 1f));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        params.setMargins(0, dp(7), 0, dp(5));
        section.addView(choices, params);
        return section;
    }

    private TextView surfaceStyleChoice(String label, boolean selected, String style) {
        TextView choice = text(label, 12,
                selected ? R.color.zen_text : R.color.zen_muted);
        choice.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        choice.setGravity(Gravity.CENTER);
        choice.setBackgroundResource(selected
                ? R.drawable.bg_settings_theme_selected
                : R.drawable.bg_workspace_idle);
        choice.setOnClickListener(v -> {
            preferences.edit().putString(ZenLiquidGlass.KEY_STYLE, style).apply();
            changed();
            activity.recreate();
        });
        return choice;
    }

    private View glassIntensitySelector() {
        LinearLayout section = new LinearLayout(activity);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(5), dp(8), dp(5), dp(10));

        boolean glass = ZenLiquidGlass.isEnabled(activity);
        TextView heading = text("INTENSIDAD", 10, R.color.zen_muted);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setLetterSpacing(.11f);
        section.addView(heading);

        TextView status = text(glass
                ? "Calidad: " + qualityLabel(ZenLiquidGlass.quality(activity))
                : "Activa Cristal líquido para ajustar este control.",
                10, R.color.zen_muted);
        status.setPadding(0, dp(4), 0, dp(3));
        section.addView(status);

        SeekBar slider = new SeekBar(activity);
        slider.setMax(100);
        slider.setProgress(ZenLiquidGlass.intensity(activity));
        slider.setEnabled(glass);
        slider.setAlpha(glass ? 1f : .42f);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                preferences.edit().putInt(ZenLiquidGlass.KEY_INTENSITY, progress).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                changed();
            }
        });
        section.addView(slider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        return section;
    }

    private String qualityLabel(String quality) {
        if (ZenLiquidGlass.QUALITY_FULL.equals(quality)) return "Desenfoque real";
        if (ZenLiquidGlass.QUALITY_REDUCED.equals(quality)) return "Reducida";
        return "Compatibilidad sin blur";
    }

    private TextView themeChoice(String label, boolean selected, String mode) {
        TextView choice = text(label, 13, selected ? R.color.zen_text : R.color.zen_muted);
        choice.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        choice.setGravity(Gravity.CENTER);
        choice.setBackgroundResource(selected
                ? R.drawable.bg_settings_theme_selected
                : R.drawable.bg_workspace_idle);
        choice.setOnClickListener(v -> ZenTheme.setMode(activity, mode));
        return choice;
    }

    private void addToggle(
            LinearLayout page,
            String title,
            String summary,
            String key,
            boolean defaultValue) {
        page.addView(toggleRow(title, summary, key, defaultValue));
        page.addView(divider());
    }

    private View toggleRow(
            String title,
            String summary,
            String key,
            boolean defaultValue) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(5), dp(3), dp(5));
        ZenLiquidGlass.applyGenericSurface(activity, row, R.drawable.bg_settings_row);

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
        row.addView(labels, new LinearLayout.LayoutParams(0, dp(52), 1f));

        Switch toggle = new Switch(activity);
        toggle.setShowText(false);
        toggle.setChecked(preferences.getBoolean(key, defaultValue));
        toggle.setThumbTintList(new ColorStateList(
                new int[][] { new int[] {android.R.attr.state_checked}, new int[] {} },
                new int[] { activity.getColor(R.color.zen_accent),
                        activity.getColor(R.color.zen_muted) }));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean(key, checked).apply();
            changed();
        });
        row.addView(toggle, new LinearLayout.LayoutParams(dp(54), dp(48)));
        row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
        row.setLayoutParams(rowParams(64));
        return row;
    }

    private void addAction(
            LinearLayout page,
            int iconRes,
            String title,
            String summary,
            Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(5), dp(6), dp(5));
        ZenLiquidGlass.applyGenericSurface(activity, row, R.drawable.bg_settings_row);

        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(activity.getColor(R.color.zen_text)));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        row.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(13), 0, 0, 0);
        TextView primary = text(title, 13, R.color.zen_text);
        primary.setTypeface(Typeface.DEFAULT_BOLD);
        TextView secondary = text(summary, 10, R.color.zen_muted);
        secondary.setSingleLine(true);
        secondary.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(primary);
        labels.addView(secondary);
        row.addView(labels, new LinearLayout.LayoutParams(0, dp(50), 1f));
        row.setOnClickListener(v -> { if (action != null) action.run(); });
        page.addView(row, rowParams(60));
        page.addView(divider());
    }

    private void addHomeUrl(LinearLayout page) {
        page.addView(sectionLabel("PÁGINA DE INICIO"));
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setText(preferences.getString(ZenPanelController.KEY_HOME_URL, "about:blank"));
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
            preferences.edit().putString(ZenPanelController.KEY_HOME_URL, value).apply();
            changed();
            toast("Página de inicio guardada");
        };
        input.setOnEditorActionListener((view, actionId, event) -> {
            save.run();
            return true;
        });
        addAction(page, R.drawable.ic_home, "Guardar página de inicio",
                "Aplica la dirección escrita arriba.", save);
    }

    private void addSearchEngines(LinearLayout page) {
        page.addView(sectionLabel("MOTOR DE BÚSQUEDA"));
        SearchEngine selected = browser.getSearchEngine();
        for (SearchEngine engine : SearchEngine.values()) {
            addAction(page, R.drawable.ic_search,
                    (engine == selected ? "✓  " : "") + engine.displayName,
                    engine == selected ? "Motor seleccionado" : "Seleccionar este motor",
                    () -> {
                        browser.setSearchEngine(engine);
                        changed();
                        showCategory("search");
                    });
        }
    }

    private void addTitle(LinearLayout page, String title, String summary) {
        TextView primary = text(title, 21, R.color.zen_text);
        primary.setTypeface(Typeface.DEFAULT_BOLD);
        primary.setPadding(dp(5), dp(18), dp(5), 0);
        page.addView(primary);
        TextView secondary = text(summary, 11, R.color.zen_muted);
        secondary.setPadding(dp(5), dp(2), dp(5), dp(10));
        page.addView(secondary);
    }

    private void addInfo(LinearLayout page, String value) {
        TextView info = text(value, 11, R.color.zen_muted);
        info.setPadding(dp(8), dp(10), dp(8), dp(10));
        info.setLineSpacing(0f, 1.18f);
        page.addView(info);
    }

    private TextView sectionLabel(String value) {
        TextView label = text(value, 10, R.color.zen_muted);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setLetterSpacing(.11f);
        label.setPadding(dp(7), dp(18), dp(7), dp(7));
        return label;
    }

    private View divider() {
        View divider = new View(activity);
        divider.setBackgroundColor(activity.getColor(R.color.zen_border));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.setMargins(dp(48), 0, dp(6), 0);
        divider.setLayoutParams(params);
        return divider;
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
        page.setPadding(dp(1), dp(1), dp(1), dp(22));
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
