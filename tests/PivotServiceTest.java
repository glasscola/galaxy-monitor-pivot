package com.pivotcontrol.app;
import android.os.Parcel;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ScheduledExecutorService;

/** Host tests execute real service transaction/rollback logic with fake Android and wm I/O. */
public class PivotServiceTest {
    public static class Info { public String uniqueId="external-A"; public int type=2, rotation=0; }
    static class Fake extends PivotService {
        final Info info=new Info(); String rotation="free", fixed="default";
        boolean disconnected, failLock; int writes;
        @Override Object readDisplayInfo(int id) { return disconnected?null:info; }
        @Override String run(String[] cmd) throws Exception {
            if (cmd.length==4) return cmd[1].equals("user-rotation")?rotation:fixed;
            writes++;
            if (cmd[1].equals("fixed-to-user-rotation")) { fixed=cmd[4]; return ""; }
            if (failLock&&cmd[4].equals("lock")&&cmd[5].equals("1")) throw new Exception("Firmware refused lock");
            rotation=cmd[4].equals("free")?"free":"lock "+cmd[5];
            if (rotation.startsWith("lock")) info.rotation=Integer.parseInt(cmd[5]);
            return "";
        }
    }
    static int count;
    static void check(boolean good,String name) { if (!good) throw new AssertionError(name); count++; System.out.println("PASS "+name); }
    static String request(Fake service,int code,int id,String unique,Object extra) throws Exception {
        Parcel in=new Parcel(),out=new Parcel(); in.writeString(PivotService.TOKEN); in.writeInt(id); in.writeString(unique);
        if (extra instanceof Integer) in.writeInt((Integer)extra); else if (extra instanceof String) in.writeString((String)extra);
        service.onTransact(code,in,out,0); return out.readString();
    }
    static String request(Fake s,int code,Object extra) throws Exception { return request(s,code,2,"external-A",extra); }
    static void stop(Fake s) throws Exception { Field f=PivotService.class.getDeclaredField("timer"); f.setAccessible(true); ((ScheduledExecutorService)f.get(s)).shutdownNow(); }
    public static void main(String[] args) throws Exception {
        Fake s=new Fake();
        try {
            check(RotationPolicy.target(false,true,false)==1&&RotationPolicy.target(false,false,false)==0&&RotationPolicy.target(false,true,true)==3,"landscape-native monitor and reverse orientation");
            check(RotationPolicy.target(true,true,false)==0&&RotationPolicy.target(true,false,false)==1&&RotationPolicy.target(true,false,true)==3,"portrait-native monitor");
            check(!RotationPolicy.validSnapshot("lock 4","default")&&!RotationPolicy.validSnapshot("free;reboot","enabled"),"reject invalid snapshot and command injection");
            check(request(s,PivotService.APPLY,0,"external-A",1).startsWith("ERROR|")&&s.writes==0,"never modify display zero");
            s.info.type=1; check(request(s,PivotService.APPLY,1).startsWith("ERROR|")&&s.writes==0,"reject built-in secondary screen");
            s.info.type=5; check(request(s,PivotService.APPLY,1).startsWith("ERROR|")&&s.writes==0,"reject virtual screen"); s.info.type=2;
            check(request(s,PivotService.APPLY,2,"wrong-monitor",1).startsWith("ERROR|")&&s.writes==0,"reject reused display id with changed identity");
            check(request(s,PivotService.SNAPSHOT,null).equals("OK|free|default|0"),"capture original policies and observed angle");
            check(request(s,PivotService.APPLY,1).equals("OK|APPLIED")&&s.rotation.equals("lock 1")&&s.fixed.equals("enabled"),"apply external portrait rotation");
            check(request(s,PivotService.APPLY,3).startsWith("ERROR|"),"reject overlapping changes");
            s.info.rotation=0; check(request(s,PivotService.KEEP,null).startsWith("ERROR|"),"cannot confirm unobserved rotation");
            s.info.rotation=1; check(request(s,PivotService.KEEP,null).equals("OK|KEPT"),"keep observed orientation");
            check(request(s,PivotService.STATUS,null).startsWith("OK|IDLE|"),"confirmation cancels pending rollback");
            check(request(s,PivotService.RESTORE,"free|default|0").equals("OK|RESTORED")&&s.rotation.equals("free")&&s.fixed.equals("default")&&s.info.rotation==0,"restore original angle as well as unlocked policy");
            s.failLock=true; check(request(s,PivotService.APPLY,1).startsWith("ERROR|")&&s.fixed.equals("default")&&s.rotation.equals("free"),"partial failure rolls back both settings"); s.failLock=false;
            request(s,PivotService.APPLY,3); int before=s.writes; s.info.uniqueId="external-B";
            Method rollback=PivotService.class.getDeclaredMethod("rollbackNow"); rollback.setAccessible(true); rollback.invoke(s);
            check(s.writes==before,"rollback never writes to a replacement monitor"); s.info.uniqueId="external-A";
            request(s,PivotService.RESTORE,"free|default|0");
            request(s,PivotService.APPLY,1);
            System.out.println("Waiting for the real 20-second rollback timer...");
            Thread.sleep(21100);
            check(s.rotation.equals("free")&&s.fixed.equals("default")&&s.info.rotation==0&&request(s,PivotService.STATUS,null).startsWith("OK|IDLE|"),"automatic rollback restores angle without UI confirmation");
            check(request(s,PivotService.KEEP,null).startsWith("ERROR|"),"expired confirmation cannot keep settings");
            System.out.println("All "+count+" checks passed. Hardware/firmware compatibility is not covered.");
        } finally { stop(s); }
    }
}
