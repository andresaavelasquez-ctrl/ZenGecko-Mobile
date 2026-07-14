package com.andres.zengecko;

import android.app.Activity;
import android.app.AlertDialog;
import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
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
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
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
import java.io.File;
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
    private FrameLayout paintGuard;
    private TextView paintGuardStatus;
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
    private SoundPool keySoundPool;
    private int mechanicalKeySoundId;
    private boolean mechanicalKeySoundReady;
    private ImageView homeBackgroundView;
    private View homeBackgroundScrim;
    private TextView homeKeyView;
    private boolean homeWakePlayed;
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
    private Runnable paintGuardTimeout;
    private Runnable downloadNoticeTicker;
    private OnBackInvokedCallback backInvokedCallback;
    private boolean activityResumed;
    private int safeInsetLeft;
    private int safeInsetTop;
    private int safeInsetRight;
    private int safeInsetBottom;
    private String lastSidebarFingerprint = "";
    private String lastPopupSidebarFingerprint = "";
    private LinearLayout paintGuardSlowPanel;
    private View addressGlow;
    private ObjectAnimator addressGlowAnimator;
    private ImageView transitionSnapshot;
    private PopupWindow contextMenuPopup;
    private PopupWindow contextPreviewPopup;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        configureEdgeToEdge(window);

        Log.i(TAG, "onCreate; widthDp=" + getResources().getConfiguration().screenWidthDp);
        browser = BrowserRepository.get(this);
        browser.addObserver(this);
        initializeMechanicalKeySound();
        buildUi();
        registerModernBackCallback();
        applySystemBars(true);

        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            browser.loadInActiveTab(intent.getDataString());
        }
        render();
    }

    @Override protected void onStart() {
        super.onStart();
        if (browser != null) browser.setAppForeground(true);
        recoverVisibleSurface();
    }

    @Override protected void onResume() {
        super.onResume();
        activityResumed = true;
        if (browser != null) browser.setAppForeground(true);
        recoverVisibleSurface();
        applyRuntimePreferences();
        render();
        ZenPanelController.maybeTrimCache(this);
        mainHandler.postDelayed(() -> applySystemBars(true), 60L);
    }

    @Override protected void onPause() {
        activityResumed = false;
        super.onPause();
    }

    @Override protected void onStop() {
        if (browser != null) browser.setAppForeground(false);
        super.onStop();
    }

    @Override protected void onDestroy() {
        Log.i(TAG, "onDestroy");
        activityResumed = false;
        if (browser != null) browser.removeObserver(this);
        unregisterModernBackCallback();
        cancelPaintGuardTimeout();
        if (downloadNoticeTicker != null) mainHandler.removeCallbacks(downloadNoticeTicker);
        dismissSearchPopupImmediate();
        dismissSidebarPopupImmediate();
        dismissContextMenuImmediate();
        stopAddressGlow();
        clearTransitionSnapshot();
        ZenPanelController.dismiss();
        releaseMechanicalKeySound();
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
            if (!success) hidePaintGuard(session, true);
        });
    }

    @Override public void onPaintStatusReset(GeckoSession session) {
        runOnUiThread(() -> {
            BrowserTab active = browser == null ? null : browser.getActiveTab();
            if (active == null || active.session != session) return;
            lastPaintCoverKey = "";
            if (!activityResumed || active.showStartPage) return;
            showPaintGuard(session, "Preparando la página…");
        });
    }

    @Override public void onFirstComposite(GeckoSession session) {
        runOnUiThread(() -> {
            BrowserTab active = browser == null ? null : browser.getActiveTab();
            if (active == null || active.session != session) return;
            if (transitionSnapshot != null) {
                mainHandler.postDelayed(
                        () -> fadeTransitionSnapshot(transitionGeneration), 25L);
            }
        });
    }

    @Override public void onFirstContentfulPaint(GeckoSession session) {
        runOnUiThread(() -> {
            hidePaintGuard(session, false);
            if (transitionSnapshot != null) {
                fadeTransitionSnapshot(transitionGeneration);
            }
        });
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.i(TAG, "onConfigurationChanged widthDp=" + newConfig.screenWidthDp
                + " previousWide=" + wideLayout);
        applyResponsiveLayout(newConfig);
        applyHomePreferences();
        dismissContextMenuImmediate();
        if (appRoot != null) appRoot.requestApplyInsets();
        recoverVisibleSurface();
        render();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) mainHandler.postDelayed(() -> applySystemBars(true), 80L);
    }

    @Override public void onBackPressed() {
        handleBackNavigation();
    }

    private void handleBackNavigation() {
        if (contextPreviewPopup != null && contextPreviewPopup.isShowing()) {
            contextPreviewPopup.dismiss();
            return;
        }
        if (contextMenuPopup != null && contextMenuPopup.isShowing()) {
            contextMenuPopup.dismiss();
            return;
        }
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

        BrowserTab tab = browser == null ? null : browser.getActiveTab();
        if (tab != null && tab.session != null && tab.canGoBack) {
            tab.session.goBack();
            return;
        }
        if (browser != null && browser.selectPreviousTab()) {
            recoverVisibleSurface();
            return;
        }
        confirmExitApplication();
    }

    private void registerModernBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || backInvokedCallback != null) return;
        backInvokedCallback = this::handleBackNavigation;
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                backInvokedCallback);
    }

    private void unregisterModernBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || backInvokedCallback == null) return;
        try {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backInvokedCallback);
        } catch (RuntimeException ignored) { }
        backInvokedCallback = null;
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
        if (browser != null) browser.setAppForeground(active);
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
        progressBar.setProgressDrawable(
                getDrawable(R.drawable.progress_navigation_edge));
        progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        progressBar.setAlpha(0f);
        browserColumn.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

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

        paintGuard = createPaintGuard();
        paintGuard.setVisibility(View.GONE);
        webHost.addView(paintGuard, new FrameLayout.LayoutParams(
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


    private void installSafeAreaInsets() {
        if (appRoot == null) return;
        appRoot.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            int left = 0;
            int top = 0;
            int right = 0;
            int bottom = 0;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets cutout = windowInsets.getInsets(
                        WindowInsets.Type.displayCutout());
                left = cutout.left;
                top = cutout.top;
                right = cutout.right;
                bottom = cutout.bottom;
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                DisplayCutout cutout = windowInsets.getDisplayCutout();
                if (cutout != null) {
                    left = cutout.getSafeInsetLeft();
                    top = cutout.getSafeInsetTop();
                    right = cutout.getSafeInsetRight();
                    bottom = cutout.getSafeInsetBottom();
                }
            }

            boolean changed = left != safeInsetLeft
                    || top != safeInsetTop
                    || right != safeInsetRight
                    || bottom != safeInsetBottom;

            safeInsetLeft = left;
            safeInsetTop = top;
            safeInsetRight = right;
            safeInsetBottom = bottom;

            if (root != null) {
                if (contentFullScreen) {
                    root.setPadding(0, 0, 0, 0);
                } else {
                    root.setPadding(left, top, right, bottom);
                }
            }

            if (changed) {
                dismissSearchPopupImmediate();
                dismissSidebarPopupImmediate();
            }
            return windowInsets;
        });
        appRoot.requestApplyInsets();
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
        if (geckoView != null) geckoView.requestLayout();
    }

    private void recoverVisibleSurface() {
        if (geckoView == null || browser == null) return;
        BrowserTab active = browser.getActiveTab();
        if (active == null || active.session == null) return;
        try {
            if (displayedSession != active.session) attachSession(active.session);
            geckoView.requestLayout();
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to restore the active Gecko session", error);
        }
    }

    private FrameLayout createPaintGuard() {
        FrameLayout guard = new FrameLayout(this);
        guard.setBackgroundResource(R.drawable.bg_paint_guard_minimal);
        guard.setClickable(false);

        paintGuardSlowPanel = new LinearLayout(this);
        paintGuardSlowPanel.setOrientation(LinearLayout.VERTICAL);
        paintGuardSlowPanel.setGravity(Gravity.CENTER);
        paintGuardSlowPanel.setPadding(dp(14), dp(10), dp(14), dp(10));
        paintGuardSlowPanel.setBackgroundResource(R.drawable.bg_slow_load_panel);
        paintGuardSlowPanel.setVisibility(View.GONE);
        paintGuardSlowPanel.setClickable(true);

        paintGuardStatus = text("", 11, R.color.zen_muted);
        paintGuardStatus.setGravity(Gravity.CENTER);
        paintGuardStatus.setSingleLine(true);
        paintGuardStatus.setEllipsize(TextUtils.TruncateAt.END);
        paintGuardSlowPanel.addView(paintGuardStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);

        TextView retry = text("REINTENTAR", 11, R.color.zen_accent);
        retry.setTypeface(Typeface.DEFAULT_BOLD);
        retry.setGravity(Gravity.CENTER);
        retry.setBackgroundResource(R.drawable.bg_context_action);
        retry.setOnClickListener(v -> {
            BrowserTab tab = browser == null ? null : browser.getActiveTab();
            if (tab != null && tab.session != null) tab.session.reload();
        });
        actions.addView(retry, new LinearLayout.LayoutParams(dp(112), dp(38)));

        TextView cancel = text("CANCELAR", 11, R.color.zen_muted);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackgroundResource(R.drawable.bg_context_action);
        cancel.setOnClickListener(v -> {
            BrowserTab tab = browser == null ? null : browser.getActiveTab();
            if (tab != null && tab.session != null) tab.session.stop();
            if (tab != null) hidePaintGuard(tab.session, false);
        });
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(dp(106), dp(38));
        cancelParams.setMargins(dp(7), 0, 0, 0);
        actions.addView(cancel, cancelParams);
        paintGuardSlowPanel.addView(actions);

        FrameLayout.LayoutParams slowParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        slowParams.bottomMargin = dp(28);
        guard.addView(paintGuardSlowPanel, slowParams);
        return guard;
    }

    private void showPaintGuard(GeckoSession session, String ignoredMessage) {
        if (paintGuard == null || browser == null) return;
        BrowserTab active = browser.getActiveTab();
        if (active == null || active.session != session || active.showStartPage) return;

        paintGuard.animate().cancel();
        paintGuard.setAlpha(1f);
        paintGuard.setVisibility(View.VISIBLE);
        if (paintGuardSlowPanel != null) {
            paintGuardSlowPanel.animate().cancel();
            paintGuardSlowPanel.setAlpha(0f);
            paintGuardSlowPanel.setVisibility(View.GONE);
        }
        startAddressGlow();
        cancelPaintGuardTimeout();

        paintGuardTimeout = () -> {
            BrowserTab current = browser == null ? null : browser.getActiveTab();
            if (current == null || current.session != session || paintGuard == null
                    || paintGuard.getVisibility() != View.VISIBLE) {
                return;
            }
            String host = displayHost(current.url);
            if (paintGuardStatus != null) {
                paintGuardStatus.setText(
                        (host.isEmpty() ? "La página" : host)
                                + " está tardando más de lo habitual");
            }
            if (paintGuardSlowPanel != null) {
                paintGuardSlowPanel.setVisibility(View.VISIBLE);
                paintGuardSlowPanel.setTranslationY(dp(8));
                paintGuardSlowPanel.setAlpha(0f);
                paintGuardSlowPanel.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(170L)
                        .start();
            }
        };
        mainHandler.postDelayed(paintGuardTimeout, 6200L);
    }

    private void hidePaintGuard(GeckoSession session, boolean immediate) {
        if (paintGuard == null || browser == null) return;
        BrowserTab active = browser.getActiveTab();
        if (active == null || active.session != session) return;
        cancelPaintGuardTimeout();
        stopAddressGlow();
        paintGuard.animate().cancel();
        if (paintGuardSlowPanel != null) paintGuardSlowPanel.animate().cancel();

        if (immediate || !ZenPanelController.animationsEnabled(this)) {
            paintGuard.setAlpha(0f);
            paintGuard.setVisibility(View.GONE);
            if (paintGuardSlowPanel != null) paintGuardSlowPanel.setVisibility(View.GONE);
            clearTransitionSnapshot();
        } else {
            paintGuard.animate()
                    .alpha(0f)
                    .setDuration(145L)
                    .withEndAction(() -> {
                        paintGuard.setVisibility(View.GONE);
                        if (paintGuardSlowPanel != null) {
                            paintGuardSlowPanel.setVisibility(View.GONE);
                        }
                        clearTransitionSnapshot();
                    })
                    .start();
        }
    }

    private void cancelPaintGuardTimeout() {
        if (paintGuardTimeout != null) {
            mainHandler.removeCallbacks(paintGuardTimeout);
            paintGuardTimeout = null;
        }
    }

    private void startAddressGlow() {
        if (addressGlow == null || contentFullScreen) return;
        addressGlow.setVisibility(View.VISIBLE);
        addressGlow.setAlpha(.72f);
        if (addressGlowAnimator != null) addressGlowAnimator.cancel();
        float distance = Math.max(dp(140), addressGlow.getWidth() * .7f);
        addressGlowAnimator = ObjectAnimator.ofFloat(
                addressGlow, View.TRANSLATION_X, -distance, distance);
        addressGlowAnimator.setDuration(1250L);
        addressGlowAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        addressGlowAnimator.setRepeatMode(ObjectAnimator.RESTART);
        addressGlowAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
        addressGlowAnimator.start();
    }

    private void stopAddressGlow() {
        if (addressGlowAnimator != null) {
            addressGlowAnimator.cancel();
            addressGlowAnimator = null;
        }
        if (addressGlow != null) {
            addressGlow.animate().cancel();
            addressGlow.animate().alpha(0f).setDuration(120L)
                    .withEndAction(() -> addressGlow.setVisibility(View.INVISIBLE))
                    .start();
        }
    }

    private View createToolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(5), dp(3), dp(5), dp(3));
        bar.setBackgroundResource(R.drawable.bg_toolbar_soft);

        sidebarButton = toolbarButton(R.drawable.ic_menu, "Pestañas");
        sidebarButton.setOnClickListener(v -> showSidebarPopup());
        bar.addView(sidebarButton, square(34));

        ImageButton newTab = toolbarButton(R.drawable.ic_add, "Nueva pestaña");
        newTab.setOnClickListener(v -> openNewTabAndSearch());
        bar.addView(newTab, square(32));

        backButton = toolbarButton(R.drawable.ic_back, "Atrás");
        backButton.setOnClickListener(v -> {
            BrowserTab tab = browser.getActiveTab();
            if (tab != null && tab.session != null && tab.canGoBack) tab.session.goBack();
        });
        bar.addView(backButton, square(32));

        forwardButton = toolbarButton(R.drawable.ic_forward, "Adelante");
        forwardButton.setOnClickListener(v -> {
            BrowserTab tab = browser.getActiveTab();
            if (tab != null && tab.session != null && tab.canGoForward) tab.session.goForward();
        });
        bar.addView(forwardButton, square(32));

        FrameLayout addressShell = new FrameLayout(this);
        addressShell.setBackgroundResource(R.drawable.bg_address_soft);
        addressShell.setClipToOutline(true);

        addressGlow = new View(this);
        addressGlow.setBackgroundResource(R.drawable.bg_address_glow);
        addressGlow.setAlpha(0f);
        addressGlow.setVisibility(View.INVISIBLE);
        FrameLayout.LayoutParams glowParams = new FrameLayout.LayoutParams(
                dp(170), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        addressShell.addView(addressGlow, glowParams);

        addressDisplay = text("Nueva pestaña", 12, R.color.zen_muted);
        addressDisplay.setGravity(Gravity.CENTER_VERTICAL);
        addressDisplay.setSingleLine(true);
        addressDisplay.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        addressDisplay.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_search, 0, 0, 0);
        addressDisplay.setCompoundDrawablePadding(dp(7));
        addressDisplay.setPadding(dp(13), 0, dp(13), 0);
        addressDisplay.setBackgroundColor(Color.TRANSPARENT);
        addressDisplay.setOnClickListener(v -> showSearchPopup(false));
        addressShell.addView(addressDisplay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams addressParams =
                new LinearLayout.LayoutParams(0, dp(38), 1f);
        addressParams.setMargins(dp(5), 0, dp(5), 0);
        bar.addView(addressShell, addressParams);

        reloadButton = toolbarButton(R.drawable.ic_reload, "Recargar o detener");
        reloadButton.setOnClickListener(v -> {
            BrowserTab tab = browser.getActiveTab();
            if (tab != null && tab.session != null) {
                if (tab.loading) tab.session.stop(); else tab.session.reload();
            }
        });
        bar.addView(reloadButton, square(32));

        ImageButton search = toolbarButton(R.drawable.ic_search, "Desplegar búsqueda");
        search.setOnClickListener(v -> showSearchPopup(false));
        bar.addView(search, square(34));
        return bar;
    }

    private View createNewTabSurface() {
        FrameLayout surface = new FrameLayout(this);
        surface.setBackgroundColor(getColor(R.color.zen_bg));

        homeBackgroundView = new ImageView(this);
        homeBackgroundView.setImageResource(R.drawable.zen_home_bonsai);
        homeBackgroundView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        homeBackgroundView.setBackgroundColor(getColor(R.color.zen_bg));
        surface.addView(homeBackgroundView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        homeBackgroundScrim = new View(this);
        homeBackgroundScrim.setBackgroundResource(R.drawable.bg_home_overlay);
        surface.addView(homeBackgroundScrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(18), dp(24), dp(18), dp(22));
        scroll.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        homeKeyView = createZenKey();
        page.addView(homeKeyView, new LinearLayout.LayoutParams(dp(72), dp(78)));

        TextView title = text("Zen Browser", 21, R.color.zen_text);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, dp(13), 0, dp(3));
        page.addView(title, titleParams);

        TextView subtitle = text("Un espacio tranquilo para navegar", 13, R.color.zen_muted);
        subtitle.setGravity(Gravity.CENTER);
        page.addView(subtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView search = text("Buscar o escribir una dirección", 14, R.color.zen_text);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
        search.setCompoundDrawablePadding(dp(9));
        search.setPadding(dp(16), 0, dp(16), 0);
        search.setBackgroundResource(R.drawable.bg_search_large);
        search.setOnClickListener(v -> showSearchPopup(true));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        searchParams.setMargins(0, dp(20), 0, dp(16));
        page.addView(search, searchParams);

        TextView quickLabel = label("ACCESOS RÁPIDOS");
        quickLabel.setGravity(Gravity.CENTER);
        page.addView(quickLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout quickRow = new LinearLayout(this);
        quickRow.setGravity(Gravity.CENTER);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        quickRow.addView(quickSite(
                "GitHub", "https://github.com", R.drawable.ic_site_github));
        quickRow.addView(quickSite(
                "YouTube", "https://youtube.com", R.drawable.ic_site_youtube));
        quickRow.addView(quickSite(
                "Wikipedia", "https://wikipedia.org", R.drawable.ic_site_wikipedia));
        page.addView(quickRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        surface.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        surface.post(() -> {
            applyHomePreferences();
            runHomeWakeAnimation();
        });
        return surface;
    }

    private View quickSite(String title, String url, int iconRes) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackgroundResource(R.drawable.bg_quick_site);
        card.setPadding(dp(5), dp(6), dp(5), dp(5));
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
        card.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(30)));

        TextView label = text(title, 10, R.color.zen_muted);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(0, dp(5), 0, 0);
        card.addView(label, labelParams);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(dp(72), dp(64));
        cardParams.setMargins(dp(3), 0, dp(3), 0);
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
        panel.setPadding(dp(10), dp(8), dp(10), dp(8));
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
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

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
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        panel.addView(createSidebarShortcutGrid());

        LinearLayout workspace = new LinearLayout(this);
        workspace.setGravity(Gravity.CENTER_VERTICAL);
        workspace.setPadding(dp(10), 0, dp(8), 0);
        workspace.setBackgroundResource(R.drawable.bg_profile_selector);
        ImageView workspaceIcon = new ImageView(this);
        workspaceIcon.setImageResource(workspaceIconRes(browser.getActiveWorkspaceId()));
        workspaceIcon.setImageTintList(ColorStateList.valueOf(getColor(R.color.zen_text)));
        workspace.addView(workspaceIcon, new LinearLayout.LayoutParams(dp(24), dp(24)));

        TextView workspaceName = text(
                browser.getActiveWorkspaceName(), 14, R.color.zen_text);
        workspaceName.setGravity(Gravity.CENTER);
        workspaceName.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams workspaceNameParams =
                new LinearLayout.LayoutParams(0, dp(42), 1f);
        workspaceNameParams.setMargins(dp(6), 0, dp(6), 0);
        workspace.addView(workspaceName, workspaceNameParams);

        ImageView workspaceArrow = new ImageView(this);
        workspaceArrow.setImageResource(R.drawable.ic_chevron_down);
        workspaceArrow.setImageTintList(ColorStateList.valueOf(getColor(R.color.zen_muted)));
        workspace.addView(workspaceArrow, new LinearLayout.LayoutParams(dp(24), dp(24)));

        workspace.setOnClickListener(v -> showWorkspaceMenu(workspace));
        LinearLayout.LayoutParams workspaceParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        workspaceParams.setMargins(0, dp(5), 0, dp(3));
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
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        newTabParams.setMargins(0, dp(2), 0, dp(3));
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
                new LinearLayout.LayoutParams(0, dp(46), 1f));
        utilities.addView(sidebarBottomAction(
                R.drawable.ic_downloads,
                "Descargas",
                () -> ZenPanelController.showDownloads(
                        this, browser, this::showSidebarPopup)),
                new LinearLayout.LayoutParams(0, dp(46), 1f));
        utilities.addView(sidebarBottomAction(
                R.drawable.ic_history,
                "Historial",
                () -> ZenPanelController.showHistory(
                        this, browser, this::showSidebarPopup)),
                new LinearLayout.LayoutParams(0, dp(46), 1f));
        utilities.addView(sidebarBottomAction(
                R.drawable.ic_profile,
                "Perfil",
                () -> ZenPanelController.showProfiles(
                        this, this::showSidebarPopup)),
                new LinearLayout.LayoutParams(0, dp(46), 1f));
        panel.addView(utilities, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        return panel;
    }

    private View createSidebarShortcutGrid() {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, dp(4), 0, dp(2));

        List<QuickAccessStore.Item> items =
                QuickAccessStore.list(this, browser.getActiveWorkspaceId());
        int slots = Math.min(QuickAccessStore.MAX_ITEMS, items.size() + 1);

        for (int start = 0; start < slots; start += 4) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER);
            for (int column = 0; column < 4; column++) {
                int index = start + column;
                View card;
                if (index < items.size()) {
                    card = sidebarShortcut(items.get(index));
                } else if (index == items.size()
                        && items.size() < QuickAccessStore.MAX_ITEMS) {
                    card = addQuickAccessCard();
                } else {
                    card = new View(this);
                }
                LinearLayout.LayoutParams params =
                        new LinearLayout.LayoutParams(0, dp(58), 1f);
                params.setMargins(dp(2), dp(1), dp(2), dp(1));
                row.addView(card, params);
            }
            grid.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));
        }
        return grid;
    }

    private View addQuickAccessCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackgroundResource(R.drawable.bg_sidebar_shortcut);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_add);
        icon.setImageTintList(ColorStateList.valueOf(getColor(R.color.zen_muted)));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        card.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));

        TextView label = text("Añadir", 8, R.color.zen_muted);
        label.setGravity(Gravity.CENTER);
        card.addView(label);
        card.setOnClickListener(v -> showQuickAccessEditor(null));
        return card;
    }

    private View sidebarShortcut(QuickAccessStore.Item item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(3), dp(3), dp(3), dp(2));
        card.setBackgroundResource(R.drawable.bg_sidebar_shortcut);
        card.setContentDescription(item.name);

        FrameLayout iconHost = new FrameLayout(this);
        TextView fallback = text(quickAccessLetter(item), 12, R.color.zen_text);
        fallback.setTypeface(Typeface.DEFAULT_BOLD);
        fallback.setGravity(Gravity.CENTER);
        fallback.setBackgroundResource(R.drawable.bg_favicon);
        iconHost.addView(fallback, new FrameLayout.LayoutParams(
                dp(24), dp(24), Gravity.CENTER));

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int local = quickAccessIconResource(item.url);
        if (local != 0) {
            icon.setImageResource(local);
        } else {
            String iconUrl = item.iconUrl == null || item.iconUrl.trim().isEmpty()
                    ? QuickAccessStore.automaticIconUrl(item.url)
                    : item.iconUrl;
            RemoteAssetLoader.loadInto(this, iconUrl, 64, icon, null);
        }
        iconHost.addView(icon, new FrameLayout.LayoutParams(
                dp(24), dp(24), Gravity.CENTER));
        card.addView(iconHost, new LinearLayout.LayoutParams(dp(26), dp(26)));

        TextView label = text(item.name, 8, R.color.zen_muted);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(0, dp(2), 0, 0);
        card.addView(label, labelParams);

        card.setOnClickListener(v -> {
            dismissSidebarPopupImmediate();
            if (ZenPanelController.quickAccessOpensNewTab(this)) {
                browser.addTab(item.url, true);
            } else {
                browser.loadInActiveTab(item.url);
            }
        });

        final float[] down = new float[3];
        final boolean[] dragging = {false};
        card.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = event.getX();
                    down[1] = event.getY();
                    down[2] = android.os.SystemClock.uptimeMillis();
                    dragging[0] = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - down[0];
                    float dy = event.getY() - down[1];
                    long elapsed = android.os.SystemClock.uptimeMillis() - (long) down[2];
                    if (!dragging[0] && elapsed > 330L
                            && Math.hypot(dx, dy) > dp(8)) {
                        dragging[0] = true;
                        ClipData data = ClipData.newPlainText("quick-access", item.id);
                        view.startDragAndDrop(
                                data,
                                new View.DragShadowBuilder(view),
                                item.id,
                                0);
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        return true;
                    }
                    return dragging[0];
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    return dragging[0];
                default:
                    return false;
            }
        });

        card.setOnLongClickListener(v -> {
            showQuickAccessEditor(item);
            return true;
        });

        card.setOnDragListener((target, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return event.getLocalState() instanceof String;
                case DragEvent.ACTION_DRAG_ENTERED:
                    target.animate().scaleX(1.04f).scaleY(1.04f).setDuration(80L).start();
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    target.animate().scaleX(1f).scaleY(1f).setDuration(80L).start();
                    return true;
                case DragEvent.ACTION_DROP:
                    target.animate().scaleX(1f).scaleY(1f).setDuration(80L).start();
                    QuickAccessStore.moveBefore(
                            MainActivity.this,
                            browser.getActiveWorkspaceId(),
                            String.valueOf(event.getLocalState()),
                            item.id);
                    refreshSidebarPopup();
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    target.animate().scaleX(1f).scaleY(1f).setDuration(80L).start();
                    return true;
                default:
                    return true;
            }
        });
        return card;
    }

    private void showQuickAccessEditor(QuickAccessStore.Item existing) {
        String workspaceId = browser.getActiveWorkspaceId();
        QuickAccessStore.Item draft = existing == null
                ? QuickAccessStore.create(workspaceId, "", "https://", "")
                : existing.copy();

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(13), dp(16), dp(6));
        form.setBackgroundResource(R.drawable.bg_quick_editor);

        LinearLayout previewRow = new LinearLayout(this);
        previewRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        preview.setBackgroundResource(R.drawable.bg_favicon);
        previewRow.addView(preview, new LinearLayout.LayoutParams(dp(52), dp(52)));

        TextView previewText = text(
                existing == null ? "Nuevo acceso" : existing.name,
                14,
                R.color.zen_text);
        previewText.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams previewTextParams =
                new LinearLayout.LayoutParams(0, dp(52), 1f);
        previewTextParams.setMargins(dp(12), 0, 0, 0);
        previewRow.addView(previewText, previewTextParams);
        form.addView(previewRow);

        EditText name = quickAccessInput("Nombre", existing == null ? "" : existing.name);
        EditText url = quickAccessInput(
                "https://sitio.com",
                existing == null ? "" : existing.url);
        EditText iconUrl = quickAccessInput(
                "Icono automático o URL de imagen",
                existing == null ? "" : existing.iconUrl);
        form.addView(name, quickEditorParams());
        form.addView(url, quickEditorParams());
        form.addView(iconUrl, quickEditorParams());

        TextView detect = text("DETECTAR ICONO AUTOMÁTICAMENTE", 10, R.color.zen_accent);
        detect.setTypeface(Typeface.DEFAULT_BOLD);
        detect.setGravity(Gravity.CENTER);
        detect.setBackgroundResource(R.drawable.bg_context_action);
        detect.setOnClickListener(v -> {
            String detected = QuickAccessStore.automaticIconUrl(url.getText().toString());
            iconUrl.setText(detected);
            loadQuickAccessPreview(preview, detected);
        });
        LinearLayout.LayoutParams detectParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        detectParams.setMargins(0, dp(4), 0, 0);
        form.addView(detect, detectParams);

        String initialIcon = existing == null || existing.iconUrl == null
                || existing.iconUrl.trim().isEmpty()
                ? QuickAccessStore.automaticIconUrl(existing == null ? "" : existing.url)
                : existing.iconUrl;
        loadQuickAccessPreview(preview, initialIcon);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Añadir acceso" : "Editar acceso")
                .setView(form)
                .setNegativeButton("Cancelar", null)
                .setNeutralButton(existing == null ? "" : "Eliminar", null)
                .setPositiveButton("Guardar", null)
                .create();

        dialog.setOnShowListener(ignored -> {
            TextView save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setTextColor(getColor(R.color.zen_accent));
            save.setOnClickListener(v -> {
                String cleanName = name.getText().toString().trim();
                String cleanUrl = url.getText().toString().trim();
                if (cleanUrl.isEmpty() || "https://".equals(cleanUrl)) {
                    url.setError("Escribe una dirección");
                    return;
                }
                draft.workspaceId = workspaceId;
                draft.name = cleanName;
                draft.url = QuickAccessStore.normalizeUrl(cleanUrl);
                draft.iconUrl = iconUrl.getText().toString().trim();
                if (draft.iconUrl.isEmpty()) {
                    draft.iconUrl = QuickAccessStore.automaticIconUrl(draft.url);
                }
                if (!QuickAccessStore.save(this, draft)) {
                    Toast.makeText(
                            this,
                            "El espacio ya tiene el máximo de accesos",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                dialog.dismiss();
                refreshSidebarPopup();
            });

            if (existing != null) {
                TextView remove = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                remove.setTextColor(getColor(R.color.zen_danger));
                remove.setOnClickListener(v -> new AlertDialog.Builder(this)
                        .setTitle("Eliminar acceso")
                        .setMessage("¿Quitar " + existing.name + "?")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Eliminar", (confirm, which) -> {
                            QuickAccessStore.remove(this, workspaceId, existing.id);
                            dialog.dismiss();
                            refreshSidebarPopup();
                        })
                        .show());
            }
        });
        dialog.show();
    }

    private EditText quickAccessInput(String hint, String value) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setText(value == null ? "" : value);
        input.setTextColor(getColor(R.color.zen_text));
        input.setHintTextColor(getColor(R.color.zen_muted));
        input.setTextSize(13);
        input.setBackgroundResource(R.drawable.bg_address_rounder);
        input.setPadding(dp(13), 0, dp(13), 0);
        return input;
    }

    private LinearLayout.LayoutParams quickEditorParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        params.setMargins(0, dp(7), 0, 0);
        return params;
    }

    private void loadQuickAccessPreview(ImageView preview, String iconUrl) {
        preview.setImageDrawable(null);
        if (iconUrl == null || iconUrl.trim().isEmpty()) {
            preview.setImageResource(R.drawable.ic_open_new);
            preview.setImageTintList(ColorStateList.valueOf(getColor(R.color.zen_muted)));
            return;
        }
        preview.setImageTintList(null);
        RemoteAssetLoader.loadInto(this, iconUrl, 96, preview,
                new RemoteAssetLoader.Callback() {
                    @Override public void onLoaded(Bitmap bitmap) { }
                    @Override public void onError(Throwable error) {
                        preview.setImageResource(R.drawable.ic_open_new);
                        preview.setImageTintList(
                                ColorStateList.valueOf(getColor(R.color.zen_muted)));
                    }
                });
    }

    private int quickAccessIconResource(String url) {
        String host = QuickAccessStore.host(url);
        if (host.contains("youtube")) return R.drawable.ic_shortcut_youtube;
        if (host.contains("discord")) return R.drawable.ic_shortcut_discord;
        if (host.equals("x.com") || host.contains("twitter")) return R.drawable.ic_shortcut_x;
        if (host.contains("wikipedia")) return R.drawable.ic_shortcut_wikipedia;
        if (host.contains("reddit")) return R.drawable.ic_shortcut_reddit;
        if (host.contains("github")) return R.drawable.ic_shortcut_github;
        if (host.contains("perplexity")) return R.drawable.ic_shortcut_perplexity;
        return 0;
    }

    private String quickAccessLetter(QuickAccessStore.Item item) {
        String name = item == null ? "" : item.name;
        if (name == null || name.trim().isEmpty()) return "•";
        return name.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private View sidebarBottomAction(
            int iconRes,
            String description,
            Runnable action) {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.VERTICAL);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(3), dp(4), dp(3), dp(2));
        button.setBackgroundResource(R.drawable.bg_button);
        button.setContentDescription(description);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(getColor(R.color.zen_text)));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));

        TextView title = text(description, 9, R.color.zen_muted);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        button.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        button.setOnClickListener(v -> {
            dismissSidebarPopupImmediate();
            if (action != null) action.run();
        });
        return button;
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
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), 0, dp(10), 0);
            row.setBackgroundResource(
                    selected ? R.drawable.bg_tab_active : R.drawable.bg_panel_card);

            ImageView icon = new ImageView(this);
            icon.setImageResource(workspaceIconRes(item.id));
            icon.setImageTintList(ColorStateList.valueOf(getColor(
                    selected ? R.color.zen_text : R.color.zen_muted)));
            row.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));

            TextView name = text(item.name, 14,
                    selected ? R.color.zen_text : R.color.zen_muted);
            name.setGravity(Gravity.CENTER);
            if (selected) name.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams nameParams =
                    new LinearLayout.LayoutParams(0, dp(44), 1f);
            nameParams.setMargins(dp(8), 0, dp(8), 0);
            row.addView(name, nameParams);

            TextView check = text(selected ? "✓" : "", 15, R.color.zen_accent);
            check.setGravity(Gravity.CENTER);
            row.addView(check, new LinearLayout.LayoutParams(dp(24), dp(24)));

            row.setOnClickListener(v -> {
                if (workspacePopup != null) workspacePopup.dismiss();
                switchWorkspaceWithSlide(item.id);
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

    private int workspaceIconRes(String workspaceId) {
        if ("work".equals(workspaceId)) return R.drawable.ic_workspace_work;
        if ("education".equals(workspaceId)) return R.drawable.ic_workspace_study;
        return R.drawable.ic_workspace_personal;
    }

    private View tabRow(BrowserTab tab) {
        boolean active = browser.getActiveTab() != null
                && browser.getActiveTab().id.equals(tab.id);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(7), dp(2), dp(3), dp(2));
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

        TextView favicon = text(faviconLetter(tab), 11, R.color.zen_text);
        favicon.setTypeface(Typeface.DEFAULT_BOLD);
        favicon.setGravity(Gravity.CENTER);
        favicon.setBackgroundResource(
                tab.loading ? R.drawable.bg_favicon_loading : R.drawable.bg_favicon);
        row.addView(favicon, square(28));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text(displayTitle(tab), 12, R.color.zen_text);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(title);

        String domain = displayHost(tab.url);
        if (!domain.isEmpty() && !tab.showStartPage) {
            TextView subtitle = text(domain, 9, R.color.zen_muted);
            subtitle.setSingleLine(true);
            subtitle.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            labels.addView(subtitle);
        }

        LinearLayout.LayoutParams labelsParams =
                new LinearLayout.LayoutParams(0, dp(36), 1f);
        labelsParams.setMargins(dp(8), 0, dp(3), 0);
        row.addView(labels, labelsParams);

        if (ZenPanelController.showTabCloseButtons(this)) {
            ImageButton close = iconButton(R.drawable.ic_close, "Cerrar pestaña");
            close.setBackgroundResource(R.drawable.bg_close_button);
            close.setPadding(dp(8), dp(8), dp(8), dp(8));
            close.setOnClickListener(v -> {
                v.setEnabled(false);
                animateTabRowClose(row, tab.id, 1);
            });
            row.addView(close, square(32));
        }

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        rowParams.setMargins(0, 0, 0, dp(2));
        row.setLayoutParams(rowParams);

        if (active && ZenPanelController.animationsEnabled(this)) {
            row.setAlpha(.72f);
            row.setTranslationX(dp(8));
            row.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(150L)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
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
        dismissContextMenuImmediate();
        if (geckoView == null || webHost == null || tabTransitionRunning
                || !ZenPanelController.animationsEnabled(this)) {
            change.run();
            return;
        }

        tabTransitionRunning = true;
        final int generation = ++transitionGeneration;
        mainHandler.postDelayed(() -> {
            if (generation == transitionGeneration && tabTransitionRunning) {
                clearTransitionSnapshot();
                tabTransitionRunning = false;
            }
        }, 1200L);

        try {
            geckoView.capturePixels()
                    .withHandler(mainHandler)
                    .accept(bitmap -> {
                        if (generation != transitionGeneration) {
                            tabTransitionRunning = false;
                            return;
                        }
                        showTransitionSnapshot(bitmap);
                        change.run();
                    }, error -> runFallbackTransition(change, generation));
        } catch (RuntimeException error) {
            runFallbackTransition(change, generation);
        }
    }

    private void showTransitionSnapshot(Bitmap bitmap) {
        clearTransitionSnapshot();
        if (bitmap == null || bitmap.isRecycled() || webHost == null) return;
        transitionSnapshot = new ImageView(this);
        transitionSnapshot.setScaleType(ImageView.ScaleType.FIT_XY);
        transitionSnapshot.setImageBitmap(bitmap);
        transitionSnapshot.setBackgroundColor(getColor(R.color.zen_bg));
        transitionSnapshot.setClickable(false);
        webHost.addView(transitionSnapshot, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void fadeTransitionSnapshot(int generation) {
        if (generation != transitionGeneration) return;
        if (transitionSnapshot == null) {
            tabTransitionRunning = false;
            return;
        }
        transitionSnapshot.animate().cancel();
        transitionSnapshot.animate()
                .alpha(0f)
                .setDuration(155L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(() -> {
                    clearTransitionSnapshot();
                    tabTransitionRunning = false;
                })
                .start();
    }

    private void clearTransitionSnapshot() {
        if (transitionSnapshot == null) return;
        transitionSnapshot.animate().cancel();
        ViewParent parent = transitionSnapshot.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(transitionSnapshot);
        }
        transitionSnapshot = null;
    }

    private void runFallbackTransition(Runnable change, int generation) {
        if (transitionScrim == null) {
            change.run();
            tabTransitionRunning = false;
            return;
        }
        transitionScrim.animate().cancel();
        transitionScrim.setVisibility(View.VISIBLE);
        transitionScrim.setAlpha(0f);
        transitionScrim.animate()
                .alpha(.28f)
                .setDuration(70L)
                .withEndAction(() -> {
                    change.run();
                    transitionScrim.animate()
                            .alpha(0f)
                            .setDuration(130L)
                            .withEndAction(() -> {
                                transitionScrim.setVisibility(View.GONE);
                                if (generation == transitionGeneration) {
                                    tabTransitionRunning = false;
                                }
                            })
                            .start();
                })
                .start();
    }

    private void switchWorkspaceWithSlide(String workspaceId) {
        if (workspaceId == null || workspaceId.equals(browser.getActiveWorkspaceId())) return;
        int from = workspacePosition(browser.getActiveWorkspaceId());
        int to = workspacePosition(workspaceId);
        int direction = to >= from ? 1 : -1;

        View current = sidebarPanelHost == null || sidebarPanelHost.getChildCount() == 0
                ? null : sidebarPanelHost.getChildAt(0);
        if (current == null || !ZenPanelController.animationsEnabled(this)) {
            animateWebTransition(() -> {
                browser.switchWorkspace(workspaceId);
                refreshSidebarPopup();
            });
            return;
        }

        current.animate().cancel();
        current.animate()
                .translationX(-direction * dp(30))
                .alpha(0f)
                .setDuration(85L)
                .withEndAction(() -> animateWebTransition(() -> {
                    browser.switchWorkspace(workspaceId);
                    refreshSidebarPopup();
                    View next = sidebarPanelHost == null || sidebarPanelHost.getChildCount() == 0
                            ? null : sidebarPanelHost.getChildAt(0);
                    if (next != null) {
                        next.setTranslationX(direction * dp(30));
                        next.setAlpha(0f);
                        next.animate()
                                .translationX(0f)
                                .alpha(1f)
                                .setDuration(170L)
                                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                                .start();
                    }
                }))
                .start();
    }

    private int workspacePosition(String workspaceId) {
        List<Workspace> workspaces = browser.getWorkspaces();
        for (int index = 0; index < workspaces.size(); index++) {
            if (workspaces.get(index).id.equals(workspaceId)) return index;
        }
        return 0;
    }

    private TextView createZenKey() {
        TextView key = text("Z", 36, R.color.zen_bg);
        key.setTypeface(Typeface.DEFAULT_BOLD);
        key.setGravity(Gravity.CENTER);
        key.setBackgroundResource(R.drawable.bg_zen_key);
        key.setElevation(dp(11));
        key.setClickable(true);
        key.setFocusable(true);
        key.setSoundEffectsEnabled(false);
        key.setContentDescription("Tecla Z de Zen Browser");

        key.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    pressMechanicalKey(view, true);
                    return true;
                case MotionEvent.ACTION_UP:
                    releaseMechanicalKey(view);
                    view.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    releaseMechanicalKey(view);
                    return true;
                default:
                    return true;
            }
        });
        key.setOnClickListener(v -> { });
        return key;
    }

    private void initializeMechanicalKeySound() {
        try {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            keySoundPool = new SoundPool.Builder()
                    .setMaxStreams(2)
                    .setAudioAttributes(attributes)
                    .build();
            keySoundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
                if (sampleId == mechanicalKeySoundId) {
                    mechanicalKeySoundReady = status == 0;
                }
            });
            mechanicalKeySoundId =
                    keySoundPool.load(this, R.raw.mechanical_key, 1);
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to initialize mechanical key sound", error);
            releaseMechanicalKeySound();
        }
    }

    private void releaseMechanicalKeySound() {
        mechanicalKeySoundReady = false;
        mechanicalKeySoundId = 0;
        if (keySoundPool != null) {
            try {
                keySoundPool.release();
            } catch (RuntimeException ignored) { }
            keySoundPool = null;
        }
    }

    private void playMechanicalKeyFeedback(View key) {
        if (key == null) return;

        if (ZenPanelController.keyHapticsEnabled(this)) {
            key.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }

        if (!ZenPanelController.keySoundEnabled(this)
                || !mechanicalKeySoundReady
                || keySoundPool == null
                || mechanicalKeySoundId == 0) {
            return;
        }

        AudioManager audioManager =
                (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null
                && audioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
            return;
        }

        try {
            keySoundPool.play(
                    mechanicalKeySoundId,
                    0.82f,
                    0.82f,
                    1,
                    0,
                    1.0f);
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to play mechanical key sound", error);
        }
    }

    private void pressMechanicalKey(View key, boolean feedback) {
        if (key == null) return;
        key.setPressed(true);
        key.animate().cancel();
        key.animate()
                .translationY(dp(5))
                .scaleX(.965f)
                .scaleY(.935f)
                .setDuration(58L)
                .start();
        if (feedback) playMechanicalKeyFeedback(key);
    }

    private void releaseMechanicalKey(View key) {
        if (key == null) return;
        key.setPressed(false);
        key.animate().cancel();
        key.animate()
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(105L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    private void runHomeWakeAnimation() {
        if (homeWakePlayed
                || homeKeyView == null
                || !ZenPanelController.animationsEnabled(this)) {
            return;
        }
        homeWakePlayed = true;
        homeKeyView.postDelayed(() -> {
            pressMechanicalKey(homeKeyView, false);
            mainHandler.postDelayed(() -> releaseMechanicalKey(homeKeyView), 90L);
        }, 480L);
    }

    private void applyHomePreferences() {
        if (homeBackgroundView == null) return;

        boolean showBackground =
                ZenPanelController.homeBackgroundEnabled(this);
        homeBackgroundView.setVisibility(
                showBackground ? View.VISIBLE : View.GONE);
        if (homeBackgroundScrim != null) {
            homeBackgroundScrim.setVisibility(
                    showBackground ? View.VISIBLE : View.GONE);
        }

        homeBackgroundView.animate().cancel();
        // Reload after a configuration change so Android resolves the landscape resource.
        homeBackgroundView.setImageResource(R.drawable.zen_home_bonsai);
        homeBackgroundView.setScaleType(ImageView.ScaleType.CENTER_CROP);

        if (!showBackground) {
            homeBackgroundView.setAlpha(0f);
            homeBackgroundView.setScaleX(1f);
            homeBackgroundView.setScaleY(1f);
            return;
        }

        boolean motion = ZenPanelController.homeMotionEnabled(this)
                && ZenPanelController.animationsEnabled(this);
        if (motion) {
            homeBackgroundView.setAlpha(0f);
            homeBackgroundView.setScaleX(1.015f);
            homeBackgroundView.setScaleY(1.015f);
            homeBackgroundView.animate()
                    .alpha(1f)
                    .scaleX(1.055f)
                    .scaleY(1.055f)
                    .setDuration(24000L)
                    .setInterpolator(new android.view.animation.LinearInterpolator())
                    .start();
        } else {
            homeBackgroundView.setAlpha(1f);
            homeBackgroundView.setScaleX(1f);
            homeBackgroundView.setScaleY(1f);
        }
    }

    void applySettingsNow() {
        applyRuntimePreferences();
        applyHomePreferences();
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
        DownloadStore.Record record = DownloadStore.enqueue(this, response);
        if (record != null && ZenPanelController.downloadNotificationsEnabled(this)) {
            showDownloadProgress(record.id);
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

    @Override public void onContextMenu(
            GeckoSession session,
            int screenX,
            int screenY,
            GeckoSession.ContentDelegate.ContextElement element) {
        runOnUiThread(() -> {
            BrowserTab active = browser == null ? null : browser.getActiveTab();
            if (active == null || active.session != session || element == null) return;
            showWebContextMenu(screenX, screenY, element);
        });
    }

    @Override public void onPageIcon(GeckoSession session, String iconUrl) {
        runOnUiThread(() -> {
            BrowserTab active = browser == null ? null : browser.getActiveTab();
            if (active == null || active.session != session || iconUrl == null) return;
            if (QuickAccessStore.updateIconForUrl(
                    this,
                    active.workspaceId,
                    active.url,
                    iconUrl)) {
                refreshSidebarPopup();
            }
        });
    }

    private void showWebContextMenu(
            int screenX,
            int screenY,
            GeckoSession.ContentDelegate.ContextElement element) {
        dismissContextMenuImmediate();
        String source = safe(element.srcUri);
        String link = safe(element.linkUri);
        String base = safe(element.baseUri);
        boolean image = element.type
                == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE;
        boolean video = element.type
                == GeckoSession.ContentDelegate.ContextElement.TYPE_VIDEO;
        boolean audio = element.type
                == GeckoSession.ContentDelegate.ContextElement.TYPE_AUDIO;
        boolean media = image || video || audio;
        if (!media && link.isEmpty()) return;

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(10), dp(10), dp(11));
        panel.setBackgroundResource(R.drawable.bg_context_sheet);
        panel.setElevation(dp(24));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(2), dp(4), dp(7));

        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackgroundResource(R.drawable.bg_context_preview);
        if (image && !source.isEmpty()) {
            RemoteAssetLoader.loadInto(this, source, 160, preview, null);
        } else {
            preview.setImageResource(video ? R.drawable.ic_preview
                    : audio ? R.drawable.ic_downloads : R.drawable.ic_open_new);
            preview.setImageTintList(ColorStateList.valueOf(getColor(R.color.zen_accent)));
        }
        header.addView(preview, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        String heading = firstNonEmpty(
                element.altText,
                element.title,
                element.linkText,
                image ? "Imagen" : video ? "Video" : audio ? "Audio" : "Enlace");
        TextView title = text(heading, 14, R.color.zen_text);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        TextView domain = text(
                displayHost(!source.isEmpty() ? source : link),
                10,
                R.color.zen_muted);
        domain.setSingleLine(true);
        labels.addView(title);
        labels.addView(domain);
        LinearLayout.LayoutParams labelParams =
                new LinearLayout.LayoutParams(0, dp(58), 1f);
        labelParams.setMargins(dp(11), 0, 0, 0);
        header.addView(labels, labelParams);
        panel.addView(header);

        if (image && !source.isEmpty()) {
            panel.addView(contextAction(
                    R.drawable.ic_open_new,
                    "Abrir imagen en pestaña nueva",
                    () -> {
                        dismissContextMenuImmediate();
                        browser.addTab(source, true);
                    }));
            panel.addView(contextAction(
                    R.drawable.ic_preview,
                    "Vista previa",
                    () -> showMediaPreview(source, heading)));
            panel.addView(contextAction(
                    R.drawable.ic_downloads,
                    "Descargar imagen",
                    () -> enqueueContextDownload(
                            source, base, heading, "image/*")));
            panel.addView(contextAction(
                    R.drawable.ic_copy,
                    "Copiar dirección de imagen",
                    () -> copyText("Dirección de imagen", source)));
            panel.addView(contextAction(
                    R.drawable.ic_share,
                    "Compartir imagen",
                    () -> fetchContextMedia(source, base, false)));
            panel.addView(contextAction(
                    R.drawable.ic_copy,
                    "Copiar imagen",
                    () -> fetchContextMedia(source, base, true)));
        } else if (media && !source.isEmpty()) {
            panel.addView(contextAction(
                    R.drawable.ic_open_new,
                    video ? "Abrir video" : "Abrir audio",
                    () -> {
                        dismissContextMenuImmediate();
                        browser.addTab(source, true);
                    }));
            panel.addView(contextAction(
                    R.drawable.ic_downloads,
                    video ? "Descargar video" : "Descargar audio",
                    () -> enqueueContextDownload(
                            source, base, heading, video ? "video/*" : "audio/*")));
            panel.addView(contextAction(
                    R.drawable.ic_copy,
                    "Copiar dirección",
                    () -> copyText("Dirección multimedia", source)));
            panel.addView(contextAction(
                    R.drawable.ic_share,
                    "Compartir dirección",
                    () -> shareText(source)));
        }

        if (!link.isEmpty()) {
            panel.addView(contextAction(
                    R.drawable.ic_open_new,
                    "Abrir enlace",
                    () -> {
                        dismissContextMenuImmediate();
                        browser.loadInActiveTab(link);
                    }));
            panel.addView(contextAction(
                    R.drawable.ic_open_new,
                    "Abrir enlace en pestaña nueva",
                    () -> {
                        dismissContextMenuImmediate();
                        browser.addTab(link, true);
                    }));
            panel.addView(contextAction(
                    R.drawable.ic_copy,
                    "Copiar enlace",
                    () -> copyText("Enlace", link)));
        }

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        boolean landscape = screenWidth > screenHeight;
        int width = landscape
                ? Math.min(dp(380), screenWidth - dp(24))
                : Math.min(dp(470), screenWidth - dp(20));

        contextMenuPopup = new PopupWindow(
                panel,
                width,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        contextMenuPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        contextMenuPopup.setOutsideTouchable(true);
        contextMenuPopup.setClippingEnabled(true);
        contextMenuPopup.setElevation(dp(25));
        contextMenuPopup.setOnDismissListener(() -> contextMenuPopup = null);

        if (landscape) {
            int x = Math.max(dp(8), Math.min(screenX - width / 2, screenWidth - width - dp(8)));
            int estimatedHeight = Math.min(dp(540), screenHeight - dp(20));
            int y = Math.max(dp(8), Math.min(screenY - dp(80), screenHeight - estimatedHeight));
            contextMenuPopup.showAtLocation(appRoot, Gravity.TOP | Gravity.START, x, y);
            panel.setAlpha(0f);
            panel.setScaleX(.97f);
            panel.setScaleY(.97f);
            panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150L).start();
        } else {
            contextMenuPopup.showAtLocation(
                    appRoot,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL,
                    0,
                    safeInsetBottom + dp(8));
            panel.setTranslationY(dp(24));
            panel.setAlpha(0f);
            panel.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(185L)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
    }

    private View contextAction(int iconRes, String label, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(11), 0, dp(9), 0);
        row.setBackgroundResource(R.drawable.bg_context_action);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(getColor(R.color.zen_text)));
        row.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));

        TextView text = text(label, 13, R.color.zen_text);
        text.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(0, dp(44), 1f);
        textParams.setMargins(dp(11), 0, 0, 0);
        row.addView(text, textParams);
        row.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        params.setMargins(0, 0, 0, dp(4));
        row.setLayoutParams(params);
        return row;
    }

    private void showMediaPreview(String url, String title) {
        dismissContextMenuImmediate();
        FrameLayout container = new FrameLayout(this);
        container.setBackgroundColor(0xF5000000);
        container.setOnClickListener(v -> {
            if (contextPreviewPopup != null) contextPreviewPopup.dismiss();
        });

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription(title);
        container.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        RemoteAssetLoader.loadInto(this, url, 1600, image,
                new RemoteAssetLoader.Callback() {
                    @Override public void onLoaded(Bitmap bitmap) { }
                    @Override public void onError(Throwable error) {
                        Toast.makeText(
                                MainActivity.this,
                                "No se pudo cargar la vista previa",
                                Toast.LENGTH_SHORT).show();
                        if (contextPreviewPopup != null) contextPreviewPopup.dismiss();
                    }
                });

        contextPreviewPopup = new PopupWindow(
                container,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                true);
        contextPreviewPopup.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        contextPreviewPopup.setClippingEnabled(false);
        contextPreviewPopup.setOnDismissListener(() -> contextPreviewPopup = null);
        contextPreviewPopup.showAtLocation(appRoot, Gravity.CENTER, 0, 0);
        image.setAlpha(0f);
        image.animate().alpha(1f).setDuration(180L).start();
    }

    private void enqueueContextDownload(
            String url,
            String referrer,
            String name,
            String mime) {
        dismissContextMenuImmediate();
        DownloadStore.Record record = DownloadStore.enqueueUrl(
                this, url, referrer, name, mime);
        if (record == null) {
            Toast.makeText(this, "No se pudo iniciar la descarga", Toast.LENGTH_SHORT).show();
            return;
        }
        if (ZenPanelController.downloadNotificationsEnabled(this)) {
            showDownloadProgress(record.id);
        }
    }

    private void fetchContextMedia(String url, String referrer, boolean copy) {
        dismissContextMenuImmediate();
        Toast.makeText(
                this,
                copy ? "Preparando imagen para copiar…" : "Preparando imagen para compartir…",
                Toast.LENGTH_SHORT).show();
        ContextMediaStore.fetch(this, url, referrer, new ContextMediaStore.Callback() {
            @Override public void onReady(File file, String mime) {
                try {
                    if (copy) {
                        ContextMediaStore.copy(MainActivity.this, file, mime);
                        Toast.makeText(
                                MainActivity.this,
                                "Imagen copiada",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        ContextMediaStore.share(MainActivity.this, file, mime);
                    }
                } catch (RuntimeException error) {
                    Toast.makeText(
                            MainActivity.this,
                            "No se pudo completar la acción",
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override public void onError(Throwable error) {
                Toast.makeText(
                        MainActivity.this,
                        "No se pudo obtener la imagen",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void copyText(String label, String value) {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
        Toast.makeText(this, "Copiado", Toast.LENGTH_SHORT).show();
        dismissContextMenuImmediate();
    }

    private void shareText(String value) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, value);
        startActivity(Intent.createChooser(intent, "Compartir"));
        dismissContextMenuImmediate();
    }

    private void dismissContextMenuImmediate() {
        if (contextMenuPopup != null) contextMenuPopup.dismiss();
        contextMenuPopup = null;
        if (contextPreviewPopup != null) contextPreviewPopup.dismiss();
        contextPreviewPopup = null;
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private void showDownloadProgress(String recordId) {
        if (appRoot == null || recordId == null) return;

        View previous = appRoot.findViewWithTag("download-notice");
        if (previous != null) appRoot.removeView(previous);
        if (downloadNoticeTicker != null) {
            mainHandler.removeCallbacks(downloadNoticeTicker);
        }

        FrameLayout notice = new FrameLayout(this);
        notice.setTag("download-notice");
        notice.setBackgroundResource(R.drawable.bg_download_notice_edge);
        notice.setElevation(dp(20));
        notice.setClickable(true);

        LinearLayout body = new LinearLayout(this);
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.setPadding(dp(12), dp(9), dp(8), dp(11));

        FrameLayout iconShell = new FrameLayout(this);
        iconShell.setBackgroundResource(R.drawable.bg_download_icon_glow);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_downloads);
        icon.setImageTintList(ColorStateList.valueOf(getColor(R.color.zen_accent)));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iconShell.addView(icon, new FrameLayout.LayoutParams(
                dp(34), dp(34), Gravity.CENTER));
        body.addView(iconShell, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_VERTICAL);
        center.setPadding(dp(11), 0, dp(8), 0);

        TextView title = text("Descargando", 15, R.color.zen_text);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);

        TextView subtitle = text("Preparando archivo…", 11, R.color.zen_muted);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.MIDDLE);

        TextView details = text("Calculando velocidad", 10, R.color.zen_muted);
        details.setSingleLine(true);
        details.setEllipsize(TextUtils.TruncateAt.END);
        details.setPadding(0, dp(4), 0, 0);

        center.addView(title);
        center.addView(subtitle);
        center.addView(details);
        body.addView(center, new LinearLayout.LayoutParams(0, dp(70), 1f));

        TextView view = text("VER", 12, R.color.zen_accent);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(11), 0, dp(11), 0);
        view.setBackgroundResource(R.drawable.bg_download_action);
        view.setOnClickListener(v -> {
            if (notice.getParent() == appRoot) appRoot.removeView(notice);
            if (downloadNoticeTicker != null) {
                mainHandler.removeCallbacks(downloadNoticeTicker);
            }
            ZenPanelController.showDownloads(this, browser, this::showSidebarPopup);
        });
        body.addView(view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));

        notice.addView(body, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        ProgressBar edge = new ProgressBar(
                this, null, android.R.attr.progressBarStyleHorizontal);
        edge.setMax(100);
        edge.setProgressDrawable(getDrawable(R.drawable.progress_download_edge));
        edge.setProgressBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        FrameLayout.LayoutParams edgeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(4), Gravity.BOTTOM);
        edgeParams.leftMargin = dp(1);
        edgeParams.rightMargin = dp(1);
        edgeParams.bottomMargin = dp(1);
        notice.addView(edge, edgeParams);

        int displayWidth = getResources().getDisplayMetrics().widthPixels;
        int targetWidth = Math.min(dp(wideLayout ? 620 : 480), displayWidth - dp(24));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                targetWidth,
                dp(94),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = safeInsetBottom + dp(14);
        appRoot.addView(notice, params);

        notice.setAlpha(0f);
        notice.setScaleX(.985f);
        notice.setScaleY(.985f);
        notice.setTranslationY(dp(16));
        notice.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(190L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();

        final boolean[] finishing = {false};
        downloadNoticeTicker = new Runnable() {
            @Override public void run() {
                if (notice.getParent() != appRoot) return;

                DownloadStore.Record record =
                        DownloadStore.get(MainActivity.this, recordId);
                if (record == null) {
                    fadeDownloadNotice(notice, 260L);
                    return;
                }

                subtitle.setText(record.name == null || record.name.trim().isEmpty()
                        ? "Archivo" : record.name);

                int percent = record.total > 0L
                        ? (int) Math.min(100L, record.bytes * 100L / record.total)
                        : 0;
                boolean active = DownloadStore.DOWNLOADING.equals(record.status)
                        || DownloadStore.QUEUED.equals(record.status);

                edge.setIndeterminate(active && record.total <= 0L);
                if (!edge.isIndeterminate()) {
                    edge.setProgress(percent, true);
                }

                if (DownloadStore.COMPLETE.equals(record.status)) {
                    title.setText("Descarga completada");
                    details.setText(formatBytes(record.bytes) + "   ·   100%");
                    edge.setIndeterminate(false);
                    edge.setProgress(100, true);
                    if (!finishing[0]) {
                        finishing[0] = true;
                        mainHandler.postDelayed(
                                () -> fadeDownloadNotice(notice, 180L), 1900L);
                    }
                    return;
                }

                if (DownloadStore.FAILED.equals(record.status)
                        || DownloadStore.CANCELLED.equals(record.status)) {
                    title.setText(DownloadStore.CANCELLED.equals(record.status)
                            ? "Descarga cancelada" : "Error en la descarga");
                    details.setText(record.error == null || record.error.trim().isEmpty()
                            ? "No se pudo completar"
                            : record.error);
                    edge.setIndeterminate(false);
                    if (!finishing[0]) {
                        finishing[0] = true;
                        mainHandler.postDelayed(
                                () -> fadeDownloadNotice(notice, 180L), 2300L);
                    }
                    return;
                }

                title.setText(DownloadStore.QUEUED.equals(record.status)
                        ? "Descarga en espera" : "Descargando");
                String amount = record.total > 0L
                        ? formatBytes(record.bytes) + " / " + formatBytes(record.total)
                        : formatBytes(record.bytes);
                details.setText(formatRate(record.bytesPerSecond)
                        + "   ·   " + amount
                        + (record.total > 0L ? "   ·   " + percent + "%" : ""));

                mainHandler.postDelayed(this, 400L);
            }
        };
        mainHandler.post(downloadNoticeTicker);
    }

    private void fadeDownloadNotice(View notice, long duration) {
        if (notice == null || notice.getParent() != appRoot) return;
        notice.animate().alpha(0f).translationY(dp(14)).setDuration(duration)
                .withEndAction(() -> {
                    if (notice.getParent() == appRoot) appRoot.removeView(notice);
                }).start();
    }

    private String formatRate(long bytesPerSecond) {
        if (bytesPerSecond <= 0L) return "Calculando velocidad";
        return formatBytes(bytesPerSecond) + "/s";
    }

    private String formatBytes(long bytes) {
        if (bytes < 0L) return "—";
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
        progressBar.setProgress(tab.progress, true);
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
            if (tab.loading && contextMenuPopup != null) dismissContextMenuImmediate();
            boolean sessionChanged = displayedSession != tab.session;
            attachSession(tab.session);
            String paintCoverKey = tab.id + ":" + tab.navigationSerial;
            boolean newTab = tab.showStartPage;
            newTabSurface.setVisibility(newTab ? View.VISIBLE : View.GONE);
            if (newTab) {
                hidePaintGuard(tab.session, true);
                lastPaintCoverKey = "";
            } else if (!tab.hasValidPaint
                    && (sessionChanged || tab.loading || !paintCoverKey.equals(lastPaintCoverKey))) {
                showPaintGuard(tab.session, "Cargando página…");
                lastPaintCoverKey = paintCoverKey;
            } else if (tab.hasValidPaint && paintGuard != null
                    && paintGuard.getVisibility() == View.VISIBLE) {
                hidePaintGuard(tab.session, false);
            }
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

    private ImageButton toolbarButton(int drawableRes, String description) {
        ImageButton button = iconButton(drawableRes, description);
        button.setBackgroundResource(R.drawable.bg_toolbar_button);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        return button;
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
