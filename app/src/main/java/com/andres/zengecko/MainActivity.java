package com.andres.zengecko;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
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
import android.os.SystemClock;
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
    private static final long NEW_TAB_GUARD_MS = 650L;
    private static final long SURFACE_PROBE_DELAY_MS = 240L;
    private static final int MAX_SURFACE_RECOVERY_ATTEMPTS = 3;

    private enum RenderPhase {
        IDLE,
        NETWORK_STARTED,
        WAITING_FOR_COMPOSITE,
        FIRST_COMPOSITE,
        FIRST_CONTENTFUL_PAINT,
        VISIBLE
    }

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
    private View settingsSurface;
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
    private AlertDialog contextMenuDialog;
    private AlertDialog contextPreviewDialog;
    private int contextGeneration;
    private boolean quickEditorOpening;
    private RenderPhase renderPhase = RenderPhase.IDLE;
    private GeckoSession renderPhaseSession;
    private int surfaceProbeGeneration;
    private Runnable surfaceProbeRunnable;
    private int surfaceRecoveryAttempts;
    private long lastNewTabRequestAt;
    private boolean newTabRequestPending;
    private Bitmap transitionBitmap;
    private Runnable sidebarRefreshRunnable;
    private LinearLayout sidebarTabsHost;
    private TextView sidebarWorkspaceLabel;
    private final ImageButton[] sidebarWorkspaceButtons = new ImageButton[3];
    private boolean sidebarWorkspaceTransition;


    private static final class ContextTarget {
        final int type;
        final String source;
        final String link;
        final String base;
        final String alt;
        final String title;
        final String linkText;

        ContextTarget(
                int type,
                String source,
                String link,
                String base,
                String alt,
                String title,
                String linkText) {
            this.type = type;
            this.source = clean(source);
            this.link = clean(link);
            this.base = clean(base);
            this.alt = clean(alt);
            this.title = clean(title);
            this.linkText = clean(linkText);
        }

        static ContextTarget from(GeckoSession.ContentDelegate.ContextElement element) {
            if (element == null) return null;
            return new ContextTarget(
                    element.type,
                    element.srcUri,
                    element.linkUri,
                    element.baseUri,
                    element.altText,
                    element.title,
                    element.linkText);
        }

        private static String clean(String value) {
            return value == null ? "" : value.trim();
        }

        boolean isImage() {
            return type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE;
        }

        boolean isVideo() {
            return type == GeckoSession.ContentDelegate.ContextElement.TYPE_VIDEO;
        }

        boolean isAudio() {
            return type == GeckoSession.ContentDelegate.ContextElement.TYPE_AUDIO;
        }

        boolean isMedia() {
            return isImage() || isVideo() || isAudio();
        }
    }

    private boolean isActivityUsable() {
        if (isFinishing()) return false;
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1
                || !isDestroyed();
    }

    private boolean isRemoteHttpUrl(String value) {
        if (value == null) return false;
        String clean = value.trim().toLowerCase(Locale.ROOT);
        return clean.startsWith("https://") || clean.startsWith("http://");
    }

    @Override protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ZenTheme.wrap(newBase));
    }

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
        Log.i(TAG, "onStart");
        if (browser != null) browser.setAppForeground(true);
        recoverVisibleSurface("onStart", true);
    }

    @Override protected void onResume() {
        super.onResume();
        activityResumed = true;
        Log.i(TAG, "onResume phase=" + renderPhase);
        if (browser != null) browser.setAppForeground(true);
        recoverVisibleSurface("onResume", true);
        applyRuntimePreferences();
        render();
        ZenPanelController.maybeTrimCache(this);
        mainHandler.postDelayed(() -> applySystemBars(true), 60L);
    }

    @Override protected void onPause() {
        Log.i(TAG, "onPause phase=" + renderPhase);
        activityResumed = false;
        cancelSurfaceProbe();
        dismissContextMenuImmediate();
        if (browser != null) browser.setAppForeground(false);
        super.onPause();
    }

    @Override protected void onStop() {
        Log.i(TAG, "onStop");
        if (browser != null) browser.setAppForeground(false);
        super.onStop();
    }

    @Override protected void onDestroy() {
        Log.i(TAG, "onDestroy");
        activityResumed = false;
        if (browser != null) browser.removeObserver(this);
        unregisterModernBackCallback();
        cancelPaintGuardTimeout();
        cancelSurfaceProbe();
        if (sidebarRefreshRunnable != null) {
            mainHandler.removeCallbacks(sidebarRefreshRunnable);
            sidebarRefreshRunnable = null;
        }
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

    @Override public void onPageStarted(
            GeckoSession session,
            String url,
            boolean hadValidPaint) {
        runOnUiThread(() -> {
            if (!isActivityUsable()) return;
            BrowserTab active = browser == null ? null : browser.getActiveTab();
            if (active == null || active.session != session || active.showStartPage) return;

            setRenderPhase(session, RenderPhase.NETWORK_STARTED, "onPageStart " + url);
            surfaceRecoveryAttempts = 0;
            startAddressGlow();
            if (hadValidPaint) {
                captureNavigationSnapshot(session);
            } else {
                mainHandler.postDelayed(() -> {
                    BrowserTab current = browser == null ? null : browser.getActiveTab();
                    if (current == null
                            || current.session != session
                            || current.hasValidPaint
                            || transitionSnapshot != null
                            || !current.loading) {
                        return;
                    }
                    showPaintGuard(session, "");
                }, 220L);
            }
            scheduleSurfaceProbe(session, 900L, false, "page-start");
        });
    }

    @Override public void onPageFinished(GeckoSession session, boolean success) {
        runOnUiThread(() -> {
            BrowserTab active = browser == null ? null : browser.getActiveTab();
            if (active == null || active.session != session) return;
            Log.i(TAG, "onPageStop success=" + success + " phase=" + renderPhase);
            if (success) {
                scheduleSurfaceProbe(session, 180L, true, "page-stop");
            } else {
                mainHandler.postDelayed(() -> {
                    BrowserTab current = browser == null ? null : browser.getActiveTab();
                    if (current == null || current.session != session) return;
                    if (!current.hasValidPaint) {
                        hidePaintGuard(session, true);
                        clearTransitionSnapshot();
                        tabTransitionRunning = false;
                        setRenderPhase(session, RenderPhase.IDLE, "page-stop-failed");
                    }
                }, 1200L);
            }
        });
    }

    @Override public void onPaintStatusReset(GeckoSession session) {
        runOnUiThread(() -> {
            BrowserTab active = browser == null ? null : browser.getActiveTab();
            if (active == null || active.session != session) return;
            lastPaintCoverKey = "";
            setRenderPhase(session, RenderPhase.WAITING_FOR_COMPOSITE, "paint-status-reset");
            if (!activityResumed || active.showStartPage) return;

            if (transitionSnapshot == null) {
                mainHandler.postDelayed(() -> {
                    BrowserTab current = browser == null ? null : browser.getActiveTab();
                    if (current == null
                            || current.session != session
                            || current.hasValidPaint
                            || transitionSnapshot != null) {
                        return;
                    }
                    showPaintGuard(session, "");
                }, 180L);
            }
            scheduleSurfaceProbe(session, 520L, false, "paint-reset");
        });
    }

    @Override public void onFirstComposite(GeckoSession session) {
        runOnUiThread(() -> {
            BrowserTab active = browser == null ? null : browser.getActiveTab();
            if (active == null || active.session != session) return;
            setRenderPhase(session, RenderPhase.FIRST_COMPOSITE, "first-composite");
            if (transitionSnapshot != null) transitionSnapshot.setAlpha(1f);
            scheduleSurfaceProbe(session, 90L, true, "first-composite");
        });
    }

    @Override public void onFirstContentfulPaint(GeckoSession session) {
        runOnUiThread(() -> {
            BrowserTab active = browser == null ? null : browser.getActiveTab();
            if (active == null || active.session != session) return;
            setRenderPhase(session, RenderPhase.FIRST_CONTENTFUL_PAINT,
                    "first-contentful-paint");
            confirmVisibleSurface(session, "first-contentful-paint");
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
        recoverVisibleSurface("configuration-changed", true);
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
        if (contextPreviewDialog != null && contextPreviewDialog.isShowing()) {
            contextPreviewDialog.dismiss();
            return;
        }
        if (contextMenuDialog != null && contextMenuDialog.isShowing()) {
            contextMenuDialog.dismiss();
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
            recoverVisibleSurface("back-navigation", true);
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

        cancelSurfaceProbe();
        GeckoSession previous = displayedSession;
        Log.i(TAG, "attachSession previous=" + sessionIdentity(previous)
                + " target=" + sessionIdentity(session));
        try {
            if (previous != null) {
                GeckoSession released = geckoView.releaseSession();
                if (released != null) {
                    Log.d(TAG, "Released previous GeckoSession from GeckoView");
                }
            }

            geckoView.setSession(session);
            displayedSession = session;
            try {
                session.setActive(activityResumed);
            } catch (RuntimeException activeError) {
                Log.w(TAG, "Unable to activate attached GeckoSession", activeError);
            }
            geckoView.requestLayout();
            geckoView.invalidate();
            Log.d(TAG, "Attached GeckoSession to GeckoView");
        } catch (RuntimeException error) {
            displayedSession = null;
            Log.e(TAG, "Unable to attach GeckoSession", error);
            if (previous != null && previous != session) {
                try {
                    geckoView.setSession(previous);
                    displayedSession = previous;
                    previous.setActive(activityResumed);
                } catch (RuntimeException restoreError) {
                    Log.e(TAG, "Unable to restore previous GeckoSession", restoreError);
                }
            }
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

        settingsSurface = NativeSettingsPage.create(
                this, browser, this::applySettingsNow);
        settingsSurface.setVisibility(View.GONE);
        webHost.addView(settingsSurface, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

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
        recoverVisibleSurface("immersive-state", true);
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
        ZenTheme.applySystemBarAppearance(this);
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

    private void recoverVisibleSurface(String reason, boolean verify) {
        if (geckoView == null || browser == null || !isActivityUsable()) return;
        BrowserTab active = browser.getActiveTab();
        if (active == null || active.session == null) return;
        Log.i(TAG, "recoverVisibleSurface reason=" + reason
                + " session=" + sessionIdentity(active.session)
                + " phase=" + renderPhase
                + " painted=" + active.hasValidPaint);
        try {
            if (displayedSession != active.session) attachSession(active.session);
            active.session.setActive(activityResumed);
            geckoView.requestLayout();
            geckoView.invalidate();
            if (verify && activityResumed && !active.showStartPage) {
                scheduleSurfaceProbe(active.session, 160L, active.hasValidPaint,
                        "recover-" + reason);
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to restore the active Gecko session", error);
        }
    }

    private void setRenderPhase(
            GeckoSession session,
            RenderPhase next,
            String reason) {
        if (session == null || next == null) return;
        RenderPhase previous = renderPhaseSession == session ? renderPhase : RenderPhase.IDLE;
        renderPhaseSession = session;
        renderPhase = next;
        Log.i(TAG, "renderPhase " + previous + " -> " + next
                + " reason=" + reason
                + " session=" + sessionIdentity(session));
    }

    private String sessionIdentity(GeckoSession session) {
        return session == null ? "none"
                : Integer.toHexString(System.identityHashCode(session));
    }

    private void cancelSurfaceProbe() {
        surfaceProbeGeneration++;
        if (surfaceProbeRunnable != null) {
            mainHandler.removeCallbacks(surfaceProbeRunnable);
            surfaceProbeRunnable = null;
        }
    }

    private void scheduleSurfaceProbe(
            GeckoSession session,
            long delayMs,
            boolean allowReveal,
            String reason) {
        if (session == null || geckoView == null || !activityResumed) return;
        cancelSurfaceProbe();
        final int generation = surfaceProbeGeneration;
        surfaceProbeRunnable = () -> {
            surfaceProbeRunnable = null;
            if (generation != surfaceProbeGeneration
                    || !activityResumed
                    || displayedSession != session
                    || geckoView == null) {
                return;
            }
            BrowserTab active = browser == null ? null : browser.getActiveTab();
            if (active == null || active.session != session || active.showStartPage) return;

            Log.d(TAG, "surfaceProbe reason=" + reason
                    + " attempt=" + surfaceRecoveryAttempts
                    + " phase=" + renderPhase);
            try {
                geckoView.capturePixels()
                        .withHandler(mainHandler)
                        .accept(bitmap -> {
                            if (generation != surfaceProbeGeneration) {
                                recycleBitmap(bitmap);
                                return;
                            }
                            boolean blank = isLikelyBlankFrame(bitmap);
                            recycleBitmap(bitmap);
                            BrowserTab current = browser == null ? null : browser.getActiveTab();
                            if (current == null || current.session != session) return;

                            if (!blank) {
                                confirmVisibleSurface(session, "pixel-probe-" + reason);
                                return;
                            }

                            Log.w(TAG, "Surface probe detected a uniform frame; reason=" + reason
                                    + " attempt=" + surfaceRecoveryAttempts);
                            if (current.loading
                                    && renderPhase == RenderPhase.NETWORK_STARTED
                                    && surfaceRecoveryAttempts == 0) {
                                scheduleSurfaceProbe(session, 700L, allowReveal,
                                        reason + "-network-wait");
                                return;
                            }
                            attemptSurfaceRecovery(session, allowReveal, reason);
                        }, error -> {
                            if (generation != surfaceProbeGeneration) return;
                            Log.w(TAG, "Surface probe failed; reason=" + reason, error);
                            attemptSurfaceRecovery(session, allowReveal, reason + "-capture-error");
                        });
            } catch (RuntimeException error) {
                Log.w(TAG, "Unable to capture Gecko surface", error);
                attemptSurfaceRecovery(session, allowReveal, reason + "-capture-throw");
            }
        };
        mainHandler.postDelayed(surfaceProbeRunnable, Math.max(0L, delayMs));
    }

    private void attemptSurfaceRecovery(
            GeckoSession session,
            boolean allowReveal,
            String reason) {
        if (session == null || geckoView == null || displayedSession != session) return;
        BrowserTab active = browser == null ? null : browser.getActiveTab();
        if (active == null || active.session != session) return;

        surfaceRecoveryAttempts++;
        Log.w(TAG, "Surface recovery attempt=" + surfaceRecoveryAttempts
                + " reason=" + reason
                + " phase=" + renderPhase);
        showPaintGuard(session, "");

        try {
            GeckoSession released = geckoView.releaseSession();
            displayedSession = null;
            geckoView.setSession(session);
            displayedSession = session;
            session.setActive(activityResumed);
            geckoView.requestLayout();
            geckoView.invalidate();
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to rebind Gecko surface", error);
        }

        if (surfaceRecoveryAttempts >= MAX_SURFACE_RECOVERY_ATTEMPTS) {
            geckoView.setVisibility(View.INVISIBLE);
            geckoView.post(() -> {
                if (geckoView == null || displayedSession != session) return;
                geckoView.setVisibility(View.VISIBLE);
                geckoView.requestLayout();
                geckoView.invalidate();
                if (allowReveal || active.hasValidPaint) {
                    mainHandler.postDelayed(() ->
                            confirmVisibleSurface(session, "recovery-limit"), 260L);
                }
            });
            return;
        }

        setRenderPhase(session, RenderPhase.WAITING_FOR_COMPOSITE,
                "surface-rebind-" + reason);
        scheduleSurfaceProbe(session, SURFACE_PROBE_DELAY_MS, allowReveal,
                reason + "-retry");
    }

    private void confirmVisibleSurface(GeckoSession session, String reason) {
        BrowserTab active = browser == null ? null : browser.getActiveTab();
        if (active == null || active.session != session || active.showStartPage) return;
        cancelSurfaceProbe();
        surfaceRecoveryAttempts = 0;
        setRenderPhase(session, RenderPhase.VISIBLE, reason);
        hidePaintGuard(session, false);
        if (transitionSnapshot != null) fadeTransitionSnapshot(transitionGeneration);
        else tabTransitionRunning = false;
    }

    private boolean isLikelyBlankFrame(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()
                || bitmap.getWidth() < 2 || bitmap.getHeight() < 2) {
            return true;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int min = 255;
        int max = 0;
        long total = 0L;
        int samples = 0;
        for (int row = 1; row <= 5; row++) {
            int y = Math.min(height - 1, row * height / 6);
            for (int column = 1; column <= 5; column++) {
                int x = Math.min(width - 1, column * width / 6);
                int color = bitmap.getPixel(x, y);
                int luminance = (Color.red(color) * 2126
                        + Color.green(color) * 7152
                        + Color.blue(color) * 722) / 10000;
                min = Math.min(min, luminance);
                max = Math.max(max, luminance);
                total += luminance;
                samples++;
            }
        }
        int average = samples == 0 ? 0 : (int) (total / samples);
        return max - min <= 7 && (average <= 96 || average >= 238);
    }

    private void recycleBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return;
        try { bitmap.recycle(); } catch (RuntimeException ignored) { }
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
        if (paintGuard == null || browser == null || transitionSnapshot != null) return;
        BrowserTab active = browser.getActiveTab();
        if (active == null || active.session != session || active.showStartPage
                || active.hasValidPaint) {
            return;
        }

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
                    || paintGuard.getVisibility() != View.VISIBLE
                    || current.hasValidPaint) {
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
        mainHandler.postDelayed(paintGuardTimeout, 6500L);
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
        newTab.setOnClickListener(this::openNewTabAndSearch);
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
        bindSearchTrigger(addressShell, false);

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
        bindSearchTrigger(addressDisplay, false);
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
        search.setOnClickListener(v -> openSearchEditor(false));
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
        search.setClickable(true);
        search.setFocusable(true);
        search.setOnClickListener(v -> openHomeSearch());
        search.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                view.performClick();
            }
            return true;
        });
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

    private void openHomeSearch() {
        openSearchEditor(true);
    }

    private void bindSearchTrigger(View trigger, boolean newTabMode) {
        if (trigger == null) return;
        trigger.setClickable(true);
        trigger.setFocusable(true);
        trigger.setOnClickListener(v -> openSearchEditor(newTabMode));
    }

    private void openSearchEditor(boolean newTabMode) {
        if (!isActivityUsable() || isImmersiveMode()) return;
        dismissSearchPopupImmediate();
        mainHandler.post(() -> {
            if (!isActivityUsable()) return;
            showSearchPopup(newTabMode);
            mainHandler.postDelayed(() -> {
                if (searchInput == null || searchPopup == null
                        || !searchPopup.isShowing()) return;
                searchInput.requestFocus();
                showKeyboard(searchInput);
            }, 80L);
        });
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

        TextView zen = text("Z", 18, R.color.zen_key_text);
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
        add.setOnClickListener(this::openNewTabAndSearch);
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
        panel.setPadding(dp(10), dp(9), dp(10), dp(9));
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

        panel.addView(createSidebarTopActions(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        panel.addView(createSidebarShortcutGrid());

        sidebarWorkspaceLabel = label(
                browser.getActiveWorkspaceName().toUpperCase(Locale.ROOT)
                        + "  ·  PESTAÑAS ABIERTAS");
        panel.addView(sidebarWorkspaceLabel);

        ScrollView tabsScroll = new ScrollView(this);
        tabsScroll.setFillViewport(true);
        tabsScroll.setVerticalScrollBarEnabled(false);
        sidebarTabsHost = new LinearLayout(this);
        sidebarTabsHost.setOrientation(LinearLayout.VERTICAL);
        populateSidebarTabs();
        tabsScroll.addView(sidebarTabsHost);
        panel.addView(tabsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        View dock = createSidebarDock();
        LinearLayout.LayoutParams dockParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        dockParams.setMargins(0, dp(5), 0, 0);
        panel.addView(dock, dockParams);
        return panel;
    }

    private View createSidebarTopActions() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dp(3), dp(3), dp(3), dp(3));
        row.addView(sidebarTopAction(R.drawable.ic_home, "Inicio", () -> {
            String homeUrl = ZenPanelController.homeUrl(this);
            dismissSidebarPopupImmediate();
            browser.loadInActiveTab(homeUrl);
        }), new LinearLayout.LayoutParams(0, dp(40), 1f));
        row.addView(sidebarTopAction(R.drawable.ic_star, "Favoritos", () -> {
            dismissSidebarPopupImmediate();
            ZenPanelController.showFavorites(this, browser, this::showSidebarPopup);
        }), new LinearLayout.LayoutParams(0, dp(40), 1f));
        row.addView(sidebarTopAction(R.drawable.ic_history, "Historial", () -> {
            dismissSidebarPopupImmediate();
            ZenPanelController.showHistory(this, browser, this::showSidebarPopup);
        }), new LinearLayout.LayoutParams(0, dp(40), 1f));
        row.addView(sidebarTopAction(R.drawable.ic_profile, "Perfil", () -> {
            dismissSidebarPopupImmediate();
            ZenPanelController.showProfiles(this, this::showSidebarPopup);
        }), new LinearLayout.LayoutParams(0, dp(40), 1f));
        int themeIcon = ZenTheme.isDay(this)
                ? R.drawable.ic_theme_moon : R.drawable.ic_theme_sun;
        String themeDescription = ZenTheme.isDay(this)
                ? "Activar tema Noche" : "Activar tema Día";
        row.addView(sidebarTopAction(themeIcon, themeDescription, () -> {
            dismissSidebarPopupImmediate();
            ZenTheme.toggle(this);
        }), new LinearLayout.LayoutParams(0, dp(40), 1f));
        return row;
    }

    private ImageButton sidebarTopAction(
            int iconRes, String description, Runnable action) {
        ImageButton button = iconButton(iconRes, description);
        button.setBackgroundResource(R.drawable.bg_toolbar_button);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setOnClickListener(v -> {
            if (action != null) action.run();
        });
        return button;
    }

    private void populateSidebarTabs() {
        if (sidebarTabsHost == null) return;
        sidebarTabsHost.animate().cancel();
        sidebarTabsHost.removeAllViews();
        for (BrowserTab tab : browser.getVisibleTabs()) {
            sidebarTabsHost.addView(tabRow(tab));
        }
        TextView newTab = text("＋   Nueva pestaña", 13, R.color.zen_muted);
        newTab.setGravity(Gravity.CENTER_VERTICAL);
        newTab.setPadding(dp(12), 0, dp(12), 0);
        newTab.setBackgroundResource(R.drawable.bg_button);
        newTab.setOnClickListener(this::openNewTabFromSidebar);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        params.setMargins(0, dp(2), 0, dp(3));
        sidebarTabsHost.addView(newTab, params);
    }

    private View createSidebarDock() {
        LinearLayout dock = new LinearLayout(this);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setPadding(dp(2), dp(2), dp(2), dp(2));
        dock.setBackgroundColor(Color.TRANSPARENT);

        ImageButton settings = iconButton(R.drawable.ic_settings, "Configuración");
        settings.setBackgroundColor(Color.TRANSPARENT);
        settings.setPadding(dp(8), dp(8), dp(8), dp(8));
        settings.setOnClickListener(v -> openSettingsTabFromSidebar());
        dock.addView(settings, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout spaces = new LinearLayout(this);
        spaces.setGravity(Gravity.CENTER);
        spaces.setPadding(dp(2), dp(2), dp(2), dp(2));
        spaces.setBackgroundResource(R.drawable.bg_profile_selector);
        spaces.addView(createWorkspaceSegmentButton(
                "personal", "Personal"), new LinearLayout.LayoutParams(0, dp(36), 1f));
        spaces.addView(createWorkspaceSegmentButton(
                "work", "Trabajo"), new LinearLayout.LayoutParams(0, dp(36), 1f));
        spaces.addView(createWorkspaceSegmentButton(
                "education", "Estudio"), new LinearLayout.LayoutParams(0, dp(36), 1f));
        LinearLayout.LayoutParams spacesParams =
                new LinearLayout.LayoutParams(0, dp(40), 1f);
        spacesParams.setMargins(dp(8), 0, dp(8), 0);
        dock.addView(spaces, spacesParams);

        ImageButton downloads = iconButton(R.drawable.ic_downloads, "Descargas");
        downloads.setBackgroundColor(Color.TRANSPARENT);
        downloads.setPadding(dp(8), dp(8), dp(8), dp(8));
        downloads.setOnClickListener(v -> {
            dismissSidebarPopupImmediate();
            ZenPanelController.showDownloads(this, browser, this::showSidebarPopup);
        });
        dock.addView(downloads, new LinearLayout.LayoutParams(dp(38), dp(38)));
        return dock;
    }

    private View createWorkspaceSegmentButton(String workspaceId, String description) {
        boolean active = workspaceId.equals(browser.getActiveWorkspaceId());
        ImageButton button = iconButton(workspaceIconRes(workspaceId), description);
        int index = workspaceButtonIndex(workspaceId);
        if (index >= 0) sidebarWorkspaceButtons[index] = button;
        styleWorkspaceButton(button, active);
        button.setOnClickListener(v -> {
            if (workspaceId.equals(browser.getActiveWorkspaceId())
                    || sidebarWorkspaceTransition) return;
            switchWorkspaceWithSlide(workspaceId);
        });
        return button;
    }

    private int workspaceButtonIndex(String workspaceId) {
        if ("personal".equals(workspaceId)) return 0;
        if ("work".equals(workspaceId)) return 1;
        if ("education".equals(workspaceId)) return 2;
        return -1;
    }

    private void styleWorkspaceButton(ImageButton button, boolean active) {
        if (button == null) return;
        button.animate().cancel();
        button.setBackgroundResource(active
                ? R.drawable.bg_workspace_active : R.drawable.bg_workspace_idle);
        button.setImageTintList(ColorStateList.valueOf(getColor(
                active ? R.color.zen_text : R.color.zen_muted)));
        button.setAlpha(active ? 1f : .52f);
        button.setScaleX(active ? 1f : .82f);
        button.setScaleY(active ? 1f : .82f);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
    }

    private void updateSidebarWorkspaceChrome() {
        if (sidebarWorkspaceLabel != null) {
            sidebarWorkspaceLabel.setText(
                    browser.getActiveWorkspaceName().toUpperCase(Locale.ROOT)
                            + "  ·  PESTAÑAS ABIERTAS");
        }
        String active = browser.getActiveWorkspaceId();
        styleWorkspaceButton(sidebarWorkspaceButtons[0], "personal".equals(active));
        styleWorkspaceButton(sidebarWorkspaceButtons[1], "work".equals(active));
        styleWorkspaceButton(sidebarWorkspaceButtons[2], "education".equals(active));
    }

    private void openSettingsTabFromSidebar() {
        dismissSidebarPopupImmediate();
        dismissContextMenuImmediate();
        browser.openSettingsTab();
        render();
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
        card.setOnClickListener(v -> openQuickAccessEditor(null));
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
            openQuickAccessEditor(item);
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

    private void openQuickAccessEditor(QuickAccessStore.Item existing) {
        if (quickEditorOpening || !isActivityUsable()) return;
        final QuickAccessStore.Item snapshot =
                existing == null ? null : existing.copy();
        quickEditorOpening = true;
        dismissSidebarPopupImmediate();
        mainHandler.postDelayed(() -> {
            quickEditorOpening = false;
            if (!isActivityUsable()) return;
            showQuickAccessEditor(snapshot);
        }, 110L);
    }

    private void showQuickAccessEditor(QuickAccessStore.Item existing) {
        if (!isActivityUsable()) return;
        String workspaceId = browser.getActiveWorkspaceId();
        QuickAccessStore.Item draft = existing == null
                ? QuickAccessStore.create(workspaceId, "", "", "")
                : existing.copy();

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(18), dp(17), dp(18), dp(15));
        sheet.setBackgroundResource(R.drawable.bg_quick_editor_sheet);

        TextView title = text(
                existing == null ? "Añadir acceso" : "Editar acceso",
                20,
                R.color.zen_text);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        sheet.addView(title);
        TextView subtitle = text(
                "Personaliza el nombre, la dirección y su icono.",
                10,
                R.color.zen_muted);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.setMargins(0, dp(2), 0, dp(12));
        sheet.addView(subtitle, subtitleParams);

        LinearLayout previewRow = new LinearLayout(this);
        previewRow.setGravity(Gravity.CENTER_VERTICAL);
        previewRow.setPadding(dp(2), dp(2), dp(2), dp(5));
        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        preview.setBackgroundResource(R.drawable.bg_favicon);
        preview.setImageResource(R.drawable.ic_open_new);
        preview.setImageTintList(ColorStateList.valueOf(getColor(R.color.zen_muted)));
        previewRow.addView(preview, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView previewText = text(
                existing == null ? "Nuevo acceso" : existing.name,
                14,
                R.color.zen_text);
        previewText.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams previewTextParams =
                new LinearLayout.LayoutParams(0, dp(48), 1f);
        previewTextParams.setMargins(dp(12), 0, 0, 0);
        previewRow.addView(previewText, previewTextParams);
        sheet.addView(previewRow);

        EditText name = quickAccessInput("Nombre", existing == null ? "" : existing.name);
        EditText url = quickAccessInput("https://sitio.com", existing == null ? "" : existing.url);
        EditText iconUrl = quickAccessInput(
                "Icono automático o URL de imagen",
                existing == null ? "" : existing.iconUrl);
        sheet.addView(quickEditorField("NOMBRE", name));
        sheet.addView(quickEditorField("DIRECCIÓN", url));
        sheet.addView(quickEditorField("ICONO", iconUrl));

        TextView detect = quickEditorAction(
                "Detectar icono automáticamente", false);
        detect.setOnClickListener(v -> {
            String cleanUrl = url.getText().toString().trim();
            if (cleanUrl.isEmpty()) {
                url.setError("Escribe primero una dirección");
                return;
            }
            String detected = QuickAccessStore.automaticIconUrl(cleanUrl);
            iconUrl.setText(detected);
            loadQuickAccessPreview(preview, detected);
        });
        LinearLayout.LayoutParams detectParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        detectParams.setMargins(0, dp(8), 0, dp(12));
        sheet.addView(detect, detectParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        if (existing != null) {
            TextView remove = quickEditorAction("Eliminar", false);
            remove.setTextColor(getColor(R.color.zen_danger));
            remove.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Eliminar acceso")
                    .setMessage("¿Quitar " + existing.name + "?")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Eliminar", (confirm, which) -> {
                        QuickAccessStore.remove(
                                getApplicationContext(), workspaceId, existing.id);
                        dialog.dismiss();
                        mainHandler.postDelayed(() -> {
                            if (isActivityUsable()) showSidebarPopup();
                        }, 120L);
                    })
                    .show());
            actions.addView(remove, new LinearLayout.LayoutParams(0, dp(42), 1f));
        } else {
            actions.addView(new View(this), new LinearLayout.LayoutParams(0, dp(42), 1f));
        }

        TextView cancel = quickEditorAction("Cancelar", false);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(dp(94), dp(42));
        cancelParams.setMargins(dp(7), 0, dp(7), 0);
        actions.addView(cancel, cancelParams);

        TextView save = quickEditorAction("Guardar", true);
        actions.addView(save, new LinearLayout.LayoutParams(dp(96), dp(42)));
        sheet.addView(actions);

        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            String cleanName = name.getText().toString().trim();
            String cleanUrl = url.getText().toString().trim();
            if (cleanName.isEmpty()) {
                name.setError("Escribe un nombre");
                return;
            }
            if (cleanUrl.isEmpty()) {
                url.setError("Escribe una dirección");
                return;
            }
            v.setEnabled(false);
            try {
                draft.workspaceId = workspaceId;
                draft.name = cleanName;
                draft.url = QuickAccessStore.normalizeUrl(cleanUrl);
                draft.iconUrl = iconUrl.getText().toString().trim();
                if (draft.iconUrl.isEmpty()) {
                    draft.iconUrl = QuickAccessStore.automaticIconUrl(draft.url);
                }
                if (!QuickAccessStore.save(getApplicationContext(), draft)) {
                    Toast.makeText(this,
                            "El espacio ya tiene el máximo de accesos",
                            Toast.LENGTH_SHORT).show();
                    v.setEnabled(true);
                    return;
                }
                dialog.dismiss();
                mainHandler.postDelayed(() -> {
                    if (isActivityUsable()) showSidebarPopup();
                }, 120L);
            } catch (Throwable error) {
                Log.e(TAG, "Unable to save quick access", error);
                Toast.makeText(this,
                        "No se pudo guardar el acceso",
                        Toast.LENGTH_SHORT).show();
                v.setEnabled(true);
            }
        });

        dialog.setContentView(sheet);
        dialog.setOnDismissListener(ignored -> RemoteAssetLoader.cancel(preview));
        try {
            dialog.show();
            Window window = dialog.getWindow();
            if (window == null) return;
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = ZenTheme.isDay(this) ? .22f : .38f;
            window.setAttributes(attributes);
            int width = Math.min(dp(420),
                    getResources().getDisplayMetrics().widthPixels - dp(30));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            sheet.setAlpha(0f);
            sheet.setTranslationY(dp(12));
            sheet.animate().alpha(1f).translationY(0f).setDuration(150L).start();
            if (existing != null && existing.iconUrl != null
                    && !existing.iconUrl.trim().isEmpty()) {
                mainHandler.postDelayed(
                        () -> loadQuickAccessPreview(preview, existing.iconUrl), 80L);
            }
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to show quick access editor", error);
            try { dialog.dismiss(); } catch (RuntimeException ignored) { }
            Toast.makeText(this,
                    "No se pudo abrir el editor de accesos",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private View quickEditorField(String label, EditText input) {
        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(label, 9, R.color.zen_muted);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLetterSpacing(.10f);
        title.setPadding(dp(5), dp(6), 0, dp(4));
        field.addView(title);
        field.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        return field;
    }

    private TextView quickEditorAction(String label, boolean primary) {
        TextView action = text(label, 11,
                primary ? R.color.zen_bg : R.color.zen_text);
        action.setTypeface(Typeface.DEFAULT_BOLD);
        action.setGravity(Gravity.CENTER);
        action.setBackgroundResource(primary
                ? R.drawable.bg_quick_editor_button_primary
                : R.drawable.bg_quick_editor_button_secondary);
        action.setClickable(true);
        action.setFocusable(true);
        return action;
    }

    private EditText quickAccessInput(String hint, String value) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setText(value == null ? "" : value);
        input.setTextColor(getColor(R.color.zen_text));
        input.setHintTextColor(getColor(R.color.zen_muted));
        input.setTextSize(13);
        input.setBackgroundResource(R.drawable.bg_input_clean);
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
        if (preview == null) return;
        RemoteAssetLoader.cancel(preview);
        preview.setImageDrawable(null);
        if (iconUrl == null || iconUrl.trim().isEmpty()) {
            preview.setImageResource(R.drawable.ic_open_new);
            preview.setImageTintList(ColorStateList.valueOf(getColor(R.color.zen_muted)));
            return;
        }

        preview.setImageResource(R.drawable.ic_open_new);
        preview.setImageTintList(ColorStateList.valueOf(getColor(R.color.zen_muted)));
        mainHandler.post(() -> {
            if (!isActivityUsable() || !preview.isAttachedToWindow()) return;
            try {
                preview.setImageTintList(null);
                RemoteAssetLoader.loadInto(
                        MainActivity.this,
                        iconUrl,
                        96,
                        preview,
                        new RemoteAssetLoader.Callback() {
                            @Override public void onLoaded(Bitmap bitmap) { }

                            @Override public void onError(Throwable error) {
                                if (!isActivityUsable() || !preview.isAttachedToWindow()) {
                                    return;
                                }
                                preview.setImageResource(R.drawable.ic_open_new);
                                preview.setImageTintList(ColorStateList.valueOf(
                                        getColor(R.color.zen_muted)));
                            }
                        });
            } catch (Throwable error) {
                Log.w(TAG, "Quick access preview unavailable", error);
                if (preview.isAttachedToWindow()) {
                    preview.setImageResource(R.drawable.ic_open_new);
                    preview.setImageTintList(ColorStateList.valueOf(
                            getColor(R.color.zen_muted)));
                }
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
        int panelWidth = Math.min(dp(390), (int) (availableWidth * .90f));
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
            sidebarTabsHost = null;
            sidebarWorkspaceLabel = null;
            sidebarWorkspaceButtons[0] = null;
            sidebarWorkspaceButtons[1] = null;
            sidebarWorkspaceButtons[2] = null;
            sidebarWorkspaceTransition = false;
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
        sidebarScrim.setBackgroundColor(ZenTheme.sidebarScrim(this));
        sidebarScrim.setContentDescription("Cerrar panel");
        sidebarScrim.setOnClickListener(v -> dismissSidebarPopup());
        overlay.addView(sidebarScrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        sidebarPanelHost = new FrameLayout(this);
        sidebarAnimatedPanel = sidebarPanelHost;
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                panelWidth, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
        panelParams.leftMargin = dp(8);
        panelParams.topMargin = dp(8);
        panelParams.bottomMargin = dp(8);
        sidebarPanelHost.addView(createSidebar(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.addView(sidebarPanelHost, panelParams);
        return overlay;
    }

    private void refreshSidebarPopup() {
        if (sidebarPopup == null || !sidebarPopup.isShowing() || sidebarPanelHost == null) return;
        if (sidebarRefreshRunnable != null) {
            mainHandler.removeCallbacks(sidebarRefreshRunnable);
        }
        final PopupWindow expectedPopup = sidebarPopup;
        sidebarRefreshRunnable = () -> {
            sidebarRefreshRunnable = null;
            if (sidebarPopup != expectedPopup
                    || !expectedPopup.isShowing()
                    || sidebarPanelHost == null) {
                return;
            }
            sidebarPanelHost.removeAllViews();
            sidebarPanelHost.addView(createSidebar(), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            expectedPopup.update();
            lastPopupSidebarFingerprint = sidebarFingerprint();
        };
        mainHandler.postDelayed(sidebarRefreshRunnable, 32L);
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
        sidebarTabsHost = null;
        sidebarWorkspaceLabel = null;
        sidebarWorkspaceButtons[0] = null;
        sidebarWorkspaceButtons[1] = null;
        sidebarWorkspaceButtons[2] = null;
        sidebarWorkspaceTransition = false;
    }

    private void openNewTabFromSidebar(View trigger) {
        if (reuseActiveBlankTabForSearch(true)) return;
        if (!acquireNewTabRequest(trigger)) return;
        browser.addTab("about:blank", true);
        dismissSidebarPopup();
        mainHandler.postDelayed(() -> showSearchPopup(true), 190L);
    }

    private void openNewTabAndSearch(View trigger) {
        if (reuseActiveBlankTabForSearch(false)) return;
        if (!acquireNewTabRequest(trigger)) return;
        animateWebTransition(() -> browser.addTab("about:blank", true));
        mainHandler.postDelayed(() -> showSearchPopup(true), 250L);
    }

    private boolean reuseActiveBlankTabForSearch(boolean closeSidebar) {
        BrowserTab active = browser == null ? null : browser.getActiveTab();
        boolean blank = active != null
                && active.showStartPage
                && (active.url == null || "about:blank".equals(active.url));
        if (!blank) return false;
        Log.d(TAG, "Reusing active blank tab instead of creating a duplicate");
        if (closeSidebar) dismissSidebarPopup();
        mainHandler.postDelayed(() -> showSearchPopup(true), closeSidebar ? 190L : 40L);
        return true;
    }

    private boolean acquireNewTabRequest(View trigger) {
        long now = SystemClock.elapsedRealtime();
        if (newTabRequestPending || now - lastNewTabRequestAt < NEW_TAB_GUARD_MS) {
            Log.d(TAG, "Ignored duplicated new-tab request");
            return false;
        }
        lastNewTabRequestAt = now;
        newTabRequestPending = true;
        if (trigger != null) trigger.setEnabled(false);
        mainHandler.postDelayed(() -> {
            newTabRequestPending = false;
            if (trigger != null && trigger.isAttachedToWindow()) trigger.setEnabled(true);
        }, NEW_TAB_GUARD_MS + 70L);
        return true;
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
        scrim.setBackgroundColor(ZenTheme.searchScrim(this));
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
            boolean submit = actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_GO
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_UNSPECIFIED;
            if (submit || enter) {
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
        searchInput.post(() -> {
            if (searchInput == null || searchPopup == null
                    || !searchPopup.isShowing()) return;
            searchInput.requestFocus();
            showKeyboard(searchInput);
        });
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
        beginNavigationTransition(() -> browser.loadInActiveTab(input));
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
        if (geckoView == null || webHost == null
                || !ZenPanelController.animationsEnabled(this)) {
            change.run();
            return;
        }

        if (tabTransitionRunning) {
            change.run();
            return;
        }

        tabTransitionRunning = true;
        final int generation = ++transitionGeneration;
        mainHandler.postDelayed(() -> {
            if (generation == transitionGeneration && tabTransitionRunning) {
                clearTransitionSnapshot();
                tabTransitionRunning = false;
                BrowserTab active = browser == null ? null : browser.getActiveTab();
                if (active != null && !active.hasValidPaint && active.session != null) {
                    showPaintGuard(active.session, "");
                }
            }
        }, 3600L);

        try {
            geckoView.capturePixels()
                    .withHandler(mainHandler)
                    .accept(bitmap -> {
                        if (generation != transitionGeneration) {
                            recycleBitmap(bitmap);
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


    private void beginNavigationTransition(Runnable navigation) {
        if (navigation == null) return;
        BrowserTab active = browser == null ? null : browser.getActiveTab();
        if (active == null || active.session == null || !active.hasValidPaint) {
            navigation.run();
            return;
        }
        captureNavigationSnapshot(active.session);
        navigation.run();
    }

    private void captureNavigationSnapshot(GeckoSession session) {
        if (session == null
                || geckoView == null
                || webHost == null
                || displayedSession != session
                || transitionSnapshot != null
                || tabTransitionRunning) {
            return;
        }

        tabTransitionRunning = true;
        final int generation = ++transitionGeneration;
        mainHandler.postDelayed(() -> {
            if (generation == transitionGeneration && tabTransitionRunning) {
                clearTransitionSnapshot();
                tabTransitionRunning = false;
                BrowserTab active = browser == null ? null : browser.getActiveTab();
                if (active != null && !active.hasValidPaint && active.session != null) {
                    showPaintGuard(active.session, "");
                }
            }
        }, 3600L);

        try {
            geckoView.capturePixels()
                    .withHandler(mainHandler)
                    .accept(bitmap -> {
                        if (generation != transitionGeneration) {
                            recycleBitmap(bitmap);
                            return;
                        }
                        BrowserTab active = browser == null ? null : browser.getActiveTab();
                        if (active == null || active.session != session || active.hasValidPaint) {
                            recycleBitmap(bitmap);
                            tabTransitionRunning = false;
                            return;
                        }
                        showTransitionSnapshot(bitmap);
                    }, error -> {
                        if (generation == transitionGeneration) {
                            tabTransitionRunning = false;
                        }
                    });
        } catch (RuntimeException error) {
            tabTransitionRunning = false;
            Log.w(TAG, "Unable to capture navigation snapshot", error);
        }
    }

    private void showTransitionSnapshot(Bitmap bitmap) {
        clearTransitionSnapshot();
        if (bitmap == null || bitmap.isRecycled() || webHost == null) return;
        transitionBitmap = bitmap;
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
        ImageView snapshot = transitionSnapshot;
        Bitmap bitmap = transitionBitmap;
        transitionSnapshot = null;
        transitionBitmap = null;
        if (snapshot != null) {
            snapshot.animate().cancel();
            snapshot.setImageDrawable(null);
            ViewParent parent = snapshot.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(snapshot);
            }
        }
        recycleBitmap(bitmap);
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
        if (workspaceId == null || workspaceId.equals(browser.getActiveWorkspaceId())
                || sidebarWorkspaceTransition) return;
        int from = workspacePosition(browser.getActiveWorkspaceId());
        int to = workspacePosition(workspaceId);
        int direction = to >= from ? 1 : -1;
        View tabs = sidebarTabsHost;
        if (tabs == null) {
            animateWebTransition(() -> browser.switchWorkspace(workspaceId));
            return;
        }

        sidebarWorkspaceTransition = true;
        Runnable change = () -> {
            browser.switchWorkspace(workspaceId);
            populateSidebarTabs();
            updateSidebarWorkspaceChrome();
            lastPopupSidebarFingerprint = sidebarFingerprint();
            tabs.setTranslationX(direction * dp(16));
            tabs.setAlpha(0f);
            tabs.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(155L)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .withEndAction(() -> sidebarWorkspaceTransition = false)
                    .start();
        };

        if (!ZenPanelController.animationsEnabled(this)) {
            animateWebTransition(change);
            mainHandler.postDelayed(() -> sidebarWorkspaceTransition = false, 220L);
            return;
        }

        tabs.animate().cancel();
        tabs.animate()
                .translationX(-direction * dp(14))
                .alpha(0f)
                .setDuration(75L)
                .withEndAction(() -> animateWebTransition(change))
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
        TextView key = text("Z", 36, R.color.zen_key_text);
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
                ZenPanelController.homeBackgroundEnabled(this)
                        && !ZenTheme.isDay(this);
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
        final ContextTarget target;
        try {
            target = ContextTarget.from(element);
        } catch (Throwable error) {
            Log.e(TAG, "Unable to snapshot context element", error);
            return;
        }
        if (target == null) return;

        runOnUiThread(() -> {
            if (!isActivityUsable()) return;
            BrowserTab active = browser == null ? null : browser.getActiveTab();
            if (active == null || active.session != session) return;
            try {
                showWebContextMenu(screenX, screenY, target);
            } catch (Throwable error) {
                Log.e(TAG, "Unable to show web context menu", error);
                dismissContextMenuImmediate();
                Toast.makeText(
                        MainActivity.this,
                        "No se pudo abrir el menú del elemento",
                        Toast.LENGTH_SHORT).show();
            }
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
            ContextTarget target) {
        dismissContextMenuImmediate();
        if (!isActivityUsable() || target == null) return;

        final int generation = ++contextGeneration;
        final boolean image = target.isImage();
        final boolean video = target.isVideo();
        final boolean audio = target.isAudio();
        final boolean media = target.isMedia();
        final String source = target.source;
        final String link = target.link;
        final String base = target.base;

        if (!media && link.isEmpty()) return;

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(10), dp(10), dp(8));
        panel.setBackgroundResource(R.drawable.bg_context_sheet);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(2), dp(4), dp(7));

        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackgroundResource(R.drawable.bg_context_preview);
        preview.setImageResource(image
                ? R.drawable.ic_preview
                : video
                ? R.drawable.ic_preview
                : audio
                ? R.drawable.ic_downloads
                : R.drawable.ic_open_new);
        preview.setImageTintList(ColorStateList.valueOf(getColor(R.color.zen_accent)));
        header.addView(preview, new LinearLayout.LayoutParams(dp(50), dp(50)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        String heading = firstNonEmpty(
                target.alt,
                target.title,
                target.linkText,
                image ? "Imagen" : video ? "Video" : audio ? "Audio" : "Enlace");
        TextView title = text(heading, 13, R.color.zen_text);
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
                new LinearLayout.LayoutParams(0, dp(50), 1f);
        labelParams.setMargins(dp(10), 0, 0, 0);
        header.addView(labels, labelParams);
        panel.addView(header);

        boolean remoteSource = isRemoteHttpUrl(source);
        boolean downloadableSource = !source.isEmpty()
                && DownloadStore.validationMessage(source).isEmpty();

        if (image && !source.isEmpty()) {
            panel.addView(contextAction(
                    R.drawable.ic_open_new,
                    "Abrir imagen en pestaña nueva",
                    () -> {
                        dismissContextMenuImmediate();
                        animateWebTransition(() -> browser.addTab(source, true));
                    }));
            if (remoteSource) {
                panel.addView(contextAction(
                        R.drawable.ic_preview,
                        "Vista previa",
                        () -> showMediaPreview(source, heading)));
            }
            if (downloadableSource) {
                panel.addView(contextAction(
                        R.drawable.ic_downloads,
                        "Descargar imagen",
                        () -> enqueueContextDownload(
                                source, base, heading, "image/*")));
            }
            panel.addView(contextAction(
                    R.drawable.ic_copy,
                    "Copiar dirección de imagen",
                    () -> copyText("Dirección de imagen", source)));
            if (remoteSource) {
                panel.addView(contextAction(
                        R.drawable.ic_share,
                        "Compartir imagen",
                        () -> fetchContextMedia(source, base, false)));
                panel.addView(contextAction(
                        R.drawable.ic_copy,
                        "Copiar imagen",
                        () -> fetchContextMedia(source, base, true)));
            }
        } else if (media && !source.isEmpty()) {
            panel.addView(contextAction(
                    R.drawable.ic_open_new,
                    video ? "Abrir video" : "Abrir audio",
                    () -> {
                        dismissContextMenuImmediate();
                        animateWebTransition(() -> browser.addTab(source, true));
                    }));
            if (downloadableSource) {
                panel.addView(contextAction(
                        R.drawable.ic_downloads,
                        video ? "Descargar video" : "Descargar audio",
                        () -> enqueueContextDownload(
                                source, base, heading, video ? "video/*" : "audio/*")));
            }
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
                        beginNavigationTransition(() -> browser.loadInActiveTab(link));
                    }));
            panel.addView(contextAction(
                    R.drawable.ic_open_new,
                    "Abrir enlace en pestaña nueva",
                    () -> {
                        dismissContextMenuImmediate();
                        animateWebTransition(() -> browser.addTab(link, true));
                    }));
            panel.addView(contextAction(
                    R.drawable.ic_copy,
                    "Copiar enlace",
                    () -> copyText("Enlace", link)));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(panel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(scroll)
                .create();
        contextMenuDialog = dialog;
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnDismissListener(ignored -> {
            if (generation == contextGeneration) {
                contextMenuDialog = null;
            }
            RemoteAssetLoader.cancel(preview);
        });

        try {
            dialog.show();
            Window window = dialog.getWindow();
            if (window == null) {
                dialog.dismiss();
                return;
            }
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = .18f;

            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            boolean landscape = screenWidth > screenHeight;
            int width = Math.min(dp(360), screenWidth - dp(24));

            panel.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int height = Math.min(
                    panel.getMeasuredHeight() + dp(8),
                    screenHeight - safeInsetTop - safeInsetBottom - dp(36));

            attributes.gravity = landscape
                    ? Gravity.CENTER
                    : Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            attributes.y = landscape ? 0 : safeInsetBottom + dp(10);
            window.setAttributes(attributes);
            window.setLayout(width, Math.max(dp(150), height));

            panel.setAlpha(0f);
            panel.setTranslationY(landscape ? 0f : dp(16));
            panel.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(150L)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();

            // La miniatura se carga únicamente al solicitar Vista previa.
        } catch (RuntimeException error) {
            Log.e(TAG, "Context dialog show failed", error);
            contextMenuDialog = null;
            try { dialog.dismiss(); } catch (RuntimeException ignored) { }
            Toast.makeText(
                    this,
                    "No se pudo abrir el menú del elemento",
                    Toast.LENGTH_SHORT).show();
        }
    }


    private View contextAction(int iconRes, String label, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), 0, dp(8), 0);
        row.setBackgroundResource(R.drawable.bg_context_action);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(getColor(R.color.zen_text)));
        row.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));

        TextView text = text(label, 12, R.color.zen_text);
        text.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(0, dp(40), 1f);
        textParams.setMargins(dp(10), 0, 0, 0);
        row.addView(text, textParams);

        row.setOnClickListener(v -> {
            if (!v.isEnabled()) return;
            v.setEnabled(false);
            try {
                action.run();
            } catch (Throwable error) {
                Log.e(TAG, "Context action failed: " + label, error);
                Toast.makeText(
                        MainActivity.this,
                        "No se pudo completar la acción",
                        Toast.LENGTH_SHORT).show();
                dismissContextMenuImmediate();
            } finally {
                if (v.isAttachedToWindow()) v.setEnabled(true);
            }
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        params.setMargins(0, 0, 0, dp(3));
        row.setLayoutParams(params);
        return row;
    }


    private void showMediaPreview(String url, String title) {
        dismissContextMenuImmediate();
        if (!isActivityUsable() || !isRemoteHttpUrl(url)) return;

        final int generation = ++contextGeneration;
        FrameLayout container = new FrameLayout(this);
        container.setBackgroundColor(0xF5000000);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription(title);
        image.setImageResource(R.drawable.ic_preview);
        image.setImageTintList(ColorStateList.valueOf(getColor(R.color.zen_accent)));
        container.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(container)
                .create();
        contextPreviewDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (generation == contextGeneration) contextPreviewDialog = null;
            RemoteAssetLoader.cancel(image);
        });
        container.setOnClickListener(v -> {
            if (dialog.isShowing()) dialog.dismiss();
        });

        try {
            dialog.show();
            Window window = dialog.getWindow();
            if (window == null) {
                dialog.dismiss();
                return;
            }
            window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            window.setDimAmount(0f);
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);

            mainHandler.postDelayed(() -> {
                if (!isActivityUsable()
                        || generation != contextGeneration
                        || contextPreviewDialog != dialog
                        || !dialog.isShowing()
                        || !image.isAttachedToWindow()) {
                    return;
                }
                image.setImageTintList(null);
                RemoteAssetLoader.loadInto(
                        MainActivity.this,
                        url,
                        1024,
                        image,
                        new RemoteAssetLoader.Callback() {
                            @Override public void onLoaded(Bitmap bitmap) {
                                if (!image.isAttachedToWindow()) return;
                                image.setAlpha(0f);
                                image.animate().alpha(1f).setDuration(150L).start();
                            }

                            @Override public void onError(Throwable error) {
                                if (!isActivityUsable()
                                        || contextPreviewDialog != dialog
                                        || !dialog.isShowing()) {
                                    return;
                                }
                                Toast.makeText(
                                        MainActivity.this,
                                        "No se pudo cargar la vista previa",
                                        Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            }
                        });
            }, 80L);
        } catch (RuntimeException error) {
            Log.e(TAG, "Preview dialog show failed", error);
            contextPreviewDialog = null;
            try { dialog.dismiss(); } catch (RuntimeException ignored) { }
        }
    }


    private void enqueueContextDownload(
            String url,
            String referrer,
            String name,
            String mime) {
        dismissContextMenuImmediate();
        String validation = DownloadStore.validationMessage(url);
        if (!validation.isEmpty()) {
            Toast.makeText(this, validation, Toast.LENGTH_LONG).show();
            return;
        }
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
        if (!isActivityUsable() || !isRemoteHttpUrl(url)) {
            Toast.makeText(
                    this,
                    "Este recurso no se puede preparar de forma segura",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(
                this,
                copy ? "Preparando imagen para copiar…" : "Preparando imagen para compartir…",
                Toast.LENGTH_SHORT).show();
        ContextMediaStore.fetch(this, url, referrer, new ContextMediaStore.Callback() {
            @Override public void onReady(File file, String mime) {
                runOnUiThread(() -> {
                    if (!isActivityUsable() || !activityResumed) return;
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
                    } catch (Throwable error) {
                        Log.e(TAG, "Context media action failed", error);
                        if (isActivityUsable()) {
                            Toast.makeText(
                                    MainActivity.this,
                                    "No se pudo completar la acción",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }

            @Override public void onError(Throwable error) {
                runOnUiThread(() -> {
                    if (!isActivityUsable() || !activityResumed) return;
                    Log.w(TAG, "Context media fetch failed", error);
                    Toast.makeText(
                            MainActivity.this,
                            "No se pudo obtener la imagen",
                            Toast.LENGTH_SHORT).show();
                });
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
        contextGeneration++;
        AlertDialog menu = contextMenuDialog;
        contextMenuDialog = null;
        if (menu != null) {
            try {
                if (menu.isShowing()) menu.dismiss();
            } catch (RuntimeException error) {
                Log.w(TAG, "Unable to dismiss context menu", error);
            }
        }

        AlertDialog preview = contextPreviewDialog;
        contextPreviewDialog = null;
        if (preview != null) {
            try {
                if (preview.isShowing()) preview.dismiss();
            } catch (RuntimeException error) {
                Log.w(TAG, "Unable to dismiss preview", error);
            }
        }
    }


    private String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private void showDownloadProgress(String recordId) {
        if (appRoot == null || recordId == null || !isActivityUsable()) return;

        View previous = appRoot.findViewWithTag("download-notice");
        if (previous != null && previous.getParent() instanceof ViewGroup) {
            ((ViewGroup) previous.getParent()).removeView(previous);
        }
        if (downloadNoticeTicker != null) {
            mainHandler.removeCallbacks(downloadNoticeTicker);
        }

        FrameLayout notice = new FrameLayout(this);
        notice.setTag("download-notice");
        notice.setBackgroundResource(R.drawable.bg_download_notice_edge);
        notice.setElevation(dp(14));
        notice.setClickable(true);

        LinearLayout body = new LinearLayout(this);
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.setPadding(dp(8), dp(4), dp(8), dp(7));

        FrameLayout iconShell = new FrameLayout(this);
        iconShell.setBackgroundResource(R.drawable.bg_download_icon_glow);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_downloads);
        icon.setImageTintList(ColorStateList.valueOf(getColor(R.color.zen_accent)));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iconShell.addView(icon, new FrameLayout.LayoutParams(
                dp(18), dp(18), Gravity.CENTER));
        body.addView(iconShell, new LinearLayout.LayoutParams(dp(32), dp(32)));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_VERTICAL);
        center.setPadding(dp(7), 0, dp(4), 0);

        TextView title = text("Iniciando descarga…", 11, R.color.zen_text);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);

        TextView details = text("Preparando archivo", 8, R.color.zen_muted);
        details.setSingleLine(true);
        details.setEllipsize(TextUtils.TruncateAt.END);
        details.setPadding(0, dp(2), 0, 0);

        center.addView(title);
        center.addView(details);
        body.addView(center, new LinearLayout.LayoutParams(0, dp(38), 1f));

        TextView percentView = text("", 10, R.color.zen_accent);
        percentView.setTypeface(Typeface.DEFAULT_BOLD);
        percentView.setGravity(Gravity.CENTER);
        body.addView(percentView, new LinearLayout.LayoutParams(dp(39), dp(34)));

        notice.addView(body, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        ProgressBar edge = new ProgressBar(
                this, null, android.R.attr.progressBarStyleHorizontal);
        edge.setMax(100);
        edge.setProgressDrawable(getDrawable(R.drawable.progress_download_edge));
        edge.setProgressBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        FrameLayout.LayoutParams edgeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3), Gravity.BOTTOM);
        edgeParams.leftMargin = dp(3);
        edgeParams.rightMargin = dp(3);
        edgeParams.bottomMargin = dp(2);
        notice.addView(edge, edgeParams);

        int displayWidth = getResources().getDisplayMetrics().widthPixels;
        int targetWidth = Math.min(dp(292), displayWidth - dp(32));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                targetWidth,
                dp(58),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = safeInsetBottom + dp(14);
        appRoot.addView(notice, params);

        notice.setOnClickListener(v -> {
            if (notice.getParent() instanceof ViewGroup) {
                ((ViewGroup) notice.getParent()).removeView(notice);
            }
            if (downloadNoticeTicker != null) {
                mainHandler.removeCallbacks(downloadNoticeTicker);
            }
            ZenPanelController.showDownloads(this, browser, this::showSidebarPopup);
        });

        notice.setAlpha(0f);
        notice.setTranslationY(dp(12));
        notice.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(145L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();

        final boolean[] finishing = {false};
        downloadNoticeTicker = new Runnable() {
            @Override public void run() {
                if (!isActivityUsable() || notice.getParent() != appRoot) return;

                DownloadStore.Record record =
                        DownloadStore.get(MainActivity.this, recordId);
                if (record == null) {
                    fadeDownloadNotice(notice, 150L);
                    return;
                }

                title.setText(record.name == null || record.name.trim().isEmpty()
                        ? "Descargando archivo"
                        : record.name);

                int percent = record.total > 0L
                        ? (int) Math.min(100L, record.bytes * 100L / record.total)
                        : 0;
                boolean active = DownloadStore.DOWNLOADING.equals(record.status)
                        || DownloadStore.QUEUED.equals(record.status);

                edge.setIndeterminate(active && record.total <= 0L);
                if (!edge.isIndeterminate()) {
                    edge.setProgress(percent, true);
                }
                percentView.setText(record.total > 0L ? percent + "%" : "");

                if (DownloadStore.COMPLETE.equals(record.status)) {
                    title.setText("Descarga completada");
                    details.setText(formatBytes(record.bytes));
                    percentView.setText("100%");
                    edge.setIndeterminate(false);
                    edge.setProgress(100, true);
                    if (!finishing[0]) {
                        notice.animate().cancel();
                        notice.animate().scaleX(1.018f).scaleY(1.018f)
                                .setDuration(110L)
                                .withEndAction(() -> notice.animate()
                                        .scaleX(1f).scaleY(1f)
                                        .setDuration(150L).start())
                                .start();
                        edge.animate().alpha(0f).setStartDelay(360L)
                                .setDuration(260L).start();
                        finishing[0] = true;
                        mainHandler.postDelayed(
                                () -> fadeDownloadNotice(notice, 140L),
                                1300L);
                    }
                    return;
                }

                if (DownloadStore.FAILED.equals(record.status)
                        || DownloadStore.CANCELLED.equals(record.status)) {
                    title.setText(DownloadStore.CANCELLED.equals(record.status)
                            ? "Descarga cancelada"
                            : "Error en la descarga");
                    details.setText(record.error == null || record.error.trim().isEmpty()
                            ? "Toca para abrir Descargas"
                            : record.error);
                    percentView.setText("");
                    edge.setIndeterminate(false);
                    if (!finishing[0]) {
                        finishing[0] = true;
                        mainHandler.postDelayed(
                                () -> fadeDownloadNotice(notice, 150L),
                                2200L);
                    }
                    return;
                }

                String amount = record.total > 0L
                        ? formatBytes(record.bytes) + " / " + formatBytes(record.total)
                        : formatBytes(record.bytes);
                details.setText(formatRate(record.bytesPerSecond) + "  ·  " + amount);
                mainHandler.postDelayed(this, 450L);
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
            boolean settingsTab = browser.isSettingsTab(tab);

            if (settingsTab) {
                cancelSurfaceProbe();
                dismissContextMenuImmediate();
                clearTransitionSnapshot();
                tabTransitionRunning = false;
                cancelPaintGuardTimeout();
                stopAddressGlow();

                newTabSurface.setVisibility(View.GONE);
                settingsSurface.setVisibility(View.VISIBLE);
                settingsSurface.bringToFront();
                if (paintGuard != null) {
                    paintGuard.animate().cancel();
                    paintGuard.setVisibility(View.GONE);
                    paintGuard.setAlpha(0f);
                }
                if (transitionScrim != null) transitionScrim.setVisibility(View.GONE);

                if (addressDisplay != null) {
                    addressDisplay.setVisibility(View.VISIBLE);
                    addressDisplay.setText("Configuración");
                    addressDisplay.setContentDescription("Configuración de Zen Browser");
                }
                setEnabled(backButton, false);
                setEnabled(forwardButton, false);
                reloadButton.setImageResource(R.drawable.ic_reload);
                reloadButton.setContentDescription("No disponible en Configuración");
                setEnabled(reloadButton, false);
                updateProgressBar(tab);
            } else {
                settingsSurface.setVisibility(View.GONE);
                setEnabled(reloadButton, true);
                if (tab.loading && contextMenuDialog != null) dismissContextMenuImmediate();
                boolean sessionChanged = displayedSession != tab.session;
                attachSession(tab.session);
                if (sessionChanged && !tab.showStartPage && activityResumed) {
                    scheduleSurfaceProbe(tab.session, 180L, tab.hasValidPaint,
                            "render-session-change");
                }
                String paintCoverKey = tab.id + ":" + tab.navigationSerial;
                boolean newTab = tab.showStartPage;
                newTabSurface.setVisibility(newTab ? View.VISIBLE : View.GONE);
                if (newTab) {
                    hidePaintGuard(tab.session, true);
                    lastPaintCoverKey = "";
                } else if (!tab.hasValidPaint
                        && (sessionChanged || tab.loading
                        || !paintCoverKey.equals(lastPaintCoverKey))) {
                    showPaintGuard(tab.session, "Cargando página…");
                    lastPaintCoverKey = paintCoverKey;
                } else if (tab.hasValidPaint && paintGuard != null
                        && paintGuard.getVisibility() == View.VISIBLE) {
                    hidePaintGuard(tab.session, false);
                }
                if (addressDisplay != null) {
                    boolean showAddress = !newTab
                            && ZenPanelController.showAddressBar(this);
                    addressDisplay.setVisibility(showAddress ? View.VISIBLE : View.GONE);
                    addressDisplay.setText(
                            showAddress ? displayHost(tab.url) : "Nueva pestaña");
                    addressDisplay.setContentDescription(showAddress
                            ? "Dirección actual: " + tab.url
                            : "Buscar o escribir una dirección");
                }
                setEnabled(backButton, tab.canGoBack);
                setEnabled(forwardButton, tab.canGoForward);
                reloadButton.setImageResource(
                        tab.loading ? R.drawable.ic_stop : R.drawable.ic_reload);
                reloadButton.setContentDescription(
                        tab.loading ? "Detener" : "Recargar");
                updateProgressBar(tab);
            }

            String fingerprint = sidebarFingerprint();
            if (fixedSidebar != null && !fingerprint.equals(lastSidebarFingerprint)) {
                int index = root.indexOfChild(fixedSidebar);
                int visibility = fixedSidebar.getVisibility();
                View replacement = createCompactRail();
                replacement.setVisibility(visibility);
                root.removeView(fixedSidebar);
                root.addView(replacement, index,
                        new LinearLayout.LayoutParams(
                                dp(58), ViewGroup.LayoutParams.MATCH_PARENT));
                fixedSidebar = replacement;
                lastSidebarFingerprint = fingerprint;
                applyResponsiveLayout(getResources().getConfiguration());
            }
            if (sidebarPopup != null && sidebarPopup.isShowing()
                    && !sidebarWorkspaceTransition
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
        if (browser != null && browser.isSettingsTab(tab)) return "⚙";
        String host = displayHost(tab == null ? null : tab.url);
        if (host.isEmpty() || "Nueva pestaña".equals(host)) return tab != null && tab.essential ? "◆" : "Z";
        return host.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private String activeUrlForEditing() {
        BrowserTab tab = browser.getActiveTab();
        if (tab == null || browser.isInternalTab(tab)
                || tab.url == null || "about:blank".equals(tab.url)) return "";
        return tab.url;
    }

    private String displayHost(String url) {
        if (BrowserRepository.INTERNAL_SETTINGS_URL.equals(url)) return "Configuración";
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
