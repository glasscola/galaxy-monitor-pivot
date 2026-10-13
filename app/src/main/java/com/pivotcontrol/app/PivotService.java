package com.pivotcontrol.app;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Runs as shell via Shizuku. Accepts a fixed protocol, never arbitrary shell input. */
public class PivotService extends Binder {
    public static final String TOKEN = "com.pivotcontrol.app.PivotService";
    public static final int SNAPSHOT = 1, APPLY = 2, KEEP = 3, RESTORE = 4, STATUS = 5, DESCRIBE = 6;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> rollback;
    private int pendingId = -1, pendingRotation = -1;
    private String pendingUnique, oldRotation, oldFixed;
    private int oldAngle;
    private long deadline;
    private String lastStatus = "";

    public PivotService() { attachInterface(null, TOKEN); }

    @Override protected synchronized boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code == IBinder.INTERFACE_TRANSACTION) { reply.writeString(TOKEN); return true; }
        if (code == 16777115) {
            rollbackNow(); timer.shutdownNow(); reply.writeNoException();
            new Thread(() -> { try { Thread.sleep(100); } catch (Exception ignored) {} System.exit(0); }).start();
            return true;
        }
        data.enforceInterface(TOKEN);
        try {
            int id = data.readInt(); String unique = data.readString();
            Object info = displayInfo(id, code == DESCRIBE ? null : unique);
            String result;
            if (code == DESCRIBE) {
                result = (String)info.getClass().getField("uniqueId").get(info);
            } else if (code == SNAPSHOT) {
                result = snapshot(id, info);
            } else if (code == APPLY) {
                if (pendingId >= 0) throw new Exception("이전 화면 변경을 확인하거나 되돌려 주세요.");
                int rotation = data.readInt();
                if (rotation < 0 || rotation > 3) throw new Exception("잘못된 회전 값");
                String[] before = snapshot(id, info).split("\\|", -1);
                pendingId = id; pendingUnique = unique; pendingRotation = rotation;
                oldRotation = before[0]; oldFixed = before[1]; oldAngle = Integer.parseInt(before[2]);
                deadline = android.os.SystemClock.elapsedRealtime() + 20000;
                rollback = timer.schedule(() -> { synchronized (PivotService.this) { rollbackNow(); } }, 20, TimeUnit.SECONDS);
                try {
                    // Fixed-to-user-rotation ensures apps cannot override the user's choice.
                    run(new String[]{"/system/bin/wm", "fixed-to-user-rotation", "-d", ""+id, "enabled"});
                    run(RotationPolicy.rotationCommand(id, "lock " + rotation));
                    result = "APPLIED";
                } catch (Exception e) { rollbackNow(); throw e; }
            } else if (code == KEEP) {
                if (pendingId != id || !unique.equals(pendingUnique)) throw new Exception("확인 시간이 끝났습니다. 다시 시도해 주세요.");
                if (fieldInt(info, "rotation") != pendingRotation) throw new Exception("외부 화면 회전이 적용되지 않았습니다. 자동 복원을 기다려 주세요.");
                clearPending(); result = "KEPT"; lastStatus = "화면 방향을 유지합니다.";
            } else if (code == RESTORE) {
                String[] saved = data.readString().split("\\|", -1);
                if (saved.length != 3 || !RotationPolicy.validSnapshot(saved[0], saved[1]) || !saved[2].matches("[0-3]")) throw new Exception("복원 정보가 올바르지 않습니다.");
                if (pendingId >= 0 && (pendingId != id || !unique.equals(pendingUnique))) throw new Exception("먼저 다른 모니터의 변경을 확인해 주세요.");
                restore(id, saved[0], saved[1], Integer.parseInt(saved[2])); clearPending(); result = "RESTORED"; lastStatus = "원래 설정으로 복원했습니다.";
            } else if (code == STATUS) {
                result = pendingId >= 0 ? "PENDING|" + pendingId + "|" + pendingRotation + "|" + Math.max(0, (deadline - android.os.SystemClock.elapsedRealtime()+999)/1000) : "IDLE|" + lastStatus;
            } else { throw new Exception("지원하지 않는 요청"); }
            reply.writeNoException(); reply.writeString("OK|" + result);
        } catch (Exception e) {
            reply.writeNoException(); reply.writeString("ERROR|" + e.getMessage());
        }
        return true;
    }

    Object displayInfo(int id, String unique) throws Exception {
        if (id <= 0) throw new Exception("휴대폰 기본 화면은 변경할 수 없습니다.");
        Object info = readDisplayInfo(id);
        if (info == null) throw new Exception("모니터 연결이 해제되었습니다.");
        String realUnique = (String)info.getClass().getField("uniqueId").get(info);
        int type = fieldInt(info, "type");
        // Android TYPE_EXTERNAL/HDMI=2, WIFI=3. Reject built-in and virtual screens.
        if ((unique != null && !unique.equals(realUnique)) || (type != 2 && type != 3)) throw new Exception("실제 외부 모니터가 아니거나 연결 정보가 바뀌었습니다.");
        return info;
    }
    Object readDisplayInfo(int id) throws Exception {
        Class<?> global = Class.forName("android.hardware.display.DisplayManagerGlobal");
        Object instance = global.getMethod("getInstance").invoke(null);
        return global.getMethod("getDisplayInfo", int.class).invoke(instance, id);
    }
    private static int fieldInt(Object info, String name) throws Exception { return info.getClass().getField(name).getInt(info); }

    private String snapshot(int id, Object info) throws Exception {
        String rotation = run(new String[]{"/system/bin/wm", "user-rotation", "-d", ""+id}).trim();
        String fixed = run(new String[]{"/system/bin/wm", "fixed-to-user-rotation", "-d", ""+id}).trim();
        if (!RotationPolicy.validSnapshot(rotation, fixed)) throw new Exception("이 기종은 외부 화면 회전 명령을 지원하지 않습니다. DeX의 연결된 디스플레이 설정을 확인해 주세요.");
        return rotation + "|" + fixed + "|" + fieldInt(info,"rotation");
    }
    private void restore(int id, String rotation, String fixed, int angle) throws Exception {
        Exception failure = null;
        // 'free' alone leaves the last forced user angle behind on secondary displays.
        if (rotation.equals("free")) {
            try { run(RotationPolicy.rotationCommand(id, "lock " + angle)); } catch (Exception e) { failure = e; }
        }
        try { run(RotationPolicy.rotationCommand(id, rotation)); } catch (Exception e) { failure = e; }
        try { run(new String[]{"/system/bin/wm", "fixed-to-user-rotation", "-d", ""+id, fixed}); } catch (Exception e) { failure = e; }
        if (failure != null) throw failure;
    }
    private void rollbackNow() {
        if (pendingId < 0) return;
        try {
            displayInfo(pendingId, pendingUnique);
            restore(pendingId, oldRotation, oldFixed, oldAngle);
            lastStatus = "확인이 없어 이전 설정으로 자동 복원했습니다.";
        } catch (Exception e) { lastStatus = "자동 복원을 완료하지 못했습니다: " + e.getMessage() + " · 재연결 후 원래 설정 복원을 눌러 주세요."; }
        clearPending();
    }
    private void clearPending() {
        if (rollback != null) rollback.cancel(false);
        rollback = null; pendingId = -1; pendingRotation = -1; pendingUnique = null;
    }

    String run(String[] command) throws Exception {
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try (InputStream in = p.getInputStream()) {
                byte[] b = new byte[1024]; int n;
                while ((n = in.read(b)) != -1) { if (bytes.size() < 32768) bytes.write(b, 0, Math.min(n, 32768-bytes.size())); }
            } catch (Exception ignored) {}
        });
        reader.setDaemon(true); reader.start();
        if (!p.waitFor(3, TimeUnit.SECONDS)) { p.destroyForcibly(); reader.join(500); throw new Exception("회전 명령 응답 시간이 초과되었습니다."); }
        reader.join(500);
        String output = bytes.toString("UTF-8").trim();
        if (p.exitValue() != 0 || output.contains("Exception") || output.contains("Error:")) throw new Exception(output.isEmpty() ? "명령 실행 실패 ("+p.exitValue()+")" : output);
        return output;
    }
}
