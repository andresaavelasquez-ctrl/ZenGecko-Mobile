package com.andres.zengecko;

import android.content.Context;
import android.graphics.Rect;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import androidx.appcompat.widget.AppCompatEditText;

/** Native omnibox: Android owns selection, handles, long-click and clipboard. */
public final class ZenOmniboxEditText extends AppCompatEditText {
    public interface SelectionListener { void onSelectionChanged(int start, int end); }
    private SelectionListener selectionListener;

    public ZenOmniboxEditText(Context c) { super(c); configure(); }
    public ZenOmniboxEditText(Context c, AttributeSet a) { super(c,a); configure(); }
    public ZenOmniboxEditText(Context c, AttributeSet a, int s) { super(c,a,s); configure(); }

    private void configure() {
        setSingleLine(true);
        setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_URI
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        setTextIsSelectable(true);
        setLongClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setCursorVisible(true);
        setSelectAllOnFocus(false);
        setLinksClickable(false);
        setCustomSelectionActionModeCallback(null);
        setCustomInsertionActionModeCallback(null);
    }

    public void setSelectionListener(SelectionListener l) { selectionListener=l; }
    @Override protected void onSelectionChanged(int s,int e) {
        super.onSelectionChanged(s,e);
        if(selectionListener!=null)selectionListener.onSelectionChanged(s,e);
    }
    @Override protected void onFocusChanged(boolean f,int d,Rect r) {
        super.onFocusChanged(f,d,r); if(f)setCursorVisible(true);
    }
}
