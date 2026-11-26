package com.pivotcontrol.app;
import android.content.Context;
import android.graphics.*;
import android.hardware.input.InputManager;
import android.os.Build;
import android.view.*;
import android.widget.*;

final class RegionOverlay {
    interface Events{void apply(Scene s);void cancel();}
    private final Context context;private final WindowManager wm;private final Events events;
    private Ui ui;
    private View monitorPreview;
    private Frame frame;private View panel;private TextView zoomHint;private long nextZoomUpdate;private Scene draft;private boolean editing;private int selected,phoneW,phoneH,monitorW,monitorH;private boolean panelTop;
    private boolean toolsExpanded,ratioExpanded;
    RegionOverlay(Context c,Events e){context=c;events=e;wm=(WindowManager)c.getSystemService(Context.WINDOW_SERVICE);}
    void show(Scene s,boolean edit,int sw,int sh,int mw,int mh){hide();draft=s.copy();editing=edit;selected=Math.min(selected,draft.items.size()-1);phoneW=sw;phoneH=sh;monitorW=mw;monitorH=mh;
        ui=new Ui(context);
        toolsExpanded=false;ratioExpanded=false;
        try{frame=new Frame();WindowManager.LayoutParams p=params(-1,-1);if(!edit){p.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;float opacity=.75f;if(Build.VERSION.SDK_INT>=31)opacity=Math.min(opacity,((InputManager)context.getSystemService(Context.INPUT_SERVICE)).getMaximumObscuringOpacityForTouch());p.alpha=opacity;}
            wm.addView(frame,p);if(edit)toolbar();
        }catch(RuntimeException ex){hide();throw ex;}
    }
    private WindowManager.LayoutParams params(int w,int h){WindowManager.LayoutParams p=new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.LEFT;p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;p.setTitle("모니터 피벗 영역 안내");return p;}
    private void toolbar(){
        if(panel!=null){wm.removeView(panel);panel=null;}
        LinearLayout box=ui.column();
        box.setPadding(ui.dp(12),ui.dp(6),ui.dp(12),ui.dp(8));
        box.setBackground(ui.shape(ui.cardSurface,20));
        LinearLayout content=ui.column();
        LinearLayout previewRow=ui.row();
        monitorPreview=new MonitorPreview();
        previewRow.addView(monitorPreview,new LinearLayout.LayoutParams(ui.dp(86),ui.dp(68)));
        LinearLayout previewText=ui.column();
        ui.add(previewText,ui.text("모니터에 보일 배치",14,ui.textColor),0);
        zoomHint=ui.text("",12,ui.muted);
        ui.add(previewText,zoomHint,2);
        ui.weight(previewRow,previewText);
        ui.add(content,previewRow,0);
        updateZoomHint();
        LinearLayout picks=ui.row();
        for(int i=0;i<draft.items.size();i++){
            int index=i;
            String label=String.valueOf((char)('A'+i));
            Button pick=ui.compactButton(label,i==selected);
            pick.setContentDescription("부분 "+label);
            picks.addView(pick,new LinearLayout.LayoutParams(ui.dp(48),-2));
            pick.setOnClickListener(v->{selected=index;frame.invalidate();toolbar();});
        }
        if(draft.items.size()<4){
            Button plus=ui.compactButton("＋",false);
            plus.setContentDescription("다른 부분 추가");
            picks.addView(plus,new LinearLayout.LayoutParams(ui.dp(48),-2));
            plus.setOnClickListener(v->{
                draft.add();
                if(draft.lockRatio)draft.matchMonitor(phoneW,phoneH,monitorW,monitorH);
                selected=draft.items.size()-1;
                frame.invalidate();toolbar();
            });
        }
        ui.add(content,ui.title("보여줄 부분"),0);
        ui.add(content,picks,0);
        if(draft.items.size()>1){
            LinearLayout layout=ui.row();
            for(int mode=0;mode<2;mode++){
                int choice=mode;
                String label=mode==0?"위아래":"나란히";
                Button button=ui.compactButton((draft.layout==mode?"✓ ":"")+label,false);
                ui.weight(layout,button);
                button.setOnClickListener(v->{draft.layout=choice;draft.arrange();toolbar();});
            }
            ui.add(content,ui.text("모니터에 놓는 방법",14,ui.textColor),4);
            ui.add(content,layout,2);
        }

        LinearLayout tools=ui.row();
        Button ratio=ui.compactButton((draft.lockRatio?"모니터 비율":"자유 비율")+(ratioExpanded?" ▴":" ▾"),false);
        Button more=ui.compactButton(toolsExpanded?"도구 접기 ▴":"도구 더보기 ▾",false);
        ui.weight(tools,ratio);ui.weight(tools,more);
        ratio.setOnClickListener(v->{ratioExpanded=!ratioExpanded;toolsExpanded=false;toolbar();});
        more.setOnClickListener(v->{toolsExpanded=!toolsExpanded;ratioExpanded=false;toolbar();});
        ui.add(content,tools,0);
        if(ratioExpanded){
            LinearLayout choices=ui.row();
            for(boolean locked:new boolean[]{false,true}){
                Button option=ui.compactButton(locked?"모니터 비율":"자유 비율",draft.lockRatio==locked);
                ui.weight(choices,option);
                option.setOnClickListener(v->{
                    draft.lockRatio=locked;
                    if(locked)draft.matchMonitor(phoneW,phoneH,monitorW,monitorH);
                    ratioExpanded=false;frame.invalidate();toolbar();
                });
            }
            ui.add(content,choices,0);
        }
        if(toolsExpanded)addQuickTools(content);
        int scrollLimit=Math.max(ui.dp(48),Math.min(ui.dp(270),phoneH*2/3-ui.dp(115)));
        ScrollView scroll=new ScrollView(context){
            @Override protected void onMeasure(int widthSpec,int heightSpec){
                super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(scrollLimit,MeasureSpec.AT_MOST));
            }
        };
        scroll.addView(content);
        ui.add(box,scroll,0);

