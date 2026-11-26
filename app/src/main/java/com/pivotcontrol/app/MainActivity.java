package com.pivotcontrol.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
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
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity implements DisplayManager.DisplayListener {
    private final int BG=0xff10121b, CARD=0xff1b1e2b, TEXT=0xfff6f4ff, MUTED=0xffa7adbf, ACCENT=0xffa99bff;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ArrayList<Display> screens = new ArrayList<>();
    private final ConcurrentHashMap<Integer,String> identities = new ConcurrentHashMap<>();
    private int selectedId=-1, displayGeneration;
    private DisplayManager dm;
    private SharedPreferences prefs;
    private volatile IBinder remote;
    private boolean binding, busy, pending, closing, polling;
    private int pendingDisplay=-1, pendingTarget=-1;
    private String selectedUnique, pendingBefore;
    private TextView displayLine, permissionLine, message, pendingLine;
    private Button portrait, landscape, quarterTurn, restore, permission, confirm, cancel;
    private Spinner selector;
    private Switch reverse;
    private LinearLayout pendingBox, setup;
    private Shizuku.UserServiceArgs serviceArgs;
    private final Shizuku.OnBinderReceivedListener received = () -> ui.post(this::connectIfAllowed);
    private final Shizuku.OnBinderDeadListener dead = () -> ui.post(() -> { remote=null; binding=false; permissionLine.setText("Shizuku가 중지되었습니다. 다시 시작해 주세요."); updateControls(); });
    private final Shizuku.OnRequestPermissionResultListener granted = (request, result) -> ui.post(() -> {
        if (result == PackageManager.PERMISSION_GRANTED) connectIfAllowed();
        else { permissionLine.setText("권한이 필요합니다. Shizuku의 승인된 앱에서 모니터 피벗을 허용해 주세요."); updateControls(); }
    });
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            ui.post(() -> { if (closing) return; remote=binder; binding=false; permissionLine.setText("Shizuku 연결됨 · 회전 제어 준비 완료"); refreshDisplays(); });
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            ui.post(() -> { remote=null; binding=false; permissionLine.setText("제어 연결이 끊겼습니다. 다시 연결해 주세요."); updateControls(); });
        }
    };
    private final Runnable tick = new Runnable() {
        @Override public void run() { if (closing) return; poll(); ui.postDelayed(this, 1000); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        dm = (DisplayManager)getSystemService(DISPLAY_SERVICE);
        prefs = getSharedPreferences("pivot", MODE_PRIVATE);
        serviceArgs = new Shizuku.UserServiceArgs(new ComponentName(this, PivotService.class)).daemon(true).processNameSuffix("pivot").version(1);
        createUi();
        dm.registerDisplayListener(this, ui);
        Shizuku.addBinderReceivedListenerSticky(received);
        Shizuku.addBinderDeadListener(dead);
        Shizuku.addRequestPermissionResultListener(granted);
        refreshDisplays(); connectIfAllowed();
    }
    @Override protected void onResume() { super.onResume(); if (dm != null) { refreshDisplays(); connectIfAllowed(); ui.removeCallbacks(tick); ui.post(tick); } }
    @Override protected void onPause() { super.onPause(); ui.removeCallbacks(tick); }
    @Override protected void onDestroy() {
        closing=true; ui.removeCallbacksAndMessages(null); dm.unregisterDisplayListener(this);
        Shizuku.removeBinderReceivedListener(received); Shizuku.removeBinderDeadListener(dead); Shizuku.removeRequestPermissionResultListener(granted);
        try { if (Shizuku.pingBinder()) Shizuku.unbindUserService(serviceArgs, connection, false); } catch (Exception ignored) {}
        io.shutdown(); super.onDestroy();
    }
    @Override public void onDisplayAdded(int id) { refreshDisplays(); }
    @Override public void onDisplayRemoved(int id) { refreshDisplays(); }
    @Override public void onDisplayChanged(int id) { updateDisplay(); }

    private void createUi() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout page = column(); page.setPadding(dp(22),dp(26),dp(22),dp(26)); scroll.addView(page);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> { android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()); v.setPadding(bars.left,bars.top,bars.right,bars.bottom); return insets; });
        TextView title = text("모니터 피벗",30,TEXT); title.setTypeface(null,Typeface.BOLD); add(page,title,7,0);
        displayLine = text("모니터를 연결해 주세요",14,MUTED);
        add(page,displayLine,8,16);
        selector = new Spinner(this);
        selector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id) {
                if (position < screens.size()) { selectedId=screens.get(position).getDisplayId(); selectedUnique=identity(screens.get(position)); }
                pendingBefore = selectedUnique == null ? null : prefs.getString("pending:"+selectedUnique,null);
                updateDisplay();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); page.addView(row);
        portrait = bigButton("세로",false); landscape = bigButton("가로",false);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0,dp(64),1); half.rightMargin=dp(6); row.addView(landscape,half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0,dp(64),1); half2.leftMargin=dp(6); row.addView(portrait,half2);
        portrait.setContentDescription("외부 모니터를 세로 방향으로 전환"); landscape.setContentDescription("외부 모니터를 가로 방향으로 전환");
        portrait.setOnClickListener(v -> rotate(true)); landscape.setOnClickListener(v -> rotate(false));
        quarterTurn = smallButton("↻ 90° 회전");
        quarterTurn.setOnClickListener(v -> {
            Display display=selected();
            if(display!=null)applyRotation(display,(display.getRotation()+1)%4);
        });
        add(page,quarterTurn,8,0);
        reverse = new Switch(this); reverse.setText("반대 방향으로 회전"); reverse.setTextColor(MUTED); reverse.setTextSize(14); reverse.setMinHeight(dp(50));

        pendingBox = card(); add(page,pendingBox,12,0); pendingBox.setVisibility(View.GONE);
        pendingLine = text("화면 확인 중",15,TEXT); pendingBox.addView(pendingLine);
        confirm = smallButton("화면이 정상이에요 · 유지"); add(pendingBox,confirm,10,0); confirm.setOnClickListener(v -> keep());
        cancel = smallButton("이전 방향으로 되돌리기"); add(pendingBox,cancel,4,0); cancel.setOnClickListener(v -> restore(true));

        message = text("",14,MUTED); message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); add(page,message,12,8);

        setup = card(); page.addView(setup);
        TextView setupTitle=text("처음 한 번, 회전 권한 연결",16,TEXT); setupTitle.setTypeface(null,Typeface.BOLD); setup.addView(setupTitle);
        permissionLine = text("Shizuku 실행 상태를 확인하고 있습니다.",13,MUTED); add(setup,permissionLine,8,10);
        permission = smallButton("Shizuku 연결 / 권한 허용"); setup.addView(permission); permission.setOnClickListener(v -> requestConnection());
        Button help = smallButton("설치·연결 방법 보기"); add(setup,help,4,0); help.setOnClickListener(v -> help());
        add(page,selector,12,0);
        restore = smallButton("원래 방향 설정 복원"); add(page,restore,8,0); restore.setOnClickListener(v -> restore(false));
        add(page,reverse,4,0);
        Button mirror = smallButton("부분 출력·화면 공유");
        mirror.setOnClickListener(v -> startActivity(new Intent(this,MirrorActivity.class)));
        add(page,mirror,12,0);
        setContentView(scroll);
    }
    private void refreshDisplays() {
        screens.clear(); identities.clear(); int generation=++displayGeneration;
        ArrayList<String> names=new ArrayList<>(); int chosen=0;
        for (Display d : dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)) {
            if (d.getDisplayId()==Display.DEFAULT_DISPLAY || (d.getFlags() & Display.FLAG_PRIVATE)!=0) continue;
            screens.add(d); names.add(d.getName()+"  ·  #"+d.getDisplayId());
            if (d.getDisplayId()==selectedId) chosen=screens.size()-1;
        }
        if (screens.isEmpty()) { selectedUnique=null; names.add("외부 모니터 연결 대기"); }
        else { selectedId=screens.get(chosen).getDisplayId(); selectedUnique=null; }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names); selector.setAdapter(adapter); selector.setSelection(chosen);
        pendingBefore=selectedUnique==null ? null : prefs.getString("pending:"+selectedUnique,null);
        updateDisplay();
        if (remote!=null) {
            ArrayList<Display> found=new ArrayList<>(screens);
            io.execute(() -> {
                HashMap<Integer,String> described=new HashMap<>();
                for (Display d:found) { try { described.put(d.getDisplayId(),call(PivotService.DESCRIBE,d,0,null)); } catch (Exception ignored) {} }
                ui.post(() -> {
                    if (closing||generation!=displayGeneration) return;
                    identities.putAll(described); Display d=selected(); selectedUnique=d==null?null:identity(d);
                    pendingBefore=selectedUnique==null?null:prefs.getString("pending:"+selectedUnique,null);
                    if (d!=null&&selectedUnique==null) message.setText("이 외부 화면에서 직접 회전을 사용할 수 없습니다.");
                    updateDisplay(); poll();
                });
            });
        }
    }
    private String identity(Display d) { synchronized (identities) { return identities.get(d.getDisplayId()); } }
    private Display selected() {
        for (Display d : screens) if (d.getDisplayId()==selectedId) return dm.getDisplay(d.getDisplayId());
        return null;
    }
    private void updateDisplay() {
        Display d=selected();
        if (d==null) { displayLine.setText("모니터를 연결해 주세요"); }
        else {
            android.graphics.Point size=new android.graphics.Point(); d.getRealSize(size);
            displayLine.setText(d.getName()+" · "+(size.y>size.x?"세로":"가로"));
        }
        updateControls();
    }
    private void updateControls() {
        Display d=selected();
        boolean connected=remote!=null&&remote.isBinderAlive();
        boolean ready=connected && d!=null && selectedUnique!=null && !busy && !pending && !MirrorService.running;
        portrait.setEnabled(ready); landscape.setEnabled(ready); reverse.setEnabled(!busy&&!pending);
        quarterTurn.setEnabled(ready);
        setup.setVisibility(connected?View.GONE:View.VISIBLE);
        selector.setVisibility(screens.size()>1?View.VISIBLE:View.GONE);
        reverse.setVisibility(connected&&selectedUnique!=null?View.VISIBLE:View.GONE);
        restore.setVisibility(selectedUnique!=null&&prefs.contains("original:"+selectedUnique)?View.VISIBLE:View.GONE);
        if(MirrorService.running)message.setText("화면 공유 중입니다. 직접 회전하려면 부분 출력·화면 공유에서 먼저 종료하세요.");
        portrait.setAlpha(ready?1f:.35f); landscape.setAlpha(ready?1f:.35f);
        restore.setEnabled(ready && prefs.contains("original:"+selectedUnique));
        selector.setEnabled(!busy&&!pending);
        permission.setEnabled(!binding && !busy); permission.setText(remote!=null?"제어 연결됨":binding?"연결 중…":"Shizuku 연결 / 권한 허용");
        pendingBox.setVisibility(pending?View.VISIBLE:View.GONE);
        confirm.setEnabled(pending&&!busy&&d!=null&&d.getDisplayId()==pendingDisplay&&d.getRotation()==pendingTarget);
        cancel.setEnabled(pending&&!busy&&d!=null&&pendingBefore!=null);
    }
    private void requestConnection() {
        try {
            if (!Shizuku.pingBinder()) { help(); return; }
            if (Shizuku.isPreV11()) { error("최신 Shizuku로 업데이트해 주세요."); return; }
            if (Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED) {
                if (Shizuku.shouldShowRequestPermissionRationale()) { error("Shizuku 앱 → 승인된 앱에서 모니터 피벗을 허용해 주세요."); return; }
                Shizuku.requestPermission(100);
            } else connectIfAllowed();
        } catch (Exception e) { error("Shizuku 연결 실패: "+e.getMessage()); }
    }
    private void connectIfAllowed() {
        if (closing||binding||remote!=null) return;
        try {
            if (!Shizuku.pingBinder()) { permissionLine.setText("Shizuku를 설치하고 시작해 주세요. 설정 방법은 아래에 있어요."); updateControls(); return; }
            if (Shizuku.isPreV11()||Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED) { permissionLine.setText("Shizuku가 실행 중입니다. 아래 버튼으로 권한을 허용해 주세요."); updateControls(); return; }
            binding=true; permissionLine.setText("회전 제어에 연결하고 있습니다…"); updateControls();
            Shizuku.bindUserService(serviceArgs,connection);
            ui.postDelayed(() -> { if (!closing&&binding&&remote==null) { binding=false; permissionLine.setText("연결이 지연됩니다. Shizuku 상태를 확인하고 다시 눌러 주세요."); updateControls(); } },10000);
        } catch (Exception e) { binding=false; permissionLine.setText("연결 실패: "+e.getMessage()); updateControls(); }
    }

    private String call(int code, Display d, int rotation, String state) throws Exception {
        return callFor(code,d,rotation,state,d==null?null:identity(d));
    }
    private String callFor(int code, Display d, int rotation, String state, String unique) throws Exception {
        IBinder service=remote; if (service==null||!service.isBinderAlive()) throw new Exception("Shizuku 연결이 필요합니다.");
        if (d==null||!d.isValid()) throw new Exception("모니터가 연결되어 있지 않습니다.");
        Parcel in=Parcel.obtain(), out=Parcel.obtain();
        try {
            if (code!=PivotService.DESCRIBE && unique==null) throw new Exception("외부 모니터 정보를 확인하고 있습니다.");
            in.writeInterfaceToken(PivotService.TOKEN); in.writeInt(d.getDisplayId()); in.writeString(unique);
            if (code==PivotService.APPLY) in.writeInt(rotation);
            if (code==PivotService.RESTORE) in.writeString(state);
            if (!service.transact(code,in,out,0)) throw new Exception("제어 요청 실패");
            out.readException(); String result=out.readString();
            if (result==null||!result.startsWith("OK|")) throw new Exception(result==null?"응답 없음":result.replaceFirst("^ERROR\\|",""));
            return result.substring(3);
        } finally { in.recycle(); out.recycle(); }
    }
    private void rotate(boolean portraitMode) {
        Display d=selected(); if (d==null) return;
        Display.Mode mode=d.getMode();
        int target=RotationPolicy.target(mode.getPhysicalHeight()>mode.getPhysicalWidth(),portraitMode,reverse.isChecked());
        applyRotation(d,target);
    }
    private void applyRotation(Display d,int target){
        if(busy||pending||MirrorService.running)return;
        String unique=identity(d);
        if(unique==null)return;
        busy=true; message.setText("외부 화면에 회전 명령을 보내고 있습니다…"); updateControls();
        io.execute(() -> {
            try {
                String before=callFor(PivotService.SNAPSHOT,d,0,null,unique);
                // Persist before changing anything, so restore survives app/server restarts.
                if (unique==null) throw new Exception("모니터 연결 정보가 바뀌었습니다.");
                SharedPreferences.Editor edit=prefs.edit().putString("pending:"+unique,before);
                if (!prefs.contains("original:"+unique)) edit.putString("original:"+unique,before);
                if (!edit.commit()) throw new Exception("복원 정보를 저장하지 못해 변경을 중단했습니다.");
                callFor(PivotService.APPLY,d,target,null,unique);
                ui.post(() -> { busy=false; pending=true; pendingDisplay=d.getDisplayId(); pendingTarget=target; pendingBefore=before; message.setText("출력 화면을 확인해 주세요. 확인하지 않으면 자동으로 되돌아갑니다."); updateDisplay(); poll(); });
            } catch (Exception e) { ui.post(() -> { busy=false; error(e.getMessage()); updateControls(); }); }
        });
    }
    private void keep() {
        Display d=selected(); if (d==null||busy) return; busy=true; updateControls();
        String unique=identity(d);
        io.execute(() -> {
            try {
                callFor(PivotService.KEEP,d,0,null,unique);
                prefs.edit().putBoolean("confirmed:"+unique,true).apply();
                ui.post(() -> { busy=false; pending=false; message.setText("선택한 화면 방향을 유지합니다."); updateDisplay(); });
            }
            catch (Exception e) { ui.post(() -> { busy=false; error(e.getMessage()); updateControls(); }); }
        });
    }
    private void restore(boolean previous) {
        Display d=selected(); if (d==null||busy) return;
        String unique=identity(d);
        String saved=previous?pendingBefore:prefs.getString("original:"+unique,null);
        if (saved==null) { error("저장된 복원 정보가 없습니다."); return; }
        busy=true; updateControls();
        io.execute(() -> {
            try {
                callFor(PivotService.RESTORE,d,0,saved,unique);
                if (!previous) prefs.edit().remove("original:"+unique).remove("pending:"+unique).remove("confirmed:"+unique).commit();
                ui.post(() -> { busy=false; pending=false; message.setText(previous?"이전 방향으로 되돌렸습니다.":"처음 연결했을 때의 회전 설정으로 복원했습니다."); updateDisplay(); });
            } catch (Exception e) { ui.post(() -> { busy=false; error(e.getMessage()); updateControls(); }); }
        });
    }
    private void poll() {
        Display d=selected(); if (closing||polling||busy||remote==null||d==null) return;
        polling=true;
        io.execute(() -> {
            try {
                String result=call(PivotService.STATUS,d,0,null);
                ui.post(() -> {
                    polling=false; if (closing) return;
                    if (result.startsWith("PENDING|")) {
                        String[] s=result.split("\\|"); pending=true; pendingDisplay=Integer.parseInt(s[1]); pendingTarget=Integer.parseInt(s[2]);
                        Display now=selected(); boolean matched=now!=null&&now.getDisplayId()==pendingDisplay&&now.getRotation()==pendingTarget;
                        if(matched&&selectedUnique!=null&&prefs.getBoolean("confirmed:"+selectedUnique,false)){
                            keep();
                            return;
                        }
                        pendingLine.setText((matched?"방향이 바뀌었습니다. 화면이 정상인가요?":"회전 적용을 확인하고 있습니다…")+"\n"+s[3]+"초 후 자동 복원");
                        if (!matched&&Integer.parseInt(s[3])<12) message.setText("회전이 확인되지 않습니다. 이 기종의 화면 복제 또는 DeX가 외부 화면 회전을 막을 수 있어요.");
                    } else if (pending) {
                        pending=false; message.setText(result.length()>5?result.substring(5):"변경 확인이 종료되었습니다.");
                    }
                    updateDisplay();
                });
            } catch (Exception e) { ui.post(() -> { polling=false; if (pending) message.setText("연결 상태를 확인해 주세요. 자동 복원은 Shizuku가 실행 중일 때 동작합니다."); }); }
        });
    }
    private void help() {
        new AlertDialog.Builder(this).setTitle("처음 연결하는 방법")
            .setMessage("1. Shizuku를 설치합니다.\n\n2. 갤럭시 설정 → 휴대전화 정보 → 소프트웨어 정보 → 빌드번호를 7번 눌러 개발자 옵션을 켭니다.\n\n3. Wi-Fi에 연결하고 개발자 옵션 → 무선 디버깅을 켠 뒤 Shizuku 안내대로 페어링하고 시작합니다. 재부팅하면 다시 시작해야 합니다.\n\n4. 이 앱으로 돌아와 Shizuku 권한을 허용하고, 모니터를 연결한 뒤 세로 / 가로를 누릅니다.\n\n회전 후 20초 안에 ‘유지’를 눌러 주세요. 확인하지 않으면 이전 설정으로 되돌립니다. Shizuku를 끄면 자동 복원이 중단될 수 있습니다.\n\n• 일부 최신 DeX: 설정 → 연결된 기기 → Samsung DeX → 연결된 디스플레이 → 화면 회전에서 기본 기능을 사용할 수 있어요.\n• 외부 화면이 목록에 없으면 케이블과 DeX 연결을 확인해 주세요.\n• 일반 USB-A → USB-C 충전 케이블만으로는 영상이 출력되지 않습니다. 영상 출력 지원 휴대폰과 케이블/어댑터가 필요해요.\n• 세로 영상의 여백이나 앱 자체의 화면 비율까지 강제로 채우지는 않습니다.")
            .setPositiveButton("Shizuku 공식 사이트",(dialog,which)->openUrl("https://shizuku.rikka.app/download/"))
            .setNeutralButton("Shizuku 열기",(dialog,which)->{
                Intent launch=getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                if (launch!=null) startActivity(launch); else openUrl("https://shizuku.rikka.app/download/");
            }).setNegativeButton("닫기",null).show();
    }
    private void openUrl(String url) { try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))); } catch (Exception e) { error("브라우저에서 "+url+" 주소를 열어 주세요."); } }
    private void error(String text) { if (closing||isFinishing()) return; message.setText(text); new AlertDialog.Builder(this).setTitle("확인이 필요해요").setMessage(text).setPositiveButton("확인",null).show(); }
    private int dp(float v) { return Math.round(v*getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v=new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout card() { LinearLayout v=column(); v.setPadding(dp(18),dp(18),dp(18),dp(18)); v.setBackground(shape(CARD,18)); return v; }
    private GradientDrawable shape(int color,int radius) { GradientDrawable s=new GradientDrawable(); s.setColor(color); s.setCornerRadius(dp(radius)); return s; }
    private TextView text(String t,int size,int color) { TextView v=new TextView(this); v.setText(t); v.setTextSize(size); v.setTextColor(color); v.setLineSpacing(dp(3),1); return v; }
    private void add(LinearLayout parent,View child,int top,int bottom) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(top); p.bottomMargin=dp(bottom); parent.addView(child,p); }
    private Button bigButton(String title,boolean primary) { Button b=new Button(this); b.setText(title); b.setTextSize(23); b.setTypeface(null,Typeface.BOLD); b.setTextColor(primary?0xff17112f:TEXT); b.setAllCaps(false); b.setBackground(shape(primary?ACCENT:0xff303548,18)); return b; }
    private Button smallButton(String title) { Button b=new Button(this); b.setText(title); b.setTextSize(14); b.setTextColor(ACCENT); b.setAllCaps(false); b.setMinHeight(dp(48)); b.setBackground(shape(0xff252a3b,12)); return b; }

}
