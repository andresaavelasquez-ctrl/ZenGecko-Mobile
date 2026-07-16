package com.andres.zengecko;

import android.content.Context;
import android.graphics.Rect;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

/**
 * Editor de dirección que mantiene el cursor, las manijas de selección,
 * la barra flotante de Android y las acciones del portapapeles.
 */
public final class ZenAddressEditText extends EditText {
    public interface SelectionListener {
        void onSelectionChanged(int start, int end);
    }

    private SelectionListener selectionListener;

    public ZenAddressEditText(Context context) {
        super(context);
        setSingleLine(true);
        setTextIsSelectable(true);
        setLongClickable(true);
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setCursorVisible(true);
        setSelectAllOnFocus(false);
        setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        setShowSoftInputOnFocus(true);
        setHapticFeedbackEnabled(true);
    }

    public void setSelectionListener(SelectionListener listener) {
        selectionListener = listener;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        ViewParent parent = getParent();
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
            parent = parent.getParent();
        }
        return super.onTouchEvent(event);
    }

    @Override public boolean performLongClick() {
        requestFocus();
        setCursorVisible(true);
        return super.performLongClick();
    }

    @Override protected void onSelectionChanged(int start, int end) {
        super.onSelectionChanged(start, end);
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(start, end);
        }
    }

    @Override protected void onFocusChanged(
            boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (focused) setCursorVisible(true);
    }
}
