package com.pivotcontrol.app;
import android.content.Context;
import android.graphics.*;
import android.hardware.input.InputManager;
import android.os.Build;
import android.view.*;
import android.widget.*;

final class RegionOverlay {
    interface Events{void apply(Scene s);void cancel();}
    private final Context context;private final WindowManager wm;private final Ui ui;private final Events events;
    private Frame frame;private View panel;private Scene draft;private boolean editing;private int selected,phoneW,phoneH,monitorW,monitorH;private boolean panelTop;
    RegionOverlay(Context c,Events e){context=c;events=e;wm=(WindowManager)c.getSystemService(Context.WINDOW_SERVICE);ui=new Ui(c);}
    void show(Scene s,boolean edit,int sw,int sh,int mw,int mh){hide();draft=s.copy();editing=edit;selected=Math.min(selected,draft.items.size()-1);phoneW=sw;phoneH=sh;monitorW=mw;monitorH=mh;
        try{frame=new Frame();WindowManager.LayoutParams p=params(-1,-1);if(!edit){p.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;float opacity=.75f;if(Build.VERSION.SDK_INT>=31)opacity=Math.min(opacity,((InputManager)context.getSystemService(Context.INPUT_SERVICE)).getMaximumObscuringOpacityForTouch());p.alpha=opacity;}
            wm.addView(frame,p);if(edit)toolbar();
        }catch(RuntimeException ex){hide();throw ex;}
    }
    private WindowManager.LayoutParams params(int w,int h){WindowManager.LayoutParams p=new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.LEFT;p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;p.setTitle("모니터 피벗 영역 안내");return p;}
    private void toolbar(){if(panel!=null){wm.removeView(panel);panel=null;}LinearLayout box=ui.column();box.setPadding(ui.dp(14),ui.dp(12),ui.dp(14),ui.dp(12));box.setBackground(ui.shape(0xff182233,20));
        LinearLayout head=ui.row();TextView heading=ui.title("영역 "+(char)('A'+selected)+" 편집");head.addView(heading,new LinearLayout.LayoutParams(0,-2,1));Button move=ui.button(panelTop?"도구 ↓":"도구 ↑",false);head.addView(move);move.setOnClickListener(v->{panelTop=!panelTop;toolbar();});box.addView(head);
        ui.add(box,ui.text("선 안을 끌어 이동 · 모서리/변을 당겨 크기 조절",12,Ui.MUTED),4);
        LinearLayout picks=ui.row();for(int i=0;i<draft.items.size();i++){final int k=i;Button b=ui.button(String.valueOf((char)('A'+i)),i==selected);ui.weight(picks,b);b.setOnClickListener(v->{selected=k;frame.invalidate();toolbar();});}if(draft.items.size()<4){Button plus=ui.button("＋",false);plus.setContentDescription("영역 추가");ui.weight(picks,plus);plus.setOnClickListener(v->{draft.add();if(draft.lockRatio)draft.matchMonitor(phoneW,phoneH,monitorW,monitorH);selected=draft.items.size()-1;frame.invalidate();toolbar();});}ui.add(box,picks,6);
        LinearLayout ratio=ui.row();Button free=ui.button("자유 비율",!draft.lockRatio),lock=ui.button("모니터 비율",draft.lockRatio);ui.weight(ratio,free);ui.weight(ratio,lock);free.setOnClickListener(v->{draft.lockRatio=false;toolbar();});lock.setOnClickListener(v->{draft.lockRatio=true;int[] size=draft.canvas(monitorW,monitorH);draft.matchMonitor(phoneW,phoneH,monitorW,monitorH);frame.invalidate();toolbar();});ui.add(box,ratio,6);
        LinearLayout quick=ui.row();String[] names={"전체","위 절반","아래 절반"};for(int i=0;i<3;i++){final int k=i;Button b=ui.button(names[i],false);ui.weight(quick,b);b.setOnClickListener(v->{draft.items.get(selected).source=new Scene.Box(0,k==2?.5f:0,1,k==1?.5f:1);draft.lockRatio=false;frame.invalidate();toolbar();});}ui.add(box,quick,6);
        LinearLayout actions=ui.row();Button cancel=ui.button("취소",false),dim=ui.button(draft.dim?"음영 끄기":"음영 켜기",false),apply=ui.button("적용",true);ui.weight(actions,cancel);ui.weight(actions,dim);ui.weight(actions,apply);cancel.setOnClickListener(v->events.cancel());dim.setOnClickListener(v->{draft.dim=!draft.dim;frame.invalidate();toolbar();});apply.setOnClickListener(v->{draft.regions=true;events.apply(draft.copy());});ui.add(box,actions,6);
        ScrollView scroll=new ScrollView(context);scroll.addView(box);panel=scroll;WindowManager.LayoutParams lp=params(Math.min(phoneW-ui.dp(24),ui.dp(480)),Math.min(ui.dp(330),Math.round(phoneH*.48f)));lp.gravity=(panelTop?Gravity.TOP:Gravity.BOTTOM)|Gravity.CENTER_HORIZONTAL;lp.y=ui.dp(panelTop?40:32);wm.addView(panel,lp);
    }
    void hide(){if(panel!=null){try{wm.removeView(panel);}catch(Exception ignored){}panel=null;}if(frame!=null){try{wm.removeView(frame);}catch(Exception ignored){}frame=null;}}
    private final class Frame extends View {
        final Paint paint=new Paint(3);final int[] offset=new int[2];float downX,downY;Scene.Box original;int edge;
        Frame(){super(context);setSystemUiVisibility(SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|SYSTEM_UI_FLAG_LAYOUT_STABLE);setContentDescription(editing?"영역 편집. 안쪽을 끌거나 손잡이로 크기를 조절하세요.":"선택 영역 안내. 터치는 사용 중인 앱에 전달됩니다.");}
        RectF rect(Scene.Box b){return new RectF(b.l*phoneW,b.t*phoneH,b.r*phoneW,b.b*phoneH);}
        @Override protected void onDraw(Canvas c){getLocationOnScreen(offset);c.save();c.translate(-offset[0],-offset[1]);
            // All guide paint is clipped OUTSIDE the union of captured rectangles.
            c.save();for(Scene.Region r:draft.items)c.clipOutRect(rect(r.source));
            if(editing&&draft.dim){paint.setColor(0x88080e19);paint.setStyle(Paint.Style.FILL);c.drawRect(0,0,phoneW,phoneH,paint);}
            for(int i=0;i<draft.items.size();i++){RectF r=rect(draft.items.get(i).source);paint.setColor(SceneCanvas.COLORS[i]);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(ui.dp(editing&&i==selected?6:4));c.drawRect(r,paint);}c.restore();
            if(editing){RectF r=rect(draft.items.get(selected).source);paint.setStyle(Paint.Style.FILL);paint.setColor(SceneCanvas.COLORS[selected]);for(float x:new float[]{r.left,r.centerX(),r.right})for(float y:new float[]{r.top,r.centerY(),r.bottom})if(x!=r.centerX()||y!=r.centerY())c.drawRoundRect(x-ui.dp(5),y-ui.dp(5),x+ui.dp(5),y+ui.dp(5),ui.dp(2),ui.dp(2),paint);}
            c.restore();
        }
        @Override public boolean onTouchEvent(MotionEvent e){if(!editing)return false;getLocationOnScreen(offset);float px=e.getX()+offset[0],py=e.getY()+offset[1],x=px/phoneW,y=py/phoneH;
            if(e.getAction()==MotionEvent.ACTION_DOWN){original=null;int found=-1;RectF current=rect(draft.items.get(selected).source);current.inset(-ui.dp(24),-ui.dp(24));if(current.contains(px,py))found=selected;else for(int i=draft.items.size()-1;i>=0;i--)if(draft.items.get(i).source.contains(x,y)){found=i;break;}if(found<0)return true;boolean changed=found!=selected;selected=found;original=draft.items.get(found).source.copy();downX=x;downY=y;RectF rr=rect(original);edge=0;if(Math.abs(px-rr.left)<ui.dp(24))edge|=1;else if(Math.abs(px-rr.right)<ui.dp(24))edge|=2;if(Math.abs(py-rr.top)<ui.dp(24))edge|=4;else if(Math.abs(py-rr.bottom)<ui.dp(24))edge|=8;if(changed)toolbar();invalidate();return true;}
            if(original==null)return true;
            if(e.getAction()==MotionEvent.ACTION_MOVE){Scene.Box b=original.copy();float dx=x-downX,dy=y-downY;
                if(edge==0)b.move(original.l+dx,original.t+dy);else{
                    if((edge&1)!=0)b.l=Math.min(b.r-.04f,Math.max(0,original.l+dx));if((edge&2)!=0)b.r=Math.max(b.l+.04f,Math.min(1,original.r+dx));if((edge&4)!=0)b.t=Math.min(b.b-.04f,Math.max(0,original.t+dy));if((edge&8)!=0)b.b=Math.max(b.t+.04f,Math.min(1,original.b+dy));
                    if(draft.lockRatio){int[] size=draft.canvas(monitorW,monitorH);float ratio=size[0]/(float)size[1];if((edge&3)==0){float targetW=b.h()*phoneH*ratio/phoneW;b.l=(original.l+original.r-targetW)/2;b.r=b.l+targetW;}b=Scene.aspect(b,ratio,phoneW,phoneH);}
                }draft.items.get(selected).source=b;invalidate();return true;}
            if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){original=null;performClick();return true;}return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
