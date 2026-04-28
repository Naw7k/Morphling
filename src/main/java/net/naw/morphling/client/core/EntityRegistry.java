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

    public static List<MorphEntry> getAvailableMorphs() {
        List<MorphEntry> list = new ArrayList<>();
        // Passive
        list.add(new MorphEntry(EntityType.CHICKEN, Component.translatable("entity.minecraft.chicken")));
        list.add(new MorphEntry(EntityType.COW, Component.translatable("entity.minecraft.cow")));
        list.add(new MorphEntry(EntityType.PIG, Component.translatable("entity.minecraft.pig")));
        list.add(new MorphEntry(EntityType.SHEEP, Component.translatable("entity.minecraft.sheep")));
        list.add(new MorphEntry(EntityType.CAT, Component.translatable("entity.minecraft.cat")));
        list.add(new MorphEntry(EntityType.WOLF, Component.translatable("entity.minecraft.wolf")));
        list.add(new MorphEntry(EntityType.PARROT, Component.translatable("entity.minecraft.parrot")));
        // list.add(new MorphEntry(EntityType.HORSE, Component.translatable("entity.minecraft.horse")));
        // list.add(new MorphEntry(EntityType.FOX, Component.translatable("entity.minecraft.fox")));
        // list.add(new MorphEntry(EntityType.RABBIT, Component.translatable("entity.minecraft.rabbit")));
        // list.add(new MorphEntry(EntityType.POLAR_BEAR, Component.translatable("entity.minecraft.polar_bear")));
        // list.add(new MorphEntry(EntityType.GOAT, Component.translatable("entity.minecraft.goat")));
        // list.add(new MorphEntry(EntityType.VILLAGER, Component.translatable("entity.minecraft.villager")));
         list.add(new MorphEntry(EntityType.IRON_GOLEM, Component.translatable("entity.minecraft.iron_golem")));
         list.add(new MorphEntry(EntityType.DOLPHIN, Component.translatable("entity.minecraft.dolphin")));

        // Hostile
        list.add(new MorphEntry(EntityType.ZOMBIE, Component.translatable("entity.minecraft.zombie")));
        list.add(new MorphEntry(EntityType.SKELETON, Component.translatable("entity.minecraft.skeleton")));
        list.add(new MorphEntry(EntityType.CREEPER, Component.translatable("entity.minecraft.creeper")));
        // list.add(new MorphEntry(EntityType.SPIDER, Component.translatable("entity.minecraft.spider")));
        list.add(new MorphEntry(EntityType.ENDERMAN, Component.translatable("entity.minecraft.enderman")));
        // list.add(new MorphEntry(EntityType.SLIME, Component.translatable("entity.minecraft.slime")));
        // list.add(new MorphEntry(EntityType.WARDEN, Component.translatable("entity.minecraft.warden")));

        return list;
    }
}