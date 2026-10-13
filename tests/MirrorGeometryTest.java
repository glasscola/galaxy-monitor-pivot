package com.pivotcontrol.app;

public class MirrorGeometryTest {
    static int checks;
    static void check(boolean ok,String label) { if (!ok) throw new AssertionError(label); checks++; System.out.println("PASS "+label); }
    static boolean close(float a,float b) { return Math.abs(a-b)<.001f; }
    public static void main(String[] args) {
        check(MirrorGeometry.angle(1920,1080,true,false)==90,"portrait monitor uses quarter-turn inside native landscape output");
        check(MirrorGeometry.angle(1920,1080,false,false)==0,"landscape monitor uses unrotated output");
        check(MirrorGeometry.angle(1920,1080,true,true)==270,"reverse portrait direction");
        check(MirrorGeometry.angle(1080,1920,true,false)==0&&MirrorGeometry.angle(1080,1920,false,true)==270,"already-portrait output dimensions");
        float fit=MirrorGeometry.scale(1080,2400,1920,1080,90,false);
        check(close(fit,.8f)&&close(2400*fit,1920)&&close(1080*fit,864),"portrait fit preserves entire phone image and aspect ratio");
        float fill=MirrorGeometry.scale(1080,2400,1920,1080,90,true);
        check(close(fill,1)&&2400*fill>1920&&close(1080*fill,1080),"portrait fill crops overlong edges without stretching");
        check(close(MirrorGeometry.scale(1080,2400,1920,1080,0,false),.45f),"landscape output fits portrait phone with side bars");
        check(close(MirrorGeometry.scale(2400,1080,1920,1080,0,false),.8f),"phone landscape rotation maintains aspect ratio");
        int[] scaled=MirrorGeometry.captureSize(1440,3200);
        check(scaled[0]==864&&scaled[1]==1920,"bound capture cost while preserving Galaxy aspect ratio");
        int[] small=MirrorGeometry.captureSize(720,1600);
        check(small[0]==720&&small[1]==1600,"avoid upscaling small capture");
        boolean rejected=false; try { MirrorGeometry.scale(0,2400,1920,1080,90,false); } catch (IllegalArgumentException e) { rejected=true; }
        check(rejected,"reject invalid capture geometry");
        System.out.println("All "+checks+" mirror geometry checks passed. Real rendering and device compatibility are not covered.");
    }
}
