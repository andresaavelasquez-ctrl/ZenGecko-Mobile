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
import android.widget.FrameLayout;
import android.widget.ImageView;
import org.mozilla.geckoview.GeckoView;

/** Coherent glass material. It never captures, duplicates or blurs GeckoView. */
public final class ZenLiquidGlass {
    public static final String STYLE_SOLID="SOLID", STYLE_LIQUID_GLASS="LIQUID_GLASS";
    public static final String QUALITY_FULL="FULL", QUALITY_REDUCED="REDUCED", QUALITY_FALLBACK="FALLBACK";
    public static final int ROLE_PANEL=1,ROLE_TOOLBAR=2,ROLE_SEARCH=3,ROLE_CARD=4,ROLE_SELECTED=5,ROLE_BRAND=6;
    private static final String PREFS="zen_ui_prefs";
    public static final String KEY_STYLE="surface_style", KEY_INTENSITY="liquid_glass_intensity", KEY_REDUCE_EFFECTS="liquid_glass_reduce_effects";
    private ZenLiquidGlass(){}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    public static boolean isEnabled(Context c){return c!=null&&STYLE_LIQUID_GLASS.equals(prefs(c).getString(KEY_STYLE,STYLE_SOLID));}
    public static int intensity(Context c){return Math.max(0,Math.min(100,prefs(c).getInt(KEY_INTENSITY,52)));}
    public static boolean reduceEffects(Context c){return prefs(c).getBoolean(KEY_REDUCE_EFFECTS,false)||ZenPanelController.renderDelayMs(c)>16L;}
    public static boolean supportsRealBlur(){return Build.VERSION.SDK_INT>=31;}
    public static String quality(Context c){return !supportsRealBlur()?QUALITY_FALLBACK:reduceEffects(c)?QUALITY_REDUCED:QUALITY_FULL;}
    public static float blurRadius(Context c){return Math.max(4f,Math.min(18f,6f+intensity(c)*.12f));}
    public static void applySurface(Context c,View v,int solid,int day,int night){int role=ROLE_PANEL;if(day==R.drawable.zen_glass_toolbar_day)role=ROLE_TOOLBAR;else if(day==R.drawable.zen_glass_search_day)role=ROLE_SEARCH;apply(c,v,solid,role,false);}
    public static void applyGenericSurface(Context c,View v,int solid){apply(c,v,solid,ROLE_CARD,false);}
    public static void applySelectableSurface(Context c,View v,int solid,boolean selected){apply(c,v,solid,selected?ROLE_SELECTED:ROLE_CARD,selected);}
    public static void applyInputSurface(Context c,View v,int solid){apply(c,v,solid,ROLE_SEARCH,false);}
    public static void applyBrandSurface(Context c,View v,int solid){apply(c,v,solid,ROLE_BRAND,false);}
    private static void apply(Context c,View v,int solid,int role,boolean selected){
        if(v==null)return;v.animate().cancel();if(Build.VERSION.SDK_INT>=31)v.setRenderEffect(null);
        if(!isEnabled(c)){v.setBackgroundResource(solid);v.setClipToOutline(false);v.setElevation(0f);return;}
        v.setBackground(material(c,role,selected));v.setClipToOutline(true);
        float e=role==ROLE_PANEL?dp(c,7):role==ROLE_SEARCH?dp(c,5):role==ROLE_TOOLBAR?dp(c,3):dp(c,selected?3:1.5f);
        v.setElevation(reduceEffects(c)?e*.55f:e);
    }
    private static LayerDrawable material(Context c,int role,boolean selected){
        boolean day=ZenTheme.isDay(c);int p=intensity(c);float strength=.72f+p/100f*.18f;
        int alpha=Math.round((day?188:168)*strength);if(role==ROLE_PANEL)alpha+=8;if(role==ROLE_SEARCH)alpha+=5;if(selected)alpha+=10;alpha=Math.min(day?214:190,alpha);
        int top,bottom,border,shine,inner;
        if(day){top=Color.argb(alpha,252,251,255);bottom=Color.argb(Math.max(142,alpha-24),238,235,246);border=Color.argb(selected?188:92,selected?145:196,selected?94:185,selected?242:220);shine=Color.argb(118,255,255,255);inner=Color.argb(30,174,145,224);}
        else{top=Color.argb(alpha,12,10,18);bottom=Color.argb(Math.max(132,alpha-20),3,3,7);border=Color.argb(selected?194:82,selected?181:155,selected?111:129,255);shine=Color.argb(42,255,255,255);inner=Color.argb(24,137,91,220);}
        float radius=dp(c,role==ROLE_PANEL?27:role==ROLE_SEARCH?23:role==ROLE_TOOLBAR?20:role==ROLE_BRAND?19:17);
        GradientDrawable base=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{top,bottom});base.setCornerRadius(radius);base.setStroke(Math.max(1,Math.round(dp(c,selected?1.15f:.65f))),border);
        GradientDrawable reflection=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{shine,Color.argb(day?20:8,255,255,255),Color.TRANSPARENT});reflection.setCornerRadius(radius);
        GradientDrawable glow=new GradientDrawable();glow.setColor(Color.TRANSPARENT);glow.setCornerRadius(radius-dp(c,1));glow.setStroke(Math.max(1,Math.round(dp(c,.45f))),inner);
        LayerDrawable out=new LayerDrawable(new Drawable[]{base,reflection,glow});int inset=Math.max(1,Math.round(dp(c,1)));out.setLayerInset(1,inset,inset,inset,Math.round(radius*.62f));out.setLayerInset(2,inset,inset,inset,inset);return out;
    }
    public static ImageView installCapturedBackdrop(Activity a,FrameLayout host,GeckoView view){
        if(host!=null)for(int i=host.getChildCount()-1;i>=0;i--){View child=host.getChildAt(i);if("zen-liquid-backdrop".equals(child.getTag()))host.removeViewAt(i);}return null;
    }
    private static float dp(Context c,float v){return v*c.getResources().getDisplayMetrics().density;}
}
