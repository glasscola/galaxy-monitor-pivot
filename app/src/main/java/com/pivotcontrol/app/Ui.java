package com.pivotcontrol.app;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

final class Ui {
    static final int BLUE=0xff578dff;
    final int background,cardSurface,textColor,muted,accent,buttonSurface,onAccent,selection,outline,ripple;
    final Context c;
    final boolean dark;
    Ui(Context c){
        this.c=c;
        dark=(c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        background=dark?0xff172334:0xfff3f8ff;
        cardSurface=dark?0xff223247:0xffffffff;
        textColor=dark?0xffeef5ff:0xff142945;
        muted=dark?0xffafc2da:0xff4c627b;
        accent=dark?0xffa6ceff:0xff1756aa;
        buttonSurface=dark?0xff304562:0xffe4efff;
        onAccent=dark?0xff162942:0xffffffff;
        selection=dark?0xff29496f:0xffd7e8ff;
        outline=dark?0xff425972:0xffd3e0f0;
        ripple=dark?0x30ffffff:0x221756aa;
    }
    int dp(float v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
    LinearLayout column(){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
    LinearLayout row(){LinearLayout v=new LinearLayout(c);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    TextView text(String t,int sp,int color){TextView v=new TextView(c);v.setText(t);v.setTextSize(sp);v.setTextColor(color);v.setLineSpacing(dp(3),1);v.setFontFeatureSettings("kern");return v;}
    TextView title(String t){TextView v=text(t,18,textColor);v.setTypeface(null,Typeface.BOLD);return v;}
    Button button(String t,boolean selected){Button b=new Button(c);b.setText(t);b.setAllCaps(false);b.setTextSize(14);b.setMinHeight(dp(50));b.setMinimumHeight(dp(50));b.setPadding(dp(12),dp(10),dp(12),dp(10));b.setTypeface(null,Typeface.BOLD);style(b,selected);return b;}
    Button compactButton(String text,boolean selected){
        Button button=button(text,selected);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setPadding(dp(6),0,dp(6),0);
        if(!selected)button.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple),shape(0x00000000,10),shape(0xffffffff,10)));
        return button;
    }
    void style(Button b,boolean on){if(Boolean.valueOf(on).equals(b.getTag()))return;b.setTag(on);b.setTextColor(on?onAccent:textColor);b.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple),shape(on?accent:buttonSurface,12),null));b.setSelected(on);b.setStateDescription(null);}
    static void update(TextView v,String text){if(!v.getText().toString().equals(text))v.setText(text);}
    void add(LinearLayout p,View v,int top){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(top);p.addView(v,lp);}
    void weight(LinearLayout p,View v){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(p.getChildCount()>0)lp.leftMargin=dp(8);p.addView(v,lp);}
    LinearLayout card(LinearLayout parent,String title,String help){
        LinearLayout view=column();
        view.setPadding(dp(16),dp(16),dp(16),dp(16));
        GradientDrawable surface=shape(cardSurface,16);
        surface.setStroke(dp(1),outline);
        view.setBackground(surface);
        add(parent,view,14);
        view.addView(title(title));
        if(help!=null)add(view,text(help,14,muted),6);
        return view;
    }
}
