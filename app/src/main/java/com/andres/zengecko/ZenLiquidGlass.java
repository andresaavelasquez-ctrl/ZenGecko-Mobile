package com.andres.zengecko;

import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;

public final class ZenLiquidGlass {
    public static final String STYLE_SOLID = "SOLID";
    public static final String STYLE_LIQUID_GLASS = "LIQUID_GLASS";
    public static final String QUALITY_FULL = "FULL";
    public static final String QUALITY_REDUCED = "REDUCED";
    public static final String QUALITY_FALLBACK = "FALLBACK";

    private ZenLiquidGlass() {}

    public static boolean supportsRealBlur() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public static void applyBlurIfSupported(View target, float radius) {
        if (target == null) return;
        if (supportsRealBlur()) {
            target.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
        } else {
            target.setRenderEffect(null);
        }
    }
}
