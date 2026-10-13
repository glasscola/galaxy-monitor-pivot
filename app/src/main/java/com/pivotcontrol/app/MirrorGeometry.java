package com.pivotcontrol.app;

/** Geometry for rotating a captured phone image inside the monitor's native frame. */
public final class MirrorGeometry {
    private MirrorGeometry() {}
    public static int angle(int outputW,int outputH,boolean portrait,boolean reverse) {
        if (outputW<=0||outputH<=0) throw new IllegalArgumentException("Invalid output size");
        return ((outputH>outputW)==portrait?0:90)+(reverse?180:0);
    }
    public static float scale(int sourceW,int sourceH,int outputW,int outputH,int angle,boolean fill) {
        if (sourceW<=0||sourceH<=0||outputW<=0||outputH<=0||angle%90!=0) throw new IllegalArgumentException("Invalid geometry");
        boolean quarter=(angle/90)%2!=0;
        float x=outputW/(float)(quarter?sourceH:sourceW), y=outputH/(float)(quarter?sourceW:sourceH);
        return fill?Math.max(x,y):Math.min(x,y);
    }
    public static int[] captureSize(int w,int h) {
        if (w<=0||h<=0) throw new IllegalArgumentException("Invalid capture size");
        float s=Math.min(1f,1920f/Math.max(w,h));
        return new int[]{Math.max(2,Math.round(w*s/2)*2),Math.max(2,Math.round(h*s/2)*2)};
    }
}