        LinearLayout actions=ui.row();
        Button cancel=ui.compactButton("취소",false),apply=ui.button(draft.items.size()>1?"선택한 부분 보여주기":"이 부분 보여주기",true);
        actions.addView(cancel,new LinearLayout.LayoutParams(ui.dp(64),-2));
        ui.weight(actions,apply);
        cancel.setOnClickListener(v->events.cancel());
        apply.setOnClickListener(v->{draft.regions=true;events.apply(draft.copy());});
        ui.add(box,actions,4);
        ui.add(box,ui.text("스크롤하면 테두리 안 내용도 바뀌어요.",12,ui.muted),4);
        panel=box;
        WindowManager.LayoutParams lp=params(Math.min(phoneW-ui.dp(24),ui.dp(480)),-2);
        lp.gravity=(panelTop?Gravity.TOP:Gravity.BOTTOM)|Gravity.CENTER_HORIZONTAL;
        lp.y=ui.dp(panelTop?40:32);
        wm.addView(panel,lp);
        if(ratioExpanded||toolsExpanded)scroll.post(()->scroll.smoothScrollTo(0,tools.getTop()));
    }
    private void addQuickTools(LinearLayout content){
        LinearLayout quick=ui.row();
        String[] names={"전체 범위","위 절반","아래 절반"};
        for(int i=0;i<names.length;i++){
            int preset=i;
            Button button=ui.compactButton(names[i],false);
            ui.weight(quick,button);
            button.setOnClickListener(v->{
                draft.items.get(selected).source=new Scene.Box(0,preset==2?.5f:0,1,preset==1?.5f:1);
                draft.lockRatio=false;frame.invalidate();toolbar();
            });
        }
        ui.add(content,quick,0);
        LinearLayout options=ui.row();
        Button dim=ui.compactButton(draft.dim?"음영 끄기":"음영 켜기",false);
        Button move=ui.compactButton(panelTop?"도구를 아래로":"도구를 위로",false);
        ui.weight(options,dim);ui.weight(options,move);
        dim.setOnClickListener(v->{draft.dim=!draft.dim;frame.invalidate();toolbar();});
        move.setOnClickListener(v->{panelTop=!panelTop;toolbar();});
        ui.add(content,options,0);
        ui.add(content,ui.text("테두리 안을 끌어 옮기고 모서리를 당겨 크기를 조절하세요.",12,ui.muted),2);
    }
    private void updateZoomHint(){
        if(zoomHint==null)return;
        int[] input=Scene.captureSize(phoneW,phoneH,draft.highQuality);
        int[] canvas=draft.canvas(monitorW,monitorH);
        Scene.Region region=draft.items.get(selected);
        float[] visible=Scene.placement(region.source,region.dest,input[0],input[1],
                canvas[0],canvas[1],draft.scaling);
        float scale=visible[8];
        String label=String.format(java.util.Locale.KOREA,"약 %.1f배 확대",scale);
        if(scale<=1f)label=String.format(java.util.Locale.KOREA,"표시 크기 %.1f배",scale);
        if(visible[2]-visible[0]+.001f<region.source.w()||visible[3]-visible[1]+.001f<region.source.h())
            label+=" · 가장자리 잘림";
        if(scale>1.5f)label+="\n작은 글자가 흐려질 수 있어요.";
        Ui.update(zoomHint,label);
        monitorPreview.setContentDescription("모니터 배치 도식 · "+label.replace('\n',' '));
        monitorPreview.invalidate();
        nextZoomUpdate=android.os.SystemClock.uptimeMillis()+100;
    }
    private final class MonitorPreview extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        MonitorPreview(){super(context);}

        @Override protected void onDraw(Canvas canvas){
            int[] output=draft.canvas(monitorW,monitorH);
            int[] input=Scene.captureSize(phoneW,phoneH,draft.highQuality);
            float padding=ui.dp(3);
            float scale=Math.min((getWidth()-2*padding)/output[0],(getHeight()-2*padding)/output[1]);
            float left=(getWidth()-output[0]*scale)/2,top=(getHeight()-output[1]*scale)/2;
            RectF screen=new RectF(left,top,left+output[0]*scale,top+output[1]*scale);
            paint.setColor(ui.background);
            canvas.drawRect(screen,paint);
            paint.setColor(ui.muted);
            paint.setStrokeWidth(ui.dp(1));
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRect(screen,paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.save();
            canvas.clipRect(screen);
            for(int i=0;i<draft.items.size();i++){
                Scene.Region region=draft.items.get(i);
                float[] bounds=Scene.placement(region.source,region.dest,input[0],input[1],
                        output[0],output[1],draft.scaling);
                float x=left+bounds[4]*scale,y=top+bounds[5]*scale;
                float right=x+bounds[6]*scale,bottom=y+bounds[7]*scale;
                paint.setColor(SceneCanvas.COLORS[i]);
                paint.setAlpha(i==selected?230:145);
                canvas.drawRect(x,y,right,bottom,paint);
                if(draft.items.size()>1&&bottom-y>=ui.dp(13)&&right-x>=ui.dp(13)){
                    paint.setColor(0xff101b2a);
                    paint.setTextAlign(Paint.Align.CENTER);
                    paint.setTextSize(ui.dp(11));
                    canvas.drawText(String.valueOf((char)('A'+i)),(x+right)/2,(y+bottom)/2+ui.dp(4),paint);
                }
            }
            canvas.restore();
        }
    }
    void hide(){zoomHint=null;if(panel!=null){try{wm.removeView(panel);}catch(Exception ignored){}panel=null;}if(frame!=null){try{wm.removeView(frame);}catch(Exception ignored){}frame=null;}}
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
                }
                b.clamp();
                draft.items.get(selected).source=b;
                if(android.os.SystemClock.uptimeMillis()>=nextZoomUpdate)updateZoomHint();
                invalidate();return true;
            }
            if(e.getAction()==MotionEvent.ACTION_CANCEL){draft.items.get(selected).source=original;original=null;updateZoomHint();invalidate();return true;}
            if(e.getAction()==MotionEvent.ACTION_UP){original=null;updateZoomHint();performClick();return true;}return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
