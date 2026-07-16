package com.andres.zengecko;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.WindowInsetsController;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Stores and applies Zen Browser's explicit Day/Night theme. */
public final class ZenTheme {
    public static final String MODE_NIGHT = "night";
    public static final String MODE_DAY = "day";

    private static final String TAG = "ZenTheme";
    private static final String PREFS = "zen_ui_prefs";
    private static final String KEY_MODE = "theme_mode";

    private ZenTheme() { }

    public static String mode(Context context) {
        if (context == null) return MODE_NIGHT;
        String value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_MODE, MODE_NIGHT);
        return MODE_DAY.equals(value) ? MODE_DAY : MODE_NIGHT;
    }

    public static boolean isDay(Context context) {
        return MODE_DAY.equals(mode(context));
    }

    public static Context wrap(Context base) {
        if (base == null) return null;
        Configuration configuration = new Configuration(
                base.getResources().getConfiguration());
        int requested = isDay(base)
                ? Configuration.UI_MODE_NIGHT_NO
                : Configuration.UI_MODE_NIGHT_YES;
        configuration.uiMode = (configuration.uiMode
                & ~Configuration.UI_MODE_NIGHT_MASK) | requested;
        return base.createConfigurationContext(configuration);
    }

    public static void setMode(Activity activity, String requestedMode) {
        if (activity == null) return;
        String next = MODE_DAY.equals(requestedMode) ? MODE_DAY : MODE_NIGHT;
        SharedPreferences preferences = activity.getSharedPreferences(
                PREFS, Context.MODE_PRIVATE);
        String current = preferences.getString(KEY_MODE, MODE_NIGHT);
        if (next.equals(current)) return;
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).prepareForVisualModeChange();
        }
        preferences.edit().putString(KEY_MODE, next).apply();
        applyToRuntime(activity);
        activity.getWindow().getDecorView().postDelayed(activity::recreate, 90L);
    }

    public static void toggle(Activity activity) {
        setMode(activity, isDay(activity) ? MODE_NIGHT : MODE_DAY);
    }

    public static int searchScrim(Context context) {
        return isDay(context) ? 0x660E0B15 : 0xC20D0E12;
    }

    public static int sidebarScrim(Context context) {
        return isDay(context) ? 0x520A0710 : 0x99000000;
    }

    public static int panelScrim(Context context) {
        return isDay(context) ? 0x520A0710 : 0xB8000000;
    }

    public static int webCanvasColor(Context context) {
        return isDay(context) ? 0xFFFFFFFF : 0xFF050508;
    }

    /** Applies prefers-color-scheme to Gecko without hard-binding a minor API. */
    public static void applyToRuntime(Context context) {
        if (context == null) return;
        try {
            Object runtime = ZenGeckoApplication.runtime(context);
            Method getSettings = runtime.getClass().getMethod("getSettings");
            Object settings = getSettings.invoke(runtime);
            Class<?> settingsClass = settings.getClass();

            int scheme = isDay(context)
                    ? readScheme(settingsClass, "COLOR_SCHEME_LIGHT", 1)
                    : readScheme(settingsClass, "COLOR_SCHEME_DARK", 2);
            Method setter = settingsClass.getMethod(
                    "setPreferredColorScheme", int.class);
            setter.invoke(settings, scheme);
            Log.d(TAG, "Preferred web color scheme applied: " + scheme);
        } catch (Throwable error) {
            Log.w(TAG, "Unable to apply preferred web color scheme", error);
        }
    }

    private static int readScheme(
            Class<?> settingsClass, String fieldName, int fallback) {
        try {
            Field field = settingsClass.getField(fieldName);
            return field.getInt(null);
        } catch (Throwable ignored) {
            try {
                Class<?> canonical = Class.forName(
                        "org.mozilla.geckoview.GeckoRuntimeSettings");
                Field field = canonical.getField(fieldName);
                return field.getInt(null);
            } catch (Throwable ignoredAgain) {
                return fallback;
            }
        }
    }

    public static void applySystemBarAppearance(Activity activity) {
        if (activity == null) return;
        boolean light = isDay(activity);
        View decor = activity.getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller =
                    activity.getWindow().getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(light ? mask : 0, mask);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int flags = decor.getSystemUiVisibility();
            int mask = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decor.setSystemUiVisibility(light ? flags | mask : flags & ~mask);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = decor.getSystemUiVisibility();
            decor.setSystemUiVisibility(light
                    ? flags | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    : flags & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }
}
