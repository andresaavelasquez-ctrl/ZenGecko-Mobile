package com.andres.zengecko;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import org.mozilla.geckoview.GeckoView;

/**
 * Global surface style independent from Day/Night.
 *
 * GeckoView is never blurred live. Overlay panels use a reduced one-shot
 * snapshot. Every registered Zen surface receives the same material recipe:
 * translucent gradient, controlled edge, upper reflection and depth.
 */
public final class ZenLiquidGlass {
    public static final String STYLE_SOLID = "SOLID";
    public static final String STYLE_LIQUID_GLASS = "LIQUID_GLASS";
    public static final String QUALITY_FULL = "FULL";
    public static final String QUALITY_REDUCED = "REDUCED";
    public static final String QUALITY_FALLBACK = "FALLBACK";

    public static final int ROLE_PANEL = 1;
    public static final int ROLE_TOOLBAR = 2;
    public static final int ROLE_SEARCH = 3;
    public static final int ROLE_CARD = 4;
    public static final int ROLE_SELECTED = 5;

    private static final String PREFS = "zen_ui_prefs";
    public static final String KEY_STYLE = "surface_style";
    public static final String KEY_INTENSITY = "liquid_glass_intensity";
    public static final String KEY_REDUCE_EFFECTS = "liquid_glass_reduce_effects";

