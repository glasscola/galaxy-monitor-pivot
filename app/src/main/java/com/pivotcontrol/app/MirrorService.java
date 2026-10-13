package com.pivotcontrol.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Presentation;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.RemoteViews;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/** User-consented screen sharing to a rotated external Presentation. No recording/network. */
public class MirrorService extends Service implements DisplayManager.DisplayListener {
    public static final String START="com.pivotcontrol.app.MIRROR_START", CONFIG="com.pivotcontrol.app.MIRROR_CONFIG", STOP="com.pivotcontrol.app.MIRROR_STOP";
    public static volatile boolean running, portrait=true, reversed, fill;
    public static volatile int outputDisplay=-1;
    public static volatile String status="화면 복제 시작을 눌러 주세요.";
    private final Handler main=new Handler(Looper.getMainLooper());
    private DisplayManager dm;
    private MediaProjection projection;
    private VirtualDisplay capture;
    private Presentation presentation;
    private TextureView texture;
    private Surface surface;
    private SurfaceTexture buffer;
    private int captureW,captureH,density;
    private boolean cleaning;
    private final MediaProjection.Callback callback=new MediaProjection.Callback() {
        @Override public void onStop() { status="화면 공유가 종료되었습니다."; stopSelf(); }
        @Override public void onCapturedContentResize(int width,int height) { resize(width,height); }
    };

