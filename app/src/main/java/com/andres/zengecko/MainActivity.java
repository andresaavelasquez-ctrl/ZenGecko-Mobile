package com.andres.zengecko;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import com.andres.zengecko.model.BrowserTab;
import com.andres.zengecko.model.Workspace;

public final class MainActivity extends Activity implements BrowserRepository.Observer {
    private BrowserRepository browser;
    private GeckoView geckoView;
    private EditText addressBar;
    private TextView backButton;
    private TextView forwardButton;
    private TextView reloadButton;
    private ProgressBar progressBar;
    private LinearLayout root;
    private View fixedSidebar;
    private PopupWindow sidebarPopup;
    private GeckoSession displayedSession;
    private boolean wideLayout;
    private boolean rendering;
    private String lastSidebarFingerprint = "";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(getColor(R.color.zen_bg));
        window.setNavigationBarColor(getColor(R.color.zen_bg));

        browser = BrowserRepository.get(this);
        browser.addObserver(this);
        buildUi();

        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            browser.loadInActiveTab(intent.getDataString());
        }
        render();
    }

    @Override protected void onDestroy() {
        browser.removeObserver(this);
        if (sidebarPopup != null) sidebarPopup.dismiss();
        super.onDestroy();
    }

    @Override public void onBrowserStateChanged() { runOnUiThread(this::render); }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        buildUi();
        render();
    }

    @Override public void onBackPressed() {
        BrowserTab tab = browser.getActiveTab();
        if (sidebarPopup != null && sidebarPopup.isShowing()) { sidebarPopup.dismiss(); return; }
        if (tab != null && tab.canGoBack && tab.session != null) { tab.session.goBack(); return; }
        super.onBackPressed();
    }

    private void buildUi() {
        wideLayout = getResources().getConfiguration().screenWidthDp >= 720;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(getColor(R.color.zen_bg));

        if (wideLayout) {
            fixedSidebar = createSidebar();
            root.addView(fixedSidebar, new LinearLayout.LayoutParams(dp(286), ViewGroup.LayoutParams.MATCH_PARENT));
        } else fixedSidebar = null;

        LinearLayout browserColumn = new LinearLayout(this);
        browserColumn.setOrientation(LinearLayout.VERTICAL);
        browserColumn.setBackgroundColor(getColor(R.color.zen_bg));
        root.addView(browserColumn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        browserColumn.addView(createToolbar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.zen_accent)));
        progressBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
        browserColumn.addView(progressBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

        geckoView = new GeckoView(this);
        geckoView.setBackgroundColor(getColor(R.color.zen_bg));
        browserColumn.addView(geckoView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private View createToolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));
        bar.setBackgroundColor(getColor(R.color.zen_surface));

        TextView sidebar = navButton(wideLayout ? "◫" : "☰");
        sidebar.setContentDescription("Pestañas y espacios");
        sidebar.setOnClickListener(v -> { if (!wideLayout) showSidebarPopup(); });
        bar.addView(sidebar, square(44));

        backButton = navButton("‹");
        backButton.setContentDescription("Atrás");
        backButton.setOnClickListener(v -> { BrowserTab t=browser.getActiveTab(); if (t!=null && t.session!=null && t.canGoBack) t.session.goBack(); });
        bar.addView(backButton, square(42));

        forwardButton = navButton("›");
        forwardButton.setContentDescription("Adelante");
        forwardButton.setOnClickListener(v -> { BrowserTab t=browser.getActiveTab(); if (t!=null && t.session!=null && t.canGoForward) t.session.goForward(); });
        bar.addView(forwardButton, square(42));

        reloadButton = navButton("↻");
        reloadButton.setContentDescription("Recargar o detener");
        reloadButton.setOnClickListener(v -> {
            BrowserTab t=browser.getActiveTab();
            if (t!=null && t.session!=null) { if (t.loading) t.session.stop(); else t.session.reload(); }
        });
        bar.addView(reloadButton, square(42));

        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setTextColor(getColor(R.color.zen_text));
        addressBar.setHintTextColor(getColor(R.color.zen_muted));
        addressBar.setHint("Buscar o escribir una dirección");
        addressBar.setTextSize(15);
        addressBar.setSelectAllOnFocus(true);
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        addressBar.setBackgroundResource(R.drawable.bg_address);
        addressBar.setPadding(dp(14), 0, dp(14), 0);
        addressBar.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                browser.loadInActiveTab(addressBar.getText().toString());
                addressBar.clearFocus();
                ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(addressBar.getWindowToken(), 0);
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        addressParams.setMargins(dp(4), 0, dp(4), 0);
        bar.addView(addressBar, addressParams);

        TextView newTab = navButton("+");
        newTab.setContentDescription("Nueva pestaña");
        newTab.setOnClickListener(v -> { browser.addTab("about:blank", true); addressBar.requestFocus(); });
        bar.addView(newTab, square(44));
        return bar;
    }

    private View createSidebar() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(16), dp(12), dp(12));
        panel.setBackgroundColor(getColor(R.color.zen_surface));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("ZEN·GECKO", 18, R.color.zen_text);
        brand.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(44), 1f));
        TextView add = navButton("+");
        add.setOnClickListener(v -> browser.addTab("about:blank", true));
        header.addView(add, square(40));
        panel.addView(header);

        TextView workspacesLabel = label("ESPACIOS");
        panel.addView(workspacesLabel);
        HorizontalScrollView workspaceScroll = new HorizontalScrollView(this);
        workspaceScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout workspaceRow = new LinearLayout(this);
        workspaceRow.setOrientation(LinearLayout.HORIZONTAL);
        for (Workspace workspace : browser.getWorkspaces()) {
            TextView chip = text(workspace.name.substring(0, Math.min(1, workspace.name.length())).toUpperCase(), 14,
                    workspace.id.equals(browser.getActiveWorkspaceId()) ? R.color.zen_text : R.color.zen_muted);
            chip.setGravity(Gravity.CENTER);
            chip.setBackgroundResource(workspace.id.equals(browser.getActiveWorkspaceId()) ? R.drawable.bg_tab_active : R.drawable.bg_tab_idle);
            chip.setOnClickListener(v -> browser.switchWorkspace(workspace.id));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(42), dp(42)); cp.setMargins(0, 0, dp(8), 0);
            workspaceRow.addView(chip, cp);
        }
        TextView addWorkspace = text("＋", 17, R.color.zen_muted);
        addWorkspace.setGravity(Gravity.CENTER);
        addWorkspace.setBackgroundResource(R.drawable.bg_tab_idle);
        addWorkspace.setOnClickListener(v -> promptWorkspace());
        workspaceRow.addView(addWorkspace, new LinearLayout.LayoutParams(dp(42), dp(42)));
        workspaceScroll.addView(workspaceRow);
        panel.addView(workspaceScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        ScrollView tabsScroll = new ScrollView(this);
        tabsScroll.setFillViewport(true);
        LinearLayout tabsColumn = new LinearLayout(this);
        tabsColumn.setOrientation(LinearLayout.VERTICAL);

        List<BrowserTab> visible = browser.getVisibleTabs();
        boolean hasEssential = false;
        for (BrowserTab tab : visible) if (tab.essential) hasEssential = true;
        if (hasEssential) tabsColumn.addView(label("ESENCIALES"));
        for (BrowserTab tab : visible) if (tab.essential) tabsColumn.addView(tabRow(tab));
        tabsColumn.addView(label("PESTAÑAS"));
        for (BrowserTab tab : visible) if (!tab.essential) tabsColumn.addView(tabRow(tab));

        tabsScroll.addView(tabsColumn);
        panel.addView(tabsScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView essential = text("◆  Alternar esencial", 14, R.color.zen_muted);
        essential.setGravity(Gravity.CENTER_VERTICAL);
        essential.setPadding(dp(12), 0, dp(12), 0);
        essential.setBackgroundResource(R.drawable.bg_tab_idle);
        essential.setOnClickListener(v -> { BrowserTab t=browser.getActiveTab(); if(t!=null) browser.toggleEssential(t.id); });
        panel.addView(essential, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        return panel;
    }

    private View tabRow(BrowserTab tab) {
        boolean active = browser.getActiveTab() != null && browser.getActiveTab().id.equals(tab.id);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(5), dp(6), dp(5));
        row.setBackgroundResource(active ? R.drawable.bg_tab_active : R.drawable.bg_tab_idle);
        row.setOnClickListener(v -> { browser.selectTab(tab.id); if (sidebarPopup != null) sidebarPopup.dismiss(); });
        row.setOnLongClickListener(v -> { browser.toggleEssential(tab.id); return true; });

        TextView icon = text(tab.essential ? "◆" : "●", 11, tab.loading ? R.color.zen_accent : R.color.zen_muted);
        icon.setGravity(Gravity.CENTER);
        row.addView(icon, square(28));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(tab.title == null ? "Nueva pestaña" : tab.title, 14, R.color.zen_text);
        title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView url = text(shortUrl(tab.url), 11, R.color.zen_muted);
        url.setSingleLine(true); url.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(title); labels.addView(url);
        row.addView(labels, new LinearLayout.LayoutParams(0, dp(48), 1f));

        TextView close = text("×", 19, R.color.zen_muted);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> browser.closeTab(tab.id));
        row.addView(close, square(36));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        rp.setMargins(0, 0, 0, dp(4));
        row.setLayoutParams(rp);
        return row;
    }

    private void showSidebarPopup() {
        if (sidebarPopup != null) sidebarPopup.dismiss();
        View panel = createSidebar();
        int width = Math.min(dp(340), (int)(getResources().getDisplayMetrics().widthPixels * .88f));
        sidebarPopup = new PopupWindow(panel, width, ViewGroup.LayoutParams.MATCH_PARENT, true);
        sidebarPopup.setBackgroundDrawable(new ColorDrawable(getColor(R.color.zen_surface)));
        sidebarPopup.setElevation(dp(16));
        sidebarPopup.setAnimationStyle(android.R.style.Animation_Dialog);
        sidebarPopup.showAtLocation(root, Gravity.START | Gravity.TOP, 0, 0);
    }

    private void promptWorkspace() {
        EditText input = new EditText(this);
        input.setHint("Nombre del espacio");
        input.setSingleLine(true);
        int pad = dp(20); input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle("Nuevo espacio")
                .setView(input)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Crear", (d, w) -> browser.addWorkspace(input.getText().toString()))
                .show();
    }

    private void render() {
        if (rendering || geckoView == null) return;
        rendering = true;
        try {
            BrowserTab tab = browser.getActiveTab();
            if (tab == null) return;
            if (tab.session != null && displayedSession != tab.session) {
                geckoView.releaseSession();
                geckoView.setSession(tab.session);
                displayedSession = tab.session;
            }
            if (!addressBar.hasFocus()) addressBar.setText(tab.url == null ? "" : tab.url);
            setEnabled(backButton, tab.canGoBack);
            setEnabled(forwardButton, tab.canGoForward);
            reloadButton.setText(tab.loading ? "×" : "↻");
            progressBar.setVisibility(tab.loading ? View.VISIBLE : View.INVISIBLE);
            progressBar.setProgress(tab.progress);

            if (wideLayout && fixedSidebar != null) {
                String fingerprint = sidebarFingerprint();
                if (!fingerprint.equals(lastSidebarFingerprint)) {
                    int index = root.indexOfChild(fixedSidebar);
                    View replacement = createSidebar();
                    root.removeView(fixedSidebar);
                    root.addView(replacement, index, new LinearLayout.LayoutParams(dp(286), ViewGroup.LayoutParams.MATCH_PARENT));
                    fixedSidebar = replacement;
                    lastSidebarFingerprint = fingerprint;
                }
            }
        } finally { rendering = false; }
    }

    private TextView navButton(String value) {
        TextView view = text(value, 23, R.color.zen_text);
        view.setGravity(Gravity.CENTER);
        view.setBackgroundResource(R.drawable.bg_button);
        view.setFocusable(true);
        return view;
    }

    private TextView label(String value) {
        TextView view = text(value, 11, R.color.zen_muted);
        view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(8), dp(10), 0, dp(4));
        view.setLetterSpacing(.12f);
        return view;
    }

    private TextView text(String value, float sp, int colorRes) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(getColor(colorRes));
        view.setFontFeatureSettings("kern");
        return view;
    }

    private String sidebarFingerprint() {
        StringBuilder value = new StringBuilder(browser.getActiveWorkspaceId()).append('|');
        BrowserTab active = browser.getActiveTab();
        value.append(active == null ? "" : active.id).append('|');
        for (Workspace workspace : browser.getWorkspaces()) {
            value.append(workspace.id).append(':').append(workspace.name).append(';');
        }
        value.append('|');
        for (BrowserTab tab : browser.getVisibleTabs()) {
            value.append(tab.id).append(':')
                    .append(tab.title).append(':')
                    .append(tab.url).append(':')
                    .append(tab.essential).append(':')
                    .append(tab.loading).append(';');
        }
        return value.toString();
    }

    private LinearLayout.LayoutParams square(int sizeDp) { return new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void setEnabled(TextView view, boolean enabled) {
        view.setEnabled(enabled); view.setAlpha(enabled ? 1f : .35f);
    }

    private String shortUrl(String url) {
        if (url == null || url.equals("about:blank")) return "Página nueva";
        try {
            android.net.Uri uri = android.net.Uri.parse(url);
            return uri.getHost() == null ? url : uri.getHost();
        } catch (Exception ignored) { return url; }
    }
}
