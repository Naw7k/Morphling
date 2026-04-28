package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.naw.morphling.client.core.MorphState;

public class MobAbilities {

    public static void trigger(Minecraft client) {
        if (!MorphState.isMorphed()) return;
        if (client.player == null || client.level == null) return;

        EntityType<?> morph = MorphState.getCurrentMorph();

        if (morph == EntityType.ENDERMAN) {
            EndermanAbility.trigger(client);
        }
        // Creeper is handled via tick (hold-to-prime), not trigger
    }

    /** Called every tick — for ambient effects like enderman particles. */
    public static void tick(Minecraft client) {
        if (!MorphState.isMorphed()) return;

        EntityType<?> morph = MorphState.getCurrentMorph();

        if (morph == EntityType.ENDERMAN) {
            EndermanAbility.tickParticles(client);
        }
    }
}