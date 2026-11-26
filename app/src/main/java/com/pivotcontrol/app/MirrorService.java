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
    public static final String RESET="com.pivotcontrol.app.RESET";
    static final String WHOLE="com.pivotcontrol.app.WHOLE";
    public static final String START="com.pivotcontrol.app.MIRROR_START",CONFIG="com.pivotcontrol.app.MIRROR_CONFIG",STOP="com.pivotcontrol.app.MIRROR_STOP",EDIT="com.pivotcontrol.app.EDIT",GUIDE="com.pivotcontrol.app.GUIDE",PARTIAL="com.pivotcontrol.app.PARTIAL",ROTATE="com.pivotcontrol.app.ROTATE",COMPARE="com.pivotcontrol.app.COMPARE",HOST="com.pivotcontrol.app.HOST",COVER="com.pivotcontrol.app.COVER";
    public static volatile boolean running,portrait=true,reversed,fill,editing,guides,paused,covered,hostVisible,needsRegion;
    public static volatile int outputDisplay=-1,phoneW=1080,phoneH=2400,captureW,captureH,renderW,renderH;
    public static volatile String status="모니터를 연결하고 화면 공유를 시작하세요.";
    private final Handler main=new Handler(Looper.getMainLooper());
    private DisplayManager dm;private MediaProjection projection;private VirtualDisplay capture;private Presentation presentation;private StreamRenderer renderer;private RegionOverlay overlay;private SceneStore store;private Scene scene,phonePortraitScene,phoneLandscapeScene;private boolean cleaning,orientationWasPlaying;private int density,overlayGeneration;
    static int outputFps(){
        MirrorService service=activeService;
        StreamRenderer stream=service==null?null:service.renderer;
        return stream==null?0:stream.outputFps();
    }
    private static volatile MirrorService activeService;
    private final MediaProjection.Callback callback=new MediaProjection.Callback(){
        @Override public void onStop(){status="화면 공유가 종료되었습니다.";stopSelf();}
        @Override public void onCapturedContentResize(int w,int h){resize(w,h);}
    };
    @Override public void onCreate(){super.onCreate();dm=(DisplayManager)getSystemService(DISPLAY_SERVICE);dm.registerDisplayListener(this,main);store=new SceneStore(this);scene=store.load();sync();((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel("mirror","외부 화면 복제",NotificationManager.IMPORTANCE_LOW));
        overlay=new RegionOverlay(this,new RegionOverlay.Events(){public void apply(Scene next){scene=next;scene.hasSavedRegions=true;paused=false;needsRegion=false;store.save(scene);endEdit(true);}public void cancel(){endEdit(false);}});
    }
    private void sync(){portrait=scene.portrait;reversed=scene.reverse;fill=scene.scaling==Scene.FILL;}
    @Override public int onStartCommand(Intent i,int flags,int id){if(i==null){stopSelf();return START_NOT_STICKY;}String a=i.getAction();
        if(STOP.equals(a)){status="출력을 종료했습니다. 기본 미러링으로 돌아갑니다.";stopSelf();return START_NOT_STICKY;}
        if(!START.equals(a)){
            if(projection==null||cleaning){stopSelf();return START_NOT_STICKY;}
            try{
                if(CONFIG.equals(a)||ROTATE.equals(a)||PARTIAL.equals(a)||WHOLE.equals(a)){overlayGeneration++;if(editing){editing=false;overlay.hide();}scene=store.load();if(i.hasExtra("portrait"))scene.portrait=i.getBooleanExtra("portrait",scene.portrait);if(ROTATE.equals(a)){
                    int[] size=monitorSize();
                    scene.reverse=MirrorGeometry.reverseAfterClockwiseTurn(
                            renderW>0?renderW:size[0],renderH>0?renderH:size[1],scene.portrait,scene.reverse);
                    scene.portrait=!scene.portrait;
                }
                if(PARTIAL.equals(a)){
                    if(!scene.regions&&!scene.hasSavedRegions){beginEdit();notifyState();return START_NOT_STICKY;}
                    scene.regions=!scene.regions;
                    guides=scene.regions;
                }
                if(WHOLE.equals(a)){scene.regions=false;guides=false;}
                if(!scene.regions){needsRegion=false;paused=false;}
                if(scene.lockRatio){int[] m=monitorSize();scene.matchMonitor(phoneW,phoneH,m[0],m[1]);}
                store.save(scene);sync();resize(phoneW,phoneH);renderer.configure(scene);renderer.pauseStream(paused);
                refreshGuide();status=scene.regions?"선택한 부분 표시 중":"휴대폰 전체 화면 표시 중";
                }
                else if(RESET.equals(a))resetOutput();
                else if(HOST.equals(a)){renderer.compareSharpness(false);if(!editing)refreshGuide();} else if(EDIT.equals(a))beginEdit();
                else if(GUIDE.equals(a)){if(editing)endEdit(false);else{guides=!guides;refreshGuide();}}
                else if(COMPARE.equals(a)){renderer.compareSharpness(i.getBooleanExtra("before",false));return START_NOT_STICKY;}
                else if(COVER.equals(a)){
                    covered=!covered;
                    renderer.coverStream(covered);
                    status=covered?"모니터 가림 중":paused?"출력 일시정지":"실시간 출력 중";
                }
                notifyState();
            }catch(Exception e){status="설정 적용 실패: "+e.getMessage();Toast.makeText(this,status,Toast.LENGTH_LONG).show();notifyState();}
            return START_NOT_STICKY;
        }
        if(projection!=null)return START_NOT_STICKY;
        try{
            startForeground(41,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);outputDisplay=i.getIntExtra("display",-1);Display d=dm.getDisplay(outputDisplay);
            if(d==null||d.getDisplayId()==0||(d.getFlags()&Display.FLAG_PRESENTATION)==0)throw new IllegalStateException("외부 모니터를 찾을 수 없습니다.");
            Intent grant=Build.VERSION.SDK_INT>=33?i.getParcelableExtra("grant",Intent.class):i.getParcelableExtra("grant");if(grant==null)throw new IllegalStateException("화면 공유 승인이 필요합니다.");
            projection=((MediaProjectionManager)getApplicationContext().getSystemService(MEDIA_PROJECTION_SERVICE)).getMediaProjection(i.getIntExtra("result",0),grant);projection.registerCallback(callback,main);
            Point p=new Point();dm.getDisplay(0).getRealSize(p);phoneW=p.x;phoneH=p.y;int[] sz=Scene.captureSize(p.x,p.y,scene.highQuality);captureW=sz[0];captureH=sz[1];density=getResources().getDisplayMetrics().densityDpi;
            paused=false;covered=false;needsRegion=false;editing=false;guides=false;showExternal(d);status="외부 화면을 준비하고 있습니다…";
        }catch(Exception e){fail(e.toString());}return START_NOT_STICKY;
    }
    private void resetOutput(){
        scene=new Scene();
        store.save(scene);
        phonePortraitScene=null;
        phoneLandscapeScene=null;
        orientationWasPlaying=false;
        overlayGeneration++;
        editing=false;
        guides=false;
        needsRegion=false;
        paused=false;
        covered=false;
        overlay.hide();
        renderer.compareSharpness(false);
        renderer.coverStream(false);
        sync();
        resize(phoneW,phoneH);
        status="기본 설정 · 전체 화면 표시 중";
    }
    private void showExternal(Display display){presentation=new Presentation(this,display,android.R.style.Theme_Material_NoActionBar);Window w=presentation.getWindow();w.setBackgroundDrawableResource(android.R.color.black);w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        renderer=new StreamRenderer(presentation.getContext(),captureW,captureH,new StreamRenderer.Ready(){public void ready(Surface surface){try{if(cleaning)return;if(capture==null)capture=projection.createVirtualDisplay("Pivot live regions",captureW,captureH,density,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,surface,null,main);else capture.setSurface(surface);activeService=MirrorService.this;running=true;status="실시간 출력 중 · 원하는 앱을 열어 보세요.";renderer.configure(scene);notifyState();}catch(Exception e){fail(e.toString());}}public void error(String reason){fail(reason);}});
        renderer.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,orr,ob)->{renderW=r-l;renderH=b-t;});presentation.setContentView(renderer);presentation.setOnDismissListener(d->{if(!cleaning){status="모니터 연결이 해제되었습니다.";stopSelf();}});presentation.show();w.setLayout(-1,-1);
    }
    private int[] monitorSize(){Display d=dm.getDisplay(outputDisplay);if(d==null)return new int[]{1920,1080};return new int[]{d.getMode().getPhysicalWidth(),d.getMode().getPhysicalHeight()};}
    private void beginEdit(){if(!Settings.canDrawOverlays(this)){status="앱에서 ‘다른 앱 위에 표시’를 허용해 주세요.";Toast.makeText(this,status,Toast.LENGTH_LONG).show();return;}if(editing)return;editing=true;renderer.pauseStream(true);overlay.hide();int token=++overlayGeneration;main.postDelayed(()->{if(!cleaning&&editing&&token==overlayGeneration){try{int[] m=monitorSize();overlay.show(scene,true,phoneW,phoneH,m[0],m[1]);}catch(Exception e){editing=false;renderer.pauseStream(paused);status="테두리를 표시할 수 없습니다. 권한을 확인하세요.";notifyState();}}},100);status="테두리 편집 중 · 모니터는 마지막 화면 유지";}
    private void endEdit(boolean applied){overlayGeneration++;editing=false;overlay.hide();sync();renderer.configure(scene);if(applied)guides=true;refreshGuide();int token=overlayGeneration;main.postDelayed(()->{if(!cleaning&&!editing&&token==overlayGeneration)renderer.pauseStream(paused);},220);status=needsRegion?"휴대폰 방향 변경 · 부분을 다시 선택해 주세요.":scene.regions?"선택한 부분 표시 중":"휴대폰 전체 화면 표시 중";notifyState();}
    private void refreshGuide(){
        overlay.hide();
        if(!scene.regions||!Settings.canDrawOverlays(this))guides=false;
        if(guides&&!hostVisible&&!needsRegion){
            int[] m=monitorSize();
            try{overlay.show(scene,false,phoneW,phoneH,m[0],m[1]);}
            catch(RuntimeException e){guides=false;}
        }
    }
    private void resize(int w,int h){if(cleaning||w<=0||h<=0)return;
        boolean sizeChanged=w!=phoneW||h!=phoneH;
        boolean turned=(w>h)!=(phoneW>phoneH);
        if(sizeChanged&&editing){
            editing=false;
            overlayGeneration++;
            overlay.hide();
            status="화면 크기가 바뀌었습니다. 부분을 다시 선택할 수 있습니다.";
        }
        if(turned&&scene.regions){
            // Only remember accepted regions from this sharing session. A pending
            // orientation must not become a valid preset merely by rotating back.
            if(!needsRegion){if(phoneW>phoneH)phoneLandscapeScene=scene.copy();else phonePortraitScene=scene.copy();}
            Scene other=w>h?phoneLandscapeScene:phonePortraitScene;
            if(other!=null){
                if(needsRegion&&orientationWasPlaying)paused=false;
                needsRegion=false;scene.restoreRegionLayout(other);store.save(scene);
            }else{
                if(!needsRegion)orientationWasPlaying=!paused;
                needsRegion=true;paused=true;status="휴대폰 방향이 바뀌었습니다. 테두리를 다시 편집하세요.";
            }
        }
        phoneW=w;phoneH=h;if(turned&&scene.lockRatio){int[] m=monitorSize();scene.matchMonitor(w,h,m[0],m[1]);store.save(scene);}int[] size=Scene.captureSize(w,h,scene.highQuality);if(captureW!=size[0]||captureH!=size[1]){captureW=size[0];captureH=size[1];try{if(renderer!=null)renderer.resizeSource(captureW,captureH);if(capture!=null)capture.resize(captureW,captureH,density);}catch(Exception e){fail(e.toString());}}
        if(renderer!=null){renderer.configure(scene);renderer.pauseStream(paused||editing);}if(sizeChanged){refreshGuide();notifyState();}
    }
    private PendingIntent action(String action,int request,Boolean direction){Intent i=new Intent(this,MirrorService.class).setAction(action);if(direction!=null)i.putExtra("portrait",direction);return PendingIntent.getService(this,request,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private int res(String n,String type){return getResources().getIdentifier(n,type,getPackageName());}
    private Notification notification(){Notification.Builder b=new Notification.Builder(this,"mirror").setSmallIcon(res("ic_notification","drawable")).setContentTitle("모니터 피벗").setContentText(status).setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MirrorActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false).setVisibility(Notification.VISIBILITY_PRIVATE).setStyle(new Notification.DecoratedCustomViewStyle()).setCustomContentView(controls(false)).setCustomBigContentView(controls(true));if(Build.VERSION.SDK_INT>=31)b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);return b.build();}
    private RemoteViews controls(boolean expanded){
        RemoteViews views=new RemoteViews(getPackageName(),res(
                expanded?"notification_pivot_expanded":"notification_pivot","layout"));
        int portraitId=res("notification_portrait","id"), landscapeId=res("notification_landscape","id");
        directionControl(views,portraitId,"세로",portrait,1,true);
        directionControl(views,landscapeId,"가로",!portrait,2,false);
        views.setOnClickPendingIntent(res("notification_rotate","id"),action(ROTATE,8,null));
        if(expanded){
            PendingIntent editor=PendingIntent.getActivity(this,4,new Intent(this,RegionEntryActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            int edit=res("notification_edit","id"), partial=res("notification_partial","id");
            int guide=res("notification_guide","id"), cover=res("notification_cover","id");
            int hint=res("notification_hint","id");
            views.setTextViewText(hint,covered?"모니터 화면 가림 중":needsRegion?"휴대폰 방향이 바뀌었어요":
                    editing?"보여줄 부분 선택 중":scene.regions?"선택한 부분 표시 중":"전체 화면 표시 중");
            boolean dark=(getResources().getConfiguration().uiMode&48)==32;
            views.setTextColor(hint,dark?0xffc0c6d5:0xff515969);
            views.setTextViewText(edit,needsRegion?"부분 다시 선택":scene.regions?"부분 바꾸기":"보여줄 부분 선택");
            views.setOnClickPendingIntent(edit,editor);
            views.setViewVisibility(partial,scene.regions||scene.hasSavedRegions?View.VISIBLE:View.GONE);
            views.setTextViewText(partial,needsRegion?"전체 화면으로 보기":scene.regions?"부분 출력 해제":"이전 부분 다시 보기");
            views.setInt(partial,"setBackgroundResource",res(scene.regions?"notification_selected":"notification_unselected","drawable"));
            views.setTextColor(partial,scene.regions?0xffffffff:0xff142945);
            views.setOnClickPendingIntent(partial,action(PARTIAL,6,null));
            views.setViewVisibility(guide,scene.regions?View.VISIBLE:View.GONE);
            views.setTextViewText(guide,editing?"편집 취소":guides?"테두리 숨기기":"테두리 표시");
            views.setContentDescription(guide,editing?"편집 취소":guides?"휴대폰 테두리 숨기기":"휴대폰 테두리 표시");
            views.setOnClickPendingIntent(guide,action(GUIDE,5,null));
            views.setTextViewText(cover,covered?"모니터 다시 보이기":"모니터 가리기");
            views.setOnClickPendingIntent(cover,action(COVER,7,null));
            views.setOnClickPendingIntent(res("notification_stop","id"),action(STOP,3,null));
        }
        return views;
    }
    private void directionControl(RemoteViews views,int id,String label,boolean selected,int request,boolean vertical){
        views.setTextViewText(id,selected?"✓ "+label:label);
        views.setTextColor(id,selected?0xffffffff:0xff142945);
        views.setInt(id,"setBackgroundResource",res(selected?"notification_selected":"notification_unselected","drawable"));
        views.setOnClickPendingIntent(id,action(CONFIG,request,vertical));
    }
    private void notifyState(){sync();((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(41,notification());}
    private void fail(String why){status="화면 공유 실패: "+why;stopSelf();}
    @Override public void onDisplayAdded(int id){}
    @Override public void onDisplayRemoved(int id){if(id==outputDisplay){status="모니터 연결이 해제되었습니다.";stopSelf();}}
    @Override public void onDisplayChanged(int id){if(id==0&&Build.VERSION.SDK_INT<34){Point p=new Point();dm.getDisplay(0).getRealSize(p);resize(p.x,p.y);}if(id==outputDisplay&&renderer!=null){renderer.configure(scene);if(!editing)refreshGuide();}}
    @Override public void onDestroy(){
        cleaning=true;
        if(activeService==this)activeService=null;
        running=false;editing=false;guides=false;paused=false;covered=false;needsRegion=false;
        outputDisplay=-1;
        main.removeCallbacksAndMessages(null);
        dm.unregisterDisplayListener(this);
        if(overlay!=null){overlay.hide();overlay=null;}
        if(capture!=null){capture.release();capture=null;}
        if(renderer!=null){renderer.close();renderer=null;}
        if(presentation!=null){presentation.dismiss();presentation=null;}
        if(projection!=null){
            projection.unregisterCallback(callback);
            projection.stop();
            projection=null;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){return null;}
}
