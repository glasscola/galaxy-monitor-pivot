package com.pivotcontrol.app;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.Point;
import android.hardware.display.*;
import android.media.projection.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class MirrorService extends Service implements DisplayManager.DisplayListener {
    public static final String START="com.pivotcontrol.app.MIRROR_START",CONFIG="com.pivotcontrol.app.MIRROR_CONFIG",STOP="com.pivotcontrol.app.MIRROR_STOP",EDIT="com.pivotcontrol.app.EDIT",GUIDE="com.pivotcontrol.app.GUIDE",PAUSE="com.pivotcontrol.app.PAUSE",HOST="com.pivotcontrol.app.HOST";
    public static volatile boolean running,portrait=true,reversed,fill,editing,guides,paused,hostVisible,needsRegion;
    public static volatile int outputDisplay=-1,phoneW=1080,phoneH=2400,captureW,captureH,renderW,renderH;
    public static volatile String status="모니터를 연결하고 화면 공유를 시작하세요.";
    private final Handler main=new Handler(Looper.getMainLooper());
    private DisplayManager dm;private MediaProjection projection;private VirtualDisplay capture;private Presentation presentation;private StreamRenderer renderer;private RegionOverlay overlay;private SceneStore store;private Scene scene,phonePortraitScene,phoneLandscapeScene;private boolean cleaning,orientationWasPlaying;private int density,overlayGeneration;
    private final MediaProjection.Callback callback=new MediaProjection.Callback(){
        @Override public void onStop(){status="화면 공유가 종료되었습니다.";stopSelf();}
        @Override public void onCapturedContentResize(int w,int h){resize(w,h);}
    };
    @Override public void onCreate(){super.onCreate();dm=(DisplayManager)getSystemService(DISPLAY_SERVICE);dm.registerDisplayListener(this,main);store=new SceneStore(this);scene=store.load();sync();((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel("mirror","외부 화면 복제",NotificationManager.IMPORTANCE_LOW));
        overlay=new RegionOverlay(this,new RegionOverlay.Events(){public void apply(Scene next){scene=next;paused=false;needsRegion=false;store.save(scene);endEdit();}public void cancel(){endEdit();}});
    }
    private void sync(){portrait=scene.portrait;reversed=scene.reverse;fill=scene.scaling==Scene.FILL;}
    @Override public int onStartCommand(Intent i,int flags,int id){if(i==null){stopSelf();return START_NOT_STICKY;}String a=i.getAction();
        if(STOP.equals(a)){status="출력을 종료했습니다. 기본 미러링으로 돌아갑니다.";stopSelf();return START_NOT_STICKY;}
        if(!START.equals(a)){
            if(projection==null||cleaning){stopSelf();return START_NOT_STICKY;}
            try{
                if(CONFIG.equals(a)){overlayGeneration++;if(editing){editing=false;overlay.hide();}scene=store.load();if(i.hasExtra("portrait"))scene.portrait=i.getBooleanExtra("portrait",scene.portrait);if(scene.lockRatio){int[] m=monitorSize();scene.matchMonitor(phoneW,phoneH,m[0],m[1]);}store.save(scene);sync();resize(phoneW,phoneH);renderer.configure(scene);renderer.pauseStream(paused);refreshGuide();status=paused?"출력 일시정지":"선택한 설정으로 출력 중";}
                else if(HOST.equals(a)){if(!editing)refreshGuide();} else if(EDIT.equals(a))beginEdit();
                else if(GUIDE.equals(a)){if(editing)endEdit();else{guides=!guides;refreshGuide();}}
                else if(PAUSE.equals(a)){if(editing)endEdit();paused=!paused;if(!paused)needsRegion=false;renderer.pauseStream(paused);status=paused?"출력 일시정지 · 마지막 화면 유지":"실시간 출력 중";}
                notifyState();
            }catch(Exception e){status="설정 적용 실패: "+e.getMessage();Toast.makeText(this,status,Toast.LENGTH_LONG).show();notifyState();}
            return START_NOT_STICKY;
        }
        if(projection!=null)return START_NOT_STICKY;
        try{
            startForeground(41,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);outputDisplay=i.getIntExtra("display",-1);Display d=dm.getDisplay(outputDisplay);
            if(d==null||d.getDisplayId()==0||(d.getFlags()&Display.FLAG_PRESENTATION)==0)throw new IllegalStateException("외부 모니터를 찾을 수 없습니다.");
            Intent grant=Build.VERSION.SDK_INT>=33?i.getParcelableExtra("grant",Intent.class):i.getParcelableExtra("grant");if(grant==null)throw new IllegalStateException("화면 공유 승인이 필요합니다.");
            projection=((MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE)).getMediaProjection(i.getIntExtra("result",0),grant);projection.registerCallback(callback,main);
            Point p=new Point();dm.getDisplay(0).getRealSize(p);phoneW=p.x;phoneH=p.y;int[] sz=Scene.captureSize(p.x,p.y,scene.highQuality);captureW=sz[0];captureH=sz[1];density=getResources().getDisplayMetrics().densityDpi;
            paused=false;needsRegion=false;editing=false;guides=false;showExternal(d);status="외부 화면을 준비하고 있습니다…";
        }catch(Exception e){fail(e.toString());}return START_NOT_STICKY;
    }
    private void showExternal(Display display){presentation=new Presentation(this,display,android.R.style.Theme_Material_NoActionBar);Window w=presentation.getWindow();w.setBackgroundDrawableResource(android.R.color.black);w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        renderer=new StreamRenderer(presentation.getContext(),captureW,captureH,new StreamRenderer.Ready(){public void ready(Surface surface){try{if(cleaning)return;if(capture==null)capture=projection.createVirtualDisplay("Pivot live regions",captureW,captureH,density,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,surface,null,main);else capture.setSurface(surface);running=true;status="실시간 출력 중 · 원하는 앱을 열어 보세요.";renderer.configure(scene);notifyState();}catch(Exception e){fail(e.toString());}}public void error(String reason){fail(reason);}});
        renderer.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,orr,ob)->{renderW=r-l;renderH=b-t;});presentation.setContentView(renderer);presentation.setOnDismissListener(d->{if(!cleaning){status="모니터 연결이 해제되었습니다.";stopSelf();}});presentation.show();w.setLayout(-1,-1);
    }
    private int[] monitorSize(){Display d=dm.getDisplay(outputDisplay);if(d==null)return new int[]{1920,1080};return new int[]{d.getMode().getPhysicalWidth(),d.getMode().getPhysicalHeight()};}
    private void beginEdit(){if(!Settings.canDrawOverlays(this)){status="앱에서 ‘다른 앱 위에 표시’를 허용해 주세요.";Toast.makeText(this,status,Toast.LENGTH_LONG).show();return;}if(editing)return;editing=true;renderer.pauseStream(true);overlay.hide();int token=++overlayGeneration;main.postDelayed(()->{if(!cleaning&&editing&&token==overlayGeneration){try{int[] m=monitorSize();overlay.show(scene,true,phoneW,phoneH,m[0],m[1]);}catch(Exception e){editing=false;renderer.pauseStream(paused);status="테두리를 표시할 수 없습니다. 권한을 확인하세요.";notifyState();}}},100);status="테두리 편집 중 · 모니터는 마지막 화면 유지";}
    private void endEdit(){overlayGeneration++;editing=false;overlay.hide();sync();renderer.configure(scene);guides=true;refreshGuide();int token=overlayGeneration;main.postDelayed(()->{if(!cleaning&&!editing&&token==overlayGeneration)renderer.pauseStream(paused);},220);status=paused?"출력 일시정지":"영역 출력 중 · 테두리 안팎에서 앱을 조작하세요.";notifyState();}
    private void refreshGuide(){overlay.hide();if(!scene.regions||!Settings.canDrawOverlays(this))guides=false;if(guides&&!hostVisible){int[] m=monitorSize();overlay.show(scene,false,phoneW,phoneH,m[0],m[1]);}}
    private void resize(int w,int h){if(cleaning||w<=0||h<=0)return;
        boolean turned=(w>h)!=(phoneW>phoneH);
        if(turned&&scene.regions){
            // Only remember accepted regions from this sharing session. A pending
            // orientation must not become a valid preset merely by rotating back.
            if(!needsRegion){if(phoneW>phoneH)phoneLandscapeScene=scene.copy();else phonePortraitScene=scene.copy();}
            Scene other=w>h?phoneLandscapeScene:phonePortraitScene;
            if(other!=null){
                if(needsRegion&&orientationWasPlaying)paused=false;
                needsRegion=false;scene.items.clear();for(Scene.Region r:other.items)scene.items.add(r.copy());scene.arrange();store.save(scene);
            }else{
                if(!needsRegion)orientationWasPlaying=!paused;
                needsRegion=true;paused=true;status="휴대폰 방향이 바뀌었습니다. 테두리를 다시 편집하세요.";
            }
            editing=false;overlayGeneration++;overlay.hide();
        }
        phoneW=w;phoneH=h;if(turned&&scene.lockRatio){int[] m=monitorSize();scene.matchMonitor(w,h,m[0],m[1]);store.save(scene);}int[] size=Scene.captureSize(w,h,scene.highQuality);if(captureW!=size[0]||captureH!=size[1]){captureW=size[0];captureH=size[1];try{if(renderer!=null)renderer.resizeSource(captureW,captureH);if(capture!=null)capture.resize(captureW,captureH,density);}catch(Exception e){fail(e.toString());}}
        if(renderer!=null){renderer.configure(scene);renderer.pauseStream(paused||editing);}if(turned){refreshGuide();notifyState();}
    }
    private PendingIntent action(String action,int request,Boolean direction){Intent i=new Intent(this,MirrorService.class).setAction(action);if(direction!=null)i.putExtra("portrait",direction);return PendingIntent.getService(this,request,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private int res(String n,String type){return getResources().getIdentifier(n,type,getPackageName());}
    private Notification notification(){Notification.Builder b=new Notification.Builder(this,"mirror").setSmallIcon(res("ic_notification","drawable")).setContentTitle("모니터 피벗").setContentText(status).setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MirrorActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false).setVisibility(Notification.VISIBILITY_PRIVATE).setStyle(new Notification.DecoratedCustomViewStyle()).setCustomContentView(controls(false)).setCustomBigContentView(controls(true));if(Build.VERSION.SDK_INT>=31)b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);return b.build();}
    private RemoteViews controls(boolean expanded){RemoteViews v=new RemoteViews(getPackageName(),res(expanded?"notification_pivot_expanded":"notification_pivot","layout"));int p=res("notification_portrait","id"),l=res("notification_landscape","id");v.setTextViewText(p,portrait?"✓ 세로":"세로");v.setTextViewText(l,portrait?"가로":"✓ 가로");v.setTextColor(p,portrait?0xff161323:0xfff6f4ff);v.setTextColor(l,portrait?0xfff6f4ff:0xff161323);v.setInt(p,"setBackgroundResource",res(portrait?"notification_selected":"notification_unselected","drawable"));v.setInt(l,"setBackgroundResource",res(portrait?"notification_unselected":"notification_selected","drawable"));v.setOnClickPendingIntent(p,action(CONFIG,1,true));v.setOnClickPendingIntent(l,action(CONFIG,2,false));v.setOnClickPendingIntent(res("notification_stop","id"),action(STOP,3,null));if(expanded){v.setTextViewText(res("notification_hint","id"),(needsRegion?"방향 변경 · 테두리 재지정 필요":editing?"편집 중":paused?"일시정지":scene.regions?scene.items.size()+"개 영역 출력 중":"전체 화면 출력 중")+" · "+(guides?"테두리 표시":"테두리 숨김"));boolean dark=(getResources().getConfiguration().uiMode&48)==32;v.setTextColor(res("notification_hint","id"),dark?0xffc0c6d5:0xff515969);v.setOnClickPendingIntent(res("notification_edit","id"),PendingIntent.getActivity(this,4,new Intent(this,RegionEntryActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));v.setTextViewText(res("notification_guide","id"),editing?"편집 취소":guides?"테두리 숨김":"테두리 표시");v.setOnClickPendingIntent(res("notification_guide","id"),action(GUIDE,5,null));v.setTextViewText(res("notification_pause","id"),paused?"출력 재개":"일시정지");v.setOnClickPendingIntent(res("notification_pause","id"),action(PAUSE,6,null));}return v;}
    private void notifyState(){sync();((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(41,notification());}
    private void fail(String why){status="화면 공유 실패: "+why;stopSelf();}
    @Override public void onDisplayAdded(int id){}
    @Override public void onDisplayRemoved(int id){if(id==outputDisplay){status="모니터 연결이 해제되었습니다.";stopSelf();}}
    @Override public void onDisplayChanged(int id){if(id==0&&Build.VERSION.SDK_INT<34){Point p=new Point();dm.getDisplay(0).getRealSize(p);resize(p.x,p.y);}if(id==outputDisplay&&renderer!=null){renderer.configure(scene);if(!editing)refreshGuide();}}
    @Override public void onDestroy(){cleaning=true;running=false;editing=false;guides=false;paused=false;needsRegion=false;outputDisplay=-1;main.removeCallbacksAndMessages(null);dm.unregisterDisplayListener(this);if(overlay!=null)overlay.hide();if(capture!=null)capture.release();if(renderer!=null)renderer.close();if(presentation!=null)presentation.dismiss();if(projection!=null){projection.unregisterCallback(callback);projection.stop();}stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
