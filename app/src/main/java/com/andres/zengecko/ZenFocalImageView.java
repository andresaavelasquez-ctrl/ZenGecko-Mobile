package com.andres.zengecko;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * ImageView with a controllable focal point and a landscape FOCAL_FIT mode.
 * Portrait keeps full cover. Landscape limits enlargement and lets the view's
 * background color fill tiny side bands instead of over-zooming the bonsai.
 */
public final class ZenFocalImageView extends ImageView {
    private float focalX = .5f;
    private float focalY = .5f;
    private float maxLandscapeZoomFromFit = 1.16f;

    public ZenFocalImageView(Context context) {
        super(context);
        init();
    }

    public ZenFocalImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        super.setScaleType(ScaleType.MATRIX);
    }

    public void setFocalPoint(float x, float y) {
        focalX = clamp(x);
        focalY = clamp(y);
        updateMatrix();
    }

    public void setMaxLandscapeZoomFromFit(float value) {
        maxLandscapeZoomFromFit = Math.max(1f, Math.min(1.35f, value));
        updateMatrix();
    }

    @Override public void setScaleType(ScaleType ignored) {
        super.setScaleType(ScaleType.MATRIX);
        updateMatrix();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateMatrix();
    }

    @Override public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        post(this::updateMatrix);
    }

    private void updateMatrix() {
        Drawable drawable = getDrawable();
        int viewW = getWidth() - getPaddingLeft() - getPaddingRight();
        int viewH = getHeight() - getPaddingTop() - getPaddingBottom();
        if (drawable == null || viewW <= 0 || viewH <= 0
                || drawable.getIntrinsicWidth() <= 0
                || drawable.getIntrinsicHeight() <= 0) {
            return;
        }

        float sourceW = drawable.getIntrinsicWidth();
        float sourceH = drawable.getIntrinsicHeight();
        float coverScale = Math.max(viewW / sourceW, viewH / sourceH);
        float fitScale = Math.min(viewW / sourceW, viewH / sourceH);
        boolean landscape = viewW > viewH;
        float scale = landscape
                ? Math.min(coverScale, fitScale * maxLandscapeZoomFromFit)
                : coverScale;

        float scaledW = sourceW * scale;
        float scaledH = sourceH * scale;
        float tx = translationForAxis(viewW, scaledW, focalX);
        float ty = translationForAxis(viewH, scaledH, focalY);

        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate(tx + getPaddingLeft(), ty + getPaddingTop());
        setImageMatrix(matrix);
    }

    private static float translationForAxis(
            float viewSize, float scaledSize, float focal) {
        if (scaledSize <= viewSize) {
            return (viewSize - scaledSize) * .5f;
        }
        float desired = viewSize * .5f - scaledSize * focal;
        float minimum = viewSize - scaledSize;
        return Math.max(minimum, Math.min(0f, desired));
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