    @Override public void onCreate() {
        super.onCreate(); dm=(DisplayManager)getSystemService(DISPLAY_SERVICE); dm.registerDisplayListener(this,main);
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel("mirror","외부 화면 복제",NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if (intent==null) { stopSelf(); return START_NOT_STICKY; }
        String action=intent.getAction();
        if (STOP.equals(action)) { status="화면 복제를 종료했습니다. 기본 미러링으로 돌아갑니다."; stopSelf(); return START_NOT_STICKY; }
        if (CONFIG.equals(action)) {
            if (projection==null||cleaning) { stopSelf(); return START_NOT_STICKY; }
            readOptions(intent); transform(); notifyState(); return START_NOT_STICKY;
        }
        if (!START.equals(action)) { stopSelf(); return START_NOT_STICKY; }
        if (projection!=null) return START_NOT_STICKY;
        try {
            readOptions(intent);
            startForeground(41,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            outputDisplay=intent.getIntExtra("display",-1);
            Display external=dm.getDisplay(outputDisplay);
            if (external==null||outputDisplay==Display.DEFAULT_DISPLAY||(external.getFlags()&Display.FLAG_PRESENTATION)==0)
                throw new Exception("외부 모니터를 찾을 수 없습니다. 케이블 연결을 확인해 주세요.");
            Intent grant;
            if (Build.VERSION.SDK_INT>=33) grant=intent.getParcelableExtra("grant",Intent.class);
            else grant=intent.getParcelableExtra("grant");
            if (grant==null) throw new Exception("화면 공유 승인이 필요합니다.");
            projection=((MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE)).getMediaProjection(intent.getIntExtra("result",0),grant);
            if (projection==null) throw new Exception("화면 공유를 시작하지 못했습니다.");
            projection.registerCallback(callback,main);
            Point size=new Point(); dm.getDisplay(Display.DEFAULT_DISPLAY).getRealSize(size);
            int[] initial=MirrorGeometry.captureSize(size.x,size.y); captureW=initial[0]; captureH=initial[1];
            density=getResources().getDisplayMetrics().densityDpi;
            showExternal(external);
            status="모니터 화면을 준비하고 있습니다…";
        } catch (Exception e) { fail(e); }
        return START_NOT_STICKY;
    }
    private void readOptions(Intent in) {
        portrait=in.getBooleanExtra("portrait",portrait); reversed=in.getBooleanExtra("reverse",reversed); fill=in.getBooleanExtra("fill",fill);
        getSharedPreferences("mirror",MODE_PRIVATE).edit().putBoolean("portrait",portrait).putBoolean("reverse",reversed).putBoolean("fill",fill).apply();
    }
    private void showExternal(Display display) {
        presentation=new Presentation(this,display,android.R.style.Theme_Material_NoActionBar);
        Window window=presentation.getWindow();
        window.setBackgroundDrawableResource(android.R.color.black);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        texture=new TextureView(presentation.getContext()); texture.setOpaque(false);
        texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture st,int width,int height) {
                if (cleaning) return;
                try {
                    buffer=st; st.setDefaultBufferSize(captureW,captureH); surface=new Surface(st);
                    if (capture==null) capture=projection.createVirtualDisplay("Pivot phone capture",captureW,captureH,density,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,surface,null,main);
                    else capture.setSurface(surface);
                    running=true; status="화면 복제 중 · 휴대폰에서 유튜브 등 원하는 앱을 여세요."; transform(); notifyState();
                } catch (Exception e) { fail(e); }
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st,int width,int height) { transform(); }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                if (!cleaning) { status="외부 모니터 화면이 종료되었습니다. 다시 시작해 주세요."; stopSelf(); }
                return true;
            }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture st) {}
        });
        presentation.setContentView(texture);
        presentation.setOnDismissListener(dialog -> { if (!cleaning) { status="모니터 연결이 해제되어 복제를 종료했습니다."; stopSelf(); } });
        presentation.show(); window.setLayout(-1,-1);
    }
    private void resize(int w,int h) {
        if (cleaning||w<=0||h<=0) return;
        int[] size=MirrorGeometry.captureSize(w,h);
        if (captureW==size[0]&&captureH==size[1]) return;
        captureW=size[0]; captureH=size[1];
        try {
            if (buffer!=null) buffer.setDefaultBufferSize(captureW,captureH);
            if (capture!=null) capture.resize(captureW,captureH,density);
            transform();
        } catch (Exception e) { fail(e); }
    }
    private void transform() {
        if (texture==null||captureW<=0||texture.getWidth()<=0||texture.getHeight()<=0) return;
        int w=texture.getWidth(),h=texture.getHeight();
        int angle=MirrorGeometry.angle(w,h,portrait,reversed);
        float scale=MirrorGeometry.scale(captureW,captureH,w,h,angle,fill);
        Matrix m=new Matrix();
        // TextureView initially stretches the buffer to its view size. Undo this first.
        m.setScale(captureW/(float)w,captureH/(float)h);
        m.postTranslate(-captureW/2f,-captureH/2f);
        m.postRotate(angle); m.postScale(scale,scale); m.postTranslate(w/2f,h/2f);
        texture.setTransform(m);
    }
    private PendingIntent serviceAction(String action,int request,Boolean direction) {
        Intent i=new Intent(this,MirrorService.class).setAction(action);
        if (direction!=null) i.putExtra("portrait",direction.booleanValue());
        return PendingIntent.getService(this,request,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private Notification notification() {
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MirrorActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(this,"mirror").setSmallIcon(resource("ic_notification","drawable"))
            .setContentTitle("모니터 피벗 · "+(portrait?"세로":"가로")+" 선택됨")
            .setContentText("세로 또는 가로 버튼을 누르면 바로 전환합니다")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setSound(null).setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC).setCategory(Notification.CATEGORY_SERVICE)
            .setStyle(new Notification.DecoratedCustomViewStyle())
            .setCustomContentView(controls(false)).setCustomBigContentView(controls(true));
        if (Build.VERSION.SDK_INT>=31) builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        return builder.build();
    }
    private int resource(String name,String type) { return getResources().getIdentifier(name,type,getPackageName()); }
    private RemoteViews controls(boolean expanded) {
        RemoteViews views=new RemoteViews(getPackageName(),resource(expanded?"notification_pivot_expanded":"notification_pivot","layout"));
        int p=resource("notification_portrait","id"),l=resource("notification_landscape","id"),stop=resource("notification_stop","id");
        views.setTextViewText(p,portrait?"✓ 세로":"세로"); views.setTextViewText(l,portrait?"가로":"✓ 가로");
        views.setTextColor(p,portrait?0xff161323:0xfff6f4ff); views.setTextColor(l,portrait?0xfff6f4ff:0xff161323);
        views.setInt(p,"setBackgroundResource",resource(portrait?"notification_selected":"notification_unselected","drawable"));
        views.setInt(l,"setBackgroundResource",resource(portrait?"notification_unselected":"notification_selected","drawable"));
        // Both collapsed and expanded buttons target the running service, never an Activity.
        views.setOnClickPendingIntent(p,serviceAction(CONFIG,1,true));
        views.setOnClickPendingIntent(l,serviceAction(CONFIG,2,false));
        views.setOnClickPendingIntent(stop,serviceAction(STOP,3,null));
        if (expanded) {
            boolean dark=(getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
            views.setTextColor(resource("notification_hint","id"),dark?0xffc0c6d5:0xff515969);
        }
        return views;
    }
    private void notifyState() { ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(41,notification()); }
    private void fail(Exception e) { status="화면 복제 실패: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()); stopSelf(); }
    @Override public void onDisplayAdded(int id) {}
    @Override public void onDisplayRemoved(int id) { if (id==outputDisplay) { status="모니터 연결이 해제되었습니다."; stopSelf(); } }
    @Override public void onDisplayChanged(int id) {
        if (id==Display.DEFAULT_DISPLAY&&Build.VERSION.SDK_INT<34) { Point p=new Point(); Display d=dm.getDisplay(id); if (d!=null) { d.getRealSize(p); resize(p.x,p.y); } }
        if (id==outputDisplay) transform();
    }
    @Override public void onDestroy() {
        cleaning=true; running=false; outputDisplay=-1; main.removeCallbacksAndMessages(null); dm.unregisterDisplayListener(this);
        if (capture!=null) { capture.release(); capture=null; }
        if (surface!=null) { surface.release(); surface=null; }
        if (presentation!=null) { presentation.dismiss(); presentation=null; }
        if (projection!=null) { projection.unregisterCallback(callback); projection.stop(); projection=null; }
        stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy();
    }
    @Override public IBinder onBind(Intent in) { return null; }
}
