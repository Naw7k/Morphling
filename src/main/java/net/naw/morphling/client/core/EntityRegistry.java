package net.naw.morphling.client.core;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class EntityRegistry {

    public record MorphEntry(EntityType<?> type, Component name) {}

    // Mobs that can fly — when morphed into these, flight mode activates
    public static final Set<EntityType<?>> FLYING_MOBS = Set.of(
            EntityType.PARROT,
            EntityType.BAT,
            EntityType.BEE,
            EntityType.PHANTOM,
            EntityType.ALLAY,
            EntityType.GHAST,
            EntityType.VEX,
            EntityType.BLAZE
    );

    // Helper method
    private static void addMorph(List<MorphEntry> list, EntityType<?> type) {
        list.add(new MorphEntry(
                type,
                Component.translatable(type.getDescriptionId())
        ));
    }

    public static List<MorphEntry> getAvailableMorphs() {

        List<MorphEntry> list = new ArrayList<>();

        // Passive
        addMorph(list, EntityType.CHICKEN);
        addMorph(list, EntityType.COW);
        addMorph(list, EntityType.PIG);
        addMorph(list, EntityType.SHEEP);
        addMorph(list, EntityType.CAT);
        addMorph(list, EntityType.WOLF);
        addMorph(list, EntityType.PARROT);
        addMorph(list, EntityType.HORSE);
        addMorph(list, EntityType.VILLAGER);
        addMorph(list, EntityType.IRON_GOLEM);
        addMorph(list, EntityType.DOLPHIN);
        addMorph(list, EntityType.BEE);

        // Hostile
        addMorph(list, EntityType.ZOMBIE);
        addMorph(list, EntityType.SKELETON);
        addMorph(list, EntityType.CREEPER);
        addMorph(list, EntityType.SPIDER);
        addMorph(list, EntityType.ENDERMAN);
        addMorph(list, EntityType.SLIME);

        return list;
    }
}
