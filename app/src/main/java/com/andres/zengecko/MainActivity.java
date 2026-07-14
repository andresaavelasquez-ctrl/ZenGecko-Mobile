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
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;
import org.mozilla.geckoview.WebResponse;
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
    private LinearLayout browserColumn;
    private View toolbarView;
    private ImageButton sidebarButton;
    private FrameLayout webHost;
    private GeckoView geckoView;
    private View newTabSurface;
    private View transitionScrim;
    private View fixedSidebar;
    private View edgeGestureHandle;
    private PopupWindow sidebarPopup;
    private PopupWindow searchPopup;
    private FrameLayout sidebarPanelHost;
    private View sidebarScrim;
    private View sidebarAnimatedPanel;
    private EditText searchInput;
    private LinearLayout searchResults;
    private View searchAnimatedPanel;
    private PopupWindow searchEnginePopup;
    private PopupWindow workspacePopup;
    private TextView searchEngineButton;
    private TextView addressDisplay;
    private ImageButton backButton;
    private ImageButton forwardButton;
    private ImageButton reloadButton;
    private ProgressBar progressBar;
    private GeckoSession displayedSession;
    private boolean wideLayout;
    private boolean rendering;
    private boolean renderScheduled;
    private boolean tabTransitionRunning;
    private boolean contentFullScreen;
    private boolean manualFullScreen;
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
        configureEdgeToEdge(window);

        Log.i(TAG, "onCreate; widthDp=" + getResources().getConfiguration().screenWidthDp);
        browser = BrowserRepository.get(this);
        browser.addObserver(this);
        buildUi();
        applySystemBars(true);

        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            browser.loadInActiveTab(intent.getDataString());
        }
        render();
    }

    @Override protected void onStart() {
        super.onStart();
        setCurrentSessionActive(true);
        recoverVisibleSurface();
    }

    @Override protected void onResume() {
        super.onResume();
        setCurrentSessionActive(true);
        recoverVisibleSurface();
        applyRuntimePreferences();
        mainHandler.postDelayed(() -> applySystemBars(true), 60L);
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
        ZenPanelController.dismiss();
        detachGeckoView();
        super.onDestroy();
    }

    @Override public void onBrowserStateChanged() {
        runOnUiThread(this::scheduleRender);
    }

    @Override public void onFullScreenChanged(GeckoSession session, boolean fullScreen) {
        runOnUiThread(() -> {
            BrowserTab active = browser == null ? null : browser.getActiveTab();
            if (active == null || active.session != session) return;
            contentFullScreen = fullScreen;
            if (fullScreen) manualFullScreen = false;
            applyImmersiveState();
        });
    }

    @Override public void onDownloadRequested(WebResponse response) {
        runOnUiThread(() -> {
            if (ZenPanelController.confirmDownloads(this)
                    && response != null && response.body == null) {
                String name = downloadDisplayName(response);
                new AlertDialog.Builder(this)
                        .setTitle("Descargar archivo")
                        .setMessage(name)
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Descargar", (dialog, which) ->
                                startDownload(response))
                        .show();
            } else {
                startDownload(response);
            }
        });
    }

    @Override public void onPageFinished(GeckoSession session, boolean success) {
        runOnUiThread(() -> {
            BrowserTab active = browser == null ? null : browser.getActiveTab();
            if (active == null || active.session != session) return;
            if (ZenPanelController.pageRecoveryEnabled(this)) {
                mainHandler.postDelayed(this::recoverVisibleSurface, success ? 90L : 260L);
            }
        });
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.i(TAG, "onConfigurationChanged widthDp=" + newConfig.screenWidthDp
                + " previousWide=" + wideLayout);
        applyResponsiveLayout(newConfig);
        if (appRoot != null) appRoot.requestApplyInsets();
        recoverVisibleSurface();
        render();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) mainHandler.postDelayed(() -> applySystemBars(true), 80L);
    }

    @Override public void onBackPressed() {
        if (contentFullScreen) {
            exitContentFullScreen();
            return;
        }
        if (ZenPanelController.handleBack()) return;
        if (workspacePopup != null && workspacePopup.isShowing()) {
            workspacePopup.dismiss();
            return;
        }
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
        if (browser.selectPreviousTab()) {
            recoverVisibleSurface();
            return;
        }
        confirmExitApplication();
    }

    private void rebuildUiPreservingSession() {
        applyResponsiveLayout(getResources().getConfiguration());
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
        appRoot.setBackgroundResource(R.drawable.bg_browser_canvas);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Color.TRANSPARENT);
        appRoot.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        fixedSidebar = createCompactRail();
        root.addView(fixedSidebar, new LinearLayout.LayoutParams(
                dp(58), ViewGroup.LayoutParams.MATCH_PARENT));

        browserColumn = new LinearLayout(this);
        browserColumn.setOrientation(LinearLayout.VERTICAL);
        browserColumn.setBackgroundColor(Color.TRANSPARENT);
        root.addView(browserColumn, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        toolbarView = createToolbar();
        browserColumn.addView(toolbarView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressDrawable(getDrawable(R.drawable.progress_zen));
        progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        progressBar.setAlpha(0f);
        browserColumn.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

        webHost = new FrameLayout(this);
        webHost.setBackgroundResource(R.drawable.bg_web_frame);
        webHost.setElevation(dp(2));
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

        installEdgeGestureHandle();
        setContentView(appRoot);
        installSafeAreaInsets();
        applyResponsiveLayout(getResources().getConfiguration());
    }

    private void configureEdgeToEdge(Window window) {
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attributes);
            window.setNavigationBarDividerColor(Color.TRANSPARENT);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    private boolean isImmersiveMode() {
        return contentFullScreen;
    }

    private void exitContentFullScreen() {
        BrowserTab active = browser == null ? null : browser.getActiveTab();
        if (active != null && active.session != null) {
            try { active.session.exitFullScreen(); }
            catch (RuntimeException error) { Log.w(TAG, "Unable to exit content fullscreen", error); }
        }
        contentFullScreen = false;
        applyImmersiveState();
    }

    private void applyImmersiveState() {
        boolean immersive = isImmersiveMode();
        if (immersive) {
            dismissSearchPopupImmediate();
            dismissSidebarPopupImmediate();
        }
        applySystemBars(true);
        applyResponsiveLayout(getResources().getConfiguration());
        if (appRoot != null) appRoot.requestApplyInsets();
        recoverVisibleSurface();
    }

    private void applySystemBars(boolean hide) {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                if (hide) controller.hide(WindowInsets.Type.systemBars());
                else controller.show(WindowInsets.Type.systemBars());
            }
        } else {
            int layoutFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (hide) {
                layoutFlags |= View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            }
            window.getDecorView().setSystemUiVisibility(layoutFlags);
        }
    }

    private void applyResponsiveLayout(Configuration configuration) {
        if (root == null) return;
        wideLayout = shouldUseFixedSidebar(configuration);
        boolean immersive = isImmersiveMode();
        boolean showRail = wideLayout && !immersive;

        if (fixedSidebar != null) {
            fixedSidebar.setVisibility(showRail ? View.VISIBLE : View.GONE);
            ViewGroup.LayoutParams raw = fixedSidebar.getLayoutParams();
            if (raw instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) raw;
                params.width = showRail ? dp(58) : 0;
                fixedSidebar.setLayoutParams(params);
            }
        }
        if (sidebarButton != null) {
            sidebarButton.setVisibility(!wideLayout && !immersive ? View.VISIBLE : View.GONE);
        }
        if (edgeGestureHandle != null) {
            edgeGestureHandle.setVisibility(!wideLayout && !immersive
                    && ZenPanelController.edgeSwipeEnabled(this)
                    ? View.VISIBLE : View.GONE);
        }
        if (toolbarView != null) {
            toolbarView.setVisibility(immersive ? View.GONE : View.VISIBLE);
        }
        if (progressBar != null && immersive) {
            progressBar.setVisibility(View.GONE);
        }

        if (webHost != null && webHost.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams params =
                    (LinearLayout.LayoutParams) webHost.getLayoutParams();
            params.setMargins(0, 0, 0, 0);
            webHost.setLayoutParams(params);
            webHost.setElevation(0f);
            webHost.setBackgroundColor(getColor(R.color.zen_bg));
        }

        if (immersive) root.setPadding(0, 0, 0, 0);
        else root.setPadding(safeInsetLeft, safeInsetTop, safeInsetRight, safeInsetBottom);

        root.requestLayout();
        if (geckoView != null) {
            geckoView.requestLayout();
            geckoView.invalidate();
        }
    }

    private void recoverVisibleSurface() {
        if (geckoView == null || browser == null) return;
        Runnable recover = () -> {
            BrowserTab active = browser.getActiveTab();
            if (active == null || active.session == null) return;
            try {
                active.session.setActive(true);
                if (displayedSession != active.session) attachSession(active.session);
                geckoView.requestLayout();
                geckoView.postInvalidateOnAnimation();
            } catch (RuntimeException error) {
                Log.w(TAG, "Unable to recover Gecko surface", error);
            }
        };
        mainHandler.post(recover);
        if (ZenPanelController.pageRecoveryEnabled(this)) {
            mainHandler.postDelayed(recover, 180L);
            mainHandler.postDelayed(recover, 650L);
        }
    }

    private void installSafeAreaInsets() {
        if (appRoot == null) return;
        appRoot.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            int left = 0, top = 0, right = 0, bottom = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets cutout = windowInsets.getInsets(WindowInsets.Type.displayCutout());
                left = cutout.left; top = cutout.top; right = cutout.right; bottom = cutout.bottom;
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                DisplayCutout cutout = windowInsets.getDisplayCutout();
                if (cutout != null) {
                    left = cutout.getSafeInsetLeft(); top = cutout.getSafeInsetTop();
                    right = cutout.getSafeInsetRight(); bottom = cutout.getSafeInsetBottom();
                }
            }
            boolean changed = left != safeInsetLeft || top != safeInsetTop
                    || right != safeInsetRight || bottom != safeInsetBottom;
            safeInsetLeft = left; safeInsetTop = top;
            safeInsetRight = right; safeInsetBottom = bottom;
            if (contentFullScreen) root.setPadding(0, 0, 0, 0);
            else root.setPadding(left, top, right, bottom);
            if (changed) {
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
        bar.setPadding(dp(4), dp(2), dp(4), dp(2));
        bar.setBackgroundResource(R.drawable.bg_toolbar_unified);

        sidebarButton = iconButton(R.drawable.ic_menu, "Pestañas");
        sidebarButton.setOnClickListener(v -> showSidebarPopup());
        bar.addView(sidebarButton, square(38));

        ImageButton newTab = iconButton(R.drawable.ic_add, "Nueva pestaña");
        newTab.setOnClickListener(v -> openNewTabAndSearch());
        bar.addView(newTab, square(38));

        backButton = iconButton(R.drawable.ic_back, "Atrás");
        backButton.setOnClickListener(v -> {
            BrowserTab tab = browser.getActiveTab();
            if (tab != null && tab.session != null && tab.canGoBack) tab.session.goBack();
        });
        bar.addView(backButton, square(36));

        forwardButton = iconButton(R.drawable.ic_forward, "Adelante");
        forwardButton.setOnClickListener(v -> {
            BrowserTab tab = browser.getActiveTab();
            if (tab != null && tab.session != null && tab.canGoForward) tab.session.goForward();
        });
        bar.addView(forwardButton, square(36));

        addressDisplay = text("Nueva pestaña", 12, R.color.zen_muted);
        addressDisplay.setGravity(Gravity.CENTER_VERTICAL);
        addressDisplay.setSingleLine(true);
        addressDisplay.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        addressDisplay.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
        addressDisplay.setCompoundDrawablePadding(dp(7));
        addressDisplay.setPadding(dp(12), 0, dp(12), 0);
        addressDisplay.setBackgroundResource(R.drawable.bg_address_rounder);
        addressDisplay.setOnClickListener(v -> showSearchPopup(false));
        LinearLayout.LayoutParams addressParams =
                new LinearLayout.LayoutParams(0, dp(40), 1f);
        addressParams.setMargins(dp(4), 0, dp(4), 0);
        bar.addView(addressDisplay, addressParams);

        reloadButton = iconButton(R.drawable.ic_reload, "Recargar o detener");
        reloadButton.setOnClickListener(v -> {
            BrowserTab tab = browser.getActiveTab();
            if (tab != null && tab.session != null) {
                if (tab.loading) tab.session.stop(); else tab.session.reload();
            }
        });
        bar.addView(reloadButton, square(36));

        ImageButton search = iconButton(R.drawable.ic_search, "Desplegar búsqueda");
        search.setOnClickListener(v -> showSearchPopup(false));
        bar.addView(search, square(38));
        return bar;
    }

    private View createNewTabSurface() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundResource(R.drawable.bg_new_tab_gradient);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(22), dp(34), dp(22), dp(28));
        scroll.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView logo = text("Z", 36, R.color.zen_bg);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setGravity(Gravity.CENTER);
        logo.setBackgroundResource(R.drawable.bg_zen_mark);
        page.addView(logo, new LinearLayout.LayoutParams(dp(70), dp(70)));

        TextView title = text("Zen Browser", 23, R.color.zen_text);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, dp(15), 0, dp(3));
        page.addView(title, titleParams);

        TextView subtitle = text("Un espacio tranquilo para navegar", 13, R.color.zen_muted);
        subtitle.setGravity(Gravity.CENTER);
        page.addView(subtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView search = text("Buscar o escribir una dirección", 14, R.color.zen_muted);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
        search.setCompoundDrawablePadding(dp(9));
        search.setPadding(dp(16), 0, dp(16), 0);
        search.setBackgroundResource(R.drawable.bg_search_large);
        search.setOnClickListener(v -> showSearchPopup(true));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        searchParams.setMargins(0, dp(25), 0, dp(21));
        page.addView(search, searchParams);

        TextView quickLabel = label("ACCESOS RÁPIDOS");
        quickLabel.setGravity(Gravity.CENTER);
        page.addView(quickLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout quickRow = new LinearLayout(this);
        quickRow.setGravity(Gravity.CENTER);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        quickRow.addView(quickSite("GitHub", "https://github.com", R.drawable.ic_site_github));
        quickRow.addView(quickSite("YouTube", "https://youtube.com", R.drawable.ic_site_youtube));
        quickRow.addView(quickSite("Wikipedia", "https://wikipedia.org", R.drawable.ic_site_wikipedia));
        page.addView(quickRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private View quickSite(String title, String url, int iconRes) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackgroundResource(R.drawable.bg_quick_site);
        card.setPadding(dp(6), dp(8), dp(6), dp(6));
        card.setOnClickListener(v -> {
            if (ZenPanelController.quickAccessOpensNewTab(this)) {
                browser.addTab(url, true);
            } else {
                browser.loadInActiveTab(url);
            }
        });

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        card.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));

        TextView label = text(title, 11, R.color.zen_muted);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(0, dp(5), 0, 0);
        card.addView(label, labelParams);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(dp(82), dp(72));
        cardParams.setMargins(dp(4), 0, dp(4), 0);
        card.setLayoutParams(cardParams);
        return card;
    }

    private void installEdgeGestureHandle() {
        edgeGestureHandle = new View(this);
        edgeGestureHandle.setBackgroundColor(Color.TRANSPARENT);
        edgeGestureHandle.setContentDescription("Desliza para abrir Compact Zen");

        final float[] gesture = new float[2];
        edgeGestureHandle.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    gesture[0] = event.getX();
                    gesture[1] = event.getY();
                    return true;
                case MotionEvent.ACTION_UP:
                    float dx = event.getX() - gesture[0];
                    float dy = event.getY() - gesture[1];
                    if (ZenPanelController.edgeSwipeEnabled(this)
                            && dx > dp(46) && Math.abs(dy) < dp(96)) showSidebarPopup();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
                default:
                    return true;
            }
        });

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dp(18), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
        appRoot.addView(edgeGestureHandle, params);
    }

    private View createCompactRail() {
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.setPadding(dp(5), dp(6), dp(5), dp(6));
        rail.setBackgroundResource(R.drawable.bg_compact_rail);

        TextView zen = text("Z", 18, R.color.zen_bg);
        zen.setTypeface(Typeface.DEFAULT_BOLD);
        zen.setGravity(Gravity.CENTER);
        zen.setBackgroundResource(R.drawable.bg_zen_mark);
        zen.setContentDescription("Abrir pestañas");
        zen.setOnClickListener(v -> showSidebarPopup());
        rail.addView(zen, new LinearLayout.LayoutParams(dp(42), dp(42)));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        int essentialCount = 0;
        for (BrowserTab tab : browser.getVisibleTabs()) {
            if (!tab.essential) continue;
            column.addView(compactTabButton(tab), compactRailItemParams());
            if (++essentialCount >= 5) break;
        }
        scroll.addView(column);
        rail.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        ImageButton add = iconButton(R.drawable.ic_add, "Nueva pestaña");
        add.setOnClickListener(v -> openNewTabAndSearch());
        rail.addView(add, new LinearLayout.LayoutParams(dp(42), dp(42)));

        ImageButton expand = iconButton(R.drawable.ic_menu, "Abrir pestañas");
        expand.setOnClickListener(v -> showSidebarPopup());
        LinearLayout.LayoutParams expandParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        expandParams.setMargins(0, dp(4), 0, 0);
        rail.addView(expand, expandParams);
        return rail;
    }

    private LinearLayout.LayoutParams compactRailItemParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(46), dp(46));
        params.setMargins(0, 0, 0, dp(6));
        return params;
    }

    private TextView compactTabButton(BrowserTab tab) {
        boolean active = tab.id.equals(browser.getActiveTabId());
        TextView button = text(faviconLetter(tab), 13, R.color.zen_text);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setContentDescription(displayTitle(tab));
        button.setBackgroundResource(active ? R.drawable.bg_tab_active : R.drawable.bg_favicon);
        button.setOnClickListener(v -> {
            if (!tab.id.equals(browser.getActiveTabId())) {
                animateWebTransition(() -> browser.selectTab(tab.id));
            }
        });
        button.setOnLongClickListener(v -> {
            showSidebarPopup();
            return true;
        });
        return button;
    }

    private TextView compactWorkspaceButton(Workspace workspace) {
        boolean active = workspace.id.equals(browser.getActiveWorkspaceId());
        String mark = workspace.name == null || workspace.name.isEmpty()
                ? "•" : workspace.name.substring(0, 1).toUpperCase(Locale.ROOT);
        TextView button = text(mark, 14, active ? R.color.zen_text : R.color.zen_muted);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription("Espacio " + workspace.name);
        button.setBackgroundResource(active ? R.drawable.bg_tab_active : R.drawable.bg_tab_idle);
        button.setOnClickListener(v -> {
            if (!workspace.id.equals(browser.getActiveWorkspaceId())) {
                animateWebTransition(() -> browser.switchWorkspace(workspace.id));
            }
        });
        return button;
    }

    private View createEssentialGrid(List<BrowserTab> tabs) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, 0, 0, dp(6));

        int count = Math.min(6, tabs.size());
        for (int start = 0; start < count; start += 3) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            for (int column = 0; column < 3; column++) {
                int index = start + column;
                if (index < count) {
                    BrowserTab tab = tabs.get(index);
                    LinearLayout.LayoutParams cardParams =
                            new LinearLayout.LayoutParams(0, dp(82), 1f);
                    cardParams.setMargins(dp(3), dp(3), dp(3), dp(3));
                    row.addView(essentialCard(tab), cardParams);
                } else {
                    row.addView(new View(this),
                            new LinearLayout.LayoutParams(0, dp(82), 1f));
                }
            }
            grid.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(88)));
        }
        return grid;
    }

    private View essentialCard(BrowserTab tab) {
        boolean active = tab.id.equals(browser.getActiveTabId());
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(5), dp(7), dp(5), dp(5));
        card.setBackgroundResource(active ? R.drawable.bg_tab_active : R.drawable.bg_essential_card);
        card.setContentDescription("Esencial: " + displayTitle(tab));
        card.setOnClickListener(v -> selectTabFromSidebar(tab.id));
        card.setOnLongClickListener(v -> {
            browser.toggleEssential(tab.id);
            return true;
        });

        TextView mark = text(faviconLetter(tab), 14, R.color.zen_text);
        mark.setTypeface(Typeface.DEFAULT_BOLD);
        mark.setGravity(Gravity.CENTER);
        mark.setBackgroundResource(tab.loading
                ? R.drawable.bg_favicon_loading : R.drawable.bg_favicon);
        card.addView(mark, new LinearLayout.LayoutParams(dp(36), dp(36)));

        TextView title = text(displayTitle(tab), 11, R.color.zen_muted);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, dp(5), 0, 0);
        card.addView(title, titleParams);
        return card;
    }

    private View createSidebar() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.setBackgroundResource(R.drawable.bg_sidebar_panel);

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setPadding(dp(9), dp(5), dp(9), dp(5));
        brand.setBackgroundResource(R.drawable.bg_brand_header);

        ImageView brandIcon = new ImageView(this);
        brandIcon.setImageResource(R.mipmap.ic_launcher);
        brandIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        brand.addView(brandIcon, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout brandLabels = new LinearLayout(this);
        brandLabels.setOrientation(LinearLayout.VERTICAL);
        TextView brandTitle = text("ZEN BROWSER", 16, R.color.zen_text);
        brandTitle.setTypeface(Typeface.DEFAULT_BOLD);
        TextView brandSubtitle = text("Navegación tranquila", 10, R.color.zen_muted);
        brandLabels.addView(brandTitle);
        brandLabels.addView(brandSubtitle);
        LinearLayout.LayoutParams brandLabelParams =
                new LinearLayout.LayoutParams(0, dp(46), 1f);
        brandLabelParams.setMargins(dp(10), 0, 0, 0);
        brand.addView(brandLabels, brandLabelParams);
        panel.addView(brand, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER_VERTICAL);

        ImageButton settings = iconButton(R.drawable.ic_settings, "Configuración");
        settings.setOnClickListener(v -> {
            dismissSidebarPopupImmediate();
            ZenPanelController.showSettings(this, browser, this::showSidebarPopup);
        });
        navigation.addView(settings, square(38));

        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.zen_border));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(dp(1), dp(24));
        dividerParams.setMargins(dp(4), 0, dp(4), 0);
        navigation.addView(divider, dividerParams);

        ImageButton home = iconButton(R.drawable.ic_home, "Inicio");
        home.setOnClickListener(v -> {
            String homeUrl = ZenPanelController.homeUrl(this);
            dismissSidebarPopup();
            browser.loadInActiveTab(homeUrl);
        });
        navigation.addView(home, square(38));

        ImageButton sideBack = iconButton(R.drawable.ic_back, "Atrás");
        sideBack.setOnClickListener(v -> {
            BrowserTab tab = browser.getActiveTab();
            if (tab != null && tab.session != null && tab.canGoBack) tab.session.goBack();
        });
        navigation.addView(sideBack, square(38));

        ImageButton sideForward = iconButton(R.drawable.ic_forward, "Adelante");
        sideForward.setOnClickListener(v -> {
            BrowserTab tab = browser.getActiveTab();
            if (tab != null && tab.session != null && tab.canGoForward) tab.session.goForward();
        });
        navigation.addView(sideForward, square(38));

        ImageButton sideReload = iconButton(R.drawable.ic_reload, "Recargar");
        sideReload.setOnClickListener(v -> {
            BrowserTab tab = browser.getActiveTab();
            if (tab != null && tab.session != null) {
                if (tab.loading) tab.session.stop(); else tab.session.reload();
            }
        });
        navigation.addView(sideReload, square(38));
        panel.addView(navigation, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(43)));

        panel.addView(createSidebarShortcutGrid());

        LinearLayout workspace = new LinearLayout(this);
        workspace.setGravity(Gravity.CENTER_VERTICAL);
        workspace.setPadding(dp(12), 0, dp(9), 0);
        workspace.setBackgroundResource(R.drawable.bg_profile_selector);
        ImageView workspaceIcon = new ImageView(this);
        workspaceIcon.setImageResource(R.drawable.ic_profile);
        workspaceIcon.setImageTintList(ColorStateList.valueOf(getColor(R.color.zen_text)));
        workspace.addView(workspaceIcon, new LinearLayout.LayoutParams(dp(24), dp(24)));

        TextView workspaceName = text(
                browser.getActiveWorkspaceName(), 14, R.color.zen_text);
        LinearLayout.LayoutParams workspaceNameParams =
                new LinearLayout.LayoutParams(0, dp(44), 1f);
        workspaceNameParams.setMargins(dp(10), 0, 0, 0);
        workspace.addView(workspaceName, workspaceNameParams);

        ImageView workspaceArrow = new ImageView(this);
        workspaceArrow.setImageResource(R.drawable.ic_chevron_down);
        workspaceArrow.setImageTintList(ColorStateList.valueOf(getColor(R.color.zen_muted)));
        workspace.addView(workspaceArrow, new LinearLayout.LayoutParams(dp(22), dp(22)));

        workspace.setOnClickListener(v -> showWorkspaceMenu(workspace));
        LinearLayout.LayoutParams workspaceParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        workspaceParams.setMargins(0, dp(7), 0, dp(5));
        panel.addView(workspace, workspaceParams);

        TextView openLabel = label("PESTAÑAS ABIERTAS");
        panel.addView(openLabel);

        ScrollView tabsScroll = new ScrollView(this);
        tabsScroll.setFillViewport(true);
        tabsScroll.setVerticalScrollBarEnabled(false);
        LinearLayout tabsColumn = new LinearLayout(this);
        tabsColumn.setOrientation(LinearLayout.VERTICAL);
        for (BrowserTab tab : browser.getVisibleTabs()) {
            tabsColumn.addView(tabRow(tab));
        }

        TextView newTab = text("＋   Nueva pestaña", 13, R.color.zen_muted);
        newTab.setGravity(Gravity.CENTER_VERTICAL);
        newTab.setPadding(dp(12), 0, dp(12), 0);
        newTab.setBackgroundResource(R.drawable.bg_button);
        newTab.setOnClickListener(v -> openNewTabFromSidebar());
        LinearLayout.LayoutParams newTabParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        newTabParams.setMargins(0, dp(3), 0, dp(4));
        tabsColumn.addView(newTab, newTabParams);

        tabsScroll.addView(tabsColumn);
        panel.addView(tabsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout utilities = new LinearLayout(this);
        utilities.setGravity(Gravity.CENTER);
        utilities.addView(sidebarBottomAction(
                R.drawable.ic_star,
                "Favoritos",
                () -> ZenPanelController.showFavorites(
                        this, browser, this::showSidebarPopup)),
                new LinearLayout.LayoutParams(0, dp(50), 1f));
        utilities.addView(sidebarBottomAction(
                R.drawable.ic_downloads,
                "Descargas",
                () -> ZenPanelController.showDownloads(
                        this, browser, this::showSidebarPopup)),
                new LinearLayout.LayoutParams(0, dp(50), 1f));
        utilities.addView(sidebarBottomAction(
                R.drawable.ic_history,
                "Historial",
                () -> ZenPanelController.showHistory(
                        this, browser, this::showSidebarPopup)),
                new LinearLayout.LayoutParams(0, dp(50), 1f));
        utilities.addView(sidebarBottomAction(
                R.drawable.ic_profile,
                "Perfil",
                () -> ZenPanelController.showProfiles(
                        this, this::showSidebarPopup)),
                new LinearLayout.LayoutParams(0, dp(50), 1f));
        panel.addView(utilities, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        return panel;
    }

    private void showWorkspaceMenu(View anchor) {
        if (workspacePopup != null) workspacePopup.dismiss();

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(8), dp(8), dp(8), dp(8));
        menu.setBackgroundResource(R.drawable.bg_sidebar_panel);
        menu.setElevation(dp(20));

        for (Workspace item : browser.getWorkspaces()) {
            boolean selected = item.id.equals(browser.getActiveWorkspaceId());
            TextView row = text(
                    (selected ? "✓  " : "    ") + item.name,
                    14,
                    selected ? R.color.zen_text : R.color.zen_muted);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), 0, dp(12), 0);
            row.setBackgroundResource(
                    selected ? R.drawable.bg_tab_active : R.drawable.bg_panel_card);
            row.setOnClickListener(v -> {
                workspacePopup.dismiss();
                browser.switchWorkspace(item.id);
                refreshSidebarPopup();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
            params.setMargins(0, 0, 0, dp(5));
            menu.addView(row, params);
        }

        workspacePopup = new PopupWindow(menu, dp(250),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        workspacePopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        workspacePopup.setOutsideTouchable(true);
        workspacePopup.setElevation(dp(22));
        workspacePopup.showAsDropDown(anchor, Math.max(0, anchor.getWidth() - dp(250)), dp(4));
    }

    private View tabRow(BrowserTab tab) {
        boolean active = browser.getActiveTab() != null
                && browser.getActiveTab().id.equals(tab.id);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(7), dp(3), dp(3), dp(3));
        row.setBackgroundResource(
                active ? R.drawable.bg_tab_active : R.drawable.bg_tab_idle);
        row.setOnClickListener(v -> selectTabFromSidebar(tab.id));

        if (ZenPanelController.swipeCloseTabs(this)) {
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
                        if (Math.abs(moveX) > dp(8)) {
                            view.setTranslationX(moveX * .25f);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        view.setPressed(false);
                        float dx = event.getX() - gesture[0];
                        float dy = event.getY() - gesture[1];
                        if (Math.abs(dx) > dp(72)
                                && Math.abs(dx) > Math.abs(dy) * 1.4f) {
                            animateTabRowClose(view, tab.id, dx >= 0 ? 1 : -1);
                        } else {
                            view.animate()
                                    .translationX(0f)
                                    .setDuration(90)
                                    .withEndAction(view::performClick)
                                    .start();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        view.setPressed(false);
                        view.animate().translationX(0f).setDuration(90).start();
                        return true;
                    default:
                        return true;
                }
            });
        }

        TextView favicon = text(faviconLetter(tab), 12, R.color.zen_text);
        favicon.setTypeface(Typeface.DEFAULT_BOLD);
        favicon.setGravity(Gravity.CENTER);
        favicon.setBackgroundResource(
                tab.loading ? R.drawable.bg_favicon_loading : R.drawable.bg_favicon);
        row.addView(favicon, square(30));

        TextView title = text(displayTitle(tab), 13, R.color.zen_text);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(0, dp(38), 1f);
        titleParams.setMargins(dp(9), 0, dp(3), 0);
        row.addView(title, titleParams);

        if (ZenPanelController.showTabCloseButtons(this)) {
            ImageButton close = iconButton(R.drawable.ic_close, "Cerrar pestaña");
            close.setBackgroundResource(R.drawable.bg_close_button);
            close.setPadding(dp(8), dp(8), dp(8), dp(8));
            close.setOnClickListener(v -> {
                v.setEnabled(false);
                animateTabRowClose(row, tab.id, 1);
            });
            row.addView(close, square(34));
        }

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        rowParams.setMargins(0, 0, 0, dp(3));
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
        if (isImmersiveMode()) return;
        dismissSidebarPopupImmediate();

        int availableWidth = Math.max(dp(240),
                getResources().getDisplayMetrics().widthPixels - safeInsetLeft - safeInsetRight);
        int panelWidth = Math.min(dp(410), (int) (availableWidth * .94f));
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
        if (isImmersiveMode()) return;
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
        if (ZenPanelController.searchOpenTabsEnabled(this)) {
            for (BrowserTab tab : browser.getTabs()) {
                String haystack = (displayTitle(tab) + " " + safe(tab.url))
                        .toLowerCase(Locale.ROOT);
                if (normalizedQuery.isEmpty() || haystack.contains(normalizedQuery)) {
                    matchingTabs.add(tab);
                }
                if (matchingTabs.size() >= 6) break;
            }
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
        if (ZenPanelController.searchSuggestionsEnabled(this)) {
            for (String url : browser.getRecentUrls()) {
                String host = displayHost(url);
                String haystack = (host + " " + url).toLowerCase(Locale.ROOT);
                if (!normalizedQuery.isEmpty() && !haystack.contains(normalizedQuery)) continue;
                if (recentCount == 0) searchResults.addView(label("RECIENTES"));
                searchResults.addView(searchResultRow(host, url,
                        host.isEmpty() ? "•" : host.substring(0, 1).toUpperCase(Locale.ROOT),
                        () -> {
                            dismissSearchPopup();
                            browser.loadInActiveTab(url);
                        }));
                recentCount++;
                if (recentCount >= 5) break;
            }
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
        if (transitionScrim == null || tabTransitionRunning
                || !ZenPanelController.animationsEnabled(this)) {
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

    void applySettingsNow() {
        applyRuntimePreferences();
        render();
        if (sidebarPopup != null && sidebarPopup.isShowing()) refreshSidebarPopup();
    }

    private void applyRuntimePreferences() {
        if (ZenPanelController.keepScreenAwake(this)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        applyResponsiveLayout(getResources().getConfiguration());
    }

    private void confirmExitApplication() {
        if (!ZenPanelController.confirmExitEnabled(this)) {
            finishAndRemoveTask();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Cerrar Zen Browser")
                .setMessage("¿Quieres salir del navegador?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Cerrar", (dialog, which) -> finishAndRemoveTask())
                .show();
    }

    private void startDownload(WebResponse response) {
        DownloadStore.enqueue(this, response);
        if (ZenPanelController.downloadNotificationsEnabled(this)) {
            showDownloadStarted(response);
        }
    }

    private String downloadDisplayName(WebResponse response) {
        try {
            if (response != null && response.uri != null) {
                String segment = Uri.parse(response.uri).getLastPathSegment();
                if (segment != null && !segment.trim().isEmpty()) {
                    return Uri.decode(segment);
                }
            }
        } catch (Exception ignored) { }
        return "Archivo";
    }

    private void showDownloadStarted(WebResponse response) {
        if (appRoot == null) return;
        View previous = appRoot.findViewWithTag("download-notice");
        if (previous != null) appRoot.removeView(previous);

        LinearLayout notice = new LinearLayout(this);
        notice.setTag("download-notice");
        notice.setGravity(Gravity.CENTER_VERTICAL);
        notice.setPadding(dp(12), dp(7), dp(8), dp(7));
        notice.setBackgroundResource(R.drawable.bg_download_notice);
        notice.setElevation(dp(18));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_downloads);
        icon.setImageTintList(ColorStateList.valueOf(getColor(R.color.zen_accent)));
        notice.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(30)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("Descarga iniciada", 14, R.color.zen_text);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView subtitle = text(downloadDisplayName(response), 11, R.color.zen_muted);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        labels.addView(title);
        labels.addView(subtitle);
        LinearLayout.LayoutParams labelParams =
                new LinearLayout.LayoutParams(0, dp(46), 1f);
        labelParams.setMargins(dp(10), 0, dp(6), 0);
        notice.addView(labels, labelParams);

        TextView view = text("VER", 12, R.color.zen_accent);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), 0, dp(10), 0);
        view.setBackgroundResource(R.drawable.bg_button);
        view.setOnClickListener(v -> {
            if (notice.getParent() == appRoot) appRoot.removeView(notice);
            ZenPanelController.showDownloads(this, browser, this::showSidebarPopup);
        });
        notice.addView(view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                Math.min(dp(430),
                        getResources().getDisplayMetrics().widthPixels - dp(28)),
                dp(62),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = safeInsetBottom + dp(16);
        appRoot.addView(notice, params);

        notice.setAlpha(0f);
        notice.setTranslationY(dp(18));
        notice.animate().alpha(1f).translationY(0f).setDuration(180L).start();
        mainHandler.postDelayed(() -> {
            if (notice.getParent() == appRoot) {
                notice.animate()
                        .alpha(0f)
                        .translationY(dp(14))
                        .setDuration(150L)
                        .withEndAction(() -> {
                            if (notice.getParent() == appRoot) appRoot.removeView(notice);
                        })
                        .start();
            }
        }, 5200L);
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

    private void scheduleRender() {
        if (renderScheduled) return;
        renderScheduled = true;
        mainHandler.postDelayed(() -> {
            renderScheduled = false;
            render();
        }, ZenPanelController.renderDelayMs(this));
    }

    private void updateProgressBar(BrowserTab tab) {
        if (progressBar == null) return;
        boolean visible = tab.loading && !contentFullScreen
                && ZenPanelController.showProgressBar(this);
        progressBar.setProgress(tab.progress);
        if (visible) {
            if (progressBar.getVisibility() != View.VISIBLE) {
                progressBar.animate().cancel();
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setAlpha(0f);
                progressBar.animate().alpha(1f).setDuration(100L).start();
            }
        } else if (progressBar.getVisibility() == View.VISIBLE) {
            progressBar.animate().cancel();
            progressBar.animate().alpha(0f).setDuration(150L)
                    .withEndAction(() -> progressBar.setVisibility(View.INVISIBLE)).start();
        }
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
                geckoView.invalidate();
                lastPaintCoverKey = paintCoverKey;
            }
            boolean newTab = tab.url == null || "about:blank".equals(tab.url);
            newTabSurface.setVisibility(newTab ? View.VISIBLE : View.GONE);
            if (addressDisplay != null) {
                boolean showAddress = !newTab && ZenPanelController.showAddressBar(this);
                addressDisplay.setVisibility(showAddress ? View.VISIBLE : View.GONE);
                addressDisplay.setText(showAddress ? displayHost(tab.url) : "Nueva pestaña");
                addressDisplay.setContentDescription(showAddress
                        ? "Dirección actual: " + tab.url
                        : "Buscar o escribir una dirección");
            }
            setEnabled(backButton, tab.canGoBack);
            setEnabled(forwardButton, tab.canGoForward);
            reloadButton.setImageResource(tab.loading ? R.drawable.ic_stop : R.drawable.ic_reload);
            reloadButton.setContentDescription(tab.loading ? "Detener" : "Recargar");
            updateProgressBar(tab);

            String fingerprint = sidebarFingerprint();
            if (fixedSidebar != null && !fingerprint.equals(lastSidebarFingerprint)) {
                int index = root.indexOfChild(fixedSidebar);
                int visibility = fixedSidebar.getVisibility();
                View replacement = createCompactRail();
                replacement.setVisibility(visibility);
                root.removeView(fixedSidebar);
                root.addView(replacement, index,
                        new LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.MATCH_PARENT));
                fixedSidebar = replacement;
                lastSidebarFingerprint = fingerprint;
                applyResponsiveLayout(getResources().getConfiguration());
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
                    .append(tab.essential).append(';');
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
        boolean phoneLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                && configuration.screenWidthDp >= 600;
        boolean tablet = configuration.smallestScreenWidthDp >= 600;
        return phoneLandscape || tablet;
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
