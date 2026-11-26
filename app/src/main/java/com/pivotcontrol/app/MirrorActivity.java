package com.pivotcontrol.app;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.media.projection.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.util.TypedValue;
import android.widget.*;
import java.util.*;

public class MirrorActivity extends Activity implements DisplayManager.DisplayListener {
    private Ui ui;private SceneStore store;private Scene scene;private DisplayManager dm;private final Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout root,page;private ScrollView scroll;private TextView state,qualityInfo;private Button start,portraitButton,landscapeButton,rotateButton,guideButton,coverButton,editButton,restoreButton,stopButton;
    private TextView subtitle,connection;
    private View outputOptions;
    private Button wholeScreen, partialScreen, connectionHelp;
    private OutputDiagram directionPreview;
    private boolean adjustingSharpness;
    private Scene selectionUndo;
    private int tab,selected,displayId=-1,consentDisplay=-1;private boolean awaiting,starting;private long startAt;private String drawn;
    private final Runnable tick=new Runnable(){
        public void run(){
            Scene saved=store.load();
            if(!awaiting&&!adjustingSharpness&&!SceneStore.encode(saved).equals(drawn)){
                scene=saved;
                draw(true);
            }else refresh();
            handler.postDelayed(this,800);
        }
    };
    private boolean requestedPortrait,requestedReverse;
    @Override public void onCreate(Bundle b){super.onCreate(b);ui=new Ui(this);store=new SceneStore(this);scene=store.load();dm=(DisplayManager)getSystemService(DISPLAY_SERVICE);if(b!=null){tab=b.getInt("tab");awaiting=b.getBoolean("awaiting");consentDisplay=b.getInt("display",-1);requestedPortrait=b.getBoolean("requestedPortrait");requestedReverse=b.getBoolean("requestedReverse");}dm.registerDisplayListener(this,handler);chooseDisplay();draw(false);if(getIntent().getBooleanExtra("overlayPermission",false))handler.post(this::overlayPermission);}
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putInt("tab",tab);b.putBoolean("awaiting",awaiting);b.putInt("display",consentDisplay);b.putBoolean("requestedPortrait",requestedPortrait);b.putBoolean("requestedReverse",requestedReverse);}
    @Override protected void onResume(){super.onResume();MirrorService.hostVisible=true;if(MirrorService.running)startService(new Intent(this,MirrorService.class).setAction(MirrorService.HOST));scene=store.load();chooseDisplay();draw(true);handler.removeCallbacks(tick);handler.post(tick);}
    @Override protected void onPause(){super.onPause();MirrorService.hostVisible=false;if(MirrorService.running)startService(new Intent(this,MirrorService.class).setAction(MirrorService.HOST));handler.removeCallbacks(tick);}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);dm.unregisterDisplayListener(this);super.onDestroy();}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);ui=new Ui(this);draw(true);}
    private void chooseDisplay(){if(MirrorService.running){displayId=MirrorService.outputDisplay;return;}if(dm.getDisplay(displayId)!=null)return;displayId=-1;for(Display d:dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION))if(d.getDisplayId()!=0&&(d.getFlags()&Display.FLAG_PRIVATE)==0){displayId=d.getDisplayId();break;}}
    @Override public void onDisplayAdded(int id){chooseDisplay();refresh();}
    @Override public void onDisplayRemoved(int id){
        if(id==consentDisplay)consentDisplay=-1;
        refresh();
    }
    @Override public void onDisplayChanged(int id){refresh();}
    private void draw(boolean preserve){
        int y=preserve&&scroll!=null?scroll.getScrollY():0;selected=Math.min(selected,scene.items.size()-1);drawn=SceneStore.encode(scene);
        root=ui.column();root.setBackgroundColor(ui.background);root.setOnApplyWindowInsetsListener((v,insets)->{Insets i=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(i.left,i.top,i.right,i.bottom);return insets;});
        getWindow().setStatusBarColor(ui.background);
        getWindow().setNavigationBarColor(ui.background);
        int contentWidth=tab==2&&wideEditor()?980:600;
        int horizontalPadding=ui.dp(Math.max(20,(getResources().getConfiguration().screenWidthDp-contentWidth)/2f));
        LinearLayout header=ui.row();header.setPadding(horizontalPadding,ui.dp(14),horizontalPadding,ui.dp(8));TextView title=ui.text("모니터 피벗",18,ui.textColor);title.setTypeface(null,Typeface.BOLD);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button more=ui.button("도움말",false);header.addView(more);more.setOnClickListener(v->more());root.addView(header);
        scroll=new ScrollView(this);scroll.setFillViewport(true);page=ui.column();page.setPadding(horizontalPadding,0,horizontalPadding,ui.dp(24));scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        connection=ui.text("",14,ui.muted);
        ui.add(page,connection,8);
        state=ui.text("",24,ui.textColor);
        state.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_BALANCED);
        state.setTypeface(null,Typeface.BOLD);
        state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        ui.add(page,state,4);
        subtitle=ui.text("",14,ui.muted);
        subtitle.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_BALANCED);
        ui.add(page,subtitle,4);
        qualityInfo=null;start=null;guideButton=null;coverButton=null;
        portraitButton=null;landscapeButton=null;directionPreview=null;
        if(tab==0)outputPage();
        else if(tab==1){
            Button back=ui.compactButton("‹ 회전 화면",false);
            back.setOnClickListener(v->{tab=0;draw(false);});
            ui.add(page,back,4);
            settingsPage();
        }
        else{
            Button back=ui.compactButton("‹ 화면 설정",false);
            back.setOnClickListener(v->{tab=1;draw(false);});
            ui.add(page,back,4);
            if(tab==2)partsPage();else qualityPage();
        }
        setContentView(root);
        root.post(()->{
            WindowInsetsController controller=getWindow().getInsetsController();
            if(controller!=null){
                int bars=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(ui.dark?0:bars,bars);
            }
        });
        if(y>0)scroll.post(()->scroll.scrollTo(0,y));
        refresh();
    }
    private void outputPage(){
        ui.add(page,ui.title("모니터 방향"),12);
        LinearLayout row=ui.row();
        landscapeButton=ui.button("▭ 가로",false);
        portraitButton=ui.button("▯ 세로",false);
        rotateButton=ui.button("↻ 90°",false);
        for(Button direction:new Button[]{landscapeButton,portraitButton,rotateButton}){
            direction.setMinHeight(ui.dp(58));
            direction.setMinimumWidth(0);
            direction.setPadding(ui.dp(4),ui.dp(8),ui.dp(4),ui.dp(8));
            ui.weight(row,direction);
        }
        ui.add(page,row,6);
        landscapeButton.setOnClickListener(v->selectDirection(false));
        portraitButton.setOnClickListener(v->selectDirection(true));
        rotateButton.setContentDescription("모니터 화면을 오른쪽으로 90도 회전");
        rotateButton.setOnClickListener(v->rotateClockwise());

        connectionHelp=ui.compactButton("이미 연결했는데 안 보이나요?",false);
        connectionHelp.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("앱을 닫으면 모니터에 휴대폰 화면이 보이나요?")
                .setMessage("화면이 보이면 앱을 다시 열어 모니터 연결 상태를 확인하세요.\n\n화면이 보이지 않으면 기기와 케이블이 유선 영상 출력을 지원하는지, 모니터 입력과 전원이 맞는지 확인하세요.")
                .setPositiveButton("확인",null).show());
        ui.add(page,connectionHelp,4);

        LinearLayout options=ui.column();
        outputOptions=options;
        ui.add(options,ui.title("보여줄 화면"),0);
        LinearLayout modes=ui.row();
        wholeScreen=ui.button("휴대폰 전체",false);
        partialScreen=ui.button("일부분만",false);
        ui.weight(modes,wholeScreen);ui.weight(modes,partialScreen);
        ui.add(options,modes,6);
        wholeScreen.setOnClickListener(v->{
            if(MirrorService.running&&scene.regions)send(MirrorService.WHOLE);
        });
        partialScreen.setOnClickListener(v->{
            if(!scene.regions||MirrorService.needsRegion)editOverlay();
        });
        ui.add(page,options,18);

        start=ui.button("모니터 다시 보이기",true);
        start.setOnClickListener(v->send(MirrorService.COVER));
        ui.add(page,start,8);
        editButton=ui.compactButton("선택한 부분 바꾸기",false);
        editButton.setOnClickListener(v->editOverlay());
        ui.add(page,editButton,4);
        restoreButton=ui.compactButton("이전 부분 다시 보기",false);
        restoreButton.setOnClickListener(v->send(MirrorService.PARTIAL));
        ui.add(page,restoreButton,4);

        Button reset=ui.button("기본 설정 복원",false);
        reset.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("기본 설정으로 되돌릴까요?")
                .setMessage("세로 전체 화면, 기본 확대·해상도·화질로 돌아갑니다. 이름을 붙여 저장한 설정은 보관됩니다.")
                .setNegativeButton("취소",null)
                .setPositiveButton("되돌리기",(dialog,which)->resetOutput())
                .show());
        ui.add(page,reset,12);
        ui.add(page,ui.text("세로 · 전체 화면 · 기본 화질",13,ui.muted),4);
        directionPreview=new OutputDiagram();
        ui.add(page,directionPreview,12);
        guideButton=ui.compactButton("휴대폰 테두리 숨기기",false);
        guideButton.setOnClickListener(v->send(MirrorService.GUIDE));
        ui.add(page,guideButton,12);
        coverButton=ui.compactButton("모니터 가리기",false);
        coverButton.setOnClickListener(v->send(MirrorService.COVER));
        ui.add(page,coverButton,4);
        stopButton=ui.compactButton("화면 공유 끝내기",false);
        stopButton.setOnClickListener(v->{
            stopService(new Intent(this,MirrorService.class));
            starting=false;
            handler.postDelayed(this::refresh,200);
        });
        ui.add(page,stopButton,12);
        Button settings=ui.compactButton("부분 출력·화질 설정",false);
        settings.setOnClickListener(v->{tab=1;draw(false);});
        ui.add(page,settings,8);
    }

    private void resetOutput(){
        selected=0;
        selectionUndo=null;
        keepPlacementOpen=false;
        scene=new Scene();
        store.save(scene);
        if(MirrorService.running)send(MirrorService.RESET);
        draw(false);
        toast("기본 설정으로 되돌렸습니다.");
    }

    private void directionChoice(Button button,String title,boolean selected){
        if(Boolean.valueOf(selected).equals(button.getTag())&&button.getStateDescription()!=null)return;
        button.setTag(selected);
        Ui.update(button,(selected?"✓ ":"")+title);
        button.setSelected(selected);
        button.setTextColor(selected?ui.onAccent:ui.textColor);
        GradientDrawable background=ui.shape(selected?ui.accent:ui.cardSurface,14);
        background.setStroke(ui.dp(selected?2:1),selected?ui.accent:ui.outline);
        button.setBackgroundTintList(null);
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(ui.ripple),background,null));
        button.setStateDescription(selected?"선택됨":"선택 안 됨");
    }

    private final class OutputDiagram extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        OutputDiagram(){
            super(MirrorActivity.this);
            setMinimumHeight(ui.dp(160));
            setBackground(ui.shape(ui.cardSurface,16));
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            setContentDescription("왼쪽은 내 휴대폰, 오른쪽은 모니터에 표시되는 범위의 도식입니다.");
        }
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            setMeasuredDimension(MeasureSpec.getSize(widthSpec),resolveSize(ui.dp(158),heightSpec));
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float mid=getWidth()/2f, center=ui.dp(62);
            float phoneLeft=mid*.53f-ui.dp(23), monitorCenter=mid*1.49f;
            RectF phone=new RectF(phoneLeft,center-ui.dp(45),phoneLeft+ui.dp(46),center+ui.dp(45));
            float halfW=Math.min(mid*.39f,ui.dp(scene.portrait?33:57));
            float halfH=ui.dp(scene.portrait?46:34);
            RectF monitor=new RectF(monitorCenter-halfW,center-halfH,monitorCenter+halfW,center+halfH);
            panel(canvas,phone);panel(canvas,monitor);
            paint.setColor(ui.muted);
            paint.setStrokeWidth(ui.dp(2));
            canvas.drawLine(monitorCenter-ui.dp(15),monitor.bottom+ui.dp(5),
                    monitorCenter+ui.dp(15),monitor.bottom+ui.dp(5),paint);
            label(canvas,"→",mid,center+ui.dp(5),18,ui.muted);
            if(scene.regions){
                int index=0;
                for(Scene.Region region:scene.items){
                    String name=scene.items.size()>1?String.valueOf((char)('A'+index)):"";
                    mark(canvas,phone,region.source,name);
                    if(!MirrorService.covered){
                        if(scene.reverse){
                            Scene.Box b=region.dest;
                            mark(canvas,monitor,new Scene.Box(1-b.r,1-b.b,1-b.l,1-b.t),name);
                        }else mark(canvas,monitor,region.dest,name);
                    }
                    index++;
                }
            }else{
                float inset=ui.dp(5);
                paint.setColor(ui.muted);
                paint.setStrokeWidth(ui.dp(2));
                for(int i=1;i<=4;i++){
                    float y=phone.top+phone.height()*i/5;
                    canvas.drawLine(phone.left+inset,y,phone.right-inset,y,paint);
                    if(!MirrorService.covered){
                        canvas.save();
                        if(scene.reverse)canvas.rotate(180,monitor.centerX(),monitor.centerY());
                        float yy=monitor.top+monitor.height()*i/5;
                        canvas.drawLine(monitor.left+inset,yy,monitor.right-inset,yy,paint);
                        canvas.restore();
                    }
                }
            }
            label(canvas,scene.reverse?"↓":"↑",monitor.centerX(),
                    scene.reverse?monitor.bottom-ui.dp(4):monitor.top+ui.dp(14),13,ui.accent);
            label(canvas,"내 휴대폰",phone.centerX(),ui.dp(133),14,ui.textColor);
            label(canvas,MirrorService.running?"모니터 도식":"모니터 예시",monitor.centerX(),ui.dp(133),14,ui.textColor);
        }
        private void panel(Canvas canvas,RectF rect){
            paint.setColor(ui.background);
            canvas.drawRoundRect(rect,ui.dp(7),ui.dp(7),paint);
            paint.setColor(ui.muted);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(ui.dp(1));
            canvas.drawRoundRect(rect,ui.dp(7),ui.dp(7),paint);
            paint.setStyle(Paint.Style.FILL);
        }
        private void mark(Canvas canvas,RectF frame,Scene.Box b,String name){
            RectF region=new RectF(frame.left+frame.width()*b.l, frame.top+frame.height()*b.t,
                    frame.left+frame.width()*b.r, frame.top+frame.height()*b.b);
            paint.setColor(0x33578dff);
            canvas.drawRect(region,paint);
            paint.setColor(Ui.BLUE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(ui.dp(1.5f));
            canvas.drawRect(region,paint);
            paint.setStyle(Paint.Style.FILL);
            if(!name.isEmpty())label(canvas,name,region.centerX(),region.centerY()+ui.dp(4),12,ui.textColor);
        }
        private void label(Canvas canvas,String value,float x,float y,float sp,int color){
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,sp,getResources().getDisplayMetrics()));
            paint.setColor(color);
            canvas.drawText(value,x,y,paint);
        }
    }

    private void settingsPage(){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            Button notifications=ui.button("알림에서 가로·세로 조작 켜기",false);
            notifications.setOnClickListener(v->{
                if(shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)||
                        !getPreferences(MODE_PRIVATE).getBoolean("askedNotifications",false)){
                    getPreferences(MODE_PRIVATE).edit().putBoolean("askedNotifications",true).apply();
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},12);
                }else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
            });
            ui.add(page,notifications,12);
        }
        Button parts=ui.button("보여줄 부분과 모니터 배치",false);
        parts.setOnClickListener(v->{tab=2;draw(false);});
        ui.add(page,parts,16);
        Button quality=ui.button("화질·부드러움",false);
        quality.setOnClickListener(v->{tab=3;draw(false);});
        ui.add(page,quality,8);
        Button monitor=ui.button("모니터 상세 정보",false);
        monitor.setOnClickListener(v->monitorDialog());
        ui.add(page,monitor,8);
        LinearLayout direction=ui.card(page,"방향 미세 조정",null);
        toggle(direction,"위아래 뒤집기","거꾸로 설치한 모니터에 맞춥니다.",scene.reverse,on->{scene.reverse=on;commit();});
        presets(page);
    }

    private boolean wideEditor(){
        return getResources().getConfiguration().screenWidthDp>=840;
    }

    private void partsPage(){
        if(wideEditor()){
            LinearLayout panes=ui.row();
            panes.setGravity(Gravity.TOP);
            LinearLayout source=ui.column(),destination=ui.column();
            ui.weight(panes,source);
            ui.weight(panes,destination);
            regionsPage(source);
            layoutPage(destination,true);
            ui.add(page,panes,4);
        }else{
            regionsPage(page);
            layoutPage(page,false);
        }
        Button show=ui.button("선택한 부분 보여주기",true);
        show.setOnClickListener(v->{scene.regions=true;scene.hasSavedRegions=true;commit();tab=0;draw(false);});
        ui.add(page,show,16);
    }
    private void regionsPage(LinearLayout parent){
        LinearLayout card=ui.card(parent,"휴대폰에서 보여줄 부분",
                "파란 테두리를 잡으세요. 스크롤하면 테두리 안의 내용도 바뀝니다.");
        Button topBottom=ui.button("위·아래만 선택",false);
        ui.add(card,topBottom,8);
        topBottom.setOnClickListener(v->{selectionUndo=scene.copy();scene.selectTopAndBottom();selected=0;commit();draw(true);});
        if(selectionUndo!=null){
            Button undo=ui.compactButton("이전 선택으로 되돌리기",false);
            ui.add(card,undo,4);
            undo.setOnClickListener(v->{
                scene.items.clear();
                for(Scene.Region region:selectionUndo.items)scene.items.add(region.copy());
                scene.layout=selectionUndo.layout;
                scene.lockRatio=selectionUndo.lockRatio;
                selectionUndo=null;selected=0;commit();draw(true);
            });
        }
        regionPicker(card);
        SceneCanvas plan=canvas(false);ui.add(card,plan,10);
        ui.add(card,ui.text("휴대폰 화면 도식 · 사각형을 끌어 위치와 크기를 조절하세요",12,ui.muted),4);
        LinearLayout ratio=ui.row();Button free=ui.button("자유 비율",!scene.lockRatio),fixed=ui.button("모니터 비율",scene.lockRatio);ui.weight(ratio,free);ui.weight(ratio,fixed);ui.add(card,ratio,12);free.setOnClickListener(v->{scene.lockRatio=false;commit();draw(true);});fixed.setOnClickListener(v->{scene.lockRatio=true;int[] m=monitorSize(),p=phoneSize(),c=scene.canvas(m[0],m[1]);scene.matchMonitor(p[0],p[1],m[0],m[1]);commit();draw(true);});
        LinearLayout quick=ui.row();String[] labels={"전체 범위","위 절반","아래 절반"};for(int i=0;i<3;i++){final int k=i;Button b=ui.button(labels[i],false);ui.weight(quick,b);b.setOnClickListener(v->{scene.items.get(selected).source=new Scene.Box(0,k==2?.5f:0,1,k==1?.5f:1);scene.lockRatio=false;commit();draw(true);});}ui.add(card,quick,8);
        Button precise=ui.button("위치 · 크기 숫자로 조절",false);ui.add(card,precise,8);precise.setOnClickListener(v->numbers(false));
        Button edit=ui.button("사용 중인 앱에서 부분 선택",false);ui.add(parent,edit,14);edit.setOnClickListener(v->editOverlay());
        LinearLayout options=ui.card(parent,"안내 표시","편집할 때와 웹서핑할 때를 구분합니다.");
        toggle(options,"편집 중 바깥을 어둡게","적용 후에는 음영과 조절 도구가 사라집니다.",scene.dim,on->{scene.dim=on;commit();});
        ui.add(options,ui.text("알림에서 휴대폰의 테두리만 숨길 수 있어요.",14,ui.muted),10);
    }
    private SceneCanvas canvas(boolean dest){SceneCanvas v=new SceneCanvas(this,scene,dest,index->{selected=index;commit();});int[] p=phoneSize(),m=monitorSize();v.sourceW=p[0];v.sourceH=p[1];v.outputW=m[0];v.outputH=m[1];v.selected=selected;v.setLayoutParams(new LinearLayout.LayoutParams(-1,ui.dp(dest?280:250)));return v;}
    private void regionPicker(LinearLayout card){LinearLayout row=ui.row();for(int i=0;i<scene.items.size();i++){final int k=i;Button b=ui.button(String.valueOf((char)('A'+i)),selected==i);b.setContentDescription("부분 "+(char)('A'+i)+(i==selected?", 선택됨":""));ui.weight(row,b);b.setOnClickListener(v->{selected=k;draw(true);});}ui.add(card,row,12);
        LinearLayout actions=ui.row();Button add=ui.button("다른 부분 추가",false),remove=ui.button("선택한 부분 삭제",false);add.setEnabled(scene.items.size()<4);remove.setEnabled(scene.items.size()>1);ui.weight(actions,add);ui.weight(actions,remove);ui.add(card,actions,8);add.setOnClickListener(v->{scene.add();selected=scene.items.size()-1;commit();draw(true);});remove.setOnClickListener(v->{scene.remove(selected);selected=Math.min(selected,scene.items.size()-1);commit();draw(true);});}
    private void layoutPage(LinearLayout parent,boolean wide){
        LinearLayout body=wide?ui.card(parent,"모니터에 보일 배치",null):
                new ExpandableSection(parent,"모니터에 놓을 위치",scene.items.size()>1).body;
        ui.add(body,ui.text("휴대폰에서 고른 부분을 모니터 어디에 놓을지 정합니다.",14,ui.muted),2);
        LinearLayout modes=ui.row();
        String[] labels={"▤ 위아래","◫ 나란히","직접 놓기"};
        for(int i=0;i<labels.length;i++){
            if(scene.items.size()==1&&i==1)continue;
            final int mode=i;
            String label=scene.items.size()==1&&i==0?"가운데에 놓기":labels[i];
            Button button=ui.button(((scene.layout==mode||scene.items.size()==1&&mode==0&&scene.layout==1)?"✓ ":"")+label,false);
            ui.weight(modes,button);
            button.setOnClickListener(v->{scene.layout=mode;scene.arrange();keepPlacementOpen=true;commit();draw(true);});
        }
        ui.add(body,modes,8);
        ui.add(body,canvas(true),8);
        ui.add(body,ui.text(scene.layout==2?"모니터 그림 안에서 끌어 옮기고 손잡이로 크기를 바꾸세요.":
                "세부 위치와 크기는 ‘직접 놓기’에서 조절합니다.",14,ui.muted),4);
        Button numeric=ui.compactButton("위치·크기 숫자로 조절",false);
        numeric.setOnClickListener(v->{keepPlacementOpen=true;numbers(true);});
        ui.add(body,numeric,4);
        if(scene.items.size()>1){
            Button swap=ui.compactButton("표시 순서 뒤집기",false);
            swap.setOnClickListener(v->{Collections.reverse(scene.items);selected=0;scene.arrange();commit();draw(true);});
            ui.add(body,swap,4);
        }
    }

    private boolean keepPlacementOpen;
    private final class ExpandableSection {
        final LinearLayout body=ui.column();
        ExpandableSection(LinearLayout parent,String label,boolean expanded){
            Button toggle=ui.button(label,false);
            ui.add(parent,toggle,14);
            ui.add(parent,body,2);
            Runnable update=()->{
                boolean visible=body.getVisibility()==View.VISIBLE;
                Ui.update(toggle,label+(visible?" ▴":" ▾"));
                toggle.setStateDescription(visible?"펼쳐짐":"접힘");
            };
            body.setVisibility(expanded||keepPlacementOpen?View.VISIBLE:View.GONE);
            update.run();
            toggle.setOnClickListener(v->{
                keepPlacementOpen=body.getVisibility()!=View.VISIBLE;
                body.setVisibility(keepPlacementOpen?View.VISIBLE:View.GONE);
                update.run();
            });
        }
    }
    private void scaling(LinearLayout parent){String[] names={"전부 보기 · 여백 허용","빈틈없이 채우기 · 가장자리 잘림","원본 크기 · 확대하지 않음"};for(int i=0;i<3;i++){final int k=i;Button b=ui.button((scene.scaling==i?"✓ ":"")+names[i],scene.scaling==i);ui.add(parent,b,8);b.setOnClickListener(v->{scene.scaling=k;commit();draw(true);});}}
    private void qualityPage(){
        LinearLayout q=ui.card(page,"글자 선명도",null);
        toggle(q,"기기 해상도 유지","휴대폰이나 태블릿이 현재 표시하는 해상도로 화면을 가져옵니다.",
                scene.highQuality,on->{scene.highQuality=on;commit();refresh();});
        TextView amount=ui.text("윤곽 보정 · "+Math.round(scene.sharpness*100)+"%",14,ui.textColor);
        ui.add(q,amount,12);
        SeekBar sharp=new SeekBar(this);
        sharp.setMax(50);
        sharp.setProgress(Math.round(scene.sharpness*100));
        sharp.setContentDescription("글자 윤곽 보정, 0에서 50퍼센트");
        ui.add(q,sharp,4);
        Button compare=MirrorService.running?ui.button("",false):null;
        if(compare!=null)updateCompareButton(compare,sharp.getProgress());
        sharp.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int progress,boolean user){
                if(user){
                    scene.sharpness=progress/100f;
                    Ui.update(amount,"윤곽 보정 · "+progress+"%");
                    if(compare!=null)updateCompareButton(compare,progress);
                    commit();
                }
            }
            public void onStartTrackingTouch(SeekBar bar){adjustingSharpness=true;}
            public void onStopTrackingTouch(SeekBar bar){adjustingSharpness=false;refresh();}
        });
        if(compare!=null){
            compare.setOnTouchListener((view,event)->{
                if(!view.isEnabled())return false;
                int action=event.getActionMasked();
                if(action==MotionEvent.ACTION_DOWN){
                    view.setPressed(true);
                    Ui.update(compare,"보정 전 표시 중");
                    compareSharpness(true);
                    return true;
                }
                if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
                    view.setPressed(false);
                    updateCompareButton(compare,sharp.getProgress());
                    compareSharpness(false);
                    return true;
                }
                return true;
            });
            compare.setOnClickListener(v->{compareSharpness(true);handler.postDelayed(()->compareSharpness(false),1500);});
            ui.add(q,compare,6);
        }
        ui.add(q,ui.text("윤곽을 강조합니다. 작게 그려진 글자는 브라우저에서 글자 크기를 먼저 키워 보세요.",14,ui.muted),8);
        LinearLayout fit=ui.card(page,"화면 맞춤",null);
        scaling(fit);
        LinearLayout motion=ui.card(page,"스크롤 부드러움","목표 속도는 글자 선명도와 별개입니다.");
        LinearLayout fps=ui.row();
        for(int target:new int[]{30,60}){
            Button button=ui.button((scene.fps==target?"✓ ":"")+target+"fps 목표",false);
            ui.weight(fps,button);
            button.setOnClickListener(v->{scene.fps=target;commit();draw(true);});
        }
        ui.add(motion,fps,8);
        LinearLayout detail=ui.card(page,"해상도와 확대 배율",null);
        qualityInfo=ui.text("",14,ui.textColor);
        ui.add(detail,qualityInfo,8);
    }
    private void updateCompareButton(Button button,int amount){
        button.setEnabled(amount>0);
        button.setAlpha(amount>0?1f:.65f);
        Ui.update(button,amount>0?"누르는 동안 보정 전 보기":"윤곽 보정을 올려 비교하세요");
    }
    private void compareSharpness(boolean before){
        if(MirrorService.running)startService(new Intent(this,MirrorService.class)
                .setAction(MirrorService.COMPARE).putExtra("before",before));
    }
    interface Toggle{void set(boolean value);}
    private void toggle(LinearLayout p,String title,String desc,boolean on,Toggle cb){LinearLayout row=ui.row();LinearLayout labels=ui.column();labels.addView(ui.text(title,15,ui.textColor));ui.add(labels,ui.text(desc,13,ui.muted),4);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));Switch s=new Switch(this);s.setChecked(on);s.setMinHeight(ui.dp(52));s.setContentDescription(title+". "+desc);row.addView(s);s.setOnCheckedChangeListener((v,b)->cb.set(b));row.setOnClickListener(v->s.setChecked(!s.isChecked()));ui.add(p,row,14);}
    private void presets(LinearLayout parent){LinearLayout p=ui.card(parent,"설정 저장","영역·배치·방향·화질을 함께 기억합니다.");LinearLayout r=ui.row();Button save=ui.button("현재 설정 저장",false),load=ui.button("불러오기",false);ui.weight(r,save);ui.weight(r,load);ui.add(p,r,12);save.setOnClickListener(v->{EditText name=new EditText(this);name.setSingleLine(true);name.setHint("예: 세로 웹서핑");name.setPadding(ui.dp(20),ui.dp(12),ui.dp(20),ui.dp(12));AlertDialog d=new AlertDialog.Builder(this).setTitle("설정 이름").setView(name).setPositiveButton("저장",null).setNegativeButton("취소",null).create();d.setOnShowListener(x->d.getButton(-1).setOnClickListener(y->{String text=name.getText().toString().trim();if(text.isEmpty()){name.setError("이름을 입력하세요.");return;}if(text.length()>40){name.setError("40자 이내로 입력하세요.");return;}if(store.names().contains(text)){new AlertDialog.Builder(this).setMessage("같은 이름의 설정을 덮어쓸까요?").setPositiveButton("덮어쓰기",(a,b)->{store.preset(text,scene);d.dismiss();}).setNegativeButton("취소",null).show();}else{store.preset(text,scene);d.dismiss();toast("설정을 저장했습니다.");}}));d.show();});load.setOnClickListener(v->{ArrayList<String> n=store.names();if(n.isEmpty()){message("아직 저장한 설정이 없습니다.");return;}new AlertDialog.Builder(this).setTitle("저장한 설정").setItems(n.toArray(new String[0]),(d,i)->new AlertDialog.Builder(this).setTitle(n.get(i)).setItems(new String[]{"불러오기","삭제"},(a,k)->{if(k==0){scene=store.preset(n.get(i));selected=0;commit();draw(true);}else store.delete(n.get(i));}).setNegativeButton("취소",null).show()).setNegativeButton("닫기",null).show();});}
    private void numbers(boolean dest){Scene.Box box=dest?scene.items.get(selected).dest:scene.items.get(selected).source;LinearLayout form=ui.column();form.setPadding(ui.dp(20),ui.dp(8),ui.dp(20),0);String[] labels={"왼쪽 위치 (%)","위쪽 위치 (%)","너비 (%)","높이 (%)"};float[] vals={box.l,box.t,box.w(),box.h()};EditText[] fields=new EditText[4];for(int i=0;i<4;i++){ui.add(form,ui.text(labels[i],14,ui.muted),8);EditText input=new EditText(this);input.setText(String.format(Locale.US,"%.1f",vals[i]*100));input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);input.setSelectAllOnFocus(true);form.addView(input);fields[i]=input;}
        ScrollView sc=new ScrollView(this);sc.addView(form);AlertDialog d=new AlertDialog.Builder(this).setTitle((dest?"배치 ":"영역 ")+(char)('A'+selected)).setView(sc).setPositiveButton("적용",null).setNegativeButton("취소",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(b->{try{float l=Float.parseFloat(fields[0].getText().toString())/100,t=Float.parseFloat(fields[1].getText().toString())/100,w=Float.parseFloat(fields[2].getText().toString())/100,h=Float.parseFloat(fields[3].getText().toString())/100;if(!Float.isFinite(l+t+w+h)||l<0||t<0||w<.03f||h<.03f||l+w>1.0001f||t+h>1.0001f)throw new IllegalArgumentException();Scene.Box next=new Scene.Box(l,t,l+w,t+h);if(dest){scene.layout=2;scene.items.get(selected).dest=next;}else{scene.items.get(selected).source=next;scene.lockRatio=false;}commit();d.dismiss();draw(true);}catch(Exception e){fields[2].setError("위치+크기는 100% 이내, 크기는 3% 이상이어야 합니다.");}}));d.show();}
    private void commit(){if(scene.lockRatio){int[] p=phoneSize(),m=monitorSize();scene.matchMonitor(p[0],p[1],m[0],m[1]);}if(scene.regions)scene.hasSavedRegions=true;store.save(scene);drawn=SceneStore.encode(scene);if(MirrorService.running)startService(new Intent(this,MirrorService.class).setAction(MirrorService.CONFIG));}
    private void selectDirection(boolean portrait){
        if(MirrorService.running){
            startService(new Intent(this,MirrorService.class).setAction(MirrorService.CONFIG)
                    .putExtra("portrait",portrait));
        }else begin(portrait,scene.reverse);
    }
    private void rotateClockwise(){
        if(MirrorService.running){send(MirrorService.ROTATE);return;}
        int[] size=monitorSize();
        boolean reverse=MirrorGeometry.reverseAfterClockwiseTurn(size[0],size[1],scene.portrait,scene.reverse);
        begin(!scene.portrait,reverse);
    }
    private void send(String action){if(!MirrorService.running){message("모니터를 연결하고 화면 공유를 먼저 시작해 주세요.");return;}startService(new Intent(this,MirrorService.class).setAction(action));handler.postDelayed(this::refresh,180);}
    private void editOverlay(){
        if(!MirrorService.running){
            message("첫 화면에서 가로 또는 세로를 눌러 모니터 출력을 먼저 시작하세요.");
            return;
        }
        if(!Settings.canDrawOverlays(this)){overlayPermission();return;}
        if(getPreferences(MODE_PRIVATE).getBoolean("partEditorExplained",false)){editOverPreviousApp();return;}
        String button=scene.regions?"부분 바꾸기":"보여줄 부분 선택";
        new AlertDialog.Builder(this).setTitle("모니터에 보여줄 부분 선택")
                .setMessage("브라우저나 원하는 앱을 연 상태에서 알림의 ‘"+button+
                        "’을 누르면 그 화면 위에 파란 테두리가 나타납니다. 지금 편집하면 바로 이전 화면 위에 표시됩니다.")
                .setNegativeButton("닫기",null)
                .setPositiveButton("이전 화면에서 선택",(dialog,which)->{
                    getPreferences(MODE_PRIVATE).edit().putBoolean("partEditorExplained",true).apply();
                    editOverPreviousApp();
                }).show();
    }
    private void editOverPreviousApp(){
        moveTaskToBack(true);
        handler.postDelayed(()->send(MirrorService.EDIT),300);
    }
    private void overlayPermission(){new AlertDialog.Builder(this).setTitle("다른 앱 위에 테두리 표시").setMessage("브라우저 위에 파란 테두리를 띄우려면 ‘다른 앱 위에 표시’를 허용해 주세요. 적용 후에는 터치와 스크롤이 원래 앱에 전달됩니다.").setPositiveButton("설정 열기",(d,w)->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())))).setNegativeButton("나중에",null).show();}
    private int[] phoneSize(){if(MirrorService.running)return new int[]{MirrorService.phoneW,MirrorService.phoneH};Point p=new Point();dm.getDisplay(0).getRealSize(p);return new int[]{p.x,p.y};}
    private int[] monitorSize(){Display d=dm.getDisplay(displayId);return d==null?new int[]{1920,1080}:new int[]{d.getMode().getPhysicalWidth(),d.getMode().getPhysicalHeight()};}
    private int gcd(int a,int b){while(b!=0){int t=b;b=a%b;a=t;}return a;}
    private void refresh(){if(state==null)return;chooseDisplay();if(starting&&(MirrorService.running||
            MirrorService.status.startsWith("화면 공유 실패")||SystemClock.uptimeMillis()-startAt>12000))starting=false;
        boolean active=MirrorService.running;
        Display d=dm.getDisplay(displayId);
        int[] m=monitorSize();
        boolean covered=active&&MirrorService.covered;
        boolean needsRegion=active&&MirrorService.needsRegion;
        String current=awaiting?"Android에서 화면 공유를 허용하세요":
                starting?"모니터 화면을 준비하는 중":
                covered?"모니터 화면 가림 중":
                needsRegion?"휴대폰 방향이 바뀌었어요":
                active&&MirrorService.editing?"보여줄 부분 선택 중":
                active&&scene.regions?"선택한 부분만 보여주는 중":
                active?"모니터에 표시 중":
                d==null?"모니터를 연결해 주세요":"모니터 방향을 선택하세요";
        Ui.update(connection,d==null?"모니터 연결 대기":"모니터 연결됨 · "+d.getName());
        Ui.update(state,tab==0?current:tab==1?"화면 설정":tab==2?"부분과 배치":"화질·부드러움");
        String description=covered?"아래에서 모니터 화면을 다시 표시할 수 있어요.":
                needsRegion?"‘일부분만’을 다시 누르거나 ‘휴대폰 전체’를 고르세요.":
                active&&scene.regions?"스크롤하면 테두리 안의 내용도 함께 바뀌어요.":
                active?"":
                d==null?"영상 출력 케이블을 연결하면 가로·세로 버튼이 켜집니다.":
                "가로 또는 세로를 누르고 화면 공유를 허용하세요.";
        if(!active&&!starting&&!awaiting&&d!=null&&MirrorService.status.startsWith("화면 공유 실패"))
            description=MirrorService.status;
        Ui.update(subtitle,tab==0?description:"");
        subtitle.setVisibility(tab==0&&!description.isEmpty()?View.VISIBLE:View.GONE);
        if(start!=null){
            start.setVisibility(covered?View.VISIBLE:View.GONE);
            outputOptions.setVisibility(active&&!covered?View.VISIBLE:View.GONE);
            connectionHelp.setVisibility(d==null&&!awaiting&&!starting?View.VISIBLE:View.GONE);
            ui.style(wholeScreen,!scene.regions);
            wholeScreen.setStateDescription(scene.regions?"선택 안 됨":"선택됨");
            ui.style(partialScreen,scene.regions);
            partialScreen.setStateDescription(scene.regions?"선택됨":"선택 안 됨");
            editButton.setVisibility(active&&!covered&&scene.regions&&!needsRegion?View.VISIBLE:View.GONE);
            Ui.update(editButton,"선택한 부분 바꾸기");
            restoreButton.setVisibility(active&&!covered&&!scene.regions&&scene.hasSavedRegions?View.VISIBLE:View.GONE);
            directionChoice(portraitButton,"▯ 세로",active&&scene.portrait);
            directionChoice(landscapeButton,"▭ 가로",active&&!scene.portrait);
            boolean canRotate=d!=null&&!awaiting&&!starting;
            portraitButton.setEnabled(canRotate);
            landscapeButton.setEnabled(canRotate);
            rotateButton.setEnabled(canRotate);
            portraitButton.setAlpha(canRotate?1f:.62f);
            landscapeButton.setAlpha(canRotate?1f:.62f);
            rotateButton.setAlpha(canRotate?1f:.62f);
            directionPreview.setVisibility(active&&scene.regions?View.VISIBLE:View.GONE);
            directionPreview.invalidate();
            guideButton.setVisibility(active&&scene.regions&&!needsRegion?View.VISIBLE:View.GONE);
            Ui.update(guideButton,MirrorService.guides?"휴대폰 테두리 숨기기":"휴대폰 테두리 표시");
            coverButton.setVisibility(active&&!covered?View.VISIBLE:View.GONE);
            stopButton.setVisibility(active||starting?View.VISIBLE:View.GONE);
        }
        if(qualityInfo!=null){int[] p=phoneSize(),sz=active?new int[]{MirrorService.captureW,MirrorService.captureH}:Scene.captureSize(p[0],p[1],scene.highQuality);int ow=active&&MirrorService.renderW>0?MirrorService.renderW:m[0],oh=active&&MirrorService.renderH>0?MirrorService.renderH:m[1];int[] canvas=scene.canvas(ow,oh);StringBuilder text=new StringBuilder(active?"현재 입력 ":"예상 입력 ");text.append(sz[0]).append(" × ").append(sz[1]).append("\n모니터 신호 ").append(m[0]).append(" × ").append(m[1]).append("\n").append(active?"실제 그리기 ":"예상 그리기 ").append(ow).append(" × ").append(oh);
            text.append("\n목표 ").append(scene.fps).append(" fps");
            if(active){
                int fps=MirrorService.outputFps();
                if(MirrorService.covered||MirrorService.paused||MirrorService.editing)text.append(" · 출력 정지");
                else if(fps>0)text.append(" · 새 프레임 합성 ").append(fps).append(" fps");
                else text.append(" · 화면을 움직여 FPS 확인");
            }
            ArrayList<Scene.Region> list=scene.items;if(!scene.regions){list=new ArrayList<>();list.add(new Scene.Region(0,0,1,1));}for(int i=0;i<list.size();i++){Scene.Region r=list.get(i);float[] a=Scene.placement(r.source,r.dest,sz[0],sz[1],canvas[0],canvas[1],scene.scaling);text.append("\n\n").append(scene.regions?"부분 "+(char)('A'+i):"전체 화면").append(" · ").append(Math.round(r.source.w()*sz[0])).append(" × ").append(Math.round(r.source.h()*sz[1])).append("\n표시 ").append(Math.round(a[6])).append(" × ").append(Math.round(a[7])).append(" · ").append(String.format(Locale.KOREA,"%.2f배",a[8]));if(a[8]>1.5)text.append("\n작은 글자가 흐려질 수 있어요.");}Ui.update(qualityInfo,text.toString());}}
    private void begin(boolean portrait,boolean reverse){
        if(awaiting||starting)return;
        chooseDisplay();
        if(displayId<0)return;
        requestedPortrait=portrait;
        requestedReverse=reverse;
        launchConsent();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==12)draw(true);
    }
    private void launchConsent(){consentDisplay=displayId;MediaProjectionManager manager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);Intent i=Build.VERSION.SDK_INT>=34?manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()):manager.createScreenCaptureIntent();awaiting=true;refresh();startActivityForResult(i,204);}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=204)return;
        awaiting=false;
        if(result!=RESULT_OK||data==null){refresh();return;}
        if(dm.getDisplay(consentDisplay)==null){refresh();message("모니터 연결이 해제되었습니다.");return;}
        try{
            scene=store.load();
            scene.hasSavedRegions|=scene.regions;
            scene.regions=false;
            scene.portrait=requestedPortrait;
            scene.reverse=requestedReverse;
            store.save(scene);
            starting=true;
            startAt=SystemClock.uptimeMillis();
            MirrorService.status="외부 화면을 준비하고 있습니다.";
            startForegroundService(new Intent(this,MirrorService.class).setAction(MirrorService.START)
                    .putExtra("display",consentDisplay).putExtra("result",result).putExtra("grant",data));
            refresh();
        }catch(Exception e){starting=false;refresh();message("화면 공유를 시작하지 못했습니다: "+e.getMessage());}
    }
    private void monitorDialog(){ArrayList<Display> all=new ArrayList<>();ArrayList<String> labels=new ArrayList<>();for(Display d:dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION))if(d.getDisplayId()!=0&&(d.getFlags()&Display.FLAG_PRIVATE)==0){all.add(d);labels.add(d.getName()+" · "+d.getMode().getPhysicalWidth()+" × "+d.getMode().getPhysicalHeight());}if(all.isEmpty()){message("연결된 외부 모니터가 없습니다.");return;}new AlertDialog.Builder(this).setTitle("연결된 모니터").setItems(labels.toArray(new String[0]),(dialog,index)->{Display d=all.get(index);if(!MirrorService.running)displayId=d.getDisplayId();StringBuilder text=new StringBuilder("현재 신호: "+d.getMode().getPhysicalWidth()+" × "+d.getMode().getPhysicalHeight()+"\n\n기기가 보고한 지원 모드\n");int[] canvas=scene.canvas(d.getMode().getPhysicalWidth(),d.getMode().getPhysicalHeight());int divisor=gcd(canvas[0],canvas[1]);text.insert(text.indexOf("\n\n"),"\n출력 방향 "+(scene.portrait?"세로":"가로")+" · "+canvas[0]+" × "+canvas[1]+" · "+canvas[0]/divisor+":"+canvas[1]/divisor);for(Display.Mode mode:d.getSupportedModes())text.append(mode.getPhysicalWidth()).append(" × ").append(mode.getPhysicalHeight()).append(" · ").append(Math.round(mode.getRefreshRate())).append("Hz\n");text.append("\n모니터의 인치 크기나 실제 세워 둔 방향은 자동 감지하지 않습니다. 신호 해상도와 앱의 출력 크기는 다를 수 있습니다.");message(text.toString());refresh();}).setNegativeButton("닫기",null).show();}
    private void more(){
        new AlertDialog.Builder(this).setTitle("모니터 피벗")
                .setItems(new String[]{"사용 방법","테두리 표시 권한","알림 설정"},(dialog,choice)->{
                    if(choice==0)message("앱을 열고 모니터를 연결하세요. 가로나 세로를 누른 뒤 Android 화면 공유를 허용하면 고른 방향으로 출력됩니다.\n\n"+
                            "일부만 띄우려면 ‘일부분만’를 누르세요. 펼친 알림에서도 ‘보여줄 부분 선택’을 누를 수 있습니다.\n\n"+
                            "전체 화면으로 돌아가려면 앱의 ‘휴대폰 전체’ 또는 알림의 ‘부분 출력 해제’를 누르세요.\n\n"+
                            "보안 화면이나 DRM 영상은 검게 보일 수 있습니다. 화면은 파일로 저장하거나 서버로 보내지 않습니다.");
                    else if(choice==1)overlayPermission();
                    else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
                }).setNegativeButton("닫기",null).show();
    }
    private void message(String text){if(!isFinishing())new AlertDialog.Builder(this).setTitle("모니터 피벗").setMessage(text).setPositiveButton("확인",null).show();}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
}
