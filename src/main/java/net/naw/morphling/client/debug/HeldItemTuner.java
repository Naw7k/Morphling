package net.naw.morphling.client.debug;

/**
 * In-memory tuner for the iron golem poppy and (later) enderman block visuals.
 * Values are NOT persisted — once we settle on numbers we hardcode them and
 * delete this whole tuner.
 */
public class HeldItemTuner {

    public static class Offset {
        public float x;
        public float y;
        public float z;
        public float scale;
        public float rotX;
        public float rotY;
        public float rotZ;

        public Offset(float x, float y, float z, float scale, float rotX, float rotY, float rotZ) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.scale = scale;
            this.rotX = rotX;
            this.rotY = rotY;
            this.rotZ = rotZ;
        }
    }

    // Iron golem poppy —
    public static final Offset poppy = new Offset(-0.800F, 1.150F, -0.250F, 1.050F, 345.0F, 0.0F, 60.0F);

    // Enderman block —
    public static final Offset endermanBlock = new Offset(-0.300F, 1.000F, -0.050F, 1.000F, 0.0F, -45.0F, 90.0F);
}