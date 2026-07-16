package com.andres.zengecko;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import org.mozilla.geckoview.GeckoView;

/**
 * Visual surface style independent from the Day/Night theme.
 * GeckoView itself is never blurred. When glass is enabled, Zen captures one
 * reduced frame and blurs only that detached snapshot behind the panel.
 */
public final class ZenLiquidGlass {
    public static final String STYLE_SOLID = "SOLID";
    public static final String STYLE_LIQUID_GLASS = "LIQUID_GLASS";
    public static final String QUALITY_FULL = "FULL";
    public static final String QUALITY_REDUCED = "REDUCED";
    public static final String QUALITY_FALLBACK = "FALLBACK";

    private static final String PREFS = "zen_ui_prefs";
    public static final String KEY_STYLE = "surface_style";
    public static final String KEY_INTENSITY = "liquid_glass_intensity";
    public static final String KEY_REDUCE_EFFECTS = "liquid_glass_reduce_effects";

    private ZenLiquidGlass() { }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context context) {
        return STYLE_LIQUID_GLASS.equals(
                preferences(context).getString(KEY_STYLE, STYLE_SOLID));
    }

    public static int intensity(Context context) {
        return Math.max(0, Math.min(100,
                preferences(context).getInt(KEY_INTENSITY, 58)));
    }

    public static boolean reduceEffects(Context context) {
        return preferences(context).getBoolean(KEY_REDUCE_EFFECTS, false)
                || ZenPanelController.renderDelayMs(context) > 16L;
    }

    public static boolean supportsRealBlur() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public static String quality(Context context) {
        if (!supportsRealBlur()) return QUALITY_FALLBACK;
        return reduceEffects(context) ? QUALITY_REDUCED : QUALITY_FULL;
    }

    public static float blurRadius(Context context) {
        float radius = 8f + intensity(context) * .28f;
        if (reduceEffects(context)) radius *= .55f;
        return Math.max(6f, Math.min(34f, radius));
    }

    public static void applySurface(
            Context context,
            View target,
            int solidResource,
            int glassDayResource,
            int glassNightResource) {
        if (target == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            target.setRenderEffect(null);
        }
        if (!isEnabled(context)) {
            target.setBackgroundResource(solidResource);
            return;
        }
        target.setBackgroundResource(ZenTheme.isDay(context)
                ? glassDayResource : glassNightResource);
    }

    public static void applyGenericSurface(Context context, View target, int solidResource) {
        applySurface(context, target, solidResource,
                R.drawable.zen_glass_surface_day,
                R.drawable.zen_glass_surface_night);
    }

    /** Adds a one-shot captured backdrop as the first child of host. */
    public static ImageView installCapturedBackdrop(
            Activity activity,
            FrameLayout host,
            GeckoView geckoView) {
        if (activity == null || host == null || geckoView == null || !isEnabled(activity)) {
            return null;
        }

        ImageView backdrop = new ImageView(activity);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setBackgroundColor(Color.TRANSPARENT);
        backdrop.setAlpha(0f);
        backdrop.setClickable(false);
        host.addView(backdrop, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        final Bitmap[] owned = new Bitmap[1];
        backdrop.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { }
            @Override public void onViewDetachedFromWindow(View view) {
                Bitmap bitmap = owned[0];
                owned[0] = null;
                backdrop.setImageDrawable(null);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    backdrop.setRenderEffect(null);
                }
                if (bitmap != null && !bitmap.isRecycled()) {
                    try { bitmap.recycle(); } catch (RuntimeException ignored) { }
                }
            }
        });

        try {
            geckoView.capturePixels()
                    .withHandler(new Handler(Looper.getMainLooper()))
                    .accept(bitmap -> {
                        if (bitmap == null || bitmap.isRecycled()
                                || !backdrop.isAttachedToWindow()) {
                            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                            return;
                        }
                        int maxSide = reduceEffects(activity) ? 360 : 640;
                        int width = bitmap.getWidth();
                        int height = bitmap.getHeight();
                        float scale = Math.min(1f,
                                maxSide / (float) Math.max(1, Math.max(width, height)));
                        Bitmap reduced = bitmap;
                        if (scale < .999f) {
                            reduced = Bitmap.createScaledBitmap(
                                    bitmap,
                                    Math.max(1, Math.round(width * scale)),
                                    Math.max(1, Math.round(height * scale)),
                                    true);
                            if (reduced != bitmap) bitmap.recycle();
                        }
                        owned[0] = reduced;
                        backdrop.setImageBitmap(reduced);
                        if (supportsRealBlur() && !reduceEffects(activity)) {
                            backdrop.setRenderEffect(RenderEffect.createBlurEffect(
                                    blurRadius(activity), blurRadius(activity),
                                    Shader.TileMode.CLAMP));
                        }
                        backdrop.animate().alpha(ZenTheme.isDay(activity) ? .54f : .72f)
                                .setDuration(120L).start();
                    }, error -> {
                        // Stable fallback: translucent surfaces remain active without blur.
                    });
        } catch (RuntimeException ignored) {
            // Stable fallback: translucent surfaces remain active without blur.
        }
        return backdrop;
    }
}