    private ZenLiquidGlass() { }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context context) {
        return context != null && STYLE_LIQUID_GLASS.equals(
                preferences(context).getString(KEY_STYLE, STYLE_SOLID));
    }

    public static int intensity(Context context) {
        return Math.max(0, Math.min(100,
                preferences(context).getInt(KEY_INTENSITY, 62)));
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
        float radius = 10f + intensity(context) * .26f;
        if (reduceEffects(context)) radius *= .50f;
        return Math.max(6f, Math.min(34f, radius));
    }

    public static void applySurface(
            Context context,
            View target,
            int solidResource,
            int glassDayResource,
            int glassNightResource) {
        int role = ROLE_PANEL;
        if (glassDayResource == R.drawable.zen_glass_toolbar_day) {
            role = ROLE_TOOLBAR;
        } else if (glassDayResource == R.drawable.zen_glass_search_day) {
            role = ROLE_SEARCH;
        }
        applyMaterial(context, target, solidResource, role, false);
    }

    public static void applyGenericSurface(
            Context context, View target, int solidResource) {
        applyMaterial(context, target, solidResource, ROLE_CARD, false);
    }

    public static void applySelectableSurface(
            Context context,
            View target,
            int solidResource,
            boolean selected) {
        applyMaterial(context, target, solidResource,
                selected ? ROLE_SELECTED : ROLE_CARD, selected);
    }

    public static void applyInputSurface(
            Context context, View target, int solidResource) {
        applyMaterial(context, target, solidResource, ROLE_SEARCH, false);
    }

    private static void applyMaterial(
            Context context,
            View target,
            int solidResource,
            int role,
            boolean selected) {
        if (target == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            target.setRenderEffect(null);
        }
        target.animate().cancel();
        if (!isEnabled(context)) {
            target.setBackgroundResource(solidResource);
            target.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            target.setClipToOutline(false);
            return;
        }

        target.setBackground(buildGlassDrawable(context, role, selected));
        target.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        target.setClipToOutline(true);
        float elevation = role == ROLE_PANEL || role == ROLE_SEARCH
                ? dp(context, reduceEffects(context) ? 7f : 13f)
                : role == ROLE_TOOLBAR
                ? dp(context, 5f)
                : dp(context, selected ? 6f : 3f);
        target.setElevation(elevation);
    }

    private static LayerDrawable buildGlassDrawable(
            Context context, int role, boolean selected) {
        boolean day = ZenTheme.isDay(context);
        int strength = intensity(context);
        int baseAlpha = day ? 194 : 178;
        baseAlpha += Math.round(strength * .34f);
        if (role == ROLE_PANEL || role == ROLE_SEARCH) baseAlpha += 12;
        if (selected) baseAlpha += 15;
        baseAlpha = Math.max(145, Math.min(238, baseAlpha));

        int top;
        int bottom;
        int border;
        int innerHighlight;
        if (day) {
            top = Color.argb(baseAlpha,
                    selected ? 249 : 255,
                    selected ? 244 : 253,
                    255);
            bottom = Color.argb(Math.max(135, baseAlpha - 34),
                    selected ? 222 : 238,
                    selected ? 211 : 234,
                    selected ? 252 : 248);
            border = Color.argb(selected ? 205 : 125,
                    selected ? 126 : 190,
                    selected ? 83 : 174,
                    selected ? 232 : 216);
            innerHighlight = Color.argb(150, 255, 255, 255);
        } else {
            top = Color.argb(baseAlpha,
                    selected ? 40 : 28,
                    selected ? 25 : 25,
                    selected ? 58 : 39);
            bottom = Color.argb(Math.max(120, baseAlpha - 42),
                    selected ? 22 : 10,
                    selected ? 13 : 11,
                    selected ? 38 : 18);
            border = Color.argb(selected ? 210 : 105,
                    selected ? 180 : 211,
                    selected ? 116 : 196,
                    255);
            innerHighlight = Color.argb(82, 255, 255, 255);
        }

        float radius = dp(context,
                role == ROLE_PANEL ? 28f
                        : role == ROLE_SEARCH ? 25f
                        : role == ROLE_TOOLBAR ? 21f
                        : 18f);

        GradientDrawable base = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { top, bottom });
        base.setCornerRadius(radius);
        base.setStroke(Math.max(1, Math.round(dp(context, selected ? 1.35f : .8f))),
                border);

        GradientDrawable reflection = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {
                        innerHighlight,
                        Color.argb(day ? 34 : 20, 255, 255, 255),
                        Color.TRANSPARENT
                });
        reflection.setCornerRadius(Math.max(0f, radius - dp(context, 1f)));

        LayerDrawable layers = new LayerDrawable(new android.graphics.drawable.Drawable[] {
                base, reflection
        });
        int inset = Math.max(1, Math.round(dp(context, 1f)));
        layers.setLayerInset(1, inset, inset, inset, Math.round(radius * .48f));
        return layers;
    }

    /** Adds a reduced, one-shot captured backdrop as the first overlay layer. */
    public static ImageView installCapturedBackdrop(
            Activity activity,
            FrameLayout host,
            GeckoView geckoView) {
        if (activity == null || host == null || geckoView == null
                || !isEnabled(activity)) {
            return null;
        }

        ImageView backdrop = new ImageView(activity);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setBackgroundColor(Color.TRANSPARENT);
        backdrop.setAlpha(0f);
        backdrop.setScaleX(1.035f);
        backdrop.setScaleY(1.035f);
        backdrop.setClickable(false);
        host.addView(backdrop, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        View tint = new View(activity);
        tint.setClickable(false);
        tint.setBackgroundColor(ZenTheme.isDay(activity)
                ? Color.argb(42, 250, 246, 255)
                : Color.argb(74, 7, 6, 12));
        host.addView(tint, 1, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        final Bitmap[] owned = new Bitmap[1];
        backdrop.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { }

            @Override public void onViewDetachedFromWindow(View view) {
                Bitmap bitmap = owned[0];
                owned[0] = null;
                backdrop.animate().cancel();
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
                        int maxSide = reduceEffects(activity) ? 420 : 720;
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
                            float radius = blurRadius(activity);
                            backdrop.setRenderEffect(RenderEffect.createBlurEffect(
                                    radius, radius, Shader.TileMode.CLAMP));
                        }
                        backdrop.animate()
                                .alpha(ZenTheme.isDay(activity) ? .48f : .64f)
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(reduceEffects(activity) ? 90L : 170L)
                                .start();
                    }, error -> {
                        // Stable fallback: material surfaces remain active without blur.
                    });
        } catch (RuntimeException ignored) {
            // Stable fallback: material surfaces remain active without blur.
        }
        return backdrop;
    }

    private static float dp(Context context, float value) {
        return value * context.getResources().getDisplayMetrics().density;
    }
}
