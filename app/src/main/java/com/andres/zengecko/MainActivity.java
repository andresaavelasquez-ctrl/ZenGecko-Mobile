package com.andres.zengecko;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
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
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import com.andres.zengecko.model.BrowserTab;
import com.andres.zengecko.model.Workspace;

public final class MainActivity extends Activity implements BrowserRepository.Observer {
    private static final String TAG = "ZenGecko/Main";
    private static final long TAB_FADE_OUT_MS = 85L;
    private static final long TAB_FADE_IN_MS = 150L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private BrowserRepository browser;
    private FrameLayout appRoot;
    private LinearLayout root;
    private FrameLayout webHost;
    private GeckoView geckoView;
    private View newTabSurface;
    private View transitionScrim;
    private View fixedSidebar;
    private PopupWindow sidebarPopup;
    private PopupWindow searchPopup;
    private FrameLayout sidebarPanelHost;
    private View sidebarScrim;
    private View sidebarAnimatedPanel;
    private EditText searchInput;
    private LinearLayout searchResults;
    private View searchAnimatedPanel;
    private PopupWindow searchEnginePopup;
    private TextView searchEngineButton;
    private TextView addressDisplay;
    private ImageButton backButton;
    private ImageButton forwardButton;
    private ImageButton reloadButton;
    private ProgressBar progressBar;
    private GeckoSession displayedSession;
    private boolean wideLayout;
    private boolean rendering;
    private boolean tabTransitionRunning;
    private int transitionGeneration;
    private String lastPaintCoverKey = "";
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
        dismissSearchPopupImmediate();
        dismissSidebarPopupImmediate();
        detachGeckoView();
        super.onDestroy();
    }

    @Override public void onBrowserStateChanged() { runOnUiThread(this::render); }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean nextWideLayout = shouldUseFixedSidebar(newConfig);
        Log.i(TAG, "onConfigurationChanged widthDp=" + newConfig.screenWidthDp
                + " wide=" + nextWideLayout + " previousWide=" + wideLayout);
        if (nextWideLayout != wideLayout) rebuildUiPreservingSession();
        if (appRoot != null) appRoot.requestApplyInsets();
        render();
    }

    @Override public void onBackPressed() {
        if (searchEnginePopup != null && searchEnginePopup.isShowing()) {
            dismissSearchEnginePopup();
            return;
        }
        if (searchPopup != null && searchPopup.isShowing()) {
            dismissSearchPopup();
            return;
        }
        if (sidebarPopup != null && sidebarPopup.isShowing()) {
            dismissSidebarPopup();
            return;
        }
        BrowserTab tab = browser.getActiveTab();
        if (tab != null && tab.canGoBack && tab.session != null) {
            tab.session.goBack();
            return;
        }
        super.onBackPressed();
    }

    private void rebuildUiPreservingSession() {
        Log.i(TAG, "Rebuilding UI while preserving GeckoSession");
        dismissSearchPopupImmediate();
        dismissSidebarPopupImmediate();
        detachGeckoView();
        lastSidebarFingerprint = "";
        lastPopupSidebarFingerprint = "";
        buildUi();
    }

    private void detachGeckoView() {
        displayedSession = null;
        lastPaintCoverKey = "";
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
            geckoView.coverUntilFirstPaint(getColor(R.color.zen_bg));
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

        appRoot = new FrameLayout(this);
        appRoot.setBackgroundColor(getColor(R.color.zen_bg));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(getColor(R.color.zen_bg));
        appRoot.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (wideLayout) {
            fixedSidebar = createSidebar();
            root.addView(fixedSidebar, new LinearLayout.LayoutParams(dp(292), ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            fixedSidebar = null;
        }

        LinearLayout browserColumn = new LinearLayout(this);
        browserColumn.setOrientation(LinearLayout.VERTICAL);
        browserColumn.setBackgroundColor(getColor(R.color.zen_bg));
        root.addView(browserColumn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        browserColumn.addView(createToolbar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(ColorStateList.valueOf(getColor(R.color.zen_accent)));
        progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        browserColumn.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        webHost = new FrameLayout(this);
        webHost.setBackgroundColor(getColor(R.color.zen_bg));
        browserColumn.addView(webHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        geckoView = new GeckoView(this);
        geckoView.setBackgroundColor(getColor(R.color.zen_bg));
        webHost.addView(geckoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        newTabSurface = createNewTabSurface();
        webHost.addView(newTabSurface, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        transitionScrim = new View(this);
        transitionScrim.setBackgroundColor(getColor(R.color.zen_bg));
        transitionScrim.setAlpha(0f);
        transitionScrim.setVisibility(View.GONE);
        transitionScrim.setClickable(false);
        webHost.addView(transitionScrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(appRoot);
        installSafeAreaInsets();
    }

    private void installSafeAreaInsets() {
        if (appRoot == null) return;
        appRoot.setOnApplyWindowInsetsListener((view, windowInsets) -> {
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
            root.setPadding(left, top, right, bottom);

            if (changed) {
                Log.d(TAG, "Safe area: " + left + "," + top + "," + right + "," + bottom);
                dismissSearchPopupImmediate();
                dismissSidebarPopupImmediate();
            }
            return windowInsets;
        });
        appRoot.requestApplyInsets();
    }

    private View createToolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));
        bar.setBackgroundColor(getColor(R.color.zen_surface));

        ImageButton sidebar = iconButton(R.drawable.ic_menu, "Pestañas y espacios");
        sidebar.setOnClickListener(v -> { if (!wideLayout) showSidebarPopup(); });
        bar.addView(sidebar, square(44));

        backButton = iconButton(R.drawable.ic_back, "Atrás");
        backButton.setOnClickListener(v -> {
            BrowserTab tab = browser.getActiveTab();
            if (tab != null && tab.session != null && tab.canGoBack) tab.session.goBack();
        });
        bar.addView(backButton, square(40));

        forwardButton = iconButton(R.drawable.ic_forward, "Adelante");
        forwardButton.setOnClickListener(v -> {
            BrowserTab tab = browser.getActiveTab();
            if (tab != null && tab.session != null && tab.canGoForward) tab.session.goForward();
        });
        bar.addView(forwardButton, square(40));

        addressDisplay = text("Nueva pestaña", 14, R.color.zen_text);
        addressDisplay.setSingleLine(true);
        addressDisplay.setEllipsize(TextUtils.TruncateAt.END);
        addressDisplay.setGravity(Gravity.CENTER_VERTICAL);
        addressDisplay.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
        addressDisplay.setCompoundDrawablePadding(dp(9));
        addressDisplay.setBackgroundResource(R.drawable.bg_address);
        addressDisplay.setPadding(dp(14), 0, dp(14), 0);
        addressDisplay.setFocusable(true);
        addressDisplay.setClickable(true);
        addressDisplay.setContentDescription("Buscar o escribir una dirección");
        addressDisplay.setOnClickListener(v -> showSearchPopup(false));
        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        addressParams.setMargins(dp(4), 0, dp(4), 0);
        bar.addView(addressDisplay, addressParams);

        reloadButton = iconButton(R.drawable.ic_reload, "Recargar o detener");
        reloadButton.setOnClickListener(v -> {
            BrowserTab tab = browser.getActiveTab();
            if (tab != null && tab.session != null) {
                if (tab.loading) tab.session.stop(); else tab.session.reload();
            }
        });
        bar.addView(reloadButton, square(40));

        ImageButton newTab = iconButton(R.drawable.ic_add, "Nueva pestaña");
        newTab.setOnClickListener(v -> openNewTabAndSearch());
        bar.addView(newTab, square(44));
        return bar;
    }

    private View createNewTabSurface() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundResource(R.drawable.bg_new_tab_gradient);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(28), dp(54), dp(28), dp(36));
        scroll.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView logo = text("Z", 42, R.color.zen_bg);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setGravity(Gravity.CENTER);
        logo.setBackgroundResource(R.drawable.bg_zen_mark);
        page.addView(logo, new LinearLayout.LayoutParams(dp(82), dp(82)));

        TextView title = text("ZenGecko", 26, R.color.zen_text);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, dp(20), 0, dp(5));
        page.addView(title, titleParams);

        TextView subtitle = text("Un espacio tranquilo para navegar", 14, R.color.zen_muted);
        subtitle.setGravity(Gravity.CENTER);
        page.addView(subtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView search = text("Buscar o escribir una dirección", 15, R.color.zen_muted);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
        search.setCompoundDrawablePadding(dp(10));
        search.setPadding(dp(18), 0, dp(18), 0);
        search.setBackgroundResource(R.drawable.bg_search_large);
        search.setOnClickListener(v -> showSearchPopup(true));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                Math.min(dp(620), getResources().getDisplayMetrics().widthPixels - dp(56)), dp(56));
        searchParams.setMargins(0, dp(30), 0, dp(26));
        page.addView(search, searchParams);

        TextView quickLabel = label("ACCESOS RÁPIDOS");
        quickLabel.setGravity(Gravity.CENTER);
        page.addView(quickLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout quickRow = new LinearLayout(this);
        quickRow.setGravity(Gravity.CENTER);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        quickRow.addView(quickSite("GitHub", "https://github.com"));
        quickRow.addView(quickSite("YouTube", "https://youtube.com"));
        quickRow.addView(quickSite("Wikipedia", "https://wikipedia.org"));
        page.addView(quickRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private View quickSite(String title, String url) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackgroundResource(R.drawable.bg_quick_site);
        card.setPadding(dp(8), dp(10), dp(8), dp(8));
        card.setOnClickListener(v -> browser.loadInActiveTab(url));

        TextView mark = text(title.substring(0, 1).toUpperCase(Locale.ROOT), 18, R.color.zen_text);
        mark.setTypeface(Typeface.DEFAULT_BOLD);
        mark.setGravity(Gravity.CENTER);
        mark.setBackgroundResource(R.drawable.bg_favicon);
        card.addView(mark, new LinearLayout.LayoutParams(dp(42), dp(42)));

        TextView label = text(title, 12, R.color.zen_muted);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(0, dp(7), 0, 0);
        card.addView(label, labelParams);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(dp(94), dp(84));
        cardParams.setMargins(dp(5), 0, dp(5), 0);
        card.setLayoutParams(cardParams);
        return card;
    }

    private View createSidebar() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(14), dp(12), dp(12));
        panel.setBackgroundColor(getColor(R.color.zen_surface));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("ZEN·GECKO", 18, R.color.zen_text);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(44), 1f));
        ImageButton add = iconButton(R.drawable.ic_add, "Nueva pestaña");
        add.setOnClickListener(v -> openNewTabFromSidebar());
        header.addView(add, square(42));
        panel.addView(header);

        Workspace activeWorkspace = activeWorkspace();
        if (activeWorkspace != null) {
            TextView workspaceTitle = text(activeWorkspace.name, 13, R.color.zen_muted);
            workspaceTitle.setSingleLine(true);
            workspaceTitle.setPadding(dp(8), 0, dp(8), dp(7));
            panel.addView(workspaceTitle);
        }

        TextView workspacesLabel = label("ESPACIOS");
        panel.addView(workspacesLabel);
        HorizontalScrollView workspaceScroll = new HorizontalScrollView(this);
        workspaceScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout workspaceRow = new LinearLayout(this);
        workspaceRow.setOrientation(LinearLayout.HORIZONTAL);
        for (Workspace workspace : browser.getWorkspaces()) {
            boolean active = workspace.id.equals(browser.getActiveWorkspaceId());
            TextView chip = text(workspace.name.substring(0, Math.min(1, workspace.name.length())).toUpperCase(Locale.ROOT),
                    14, active ? R.color.zen_text : R.color.zen_muted);
            chip.setGravity(Gravity.CENTER);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setContentDescription(workspace.name);
            chip.setBackgroundResource(active ? R.drawable.bg_tab_active : R.drawable.bg_tab_idle);
            chip.setOnClickListener(v -> animateWebTransition(() -> browser.switchWorkspace(workspace.id)));
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(dp(44), dp(44));
            chipParams.setMargins(0, 0, dp(8), 0);
            workspaceRow.addView(chip, chipParams);
        }
        TextView addWorkspace = text("＋", 17, R.color.zen_muted);
        addWorkspace.setGravity(Gravity.CENTER);
        addWorkspace.setBackgroundResource(R.drawable.bg_tab_idle);
        addWorkspace.setOnClickListener(v -> promptWorkspace());
        workspaceRow.addView(addWorkspace, new LinearLayout.LayoutParams(dp(44), dp(44)));
        workspaceScroll.addView(workspaceRow);
        panel.addView(workspaceScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

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
        panel.addView(tabsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView essential = text("◆  Alternar esencial", 14, R.color.zen_muted);
        essential.setGravity(Gravity.CENTER_VERTICAL);
        essential.setPadding(dp(12), 0, dp(12), 0);
        essential.setBackgroundResource(R.drawable.bg_tab_idle);
        essential.setOnClickListener(v -> {
            BrowserTab tab = browser.getActiveTab();
            if (tab != null) browser.toggleEssential(tab.id);
        });
        panel.addView(essential, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        return panel;
    }

    private View tabRow(BrowserTab tab) {
        boolean active = browser.getActiveTab() != null && browser.getActiveTab().id.equals(tab.id);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(4), dp(4), dp(4));
        row.setBackgroundResource(active ? R.drawable.bg_tab_active : R.drawable.bg_tab_idle);
        row.setOnClickListener(v -> selectTabFromSidebar(tab.id));

        final float[] gesture = new float[2];
        row.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    gesture[0] = event.getX();
                    gesture[1] = event.getY();
                    view.setPressed(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float moveX = event.getX() - gesture[0];
                    if (Math.abs(moveX) > dp(8)) view.setTranslationX(moveX * .28f);
                    return true;
                case MotionEvent.ACTION_UP:
                    view.setPressed(false);
                    float dx = event.getX() - gesture[0];
                    float dy = event.getY() - gesture[1];
                    if (Math.abs(dx) > dp(82) && Math.abs(dx) > Math.abs(dy) * 1.4f) {
                        animateTabRowClose(view, tab.id, dx >= 0 ? 1 : -1);
                    } else {
                        view.animate().translationX(0f).setDuration(100).withEndAction(view::performClick).start();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    view.setPressed(false);
                    view.animate().translationX(0f).setDuration(100).start();
                    return true;
                default:
                    return true;
            }
        });

        TextView favicon = text(faviconLetter(tab), 13, R.color.zen_text);
        favicon.setTypeface(Typeface.DEFAULT_BOLD);
        favicon.setGravity(Gravity.CENTER);
        favicon.setBackgroundResource(tab.loading ? R.drawable.bg_favicon_loading : R.drawable.bg_favicon);
        row.addView(favicon, square(34));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(displayTitle(tab), 14, R.color.zen_text);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        TextView url = text(shortUrl(tab.url), 11, R.color.zen_muted);
        url.setSingleLine(true);
        url.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(title);
        labels.addView(url);
        row.addView(labels, new LinearLayout.LayoutParams(0, dp(50), 1f));

        ImageButton close = iconButton(R.drawable.ic_close, "Cerrar pestaña");
        close.setBackgroundResource(R.drawable.bg_close_button);
        close.setOnClickListener(v -> {
            v.setEnabled(false);
            animateTabRowClose(row, tab.id, 1);
        });
        row.addView(close, square(48));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62));
        rowParams.setMargins(0, 0, 0, dp(4));
        row.setLayoutParams(rowParams);
        return row;
    }

    private void animateTabRowClose(View row, String tabId, int direction) {
        if (!row.isEnabled()) return;
        row.setEnabled(false);
        boolean closingActive = tabId.equals(browser.getActiveTabId());
        row.animate()
                .translationX(direction * dp(72))
                .alpha(0f)
                .setDuration(145)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(() -> {
                    Runnable close = () -> {
                        browser.closeTab(tabId);
                        showUndoSnackbar();
                    };
                    if (closingActive) animateWebTransition(close); else close.run();
                })
                .start();
    }

    private void selectTabFromSidebar(String tabId) {
        if (tabId.equals(browser.getActiveTabId())) {
            dismissSidebarPopup();
            return;
        }
        animateWebTransition(() -> browser.selectTab(tabId));
        dismissSidebarPopup();
    }

    private void showSidebarPopup() {
        dismissSidebarPopupImmediate();

        int availableWidth = Math.max(dp(240),
                getResources().getDisplayMetrics().widthPixels - safeInsetLeft - safeInsetRight);
        int panelWidth = Math.min(dp(372), (int) (availableWidth * .9f));
        int measuredHeight = appRoot == null ? 0 : appRoot.getHeight();
        int availableHeight = measuredHeight - safeInsetTop - safeInsetBottom;
        int height = availableHeight > 0 ? availableHeight : ViewGroup.LayoutParams.MATCH_PARENT;

        View overlay = createSidebarOverlay(panelWidth);
        sidebarPopup = new PopupWindow(overlay, availableWidth, height, true);
        sidebarPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        sidebarPopup.setOutsideTouchable(true);
        sidebarPopup.setClippingEnabled(true);
        sidebarPopup.setElevation(dp(18));
        sidebarPopup.setOnDismissListener(() -> {
            sidebarPopup = null;
            sidebarPanelHost = null;
            sidebarScrim = null;
            sidebarAnimatedPanel = null;
            lastPopupSidebarFingerprint = "";
        });
        lastPopupSidebarFingerprint = sidebarFingerprint();
        sidebarPopup.showAtLocation(appRoot, Gravity.START | Gravity.TOP, safeInsetLeft, safeInsetTop);

        if (sidebarScrim != null) {
            sidebarScrim.setAlpha(0f);
            sidebarScrim.animate().alpha(1f).setDuration(180).start();
        }
        if (sidebarAnimatedPanel != null) {
            sidebarAnimatedPanel.setTranslationX(-panelWidth);
            sidebarAnimatedPanel.animate().translationX(0f).setDuration(210)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        }
    }

    private View createSidebarOverlay(int panelWidth) {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.TRANSPARENT);

        sidebarScrim = new View(this);
        sidebarScrim.setBackgroundColor(0x99000000);
        sidebarScrim.setContentDescription("Cerrar panel");
        sidebarScrim.setOnClickListener(v -> dismissSidebarPopup());
        overlay.addView(sidebarScrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        sidebarPanelHost = new FrameLayout(this);
        sidebarAnimatedPanel = sidebarPanelHost;
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                panelWidth, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
        sidebarPanelHost.addView(createSidebar(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.addView(sidebarPanelHost, panelParams);
        return overlay;
    }

    private void refreshSidebarPopup() {
        if (sidebarPopup == null || !sidebarPopup.isShowing() || sidebarPanelHost == null) return;
        sidebarPanelHost.removeAllViews();
        sidebarPanelHost.addView(createSidebar(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sidebarPopup.update();
        lastPopupSidebarFingerprint = sidebarFingerprint();
    }

    private void dismissSidebarPopup() {
        PopupWindow popup = sidebarPopup;
        if (popup == null || !popup.isShowing()) return;
        View panel = sidebarAnimatedPanel;
        View scrim = sidebarScrim;
        if (scrim != null) scrim.animate().alpha(0f).setDuration(150).start();
        if (panel != null) {
            panel.animate().translationX(-panel.getWidth()).setDuration(170)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .withEndAction(() -> { if (popup.isShowing()) popup.dismiss(); }).start();
        } else {
            popup.dismiss();
        }
    }

    private void dismissSidebarPopupImmediate() {
        if (sidebarPopup != null) sidebarPopup.dismiss();
        sidebarPopup = null;
        sidebarPanelHost = null;
        sidebarScrim = null;
        sidebarAnimatedPanel = null;
    }

    private void openNewTabFromSidebar() {
        browser.addTab("about:blank", true);
        dismissSidebarPopup();
        mainHandler.postDelayed(() -> showSearchPopup(true), 190);
    }

    private void openNewTabAndSearch() {
        animateWebTransition(() -> browser.addTab("about:blank", true));
        mainHandler.postDelayed(() -> showSearchPopup(true), 250);
    }

    private void showSearchPopup(boolean newTabMode) {
        dismissSearchPopupImmediate();

        int availableWidth = Math.max(dp(260),
                getResources().getDisplayMetrics().widthPixels - safeInsetLeft - safeInsetRight);
        int measuredHeight = appRoot == null ? 0 : appRoot.getHeight();
        int availableHeight = Math.max(dp(320), measuredHeight - safeInsetTop - safeInsetBottom);

        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.TRANSPARENT);
        View scrim = new View(this);
        scrim.setBackgroundColor(0xC20D0E12);
        scrim.setOnClickListener(v -> dismissSearchPopup());
        overlay.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(12), dp(12), dp(12));
        panel.setBackgroundResource(R.drawable.bg_search_panel);
        searchAnimatedPanel = panel;

        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextColor(getColor(R.color.zen_text));
        searchInput.setHintTextColor(getColor(R.color.zen_muted));
        searchInput.setTextSize(16);
        searchInput.setHint("Buscar o escribir una dirección");
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setBackgroundResource(R.drawable.bg_address);
        searchInput.setPadding(dp(16), 0, dp(16), 0);
        String initial = newTabMode ? "" : activeUrlForEditing();
        searchInput.setText(initial);
        if (!initial.isEmpty()) searchInput.setSelection(0, initial.length());
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
        inputParams.setMargins(0, 0, dp(7), 0);
        searchBar.addView(searchInput, inputParams);

        searchEngineButton = createSearchEngineButton();
        searchEngineButton.setOnClickListener(v -> showSearchEngineMenu(searchEngineButton));
        searchBar.addView(searchEngineButton, square(46));
        panel.addView(searchBar);

        ScrollView resultsScroll = new ScrollView(this);
        resultsScroll.setFillViewport(false);
        searchResults = new LinearLayout(this);
        searchResults.setOrientation(LinearLayout.VERTICAL);
        searchResults.setPadding(0, dp(8), 0, dp(6));
        resultsScroll.addView(searchResults);
        panel.addView(resultsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        int panelWidth = Math.min(dp(720), availableWidth - dp(20));
        int panelHeight = Math.min(dp(590), availableHeight - dp(20));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                panelWidth, panelHeight, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        panelParams.topMargin = dp(10);
        overlay.addView(panel, panelParams);

        searchPopup = new PopupWindow(overlay, availableWidth, availableHeight, true);
        searchPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        searchPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        searchPopup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        searchPopup.setClippingEnabled(true);
        searchPopup.setElevation(dp(24));
        searchPopup.setOnDismissListener(() -> {
            dismissSearchEnginePopupImmediate();
            searchPopup = null;
            searchInput = null;
            searchResults = null;
            searchAnimatedPanel = null;
            searchEngineButton = null;
        });
        searchPopup.showAtLocation(appRoot, Gravity.START | Gravity.TOP, safeInsetLeft, safeInsetTop);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                refreshSearchResults(value == null ? "" : value.toString());
            }
            @Override public void afterTextChanged(Editable value) { }
        });
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                submitSearch(searchInput.getText().toString());
                return true;
            }
            return false;
        });

        refreshSearchResults(initial);
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(160).start();
        panel.setAlpha(0f);
        panel.setTranslationY(-dp(14));
        panel.animate().alpha(1f).translationY(0f).setDuration(190)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        showKeyboard(searchInput);
    }

    private TextView createSearchEngineButton() {
        SearchEngine engine = browser.getSearchEngine();
        TextView button = text(engine.mark, 15, R.color.zen_text);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundResource(R.drawable.bg_button);
        button.setContentDescription("Motor de búsqueda: " + engine.displayName);
        button.setFocusable(true);
        button.setClickable(true);
        return button;
    }

    private void updateSearchEngineButton() {
        if (searchEngineButton == null) return;
        SearchEngine engine = browser.getSearchEngine();
        searchEngineButton.setText(engine.mark);
        searchEngineButton.setContentDescription("Motor de búsqueda: " + engine.displayName);
    }

    private void showSearchEngineMenu(View anchor) {
        dismissSearchEnginePopupImmediate();
        if (anchor == null || appRoot == null) return;

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(10), dp(10), dp(10), dp(10));
        menu.setBackgroundResource(R.drawable.bg_search_panel);

        TextView heading = label("MOTOR DE BÚSQUEDA");
        menu.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (SearchEngine engine : SearchEngine.values()) {
            menu.addView(searchEngineRow(engine), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        }

        int menuWidth = dp(248);
        searchEnginePopup = new PopupWindow(
                menu, menuWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        searchEnginePopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        searchEnginePopup.setOutsideTouchable(true);
        searchEnginePopup.setClippingEnabled(true);
        searchEnginePopup.setElevation(dp(24));
        searchEnginePopup.setOnDismissListener(() -> searchEnginePopup = null);

        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int x = Math.max(safeInsetLeft + dp(8),
                location[0] + anchor.getWidth() - menuWidth);
        int y = location[1] + anchor.getHeight() + dp(6);
        searchEnginePopup.showAtLocation(appRoot, Gravity.TOP | Gravity.START, x, y);
    }

    private View searchEngineRow(SearchEngine engine) {
        boolean selected = engine == browser.getSearchEngine();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), 0, dp(8), 0);
        row.setBackgroundResource(selected
                ? R.drawable.bg_tab_active : R.drawable.bg_search_result);

        TextView mark = text(engine.mark, 14, R.color.zen_text);
        mark.setTypeface(Typeface.DEFAULT_BOLD);
        mark.setGravity(Gravity.CENTER);
        mark.setBackgroundResource(R.drawable.bg_favicon);
        row.addView(mark, square(34));

        TextView name = text(engine.displayName, 14,
                selected ? R.color.zen_text : R.color.zen_muted);
        name.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams nameParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        nameParams.setMargins(dp(10), 0, dp(4), 0);
        row.addView(name, nameParams);

        if (selected) {
            TextView check = text("✓", 16, R.color.zen_accent);
            check.setTypeface(Typeface.DEFAULT_BOLD);
            check.setGravity(Gravity.CENTER);
            row.addView(check, square(32));
        }

        row.setOnClickListener(v -> {
            browser.setSearchEngine(engine);
            updateSearchEngineButton();
            dismissSearchEnginePopup();
            refreshSearchResults(searchInput == null ? "" : searchInput.getText().toString());
        });
        return row;
    }

    private void dismissSearchEnginePopup() {
        if (searchEnginePopup != null && searchEnginePopup.isShowing()) {
            searchEnginePopup.dismiss();
        }
    }

    private void dismissSearchEnginePopupImmediate() {
        if (searchEnginePopup != null) searchEnginePopup.dismiss();
        searchEnginePopup = null;
    }

    private void refreshSearchResults(String rawQuery) {
        if (searchResults == null) return;
        searchResults.removeAllViews();
        String query = rawQuery == null ? "" : rawQuery.trim();
        String normalizedQuery = query.toLowerCase(Locale.ROOT);

        List<BrowserTab> matchingTabs = new ArrayList<>();
        for (BrowserTab tab : browser.getTabs()) {
            String haystack = (displayTitle(tab) + " " + safe(tab.url)).toLowerCase(Locale.ROOT);
            if (normalizedQuery.isEmpty() || haystack.contains(normalizedQuery)) matchingTabs.add(tab);
            if (matchingTabs.size() >= 6) break;
        }
        if (!matchingTabs.isEmpty()) {
            searchResults.addView(label("PESTAÑAS ABIERTAS"));
            for (BrowserTab tab : matchingTabs) {
                searchResults.addView(searchResultRow(
                        displayTitle(tab), shortUrl(tab.url), faviconLetter(tab),
                        () -> {
                            dismissSearchPopup();
                            if (!tab.id.equals(browser.getActiveTabId())) {
                                animateWebTransition(() -> browser.selectTab(tab.id));
                            }
                        }));
            }
        }

        int recentCount = 0;
        for (String url : browser.getRecentUrls()) {
            String host = displayHost(url);
            String haystack = (host + " " + url).toLowerCase(Locale.ROOT);
            if (!normalizedQuery.isEmpty() && !haystack.contains(normalizedQuery)) continue;
            if (recentCount == 0) searchResults.addView(label("RECIENTES"));
            searchResults.addView(searchResultRow(host, url, host.isEmpty() ? "•" : host.substring(0, 1).toUpperCase(Locale.ROOT),
                    () -> {
                        dismissSearchPopup();
                        browser.loadInActiveTab(url);
                    }));
            recentCount++;
            if (recentCount >= 5) break;
        }

        if (!query.isEmpty()) {
            searchResults.addView(label("IR A"));
            String subtitle = query.contains(".") && !query.contains(" ")
                    ? browser.normalizeInput(query)
                    : "Buscar en " + browser.getSearchEngine().displayName;
            searchResults.addView(searchResultRow(
                    "Buscar “" + query + "”", subtitle, "↗", () -> submitSearch(query)));
        } else if (matchingTabs.isEmpty() && recentCount == 0) {
            TextView empty = text("Empieza a escribir para buscar", 14, R.color.zen_muted);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(34), dp(16), dp(34));
            searchResults.addView(empty);
        }
    }

    private View searchResultRow(String title, String subtitle, String mark, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(6), dp(10), dp(6));
        row.setBackgroundResource(R.drawable.bg_search_result);
        row.setOnClickListener(v -> action.run());

        TextView icon = text(mark, 13, R.color.zen_text);
        icon.setTypeface(Typeface.DEFAULT_BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setBackgroundResource(R.drawable.bg_favicon);
        row.addView(icon, square(38));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView primary = text(title, 14, R.color.zen_text);
        primary.setSingleLine(true);
        primary.setEllipsize(TextUtils.TruncateAt.END);
        TextView secondary = text(subtitle, 11, R.color.zen_muted);
        secondary.setSingleLine(true);
        secondary.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(primary);
        labels.addView(secondary);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(0, dp(50), 1f);
        labelsParams.setMargins(dp(10), 0, dp(4), 0);
        row.addView(labels, labelsParams);

        ImageButton go = iconButton(R.drawable.ic_go, "Abrir");
        go.setOnClickListener(v -> action.run());
        row.addView(go, square(42));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62));
        rowParams.setMargins(0, 0, 0, dp(4));
        row.setLayoutParams(rowParams);
        return row;
    }

    private void submitSearch(String value) {
        String input = value == null ? "" : value.trim();
        dismissSearchPopup();
        if (input.isEmpty()) return;
        browser.loadInActiveTab(input);
    }

    private void dismissSearchPopup() {
        dismissSearchEnginePopupImmediate();
        PopupWindow popup = searchPopup;
        if (popup == null || !popup.isShowing()) return;
        hideKeyboard(searchInput);
        View panel = searchAnimatedPanel;
        if (panel != null) {
            panel.animate().alpha(0f).translationY(-dp(10)).setDuration(145)
                    .withEndAction(() -> { if (popup.isShowing()) popup.dismiss(); }).start();
        } else {
            popup.dismiss();
        }
    }

    private void dismissSearchPopupImmediate() {
        dismissSearchEnginePopupImmediate();
        if (searchPopup != null) searchPopup.dismiss();
        searchPopup = null;
        searchInput = null;
        searchResults = null;
        searchAnimatedPanel = null;
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
                animateWebTransition(() -> browser.addWorkspace(name));
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

    private void animateWebTransition(Runnable change) {
        if (change == null) return;
        if (transitionScrim == null || tabTransitionRunning) {
            change.run();
            return;
        }
        tabTransitionRunning = true;
        final int generation = ++transitionGeneration;
        mainHandler.postDelayed(() -> {
            if (tabTransitionRunning && generation == transitionGeneration
                    && transitionScrim != null) {
                Log.w(TAG, "Transition safety reset");
                transitionScrim.animate().cancel();
                transitionScrim.setAlpha(0f);
                transitionScrim.setVisibility(View.GONE);
                tabTransitionRunning = false;
            }
        }, 900L);
        transitionScrim.animate().cancel();
        transitionScrim.setVisibility(View.VISIBLE);
        transitionScrim.setAlpha(0f);
        transitionScrim.animate()
                .alpha(.94f)
                .setDuration(TAB_FADE_OUT_MS)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    change.run();
                    transitionScrim.postDelayed(() -> transitionScrim.animate()
                            .alpha(0f)
                            .setDuration(TAB_FADE_IN_MS)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator())
                            .withEndAction(() -> {
                                transitionScrim.setVisibility(View.GONE);
                                tabTransitionRunning = false;
                            }).start(), 35L);
                }).start();
    }

    private void showUndoSnackbar() {
        if (appRoot == null || !browser.canRestoreLastClosedTab()) return;
        View existing = appRoot.findViewWithTag("undo-snackbar");
        if (existing != null) appRoot.removeView(existing);

        LinearLayout bar = new LinearLayout(this);
        bar.setTag("undo-snackbar");
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), 0, dp(8), 0);
        bar.setBackgroundResource(R.drawable.bg_snackbar);
        bar.setElevation(dp(12));

        TextView message = text("Pestaña cerrada", 14, R.color.zen_text);
        bar.addView(message, new LinearLayout.LayoutParams(0, dp(52), 1f));
        TextView undo = text("DESHACER", 13, R.color.zen_accent);
        undo.setTypeface(Typeface.DEFAULT_BOLD);
        undo.setGravity(Gravity.CENTER);
        undo.setPadding(dp(12), 0, dp(12), 0);
        undo.setOnClickListener(v -> {
            animateWebTransition(() -> browser.restoreLastClosedTab());
            appRoot.removeView(bar);
        });
        bar.addView(undo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                Math.min(dp(420), getResources().getDisplayMetrics().widthPixels - dp(32)), dp(56),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = safeInsetBottom + dp(18);
        appRoot.addView(bar, params);
        bar.setAlpha(0f);
        bar.setTranslationY(dp(18));
        bar.animate().alpha(1f).translationY(0f).setDuration(180).start();
        mainHandler.postDelayed(() -> {
            if (bar.getParent() != null) {
                bar.animate().alpha(0f).translationY(dp(14)).setDuration(150)
                        .withEndAction(() -> {
                            if (bar.getParent() == appRoot) appRoot.removeView(bar);
                        }).start();
            }
        }, 4300L);
    }

    private void render() {
        if (rendering || geckoView == null) return;
        rendering = true;
        try {
            BrowserTab tab = browser.getActiveTab();
            if (tab == null) return;
            attachSession(tab.session);
            String paintCoverKey = tab.id + ":" + tab.navigationSerial;
            if (tab.loading && tab.navigationSerial > 0
                    && !paintCoverKey.equals(lastPaintCoverKey)) {
                geckoView.coverUntilFirstPaint(getColor(R.color.zen_bg));
                geckoView.invalidate();
                lastPaintCoverKey = paintCoverKey;
            }
            boolean newTab = tab.url == null || "about:blank".equals(tab.url);
            newTabSurface.setVisibility(newTab ? View.VISIBLE : View.GONE);
            addressDisplay.setText(newTab ? "Nueva pestaña" : displayHost(tab.url));
            addressDisplay.setContentDescription(newTab
                    ? "Buscar o escribir una dirección"
                    : "Dirección actual: " + tab.url);
            setEnabled(backButton, tab.canGoBack);
            setEnabled(forwardButton, tab.canGoForward);
            reloadButton.setImageResource(tab.loading ? R.drawable.ic_stop : R.drawable.ic_reload);
            reloadButton.setContentDescription(tab.loading ? "Detener" : "Recargar");
            progressBar.setVisibility(tab.loading ? View.VISIBLE : View.INVISIBLE);
            progressBar.setProgress(tab.progress);

            String fingerprint = sidebarFingerprint();
            if (wideLayout && fixedSidebar != null && !fingerprint.equals(lastSidebarFingerprint)) {
                int index = root.indexOfChild(fixedSidebar);
                View replacement = createSidebar();
                root.removeView(fixedSidebar);
                root.addView(replacement, index,
                        new LinearLayout.LayoutParams(dp(292), ViewGroup.LayoutParams.MATCH_PARENT));
                fixedSidebar = replacement;
                lastSidebarFingerprint = fingerprint;
            }
            if (sidebarPopup != null && sidebarPopup.isShowing()
                    && !fingerprint.equals(lastPopupSidebarFingerprint)) {
                refreshSidebarPopup();
            }
        } finally {
            rendering = false;
        }
    }

    private ImageButton iconButton(int drawableRes, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(drawableRes);
        button.setImageTintList(ColorStateList.valueOf(getColor(R.color.zen_text)));
        button.setBackgroundResource(R.drawable.bg_button);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setContentDescription(description);
        button.setFocusable(true);
        button.setClickable(true);
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        return button;
    }

    private TextView label(String value) {
        TextView view = text(value, 11, R.color.zen_muted);
        view.setTypeface(Typeface.DEFAULT_BOLD);
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

    private void showKeyboard(View view) {
        if (view == null) return;
        view.requestFocus();
        view.postDelayed(() -> {
            InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }, 80L);
    }

    private void hideKeyboard(View view) {
        if (view == null) return;
        InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(view.getWindowToken(), 0);
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

    private Workspace activeWorkspace() {
        for (Workspace workspace : browser.getWorkspaces()) {
            if (workspace.id.equals(browser.getActiveWorkspaceId())) return workspace;
        }
        return null;
    }

    private boolean shouldUseFixedSidebar(Configuration configuration) {
        return configuration.smallestScreenWidthDp >= 600 && configuration.screenWidthDp >= 720;
    }

    private LinearLayout.LayoutParams square(int sizeDp) {
        return new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void setEnabled(ImageButton view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : .30f);
    }

    private String displayTitle(BrowserTab tab) {
        if (tab == null || tab.title == null || tab.title.trim().isEmpty()) return "Nueva pestaña";
        return tab.title;
    }

    private String faviconLetter(BrowserTab tab) {
        String host = displayHost(tab == null ? null : tab.url);
        if (host.isEmpty() || "Nueva pestaña".equals(host)) return tab != null && tab.essential ? "◆" : "Z";
        return host.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private String activeUrlForEditing() {
        BrowserTab tab = browser.getActiveTab();
        if (tab == null || tab.url == null || "about:blank".equals(tab.url)) return "";
        return tab.url;
    }

    private String displayHost(String url) {
        if (url == null || url.trim().isEmpty() || "about:blank".equals(url)) return "Nueva pestaña";
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host == null || host.trim().isEmpty()) return url;
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception ignored) {
            return url;
        }
    }

    private String shortUrl(String url) {
        return displayHost(url);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
