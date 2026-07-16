package com.andres.zengecko;

import android.content.Context;
import android.graphics.Rect;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

/** Address editor that preserves Android's native selection/copy/paste ActionMode. */
public final class ZenAddressEditText extends EditText {
    public interface SelectionListener {
        void onSelectionChanged(int start, int end);
    }

    private SelectionListener selectionListener;

    public ZenAddressEditText(Context context) {
        super(context);
        setSingleLine(true);
        setLongClickable(true);
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setCursorVisible(true);
        setSelectAllOnFocus(false);
        setTextIsSelectable(true);
        setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_URI
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        setImeOptions(EditorInfo.IME_ACTION_SEARCH
                | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        setShowSoftInputOnFocus(true);
        setHapticFeedbackEnabled(true);
    }

    public void setSelectionListener(SelectionListener listener) {
        selectionListener = listener;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        ViewParent parent = getParent();
        if (parent != null) {
            int action = event.getActionMasked();
            boolean editingGesture = action == MotionEvent.ACTION_DOWN
                    || action == MotionEvent.ACTION_MOVE;
            parent.requestDisallowInterceptTouchEvent(editingGesture);
        }
        return super.onTouchEvent(event);
    }

    @Override protected void onSelectionChanged(int start, int end) {
        super.onSelectionChanged(start, end);
        if (selectionListener != null) selectionListener.onSelectionChanged(start, end);
    }

    @Override protected void onFocusChanged(
            boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (focused) setCursorVisible(true);
    }
}
