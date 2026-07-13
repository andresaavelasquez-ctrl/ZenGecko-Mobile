package com.andres.zengecko;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
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
import android.widget.Toast;
import java.util.List;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import com.andres.zengecko.model.BrowserTab;
import com.andres.zengecko.model.Workspace;

public final class MainActivity extends Activity implements BrowserRepository.Observer {
    private static final String TAG = "ZenGecko/Main";
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
    private FrameLayout sidebarPanelHost;
    private GeckoSession displayedSession;
    private boolean wideLayout;
    private boolean rendering;
    private int safeInsetLeft;
    private int safeInsetTop;
    private int safeInsetRight;
    private int safeInsetBottom;
    private String lastSidebarFingerprint = "";
    private String lastPopupSidebarFingerprint = "";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(getColor(R.color.zen_bg));
        window.setNavigationBarColor(getColor(R.color.zen_bg));

        Log.i(TAG, "onCreate; widthDp=" + getResources().getConfiguration().screenWidthDp);
        browser = BrowserRepository.get(this);
        browser.addObserver(this);
        buildUi();

        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            browser.loadInActiveTab(intent.getDataString());
        }
        render();
    }

    @Override protected void onStart() {
        super.onStart();
        setCurrentSessionActive(true);
    }

    @Override protected void onStop() {
        setCurrentSessionActive(false);
        super.onStop();
    }

    @Override protected void onDestroy() {
        Log.i(TAG, "onDestroy");
        if (browser != null) browser.removeObserver(this);
        if (sidebarPopup != null) {
            sidebarPopup.dismiss();
            sidebarPopup = null;
        }
        detachGeckoView();
        super.onDestroy();
    }

    @Override public void onBrowserStateChanged() { runOnUiThread(this::render); }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean nextWideLayout = shouldUseFixedSidebar(newConfig);
        Log.i(TAG, "onConfigurationChanged widthDp=" + newConfig.screenWidthDp
                + " wide=" + nextWideLayout + " previousWide=" + wideLayout);

        // HyperOS can emit a configuration callback immediately after launch, even when the
        // layout class did not change. Rebuilding GeckoView in that case leaves the existing
        // GeckoSession attached to an obsolete SurfaceView. Only rebuild when the compact/wide
        // layout mode actually changes, and always detach the session first.
        if (nextWideLayout != wideLayout) rebuildUiPreservingSession();
        if (root != null) root.requestApplyInsets();
        render();
    }

    @Override public void onBackPressed() {
        BrowserTab tab = browser.getActiveTab();
        if (sidebarPopup != null && sidebarPopup.isShowing()) { sidebarPopup.dismiss(); return; }
        if (tab != null && tab.canGoBack && tab.session != null) { tab.session.goBack(); return; }
        super.onBackPressed();
    }

    private void rebuildUiPreservingSession() {
        Log.i(TAG, "Rebuilding UI while preserving GeckoSession");
        if (sidebarPopup != null) {
            sidebarPopup.dismiss();
            sidebarPopup = null;
        }
        detachGeckoView();
        lastSidebarFingerprint = "";
        lastPopupSidebarFingerprint = "";
        buildUi();
    }

    private void detachGeckoView() {
        displayedSession = null;
        if (geckoView == null) return;
        try {
            GeckoSession released = geckoView.releaseSession();
            if (released != null) Log.d(TAG, "Released GeckoSession from GeckoView");
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to release GeckoSession", error);
        }
    }

    private void attachSession(GeckoSession session) {
        if (session == null || geckoView == null || displayedSession == session) return;
        detachGeckoView();
        try {
            geckoView.setSession(session);
            displayedSession = session;
            Log.d(TAG, "Attached GeckoSession to GeckoView");
        } catch (RuntimeException error) {
            displayedSession = null;
            Log.e(TAG, "Unable to attach GeckoSession", error);
            throw error;
        }
    }

    private void setCurrentSessionActive(boolean active) {
        if (browser == null) return;
        BrowserTab tab = browser.getActiveTab();
        if (tab != null && tab.session != null) tab.session.setActive(active);
    }

    private void buildUi() {
        wideLayout = shouldUseFixedSidebar(getResources().getConfiguration());
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
        browserColumn.addView(progressBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        geckoView = new GeckoView(this);
        geckoView.setBackgroundColor(getColor(R.color.zen_bg));
        browserColumn.addView(geckoView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        installSafeAreaInsets();
    }

    private void installSafeAreaInsets() {
        if (root == null) return;
        root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            int left;
            int top;
            int right;
            int bottom;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets safeArea = windowInsets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = safeArea.left;
                top = safeArea.top;
                right = safeArea.right;
                bottom = safeArea.bottom;
            } else {
                left = windowInsets.getSystemWindowInsetLeft();
                top = windowInsets.getSystemWindowInsetTop();
                right = windowInsets.getSystemWindowInsetRight();
                bottom = windowInsets.getSystemWindowInsetBottom();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    DisplayCutout cutout = windowInsets.getDisplayCutout();
                    if (cutout != null) {
                        left = Math.max(left, cutout.getSafeInsetLeft());
                        top = Math.max(top, cutout.getSafeInsetTop());
                        right = Math.max(right, cutout.getSafeInsetRight());
                        bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                    }
                }
            }

            boolean changed = left != safeInsetLeft || top != safeInsetTop
                    || right != safeInsetRight || bottom != safeInsetBottom;
            safeInsetLeft = left;
            safeInsetTop = top;
            safeInsetRight = right;
            safeInsetBottom = bottom;
            view.setPadding(left, top, right, bottom);

            if (changed) {
                Log.d(TAG, "Safe area: " + left + "," + top + "," + right + "," + bottom);
                if (sidebarPopup != null && sidebarPopup.isShowing()) {
                    sidebarPopup.dismiss();
                    sidebarPopup = null;
                }
            }
            return windowInsets;
        });
        root.requestApplyInsets();
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
        addressBar.setFocusableInTouchMode(true);
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        addressBar.setBackgroundResource(R.drawable.bg_address);
        addressBar.setPadding(dp(14), 0, dp(14), 0);
        addressBar.setOnClickListener(v -> showKeyboard(addressBar));
        addressBar.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) addressBar.post(() -> showKeyboard(addressBar));
        });
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
        newTab.setOnClickListener(v -> { browser.addTab("about:blank", true); showKeyboard(addressBar); });
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
        add.setOnClickListener(v -> openNewTabFromSidebar());
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
        row.setPadding(dp(8), dp(4), dp(4), dp(4));
        row.setBackgroundResource(active ? R.drawable.bg_tab_active : R.drawable.bg_tab_idle);
        row.setOnClickListener(v -> {
            browser.selectTab(tab.id);
            dismissSidebarPopup();
        });
        row.setOnLongClickListener(v -> {
            browser.toggleEssential(tab.id);
            Toast.makeText(this,
                    tab.essential ? "Pestaña normal" : "Pestaña esencial",
                    Toast.LENGTH_SHORT).show();
            return true;
        });

        final float[] gesture = new float[2];
        row.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                gesture[0] = event.getX();
                gesture[1] = event.getY();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                float dx = event.getX() - gesture[0];
                float dy = event.getY() - gesture[1];
                if (Math.abs(dx) > dp(84) && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                    closeTabFromSidebar(tab.id);
                    return true;
                }
            }
            return false;
        });

        TextView icon = text(tab.essential ? "◆" : "●", 11,
                tab.loading ? R.color.zen_accent : R.color.zen_muted);
        icon.setGravity(Gravity.CENTER);
        row.addView(icon, square(28));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(tab.title == null ? "Nueva pestaña" : tab.title, 14, R.color.zen_text);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView url = text(shortUrl(tab.url), 11, R.color.zen_muted);
        url.setSingleLine(true);
        url.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(title);
        labels.addView(url);
        row.addView(labels, new LinearLayout.LayoutParams(0, dp(50), 1f));

        TextView close = text("×", 24, R.color.zen_text);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Cerrar pestaña");
        close.setFocusable(true);
        close.setClickable(true);
        close.setBackgroundResource(R.drawable.bg_close_button);
        close.setOnClickListener(v -> {
            v.setEnabled(false);
            closeTabFromSidebar(tab.id);
        });
        row.addView(close, square(48));

        LinearLayout.LayoutParams rp =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62));
        rp.setMargins(0, 0, 0, dp(4));
        row.setLayoutParams(rp);
        return row;
    }

    private void showSidebarPopup() {
        dismissSidebarPopup();

        int availableWidth = Math.max(dp(240),
                getResources().getDisplayMetrics().widthPixels - safeInsetLeft - safeInsetRight);
        int panelWidth = Math.min(dp(360), (int) (availableWidth * .88f));
        int measuredHeight = root == null ? 0 : root.getHeight();
        int availableHeight = measuredHeight - safeInsetTop - safeInsetBottom;
        int height = availableHeight > 0 ? availableHeight : ViewGroup.LayoutParams.MATCH_PARENT;

        View overlay = createSidebarOverlay(availableWidth, panelWidth);
        sidebarPopup = new PopupWindow(overlay, availableWidth, height, true);
        sidebarPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        sidebarPopup.setOutsideTouchable(true);
        sidebarPopup.setClippingEnabled(true);
        sidebarPopup.setElevation(dp(18));
        sidebarPopup.setAnimationStyle(android.R.style.Animation_Dialog);
        sidebarPopup.setOnDismissListener(() -> {
            sidebarPopup = null;
            sidebarPanelHost = null;
            lastPopupSidebarFingerprint = "";
        });
        lastPopupSidebarFingerprint = sidebarFingerprint();
        sidebarPopup.showAtLocation(root, Gravity.START | Gravity.TOP, safeInsetLeft, safeInsetTop);
    }

    private View createSidebarOverlay(int availableWidth, int panelWidth) {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.TRANSPARENT);

        View scrim = new View(this);
        scrim.setBackgroundColor(0x99000000);
        scrim.setContentDescription("Cerrar panel");
        scrim.setOnClickListener(v -> dismissSidebarPopup());
        overlay.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        sidebarPanelHost = new FrameLayout(this);
        FrameLayout.LayoutParams panelParams =
                new FrameLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
        sidebarPanelHost.addView(createSidebar(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.addView(sidebarPanelHost, panelParams);
        return overlay;
    }

    private void refreshSidebarPopup() {
        if (sidebarPopup == null || !sidebarPopup.isShowing()) return;
        if (sidebarPanelHost == null) return;
        sidebarPanelHost.removeAllViews();
        sidebarPanelHost.addView(createSidebar(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sidebarPopup.update();
        lastPopupSidebarFingerprint = sidebarFingerprint();
    }

    private void dismissSidebarPopup() {
        if (sidebarPopup != null) sidebarPopup.dismiss();
    }

    private void openNewTabFromSidebar() {
        browser.addTab("about:blank", true);
        dismissSidebarPopup();
        showKeyboard(addressBar);
    }

    private void closeTabFromSidebar(String tabId) {
        browser.closeTab(tabId);
        Toast.makeText(this, "Pestaña cerrada", Toast.LENGTH_SHORT).show();
        refreshSidebarPopup();
    }

    private void promptWorkspace() {
        EditText input = new EditText(this);
        input.setHint("Nombre del espacio");
        input.setSingleLine(true);
        input.setTextColor(getColor(R.color.zen_text));
        input.setHintTextColor(getColor(R.color.zen_muted));
        input.setBackgroundResource(R.drawable.bg_address);
        int pad = dp(16);
        input.setPadding(pad, 0, pad, 0);

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(dp(20), dp(8), dp(20), 0);
        wrapper.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Nuevo espacio")
                .setView(wrapper)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Crear", null)
                .create();

        dialog.setOnShowListener(ignored -> {
            TextView create = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            create.setEnabled(false);
            create.setOnClickListener(v -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) return;
                browser.addWorkspace(name);
                dialog.dismiss();
                refreshSidebarPopup();
            });
            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                    create.setEnabled(value != null && !value.toString().trim().isEmpty());
                }
                @Override public void afterTextChanged(Editable value) { }
            });
            showKeyboard(input);
        });
        dialog.show();
    }

    private void render() {
        if (rendering || geckoView == null) return;
        rendering = true;
        try {
            BrowserTab tab = browser.getActiveTab();
            if (tab == null) return;
            attachSession(tab.session);
            if (!addressBar.hasFocus()) {
                String visibleUrl = tab.url == null || "about:blank".equals(tab.url) ? "" : tab.url;
                addressBar.setText(visibleUrl);
            }
            setEnabled(backButton, tab.canGoBack);
            setEnabled(forwardButton, tab.canGoForward);
            reloadButton.setText(tab.loading ? "×" : "↻");
            progressBar.setVisibility(tab.loading ? View.VISIBLE : View.INVISIBLE);
            progressBar.setProgress(tab.progress);

            String fingerprint = sidebarFingerprint();
            if (wideLayout && fixedSidebar != null && !fingerprint.equals(lastSidebarFingerprint)) {
                int index = root.indexOfChild(fixedSidebar);
                View replacement = createSidebar();
                root.removeView(fixedSidebar);
                root.addView(replacement, index,
                        new LinearLayout.LayoutParams(dp(286), ViewGroup.LayoutParams.MATCH_PARENT));
                fixedSidebar = replacement;
                lastSidebarFingerprint = fingerprint;
            }
            if (sidebarPopup != null && sidebarPopup.isShowing()
                    && !fingerprint.equals(lastPopupSidebarFingerprint)) {
                refreshSidebarPopup();
            }
        } finally { rendering = false; }
    }

    private void showKeyboard(View view) {
        if (view == null) return;
        view.requestFocus();
        view.post(() -> {
            InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        });
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

    private boolean shouldUseFixedSidebar(Configuration configuration) {
        return configuration.smallestScreenWidthDp >= 600 && configuration.screenWidthDp >= 720;
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
