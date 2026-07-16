package com.andres.zengecko;

import android.content.Context;
import android.graphics.Rect;
import android.text.InputType;
import android.view.ActionMode;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

/** Native Android address editor: cursor, partial selection, copy, cut and paste. */
public final class ZenAddressEditText extends EditText {
    public interface SelectionListener { void onSelectionChanged(int start,int end); }
    private SelectionListener listener;
    public ZenAddressEditText(Context context){
        super(context);
        setSingleLine(true);setClickable(true);setLongClickable(true);setFocusable(true);setFocusableInTouchMode(true);setCursorVisible(true);
        setSelectAllOnFocus(false);setTextIsSelectable(false);
        setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        setImeOptions(EditorInfo.IME_ACTION_GO|EditorInfo.IME_FLAG_NO_EXTRACT_UI);setShowSoftInputOnFocus(true);setHapticFeedbackEnabled(true);
        setCustomSelectionActionModeCallback(new ActionMode.Callback(){public boolean onCreateActionMode(ActionMode m,android.view.Menu menu){return true;}public boolean onPrepareActionMode(ActionMode m,android.view.Menu menu){return false;}public boolean onActionItemClicked(ActionMode m,android.view.MenuItem item){return false;}public void onDestroyActionMode(ActionMode m){}});
    }
    public void setSelectionListener(SelectionListener l){listener=l;}
    @Override public boolean onTouchEvent(MotionEvent e){
        ViewParent p=getParent(); if(p!=null){int a=e.getActionMasked();p.requestDisallowInterceptTouchEvent(a!=MotionEvent.ACTION_UP&&a!=MotionEvent.ACTION_CANCEL);}return super.onTouchEvent(e);
    }
    @Override protected void onSelectionChanged(int s,int e){super.onSelectionChanged(s,e);if(listener!=null)listener.onSelectionChanged(s,e);}
    @Override protected void onFocusChanged(boolean f,int d,Rect r){super.onFocusChanged(f,d,r);if(f)setCursorVisible(true);}
}
