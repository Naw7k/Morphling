package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.naw.morphling.client.core.MorphState;

public class WolfAngryMode {

    private static boolean active = false;
    private static long activatedAt = 0L;
    private static final long DURATION_MS = 30000;

    public static boolean isActive() {
        return active && MorphState.getCurrentMorph() == EntityType.WOLF;
    }

    public static void toggle(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.WOLF) return;
        active = !active;
        activatedAt = System.currentTimeMillis();

        if (MorphState.getCachedEntity() instanceof Wolf wolf) {
            if (active) {
                // Set anger end time to far future — makes wolf visually angry
                wolf.setPersistentAngerEndTime(Long.MAX_VALUE);
            } else {
                wolf.setPersistentAngerEndTime(-1L);
            }
        }

    }

    public static void tick(Minecraft client) {
        if (!active) return;
        if (MorphState.getCurrentMorph() != EntityType.WOLF) {
            active = false;
            return;
        }
        if (System.currentTimeMillis() - activatedAt > DURATION_MS) {
            toggle(client);
        }
    }
}