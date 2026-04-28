package net.naw.morphling.client.config;

import net.minecraft.world.entity.EntityType;
import java.util.HashMap;
import java.util.Map;

/**
 * Damage overrides per-morph.
 * (they have no ATTACK_DAMAGE attribute, so without override they'd fall back to player's 1.0).
 * (wolf, polar bear, zombie, skeleton, enderman, etc.) are NOT listed —
 * they use their vanilla damage.
 */
public class MorphDamageConfig {

    private static final Map<EntityType<?>, Double> OVERRIDES = new HashMap<>();

    static {
        // Small passive mobs — weak peck
        OVERRIDES.put(EntityType.CHICKEN, 0.5);
        OVERRIDES.put(EntityType.RABBIT, 0.5);
        OVERRIDES.put(EntityType.PARROT, 0.5);
        OVERRIDES.put(EntityType.BAT, 0.3);
        OVERRIDES.put(EntityType.ALLAY, 0.5);

        // Medium passive mobs — can headbutt
        OVERRIDES.put(EntityType.PIG, 1.0);
        OVERRIDES.put(EntityType.SHEEP, 1.0);
        OVERRIDES.put(EntityType.COW, 1.5);
        OVERRIDES.put(EntityType.HORSE, 1.0);
        OVERRIDES.put(EntityType.DONKEY, 1.0);
        OVERRIDES.put(EntityType.MULE, 1.0);
        OVERRIDES.put(EntityType.STRIDER, 1.0);
        OVERRIDES.put(EntityType.TURTLE, 1.0);
        OVERRIDES.put(EntityType.AXOLOTL, 1.0);
        OVERRIDES.put(EntityType.OCELOT, 1.0);
        OVERRIDES.put(EntityType.FROG, 0.5);

        // Humanoids — non-combat
        OVERRIDES.put(EntityType.VILLAGER, 1.0);
        OVERRIDES.put(EntityType.WANDERING_TRADER, 1.0);

        // Mobs that DO attack in vanilla — NOT overridden:
        // WOLF (aggressive if provoked)
        // CAT (scares creepers but can scratch)
        // FOX (attacks chickens, small damage)
        // GOAT (rams)
        // PANDA (can be aggressive)
        // POLAR_BEAR (attacks if provoked)
        // LLAMA (spits, attacks if provoked)
        // BEE (stings)
        // ZOMBIE, SKELETON, CREEPER, ENDERMAN, WARDEN, IRON_GOLEM, SPIDER, etc.
    }

    /** Returns override damage for a morph, or -1 if no override (use default logic). */
    public static double getOverride(EntityType<?> type) {
        return OVERRIDES.getOrDefault(type, -1.0);
    }
}