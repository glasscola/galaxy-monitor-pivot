package com.pivotcontrol.app;

import java.util.ArrayList;

/** Normalized source/destination geometry, independent of Android for host tests. */
public final class Scene {
    public static final int FIT=0,FILL=1,PIXEL=2;
    public boolean portrait=true,reverse=false,regions=false,dim=true,highQuality=true,lockRatio=false;
    public int scaling=FIT,layout=0,fps=60;
    public float sharpness=0;
    public final ArrayList<Region> items=new ArrayList<>();
    public Scene() { items.add(new Region(.05f,.25f,.95f,.55f)); }
    public static final class Box {
        public float l,t,r,b;
        public Box(float l,float t,float r,float b) { this.l=l;this.t=t;this.r=r;this.b=b; }
        public float w(){return r-l;} public float h(){return b-t;}
        public Box copy(){return new Box(l,t,r,b);}
        public void clamp(){
            if(!Float.isFinite(l)||!Float.isFinite(t)||!Float.isFinite(r)||!Float.isFinite(b)){l=0;t=0;r=1;b=1;}
            float w=Math.max(.03f,Math.min(1,r-l)),h=Math.max(.03f,Math.min(1,b-t));
            l=Math.max(0,Math.min(1-w,l));t=Math.max(0,Math.min(1-h,t));r=l+w;b=t+h;
        }
        public void move(float x,float y){float w=w(),h=h();l=Math.max(0,Math.min(1-w,x));t=Math.max(0,Math.min(1-h,y));r=l+w;b=t+h;}
        public boolean contains(float x,float y){return x>=l&&x<=r&&y>=t&&y<=b;}
    }
    public static final class Region {
        public Box source,dest;
        public Region(float l,float t,float r,float b){source=new Box(l,t,r,b);dest=new Box(0,0,1,1);}
        public Region copy(){Region v=new Region(source.l,source.t,source.r,source.b);v.dest=dest.copy();return v;}
    }
    public Scene copy(){Scene s=new Scene();s.portrait=portrait;s.reverse=reverse;s.regions=regions;s.dim=dim;s.highQuality=highQuality;s.lockRatio=lockRatio;s.scaling=scaling;s.layout=layout;s.fps=fps;s.sharpness=sharpness;s.items.clear();for(Region r:items)s.items.add(r.copy());return s;}
    public int[] canvas(int w,int h){return portrait?new int[]{Math.min(w,h),Math.max(w,h)}:new int[]{Math.max(w,h),Math.min(w,h)};}
    public void arrange(){
        int n=items.size();if(layout==2)return;
        for(int i=0;i<n;i++)items.get(i).dest=layout==0?new Box(0,i/(float)n,1,(i+1f)/n):new Box(i/(float)n,0,(i+1f)/n,1);
    }
    public void add(){if(items.size()>=4)return;int n=items.size();items.add(new Region(.05f,.1f+n*.15f,.95f,.3f+n*.15f));if(layout==2)items.get(n).dest=new Box(.15f,.15f,.85f,.85f);else arrange();}
    public void remove(int index){if(items.size()>1){items.remove(index);arrange();}}
    public void matchMonitor(int sw,int sh,int mw,int mh){int[] c=canvas(mw,mh);for(Region r:items)r.source=aspect(r.source,c[0]/(float)c[1],sw,sh);}
    public static int[] captureSize(int w,int h,boolean high){if(w<=0||h<=0)throw new IllegalArgumentException("size");float k=high?1:Math.min(1,1920f/Math.max(w,h));return new int[]{Math.max(2,Math.round(w*k/2)*2),Math.max(2,Math.round(h*k/2)*2)};}
    /** Fit a rectangle into unit bounds at a physical aspect ratio. */
    public static Box aspect(Box old,float ratio,int width,int height){
        if(ratio<=0||width<=0||height<=0)return old.copy();
        float normalized=ratio*height/width,w=Math.max(old.w(),Math.max(.03f,.03f*normalized)),h=w/normalized;
        if(h>1){h=1;w=h*normalized;}if(w>1){w=1;h=w/normalized;}
        Box b=new Box((old.l+old.r-w)/2,(old.t+old.b-h)/2,(old.l+old.r+w)/2,(old.t+old.b+h)/2);b.clamp();return b;
    }
    /** Source and destination pixels after aspect-preserving fit/fill/1:1. */
    public static float[] placement(Box source,Box dest,int sw,int sh,int ow,int oh,int mode){
        float sl=source.l*sw,st=source.t*sh,srw=source.w()*sw,srh=source.h()*sh;
        float dl=dest.l*ow,dt=dest.t*oh,dw=dest.w()*ow,dh=dest.h()*oh;
        float k=mode==FILL?Math.max(dw/srw,dh/srh):Math.min(dw/srw,dh/srh);
        if(mode==PIXEL)k=1;
        float visibleW=Math.min(srw,dw/k),visibleH=Math.min(srh,dh/k);
        sl+=(srw-visibleW)/2;st+=(srh-visibleH)/2;
        float outW=visibleW*k,outH=visibleH*k;
        return new float[]{sl/sw,st/sh,(sl+visibleW)/sw,(st+visibleH)/sh,dl+(dw-outW)/2,dt+(dh-outH)/2,outW,outH,k};
    }
}
