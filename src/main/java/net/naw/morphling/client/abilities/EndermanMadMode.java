package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.EnderMan;
import net.naw.morphling.client.core.MorphState;
import net.naw.morphling.client.sounds.EndermanStareSound;
import net.naw.morphling.mixin.accessors.EndermanCreepyAccessor;

public class EndermanMadMode {

    private static final int DURATION_TICKS = 600; // 30 seconds (20 ticks/sec)
    private static boolean active = false;
    private static int ticksRemaining = 0;
    private static long lastToggleTime = 0L;

    public static boolean isActive() {
        return active && MorphState.getCurrentMorph() == EntityType.ENDERMAN;
    }

    public static void toggle(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.ENDERMAN) return;
        if (client.player == null || client.level == null) return;

        long now = System.currentTimeMillis();
        if (now - lastToggleTime < 500) return;
        lastToggleTime = now;

        if (active) {
            deactivate();
            return;
        }

        active = true;
        ticksRemaining = DURATION_TICKS;

        if (MorphState.getCachedEntity() instanceof EnderMan enderman) {
            enderman.getEntityData().set(EndermanCreepyAccessor.morphling$getDataCreepy(), true);
        }

        client.getSoundManager().play(new EndermanStareSound(client.player));
    }

    public static void deactivate() {
        active = false;
        ticksRemaining = 0;
        if (MorphState.getCachedEntity() instanceof EnderMan enderman) {
            enderman.getEntityData().set(EndermanCreepyAccessor.morphling$getDataCreepy(), false);
        }
    }

    /** Called every tick — handles auto-timeout. */
    public static void tick(Minecraft client) {
        // Deactivate if morph changed away from enderman
        if (MorphState.getCurrentMorph() != EntityType.ENDERMAN) {
            if (active) deactivate();
            return;
        }

        if (!active) return;
        ticksRemaining--;
        if (ticksRemaining <= 0) {
            deactivate();
        }
    }
}