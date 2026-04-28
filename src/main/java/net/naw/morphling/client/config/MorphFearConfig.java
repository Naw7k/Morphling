package net.naw.morphling.client.config;

import net.minecraft.world.entity.EntityType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Defines which mob types flee from which morph types.
 * Key = morph the player is wearing.
 * Value = set of mob types that should flee from that morph.
 */
public class MorphFearConfig {

    private static final Map<EntityType<?>, Set<EntityType<?>>> FEAR_MAP = new HashMap<>();

    static {
        // Cat morph scares creepers and phantoms
        FEAR_MAP.put(EntityType.CAT, new HashSet<>() {{
            add(EntityType.CREEPER);
            add(EntityType.PHANTOM);
        }});

        // Future: Warden scares many mobs
        // FEAR_MAP.put(EntityType.WARDEN, Set.of(...));

        // Future: Wolf scares foxes/rabbits
        // FEAR_MAP.put(EntityType.WOLF, Set.of(EntityType.FOX, EntityType.RABBIT));
    }

    /**
     * Returns true if a given mob type should flee from a given morph type.
     */
    public static boolean shouldFlee(EntityType<?> mobType, EntityType<?> morphType) {
        if (morphType == null) return false;
        Set<EntityType<?>> fearSet = FEAR_MAP.get(morphType);
        return fearSet != null && fearSet.contains(mobType);
    }
}