package net.naw.morphling.client.debug;

public class DebugSettings {
    private static boolean damageIndicatorEnabled = false;
    private static boolean testSpeedEnabled = false;

    private static boolean speedometerEnabled = false;

    public static boolean isSpeedometerEnabled() { return speedometerEnabled; }
    public static void setSpeedometerEnabled(boolean v) { speedometerEnabled = v; }

    public static boolean isDamageIndicatorEnabled() { return damageIndicatorEnabled; }
    public static void setDamageIndicatorEnabled(boolean v) { damageIndicatorEnabled = v; }

    public static boolean isTestSpeedEnabled() { return testSpeedEnabled; }
    public static void setTestSpeedEnabled(boolean v) { testSpeedEnabled = v; }
}