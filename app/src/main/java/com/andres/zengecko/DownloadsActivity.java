package com.andres.zengecko;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

public final class DownloadsActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout list;
    private final Runnable refreshTask = new Runnable() {
        @Override public void run() { render(); handler.postDelayed(this, 700L); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        hideBars();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(16));
        root.setBackgroundColor(getColor(R.color.zen_bg));

        TextView title = text("Descargas", 24, R.color.zen_text);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        TextView hint = text("Archivos recibidos por GeckoView", 13, R.color.zen_muted);
        root.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button system = new Button(this);
        system.setText("Abrir descargas de Android");
        system.setOnClickListener(v -> startActivity(new Intent(DownloadManagerIntent())));
        root.addView(system, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        setContentView(root);
    }

    private String DownloadManagerIntent() {
        return "android.intent.action.VIEW_DOWNLOADS";
    }

    @Override protected void onResume() { super.onResume(); hideBars(); handler.post(refreshTask); }
    @Override protected void onPause() { handler.removeCallbacks(refreshTask); super.onPause(); }

    private void render() {
        if (list == null) return;
        list.removeAllViews();
        List<DownloadStore.Record> records = DownloadStore.list(this);
        if (records.isEmpty()) {
            TextView empty = text("Todavía no hay descargas", 15, R.color.zen_muted);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(180)));
            return;
        }
        for (DownloadStore.Record record : records) list.addView(row(record));
    }

    private View row(DownloadStore.Record record) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackgroundResource(R.drawable.bg_tab_idle);

        TextView name = text(record.name, 14, R.color.zen_text);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(name);

        String status = statusText(record);
        TextView detail = text(status, 12, R.color.zen_muted);
        detail.setPadding(0, dp(4), 0, dp(7));
        card.addView(detail);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        if (DownloadStore.COMPLETE.equals(record.status)) {
            actions.addView(action("Abrir", v -> open(record)));
        } else if (DownloadStore.DOWNLOADING.equals(record.status)
                || DownloadStore.QUEUED.equals(record.status)) {
            actions.addView(action("Cancelar", v -> DownloadStore.cancel(this, record.id)));
        } else if (DownloadStore.FAILED.equals(record.status)
                || DownloadStore.CANCELLED.equals(record.status)) {
            actions.addView(action("Reintentar", v -> DownloadStore.retry(this, record.id)));
        }
        actions.addView(action("Eliminar", v -> DownloadStore.remove(this, record.id)));
        card.addView(actions);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(7));
        card.setLayoutParams(params);
        return card;
    }

    private View action(String label, View.OnClickListener listener) {
        TextView button = text(label, 12, R.color.zen_accent);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackgroundResource(R.drawable.bg_button);
        button.setOnClickListener(listener);
        return button;
    }

    private String statusText(DownloadStore.Record record) {
        if (DownloadStore.COMPLETE.equals(record.status)) return "Completada · " + size(record.bytes);
        if (DownloadStore.CANCELLED.equals(record.status)) return "Cancelada";
        if (DownloadStore.FAILED.equals(record.status)) return "Falló · " + record.error;
        if (record.total > 0) {
            int progress = (int) Math.min(100L, record.bytes * 100L / record.total);
            return "Descargando · " + progress + "% · " + size(record.bytes) + " / " + size(record.total);
        }
        return "Descargando · " + size(record.bytes);
    }

    private String size(long bytes) {
        if (bytes < 0) return "tamaño desconocido";
        if (bytes >= 1024L * 1024L * 1024L) return String.format(java.util.Locale.ROOT, "%.1f GB", bytes / 1073741824f);
        if (bytes >= 1024L * 1024L) return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1048576f);
        if (bytes >= 1024L) return String.format(java.util.Locale.ROOT, "%.1f KB", bytes / 1024f);
        return bytes + " B";
    }

    private void open(DownloadStore.Record record) {
        try {
            if (record.contentUri == null || record.contentUri.isEmpty()) return;
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(record.contentUri), record.mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Abrir descarga"));
        } catch (Exception ignored) {
            startActivity(new Intent(DownloadManagerIntent()));
        }
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(getColor(color));
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
