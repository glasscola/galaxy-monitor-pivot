package com.pivotcontrol.app;
import android.content.Context;
import android.graphics.*;
import android.view.*;

/** Interactive layout plan. It never mirrors its own UI back into itself. */
final class SceneCanvas extends View {
    interface Change{void changed(int index);}
    Scene scene;int selected;boolean destination;int sourceW=1080,sourceH=2400,outputW=1920,outputH=1080;
    private final Paint paint=new Paint(3);private final RectF bounds=new RectF();private final float density;private final Change change;
    private Scene.Box original;private float downX,downY;private boolean resize;
    static final int[] COLORS={0xff6a9bff,0xff57dbc3,0xffc19bff,0xffffc178};
    SceneCanvas(Context c,Scene s,boolean dest,Change callback){super(c);scene=s;destination=dest;change=callback;density=c.getResources().getDisplayMetrics().density;setMinimumHeight((int)(260*density));setContentDescription(dest?"모니터 배치 미리보기. 영역을 끌어 이동하고 오른쪽 아래 손잡이로 크기를 조절합니다.":"휴대폰에서 선택한 영역의 위치 미리보기");}
    @Override protected void onDraw(Canvas c){
        int[] canvas=scene.canvas(outputW,outputH);float ratio=destination?canvas[0]/(float)canvas[1]:sourceW/(float)sourceH;
        float w=getWidth()-40*density,h=getHeight()-28*density;if(w/h>ratio)w=h*ratio;else h=w/ratio;
        bounds.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);paint.setStyle(Paint.Style.FILL);paint.setColor(0xff090f1b);c.drawRoundRect(bounds,8*density,8*density,paint);
        for(int i=0;i<scene.items.size();i++){
            Scene.Box b=destination?scene.items.get(i).dest:scene.items.get(i).source;RectF r=rect(b);paint.setStyle(Paint.Style.FILL);paint.setColor((COLORS[i]&0x00ffffff)|0x33000000);c.drawRect(r,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth((i==selected?3:1)*density);paint.setColor(COLORS[i]);c.drawRect(r,paint);paint.setStyle(Paint.Style.FILL);paint.setTextSize(17*getResources().getDisplayMetrics().scaledDensity);paint.setTypeface(Typeface.DEFAULT_BOLD);c.drawText(String.valueOf((char)('A'+i)),r.left+8*density,Math.min(r.bottom-3*density,r.top+23*density),paint);
            if(i==selected&&(!destination||scene.layout==2)){c.drawCircle(r.right,r.bottom,7*density,paint);}
        }
    }
    private RectF rect(Scene.Box b){return new RectF(bounds.left+b.l*bounds.width(),bounds.top+b.t*bounds.height(),bounds.left+b.r*bounds.width(),bounds.top+b.b*bounds.height());}
    @Override public boolean onTouchEvent(android.view.MotionEvent e){if(destination&&scene.layout!=2)return false;float x=(e.getX()-bounds.left)/bounds.width(),y=(e.getY()-bounds.top)/bounds.height();
        if(e.getAction()==MotionEvent.ACTION_DOWN){for(int i=scene.items.size()-1;i>=0;i--){Scene.Box box=destination?scene.items.get(i).dest:scene.items.get(i).source;RectF r=rect(box);r.inset(-20*density,-20*density);if(r.contains(e.getX(),e.getY())){selected=i;original=box.copy();resize=Math.abs(e.getX()-rect(original).right)<30*density&&Math.abs(e.getY()-rect(original).bottom)<30*density;downX=x;downY=y;getParent().requestDisallowInterceptTouchEvent(true);invalidate();return true;}}return false;}
        if(original==null)return false;
        if(e.getAction()==MotionEvent.ACTION_MOVE){Scene.Box b=original.copy();if(resize){b.r=Math.max(b.l+.05f,Math.min(1,original.r+x-downX));b.b=Math.max(b.t+.05f,Math.min(1,original.b+y-downY));if(!destination&&scene.lockRatio){int[] size=scene.canvas(outputW,outputH);b=Scene.aspect(b,size[0]/(float)size[1],sourceW,sourceH);}}else b.move(original.l+x-downX,original.t+y-downY);if(destination)scene.items.get(selected).dest=b;else scene.items.get(selected).source=b;invalidate();return true;}
        if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){getParent().requestDisallowInterceptTouchEvent(false);original=null;change.changed(selected);performClick();return true;}return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
