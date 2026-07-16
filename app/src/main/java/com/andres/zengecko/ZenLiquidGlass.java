package com.andres.zengecko;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import org.mozilla.geckoview.GeckoView;

/** Stable, global Liquid Glass material. Never duplicates or blurs live GeckoView. */
public final class ZenLiquidGlass {
    public static final String STYLE_SOLID = "SOLID";
    public static final String STYLE_LIQUID_GLASS = "LIQUID_GLASS";
    public static final String QUALITY_FULL = "FULL";
    public static final String QUALITY_REDUCED = "REDUCED";
    public static final String QUALITY_FALLBACK = "FALLBACK";
    public static final int ROLE_PANEL=1, ROLE_TOOLBAR=2, ROLE_SEARCH=3, ROLE_CARD=4, ROLE_SELECTED=5;
    private static final String PREFS="zen_ui_prefs";
    public static final String KEY_STYLE="surface_style";
    public static final String KEY_INTENSITY="liquid_glass_intensity";
    public static final String KEY_REDUCE_EFFECTS="liquid_glass_reduce_effects";
    private ZenLiquidGlass() { }
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    public static boolean isEnabled(Context c){return c!=null&&STYLE_LIQUID_GLASS.equals(prefs(c).getString(KEY_STYLE,STYLE_SOLID));}
    public static int intensity(Context c){return Math.max(0,Math.min(100,prefs(c).getInt(KEY_INTENSITY,58)));}
    public static boolean reduceEffects(Context c){return prefs(c).getBoolean(KEY_REDUCE_EFFECTS,false)||ZenPanelController.renderDelayMs(c)>16L;}
    public static boolean supportsRealBlur(){return Build.VERSION.SDK_INT>=31;}
    public static String quality(Context c){return !supportsRealBlur()?QUALITY_FALLBACK:reduceEffects(c)?QUALITY_REDUCED:QUALITY_FULL;}
    public static float blurRadius(Context c){return Math.max(5f,Math.min(24f,7f+intensity(c)*.17f));}
    public static void applySurface(Context c,View v,int solid,int day,int night){int role=ROLE_PANEL;if(day==R.drawable.zen_glass_toolbar_day)role=ROLE_TOOLBAR;else if(day==R.drawable.zen_glass_search_day)role=ROLE_SEARCH;applyMaterial(c,v,solid,role,false);}
    public static void applyGenericSurface(Context c,View v,int solid){applyMaterial(c,v,solid,ROLE_CARD,false);}
    public static void applySelectableSurface(Context c,View v,int solid,boolean selected){applyMaterial(c,v,solid,selected?ROLE_SELECTED:ROLE_CARD,selected);}
    public static void applyInputSurface(Context c,View v,int solid){applyMaterial(c,v,solid,ROLE_SEARCH,false);}
    private static void applyMaterial(Context c,View v,int solid,int role,boolean selected){
        if(v==null)return; v.animate().cancel(); if(Build.VERSION.SDK_INT>=31)v.setRenderEffect(null);
        if(!isEnabled(c)){v.setBackgroundResource(solid);v.setClipToOutline(false);return;}
        v.setBackground(buildMaterial(c,role,selected));v.setClipToOutline(true);
        float e=role==ROLE_PANEL?dp(c,9):role==ROLE_SEARCH?dp(c,7):role==ROLE_TOOLBAR?dp(c,4):dp(c,selected?4:2);
        v.setElevation(reduceEffects(c)?e*.65f:e);
    }
    private static LayerDrawable buildMaterial(Context c,int role,boolean selected){
        boolean day=ZenTheme.isDay(c); int power=intensity(c);
        int alpha=day?178:154; alpha+=Math.round(power*.20f); if(role==ROLE_PANEL)alpha+=8; if(selected)alpha+=14; alpha=Math.min(day?220:198,alpha);
        int top,bottom,border,shine;
        if(day){top=Color.argb(alpha,255,255,255);bottom=Color.argb(Math.max(126,alpha-38),232,228,241);border=Color.argb(selected?196:112,selected?137:205,selected?91:195,selected?238:224);shine=Color.argb(136,255,255,255);}
        else{top=Color.argb(alpha,16,14,23);bottom=Color.argb(Math.max(112,alpha-30),5,5,9);border=Color.argb(selected?205:98,selected?178:198,selected?108:178,255);shine=Color.argb(52,255,255,255);}
        float radius=dp(c,role==ROLE_PANEL?28:role==ROLE_SEARCH?24:role==ROLE_TOOLBAR?20:18);
        GradientDrawable base=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{top,bottom});base.setCornerRadius(radius);base.setStroke(Math.max(1,Math.round(dp(c,selected?1.3f:.75f))),border);
        GradientDrawable reflection=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{shine,Color.argb(day?24:10,255,255,255),Color.TRANSPARENT});reflection.setCornerRadius(radius);
        LayerDrawable out=new LayerDrawable(new Drawable[]{base,reflection});int inset=Math.max(1,Math.round(dp(c,1)));out.setLayerInset(1,inset,inset,inset,Math.round(radius*.60f));return out;
    }
    /** v0.1.25: intentionally disabled to prevent duplicated, blurred web-page ghosts. */
    public static ImageView installCapturedBackdrop(Activity a,FrameLayout host,GeckoView view){
        if(host!=null){for(int i=host.getChildCount()-1;i>=0;i--){View child=host.getChildAt(i);Object tag=child.getTag();if("zen-liquid-backdrop".equals(tag)){host.removeViewAt(i);}}}
        return null;
    }
    private static float dp(Context c,float v){return v*c.getResources().getDisplayMetrics().density;}
}
