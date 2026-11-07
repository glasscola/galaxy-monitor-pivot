package com.pivotcontrol.app;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

final class Ui {
    static final int BG=0xff0d1320,CARD=0xff182233,TEXT=0xfff4f6fc,MUTED=0xffacb9d0,ACCENT=0xffb9acff,BLUE=0xff578dff;
    final Context c;Ui(Context c){this.c=c;}
    int dp(float v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
    LinearLayout column(){LinearLayout v=new LinearLayout(c);v.setOrientation(1);return v;}
    LinearLayout row(){LinearLayout v=new LinearLayout(c);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    TextView text(String t,int sp,int color){TextView v=new TextView(c);v.setText(t);v.setTextSize(sp);v.setTextColor(color);v.setLineSpacing(dp(3),1);v.setFontFeatureSettings("kern");return v;}
    TextView title(String t){TextView v=text(t,18,TEXT);v.setTypeface(null,Typeface.BOLD);return v;}
    Button button(String t,boolean selected){Button b=new Button(c);b.setText(t);b.setAllCaps(false);b.setTextSize(14);b.setMinHeight(dp(50));b.setMinimumHeight(dp(50));b.setPadding(dp(12),dp(10),dp(12),dp(10));b.setTypeface(null,Typeface.BOLD);style(b,selected);return b;}
    void style(Button b,boolean on){if(Boolean.valueOf(on).equals(b.getTag()))return;b.setTag(on);b.setTextColor(on?BG:TEXT);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x30ffffff),shape(on?ACCENT:0xff26344b,14),null));b.setSelected(on);b.setStateDescription(on?"선택됨":"선택 안 됨");}
    static void update(TextView v,String text){if(!v.getText().toString().equals(text))v.setText(text);}
    void add(LinearLayout p,View v,int top){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(top);p.addView(v,lp);}
    void weight(LinearLayout p,View v){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(p.getChildCount()>0)lp.leftMargin=dp(8);p.addView(v,lp);}
    LinearLayout card(LinearLayout parent,String title,String help){LinearLayout v=column();v.setPadding(dp(16),dp(16),dp(16),dp(16));v.setBackground(shape(CARD,20));add(parent,v,14);v.addView(title(title));if(help!=null)add(v,text(help,14,MUTED),6);return v;}
}
