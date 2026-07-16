package com.andres.zengecko;

import android.content.Context;
import android.graphics.Rect;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

/**
 * Native Android omnibox editor.
 *
 * Uses only the Android framework EditText so the project does not require
 * AppCompat. Android handles cursor movement, double-tap selection,
 * selection handles and copy/cut/paste.
 */
public final class ZenOmniboxEditText extends EditText {
    public interface SelectionListener {
        void onSelectionChanged(int start, int end);
    }

    private SelectionListener selectionListener;

    public ZenOmniboxEditText(Context context) {
        super(context);
        configure();
    }

    public ZenOmniboxEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        configure();
    }

    public ZenOmniboxEditText(Context context, AttributeSet attrs, int style) {
        super(context, attrs, style);
        configure();
    }

    private void configure() {
        setSingleLine(true);
        setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_URI
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        setImeOptions(EditorInfo.IME_ACTION_GO
                | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        setTextIsSelectable(true);
        setLongClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setCursorVisible(true);
        setSelectAllOnFocus(false);
        setLinksClickable(false);
    }

    public void setSelectionListener(SelectionListener listener) {
        selectionListener = listener;
    }

    @Override
    protected void onSelectionChanged(int start, int end) {
        super.onSelectionChanged(start, end);
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(start, end);
        }
    }

    @Override
    protected void onFocusChanged(
            boolean focused,
            int direction,
            Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (focused) {
            setCursorVisible(true);
        }
    }
}
