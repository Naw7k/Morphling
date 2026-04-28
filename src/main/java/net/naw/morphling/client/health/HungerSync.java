package net.naw.morphling.client.health;

import net.naw.morphling.client.core.MorphState;
import net.minecraft.world.entity.EntityType;

public class HungerSync {

    public static void tick() {
        // No-op for now — just here for future use
    }

    public static boolean shouldHideHunger() {
        if (!MorphState.isMorphed()) return false;
        var morph = MorphState.getCachedEntity();
        if (morph == null) return false;
        return morph.getType() == EntityType.IRON_GOLEM
                || morph.getType() == EntityType.ENDERMAN;
    }
}