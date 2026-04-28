package net.naw.morphling.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public class FaCompat {

    /**
     * Returns true if Fresh Animations resource pack is currently selected.
     */
    public static boolean isFreshAnimationsActive() {
        try {
            return Minecraft.getInstance().getResourcePackRepository()
                    .getSelectedIds().stream()
                    .anyMatch(id -> id.toLowerCase().contains("fresh"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Locks EMF (Entity Model Features) variable map values to prevent FA jump shake on morphs.
     * Called per tick on the cached morph entity.
     */
    public static void lockEmfVariables(Entity morph) {
        if (morph == null) return;

        // Skip chicken — FA's chicken animations conflict with our manual flap logic
        if (morph instanceof net.minecraft.world.entity.animal.chicken.Chicken) return;

        try {
            java.lang.reflect.Field f = net.minecraft.world.entity.Entity.class.getDeclaredField("emf$variableMap");
            f.setAccessible(true);
            Object map = f.get(morph);
            if (map instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Float> m = (java.util.Map<String, Float>) map;
                m.put("var.fall_amp", 0.0F);
                m.put("var.t_fall", 0.0F);
                m.put("var.land", 0.0F);
                m.put("var.jump", 0.0F);
                m.put("var.fall", 0.0F);
                m.put("var.vs", 0.0F);
            }
        } catch (Exception ignored) {}
    }
}