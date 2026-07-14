package com.andres.zengecko;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class SettingsActivity extends Activity {
    private LinearLayout engines;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        hideBars();

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(24));
        root.setBackgroundColor(getColor(R.color.zen_bg));
        scroll.addView(root);

        root.addView(title("Configuración", 25));
        root.addView(section("MOTOR DE BÚSQUEDA"));
        engines = new LinearLayout(this);
        engines.setOrientation(LinearLayout.VERTICAL);
        root.addView(engines);
        renderEngines();

        root.addView(section("APARIENCIA"));
        root.addView(info("Tema", "Zen AMOLED negro"));
        root.addView(info("Interfaz", "Compacta y de borde a borde"));
        root.addView(info("Barras de Android", "Ocultas por defecto; aparecen temporalmente con un gesto"));

        root.addView(section("VERSIÓN"));
        root.addView(info("ZenGecko", BuildConfig.VERSION_NAME));
        setContentView(scroll);
    }

    @Override protected void onResume() { super.onResume(); hideBars(); }

    private void renderEngines() {
        engines.removeAllViews();
        SearchEngine active = BrowserRepository.get(this).getSearchEngine();
        for (SearchEngine engine : SearchEngine.values()) {
            TextView row = new TextView(this);
            row.setText((engine == active ? "●  " : "○  ") + engine.displayName);
            row.setTextColor(getColor(engine == active ? R.color.zen_text : R.color.zen_muted));
            row.setTextSize(15);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), 0, dp(14), 0);
            row.setBackgroundResource(engine == active ? R.drawable.bg_tab_active : R.drawable.bg_tab_idle);
            row.setOnClickListener(v -> {
                BrowserRepository.get(this).setSearchEngine(engine);
                renderEngines();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
            params.setMargins(0, 0, 0, dp(6));
            engines.addView(row, params);
        }
    }

    private TextView title(String value, float size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(getColor(R.color.zen_text));
        view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        view.setPadding(0, 0, 0, dp(18));
        return view;
    }

    private TextView section(String value) {
        TextView view = title(value, 11);
        view.setTextColor(getColor(R.color.zen_muted));
        view.setLetterSpacing(.12f);
        view.setPadding(0, dp(16), 0, dp(8));
        return view;
    }

    private TextView info(String label, String value) {
        TextView view = new TextView(this);
        view.setText(label + "\n" + value);
        view.setTextSize(14);
        view.setTextColor(getColor(R.color.zen_text));
        view.setPadding(dp(14), dp(9), dp(14), dp(9));
        view.setBackgroundResource(R.drawable.bg_tab_idle);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(6));
        view.setLayoutParams(params);
        return view;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void hideBars() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.systemBars());
            }
        }
    }
}
