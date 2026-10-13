package com.pivotcontrol.app;

/** Pure policy shared by the UI, privileged service and host tests. */
public final class RotationPolicy {
    private RotationPolicy() {}
    public static int target(boolean naturalPortrait, boolean portrait, boolean reverse) {
        return (naturalPortrait == portrait ? 0 : 1) + (reverse ? 2 : 0);
    }
    public static boolean validSnapshot(String rotation, String fixed) {
        return rotation != null && rotation.matches("free|lock [0-3]")
                && fixed != null && fixed.matches("default|enabled|disabled|enabled_if_no_auto_rotation");
    }
    public static String[] rotationCommand(int display, String state) {
        if (display <= 0 || state == null || !state.matches("free|lock [0-3]"))
            throw new IllegalArgumentException("Invalid external display / rotation");
        if (state.equals("free")) return new String[]{"/system/bin/wm", "user-rotation", "-d", Integer.toString(display), "free"};
        return new String[]{"/system/bin/wm", "user-rotation", "-d", Integer.toString(display), "lock", state.substring(5)};
    }
}
