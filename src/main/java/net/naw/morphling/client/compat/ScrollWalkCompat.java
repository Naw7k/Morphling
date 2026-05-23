package net.naw.morphling.client.compat;

// Compatibility bridge between Morphling and ScrollWalk
// Uses reflection so Morphling doesn't hard-depend on ScrollWalk
// If ScrollWalk is not installed, all methods return safe defaults
public class ScrollWalkCompat {

    // Cached install check — null means not checked yet
    private static Boolean installed = null;

    // Returns true if ScrollWalk is installed, false otherwise
    // Result is cached so Class.forName only runs once
    public static boolean isInstalled() {
        if (installed == null) {
            try {
                Class.forName("net.naw.scrollwalk.ScrollWalk");
                installed = true;
            } catch (Exception e) {
                installed = false;
            }
        }
        return installed;
    }

    // Returns the current scroll speed ratio (0.0 to 1.0)
    // 1.0 = full speed (no slowdown), 0.5 = half speed etc
    // Uses momentumSpeed (the smoothed value) so morph speed transitions are smooth
    // Returns 1.0 if ScrollWalk is not installed so morph speeds are unaffected
    public static double getSpeedRatio() {
        if (!isInstalled()) return 1.0;
        try {
            Class<?> swClass = Class.forName("net.naw.scrollwalk.ScrollWalk");
            Class<?> configClass = Class.forName("net.naw.scrollwalk.ModConfig");
            // Read the smoothed momentum speed, not currentSpeed, to avoid jitter on morphs
            double currentSpeed = (double) swClass.getField("momentumSpeed").get(null);
            Object config = swClass.getField("config").get(null);
            float maxSpeed = configClass.getField("maxSpeed").getFloat(config);
            // Ratio = how far the player has scrolled relative to max speed
            return currentSpeed / maxSpeed;
        } catch (Exception e) {
            return 1.0;
        }
    }
}
