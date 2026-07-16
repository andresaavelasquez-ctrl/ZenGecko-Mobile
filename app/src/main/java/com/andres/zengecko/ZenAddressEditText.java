package com.andres.zengecko;

import android.content.Context;
import android.graphics.Rect;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

/** Address field that preserves Android's native text selection actions. */
public final class ZenAddressEditText extends EditText {
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
        return super.performLongClick();
    }

    @Override protected void onFocusChanged(
            boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (focused) setCursorVisible(true);
    }
}
