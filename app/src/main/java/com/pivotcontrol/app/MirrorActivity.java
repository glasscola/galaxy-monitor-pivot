package com.pivotcontrol.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.NotificationChannel;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.hardware.display.DisplayManager;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;

/** Default experience for Galaxy S20 wired mirroring, without DeX or Shizuku. */
public class MirrorActivity extends Activity implements DisplayManager.DisplayListener {
    private final int BG=0xff0d111b,CARD=0xff191f2e,TEXT=0xfff6f4ff,MUTED=0xffa4adc1,ACCENT=0xffb4a4ff;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ArrayList<Display> screens=new ArrayList<>();
    private DisplayManager dm; private SharedPreferences prefs;
    private Spinner picker;
    private TextView state,displayLabel,directionLabel,sessionBadge,notificationHint;
    private View notificationSettings;
    private MonitorPreview monitorPreview;
    private Button portraitButton,landscapeButton,startButton,stopButton;
    private Switch reverseSwitch,fillSwitch;
    private boolean desiredPortrait=true, awaitingConsent,starting,restoringUi,awaitingConfig;
    private long configDeadline;
    private String idleMessage;
    private int selectedId=-1,consentDisplay=-1;
    private final Runnable tick=new Runnable() { @Override public void run() { refreshState(); ui.postDelayed(this,700); } };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); dm=(DisplayManager)getSystemService(DISPLAY_SERVICE); prefs=getSharedPreferences("mirror",MODE_PRIVATE);
        desiredPortrait=prefs.getBoolean("portrait",true);
        if (saved!=null) { awaitingConsent=saved.getBoolean("awaiting"); consentDisplay=saved.getInt("consentDisplay",-1); }
        makeUi(); dm.registerDisplayListener(this,ui); refreshDisplays();
    }
    @Override protected void onSaveInstanceState(Bundle b) { super.onSaveInstanceState(b); b.putBoolean("awaiting",awaitingConsent); b.putInt("consentDisplay",consentDisplay); }
    @Override protected void onResume() { super.onResume(); refreshDisplays(); ui.removeCallbacks(tick); ui.post(tick); }
    @Override protected void onPause() { super.onPause(); ui.removeCallbacks(tick); }
    @Override protected void onDestroy() { dm.unregisterDisplayListener(this); ui.removeCallbacksAndMessages(null); super.onDestroy(); }
    @Override public void onDisplayAdded(int id) { refreshDisplays(); }
    @Override public void onDisplayRemoved(int id) { refreshDisplays(); }
    @Override public void onDisplayChanged(int id) { refreshState(); }

    private void makeUi() {
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout page=column(); page.setPadding(dp(22),dp(22),dp(22),dp(24)); scroll.addView(page);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{ android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()); v.setPadding(bars.left,bars.top,bars.right,bars.bottom); return insets; });
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); page.addView(header);
        TextView title=text("모니터 피벗",28,TEXT); title.setTypeface(null,Typeface.BOLD); header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        sessionBadge=text("연결 대기",12,MUTED); sessionBadge.setPadding(dp(10),dp(6),dp(10),dp(6)); sessionBadge.setBackground(shape(0xff252d40,20)); header.addView(sessionBadge);
        add(page,text("화면 방향, 알림창에서 간편하게",14,MUTED),7,22);

        LinearLayout screen=card(); page.addView(screen);
        screen.addView(text("외부 모니터",12,MUTED));
        displayLabel=text("모니터 연결 대기",17,TEXT); displayLabel.setTypeface(null,Typeface.BOLD); add(screen,displayLabel,6,0);
        picker=new Spinner(this); add(screen,picker,8,0);
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent,View v,int pos,long id) { if (pos<screens.size()) selectedId=screens.get(pos).getDisplayId(); refreshState(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        monitorPreview=new MonitorPreview(); screen.addView(monitorPreview,new LinearLayout.LayoutParams(-1,dp(118)));
        directionLabel=text("세로 · 선택됨",13,ACCENT); directionLabel.setGravity(Gravity.CENTER); screen.addView(directionLabel);

        TextView directionTitle=text("화면 방향",16,TEXT); directionTitle.setTypeface(null,Typeface.BOLD); add(page,directionTitle,22,10);
        LinearLayout row=new LinearLayout(this); page.addView(row);
        portraitButton=button("세로",false); landscapeButton=button("가로",false);
        portraitButton.setTypeface(null,Typeface.BOLD); landscapeButton.setTypeface(null,Typeface.BOLD);
        LinearLayout.LayoutParams first=new LinearLayout.LayoutParams(0,dp(92),1); first.rightMargin=dp(6); row.addView(portraitButton,first);
        LinearLayout.LayoutParams second=new LinearLayout.LayoutParams(0,dp(92),1); second.leftMargin=dp(6); row.addView(landscapeButton,second);
        portraitButton.setOnClickListener(v->choose(true)); landscapeButton.setOnClickListener(v->choose(false));
        state=text("모니터를 연결하고 시작해 주세요.",13,MUTED); state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); add(page,state,10,0);
        startButton=button("화면 복제 시작",true); add(page,startButton,16,0); startButton.setOnClickListener(v->begin());
        stopButton=button("화면 복제 종료",false); add(page,stopButton,16,0); stopButton.setOnClickListener(v->stop());

        LinearLayout settings=card(); add(page,settings,22,0);
        reverseSwitch=setting(settings,"반대 방향","위아래가 뒤집혀 보일 때",prefs.getBoolean("reverse",false));
        View divider=new View(this); divider.setBackgroundColor(0xff2b3345); LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(-1,dp(1)); line.topMargin=dp(10); line.bottomMargin=dp(10); settings.addView(divider,line);
        fillSwitch=setting(settings,"화면 꽉 채우기","가장자리가 일부 잘릴 수 있어요",prefs.getBoolean("fill",false));
        reverseSwitch.setOnCheckedChangeListener((v,on)->{ if (!restoringUi) updateOptions(); });
        fillSwitch.setOnCheckedChangeListener((v,on)->{ if (!restoringUi) updateOptions(); });

        LinearLayout info=card(); add(page,info,12,0);
        TextView heading=text("알림에서 바로 전환",15,TEXT); heading.setTypeface(null,Typeface.BOLD); info.addView(heading);
        notificationHint=text("복제를 시작한 뒤 알림창을 내려\n세로 · 가로 버튼을 눌러 보세요.",13,MUTED); add(info,notificationHint,6,0);
        notificationSettings=button("알림 표시 켜기",false); add(info,notificationSettings,10,0); notificationSettings.setOnClickListener(v->openNotificationSettings());
        Button more=button("사용 방법 · 더 보기",false); add(page,more,16,0); more.setOnClickListener(v->more());
        TextView footer=text("PIVOT 1.2  ·  화면을 저장하거나 전송하지 않아요",11,MUTED); footer.setGravity(Gravity.CENTER); add(page,footer,16,0);
        renderDirection();
        setContentView(scroll);
    }
    private void refreshDisplays() {
        screens.clear(); ArrayList<String> names=new ArrayList<>(); int chosen=0;
        for (Display d:dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)) {
            if (d.getDisplayId()==Display.DEFAULT_DISPLAY||(d.getFlags()&Display.FLAG_PRIVATE)!=0) continue;
            screens.add(d); names.add(d.getName()+" · #"+d.getDisplayId());
            if (d.getDisplayId()==(MirrorService.running?MirrorService.outputDisplay:selectedId)) chosen=screens.size()-1;
        }
        if (screens.isEmpty()) { selectedId=-1; names.add("외부 모니터 연결 대기"); }
        else selectedId=screens.get(chosen).getDisplayId();
        picker.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,names)); picker.setSelection(chosen); picker.setVisibility(screens.size()>1?View.VISIBLE:View.GONE); refreshState();
    }
    private void refreshState() {
        if (state==null) return;
        if (MirrorService.running) {
            starting=false;
            if (awaitingConfig && ((MirrorService.portrait==desiredPortrait&&MirrorService.reversed==reverseSwitch.isChecked()&&MirrorService.fill==fillSwitch.isChecked())||SystemClock.uptimeMillis()>=configDeadline)) awaitingConfig=false;
            if (!awaitingConfig) {
                desiredPortrait=MirrorService.portrait; restoringUi=true;
                reverseSwitch.setChecked(MirrorService.reversed); fillSwitch.setChecked(MirrorService.fill); restoringUi=false;
            }
        }
        Display d=dm.getDisplay(selectedId);
        boolean available=d!=null,active=MirrorService.running,enabled=available&&!awaitingConsent&&!starting;
        portraitButton.setEnabled(enabled); landscapeButton.setEnabled(enabled); startButton.setEnabled(enabled&&!active);
        stopButton.setEnabled(active||starting); picker.setEnabled(!active&&!awaitingConsent&&!starting);
        startButton.setVisibility(active||starting?View.GONE:View.VISIBLE); stopButton.setVisibility(active||starting?View.VISIBLE:View.GONE);
        portraitButton.setAlpha(enabled?1f:.45f); landscapeButton.setAlpha(enabled?1f:.45f);
        renderDirection();
        if (available) displayLabel.setText(d.getName()); else displayLabel.setText("모니터 연결 대기");
        if (active) state.setText(awaitingConfig?"화면 방향을 바꾸고 있어요…":"알림에서도 방향을 바로 바꿀 수 있어요.");
        else if (awaitingConsent) state.setText("화면 공유 안내에서 시작을 눌러 주세요.");
        else if (starting) state.setText("모니터 화면을 준비하고 있어요…");
        else if (!available) state.setText("영상 출력 가능한 케이블로 모니터를 연결해 주세요.");
        else if (idleMessage!=null) state.setText(idleMessage);
        else if (MirrorService.status.startsWith("화면 복제 실패")) state.setText(MirrorService.status);
        else state.setText("모니터를 원하는 방향으로 돌린 뒤 시작하세요.");
        sessionBadge.setText(active?"● 공유 중":starting||awaitingConsent?"준비 중":available?"연결됨":"연결 대기");
        sessionBadge.setTextColor(active?0xff81e5ca:MUTED); sessionBadge.setBackground(shape(active?0xff183a37:0xff252d40,20));
        startButton.setText(awaitingConsent?"화면 공유 승인 대기…":"화면 복제 시작");
        boolean notifications=notificationsEnabled(); notificationSettings.setVisibility(notifications?View.GONE:View.VISIBLE);
        notificationHint.setText(notifications?"알림창을 내려 세로 · 가로를 누르면\n앱을 열지 않고 바로 전환돼요.":"알림이 꺼져 있어요. 알림 표시를 켜면\n앱 밖에서도 바로 전환할 수 있어요.");
    }
    private void choose(boolean isPortrait) { desiredPortrait=isPortrait; updateOptions(); if (!MirrorService.running) begin(); }
    private Intent options(String action) { return new Intent(this,MirrorService.class).setAction(action).putExtra("portrait",desiredPortrait).putExtra("reverse",reverseSwitch.isChecked()).putExtra("fill",fillSwitch.isChecked()); }
    private void updateOptions() {
        prefs.edit().putBoolean("portrait",desiredPortrait).putBoolean("reverse",reverseSwitch.isChecked()).putBoolean("fill",fillSwitch.isChecked()).apply();
        if (MirrorService.running) {
            awaitingConfig=true; configDeadline=SystemClock.uptimeMillis()+1500;
            startService(options(MirrorService.CONFIG));
        }
        renderDirection();
    }
    private void begin() {
        if (awaitingConsent||starting||MirrorService.running) return;
        idleMessage=null;
        Display d=dm.getDisplay(selectedId); if (d==null) { dialog("영상 출력 가능한 케이블로 모니터를 연결해 주세요."); return; }
        if (Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},203); return;
        }
        requestCapture();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);
        if (code==203) requestCapture();
    }
    private void requestCapture() {
        Display d=dm.getDisplay(selectedId); if (d==null||awaitingConsent) return;
        consentDisplay=selectedId;
        MediaProjectionManager manager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent intent=Build.VERSION.SDK_INT>=34?manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()):manager.createScreenCaptureIntent();
        awaitingConsent=true; refreshState(); startActivityForResult(intent,204);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request!=204) return; awaitingConsent=false;
        if (result!=RESULT_OK||data==null) { idleMessage="화면 공유를 취소했어요. 다시 시작할 수 있어요."; refreshState(); return; }
        Display external=dm.getDisplay(consentDisplay);
        if (external==null) { dialog("승인 중 모니터 연결이 해제되었습니다. 다시 연결해 주세요."); refreshDisplays(); return; }
        try {
            starting=true; MirrorService.status="모니터 화면을 준비하고 있습니다…";
            startForegroundService(options(MirrorService.START).putExtra("display",consentDisplay).putExtra("result",result).putExtra("grant",data));
            ui.postDelayed(()->{ if (starting&&!MirrorService.running) { starting=false; state.setText(MirrorService.status); refreshState(); } },10000);
            refreshState();
        } catch (Exception e) { starting=false; dialog("화면 복제 시작 실패: "+e.getMessage()); refreshState(); }
    }
    private void stop() { stopService(new Intent(this,MirrorService.class)); starting=false; awaitingConfig=false; idleMessage="종료했어요. 기본 미러링으로 돌아갑니다."; MirrorService.status=idleMessage; ui.postDelayed(this::refreshState,250); }
    private boolean notificationsEnabled() {
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel=manager.getNotificationChannel("mirror");
        return manager.areNotificationsEnabled()&&(channel==null||channel.getImportance()!=NotificationManager.IMPORTANCE_NONE);
    }
    private void openNotificationSettings() {
        try { startActivity(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID,"mirror")); }
        catch (Exception e) { startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName())); }
    }
    private void more() {
        new AlertDialog.Builder(this).setTitle("더 보기").setItems(new String[]{"사용 방법","알림 표시 설정","고급: 시스템 회전 방식"},(dialog,which)->{
            if (which==0) help();
            else if (which==1) openNotificationSettings();
            else if (MirrorService.running||starting) dialog("화면 복제를 먼저 종료해 주세요.");
            else startActivity(new Intent(this,MainActivity.class));
        }).setNegativeButton("닫기",null).show();
    }
    private void renderDirection() {
        if (portraitButton==null) return;
        styleDirection(portraitButton,desiredPortrait,true); styleDirection(landscapeButton,!desiredPortrait,false);
        directionLabel.setText((desiredPortrait?"세로":"가로")+" · "+(MirrorService.running?"출력 중":"선택됨"));
        monitorPreview.invalidate();
    }
    private void styleDirection(Button b,boolean selected,boolean vertical) {
        if (Boolean.valueOf(selected).equals(b.getTag())) return;
        b.setTag(selected); b.setSelected(selected); b.setStateDescription(selected?"선택됨":"선택 안 됨");
        String label=(vertical?"세로":"가로")+"\n"+(selected?"✓ 선택됨":vertical?"세워서 보기":"눕혀서 보기");
        SpannableString title=new SpannableString(label); title.setSpan(new AbsoluteSizeSpan(22,true),0,2,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        title.setSpan(new AbsoluteSizeSpan(12,true),3,label.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.setText(title); b.setTextColor(selected?0xff17132c:MUTED);
        GradientDrawable bg=shape(selected?ACCENT:0xff1b2334,18);
        if (!selected) bg.setStroke(dp(1),0xff333d53);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(selected?0x2817132c:0x30b4a4ff),bg,null));
    }
    private Switch setting(LinearLayout parent,String title,String subtitle,boolean checked) {
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setMinimumHeight(dp(50)); parent.addView(row);
        LinearLayout labels=column(); row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        TextView heading=text(title,14,TEXT); heading.setTypeface(null,Typeface.BOLD); labels.addView(heading); add(labels,text(subtitle,12,MUTED),3,0);
        Switch toggle=new Switch(this); toggle.setShowText(false); toggle.setMinHeight(dp(48)); toggle.setChecked(checked); toggle.setContentDescription(title+". "+subtitle);
        toggle.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{ACCENT,0xff9da9bf}));
        toggle.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xff534b7a,0xff343e52}));
        row.addView(toggle); row.setOnClickListener(v->toggle.setChecked(!toggle.isChecked())); return toggle;
    }
    private void help() {
        new AlertDialog.Builder(this).setTitle("S20 화면 복제용 사용 방법")
            .setMessage("1. DeX를 켜지 않은 상태에서 영상 출력 지원 USB-C 케이블로 모니터를 연결합니다.\n\n2. 모니터 본체를 세로 또는 가로로 돌리고, 같은 방향의 버튼을 누릅니다.\n\n3. Android의 화면 공유 안내에서 ‘시작’을 허용합니다. 전체 화면을 공유하면 앱을 바꿔도 모니터에 표시됩니다.\n\n4. 휴대폰 홈으로 나가 유튜브나 원하는 앱을 엽니다. 알림에서 방향을 바꾸거나 종료할 수 있습니다.\n\n• 방향이 거꾸로면 ‘반대 방향으로 회전’을 켜세요.\n• ‘화면 꽉 채우기’는 화면 비율을 유지하며 가장자리를 잘라 빈 공간을 줄입니다. 휴대폰과 모니터의 비율이 다르면 여백이나 잘림이 생깁니다.\n• 보호된 영상과 보안 화면은 검게 표시될 수 있습니다. 소리는 휴대폰의 현재 출력 경로를 사용합니다.\n• 화면을 저장하거나 인터넷으로 보내지 않습니다. 알림과 개인정보도 전체 화면 공유에 포함될 수 있습니다.\n• 목록에 모니터가 없거나 검은 화면만 나오면 이 연결 방식에서 별도 화면 출력을 지원하지 않을 수 있습니다.\n• 종료하면 회전 출력이 사라지고 기존 미러링으로 돌아갑니다.")
            .setPositiveButton("확인",null).show();
    }
    private void dialog(String message) { if (!isFinishing()) new AlertDialog.Builder(this).setTitle("확인이 필요해요").setMessage(message).setPositiveButton("확인",null).show(); }
    private int dp(int v) { return Math.round(v*getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); return c; }
    private LinearLayout card() { LinearLayout c=column(); c.setPadding(dp(18),dp(16),dp(18),dp(16)); c.setBackground(shape(CARD,20)); return c; }
    private GradientDrawable shape(int color,int radius) { GradientDrawable s=new GradientDrawable(); s.setColor(color); s.setCornerRadius(dp(radius)); return s; }
    private TextView text(String content,int size,int color) { TextView t=new TextView(this); t.setText(content); t.setTextSize(size); t.setTextColor(color); t.setLineSpacing(dp(3),1); return t; }
    private Button button(String label,boolean primary) { Button b=new Button(this); b.setText(label); b.setTextSize(14); b.setAllCaps(false); b.setMinHeight(dp(50)); b.setTextColor(primary?BG:ACCENT); b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x30ffffff),shape(primary?ACCENT:0xff252d40,14),null)); return b; }
    private void add(LinearLayout parent,View child,int top,int bottom) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(top); p.bottomMargin=dp(bottom); parent.addView(child,p); }
    private class MonitorPreview extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        MonitorPreview() { super(MirrorActivity.this); setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas c) {
            float w=dp(desiredPortrait?54:112),h=dp(desiredPortrait?82:66),x=(getWidth()-w)/2,y=dp(12);
            paint.setColor(0xff252e45); paint.setStyle(Paint.Style.FILL); c.drawRoundRect(new RectF(x,y,x+w,y+h),dp(8),dp(8),paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2)); paint.setColor(ACCENT); c.drawRoundRect(new RectF(x,y,x+w,y+h),dp(8),dp(8),paint); paint.setStyle(Paint.Style.FILL);
            c.save(); if (reverseSwitch!=null&&reverseSwitch.isChecked()) c.rotate(180,x+w/2,y+h/2);
            paint.setColor(0xff81e5ca); c.drawRoundRect(new RectF(x+w*.24f,y+h*.25f,x+w*.76f,y+h*.48f),dp(3),dp(3),paint);
            paint.setColor(0xff737899); c.drawRoundRect(new RectF(x+w*.24f,y+h*.59f,x+w*.76f,y+h*.63f),dp(1),dp(1),paint);
            c.drawRoundRect(new RectF(x+w*.24f,y+h*.70f,x+w*.60f,y+h*.74f),dp(1),dp(1),paint); c.restore();
            paint.setColor(0xff7a729d); c.drawRoundRect(new RectF(x+w/2-dp(2),y+h,x+w/2+dp(2),y+h+dp(8)),dp(1),dp(1),paint);
            c.drawRoundRect(new RectF(x+w/2-dp(17),y+h+dp(8),x+w/2+dp(17),y+h+dp(10)),dp(2),dp(2),paint);
        }
    }
}
