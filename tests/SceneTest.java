package com.pivotcontrol.app;
import java.util.Random;
public class SceneTest {
    static int checks;
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    static boolean close(float a,float b){return Math.abs(a-b)<.002f;}
    public static void main(String[] args){
        Scene s=new Scene();s.add();s.add();s.add();s.add();check(s.items.size()==4,"region cap");
        for(int i=0;i<4;i++){Scene.Box b=s.items.get(i).dest;check(close(b.t,i*.25f)&&close(b.h(),.25f),"vertical division");}
        s.layout=1;s.arrange();for(int i=0;i<4;i++)check(close(s.items.get(i).dest.l,i*.25f),"horizontal division");
        s.layout=2;Scene.Box before=s.items.get(0).dest.copy();s.arrange();check(close(before.l,s.items.get(0).dest.l),"free placement preserved");
        Scene copy=s.copy();copy.items.get(0).source.l=.4f;check(!close(s.items.get(0).source.l,.4f),"editing draft does not change committed scene");
        int[] high=Scene.captureSize(1440,3200,true),low=Scene.captureSize(1440,3200,false);check(high[0]==1440&&high[1]==3200,"retain native source");check(low[0]==864&&low[1]==1920,"performance downscale");
        Scene.Box src=new Scene.Box(0,0,1,1),dst=new Scene.Box(0,0,1,1);
        float[] a=Scene.placement(src,dst,1080,2400,1920,1080,Scene.FIT);check(close(a[8],.45f)&&close(a[6],486)&&close(a[7],1080),"fit adds horizontal margins");
        a=Scene.placement(src,dst,1080,2400,1920,1080,Scene.FILL);check(close(a[6],1920)&&close(a[7],1080)&&a[1]>0&&a[3]<1,"fill trims without distortion");
        a=Scene.placement(new Scene.Box(0,0,.5f,.5f),dst,1080,2400,1920,1080,Scene.PIXEL);check(close(a[8],1)&&close(a[6],540)&&close(a[7],1080)&&a[1]>0,"1:1 crops oversize without resampling");
        Random random=new Random(42);for(int i=0;i<2000;i++){
            Scene.Box b=new Scene.Box(random.nextFloat(),random.nextFloat(),random.nextFloat(),random.nextFloat());b.clamp();
            check(b.l>=0&&b.t>=0&&b.r<=1.00001&&b.b<=1.00001&&b.w()>.029f&&b.h()>.029f,"bounded box");
            float ratio=i%2==0?16f/9:9f/16;Scene.Box fixed=Scene.aspect(b,ratio,1080,2400);check(close(fixed.w()*1080/(fixed.h()*2400),ratio),"locked physical ratio");
            float[] p=Scene.placement(b,dst,1080,2400,1920,1080,i%3);check(p[0]>=b.l-.00001&&p[1]>=b.t-.00001&&p[2]<=b.r+.00001&&p[3]<=b.b+.00001,"sample remains within selected crop");check(p[4]>=-.001&&p[5]>=-.001&&p[4]+p[6]<=1920.01&&p[5]+p[7]<=1080.01,"output within slot");
        }
        System.out.println("PASS "+checks+" scene geometry checks");
    }
}
