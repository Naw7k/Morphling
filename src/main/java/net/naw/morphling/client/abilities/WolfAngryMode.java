package net.naw.morphling.client.abilities;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.naw.morphling.client.core.MorphState;

public class WolfAngryMode {

    private static boolean active = false;
    private static long activatedAt = 0L;
    private static final long DURATION_MS = 30000;

    public static void toggle() {
        if (MorphState.getCurrentMorph() != EntityType.WOLF) return;
        active = !active;
        activatedAt = System.currentTimeMillis();

        if (MorphState.getCachedEntity() instanceof Wolf wolf) {
            if (active) {
                wolf.setPersistentAngerEndTime(Long.MAX_VALUE);
            } else {
                wolf.setPersistentAngerEndTime(-1L);
            }
        }

        MorphState.sendAbilityState("wolf_angry", String.valueOf(active));
    }

    public static void tick() {
        if (!active) return;
        if (MorphState.getCurrentMorph() != EntityType.WOLF) {
            active = false;
            return;
        }
        if (System.currentTimeMillis() - activatedAt > DURATION_MS) {
            toggle();
        }
    }
}
