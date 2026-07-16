package com.andres.zengecko;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

/** Center-crop ImageView with a controllable focal point for the bonsai. */
public final class ZenFocalImageView extends ImageView {
    private float focalX = .5f;
    private float focalY = .5f;

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
        focalX = Math.max(0f, Math.min(1f, x));
        focalY = Math.max(0f, Math.min(1f, y));
        updateMatrix();
    }

    @Override public void setScaleType(ScaleType scaleType) {
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
                || drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            return;
        }

        float sourceW = drawable.getIntrinsicWidth();
        float sourceH = drawable.getIntrinsicHeight();
        float scale = Math.max(viewW / sourceW, viewH / sourceH);
        float scaledW = sourceW * scale;
        float scaledH = sourceH * scale;

        float desiredX = viewW * .5f - scaledW * focalX;
        float desiredY = viewH * .5f - scaledH * focalY;
        float minX = viewW - scaledW;
        float minY = viewH - scaledH;
        float tx = Math.max(minX, Math.min(0f, desiredX));
        float ty = Math.max(minY, Math.min(0f, desiredY));

        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate(tx + getPaddingLeft(), ty + getPaddingTop());
        setImageMatrix(matrix);
    }
}
