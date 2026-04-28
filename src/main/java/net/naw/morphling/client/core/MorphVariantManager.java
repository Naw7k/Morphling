package net.naw.morphling.client.core;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.cow.CowVariant;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.CatVariant;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.wolf.WolfVariant;
import net.naw.morphling.mixin.accessors.CatVariantAccessor;
import net.naw.morphling.mixin.accessors.ParrotVariantAccessor;

import java.util.List;

public class MorphVariantManager {

    // Stored variants per mob
    private static Parrot.Variant currentParrotVariant = Parrot.Variant.RED_BLUE;
    private static Holder<CatVariant> currentCatVariant = null;
    private static Holder<WolfVariant> currentWolfVariant = null;
    private static Holder<CowVariant> currentCowVariant = null;
    private static net.minecraft.world.item.DyeColor currentSheepColor = net.minecraft.world.item.DyeColor.WHITE;

    // Returns true if this mob type has selectable variants
    public static boolean hasVariants(EntityType<?> type) {
        return type == EntityType.PARROT
                || type == EntityType.CAT
                || type == EntityType.WOLF
                || type == EntityType.COW
                || type == EntityType.SHEEP;
    }

    // Apply current variant to a freshly-created entity (called from MorphState.setMorph)
    public static void applyVariant(Entity entity) {
        if (entity instanceof Parrot parrot) {
            ((ParrotVariantAccessor) parrot).morphling$setVariant(currentParrotVariant);
        } else if (entity instanceof Cat cat && currentCatVariant != null) {
            ((CatVariantAccessor) cat).morphling$setVariant(currentCatVariant);

        } else if (entity instanceof Wolf wolf && currentWolfVariant != null) {
            ((net.naw.morphling.mixin.accessors.WolfVariantAccessor) wolf).morphling$setVariant(currentWolfVariant);


        } else if (entity instanceof Cow cow && currentCowVariant != null) {
            cow.setVariant(currentCowVariant);
        } else if (entity instanceof Sheep sheep) {
            sheep.setColor(currentSheepColor);
        }
    }

    // Parrot
    public static void setParrotVariant(Parrot.Variant variant) {
        currentParrotVariant = variant;
        Entity cached = MorphState.getCachedEntity();
        if (cached instanceof Parrot p) ((ParrotVariantAccessor) p).morphling$setVariant(variant);
    }
    public static Parrot.Variant getParrotVariant() { return currentParrotVariant; }

    // Cat
    public static void setCatVariant(Holder<CatVariant> variant) {
        currentCatVariant = variant;
        Entity cached = MorphState.getCachedEntity();
        if (cached instanceof Cat c) ((CatVariantAccessor) c).morphling$setVariant(variant);
    }
    public static Holder<CatVariant> getCatVariant() { return currentCatVariant; }
    public static List<Holder.Reference<CatVariant>> getCatVariantList() {
        return Minecraft.getInstance().level.registryAccess()
                .lookupOrThrow(Registries.CAT_VARIANT).listElements().toList();
    }

    // Wolf
    public static void setWolfVariant(Holder<WolfVariant> variant) {
        currentWolfVariant = variant;
        Entity cached = MorphState.getCachedEntity();
        if (cached instanceof Wolf w) ((net.naw.morphling.mixin.accessors.WolfVariantAccessor) w).morphling$setVariant(variant);
    }
    public static Holder<WolfVariant> getWolfVariant() { return currentWolfVariant; }
    public static List<Holder.Reference<WolfVariant>> getWolfVariantList() {
        return Minecraft.getInstance().level.registryAccess()
                .lookupOrThrow(Registries.WOLF_VARIANT).listElements().toList();
    }

    // Cow
    public static void setCowVariant(Holder<CowVariant> variant) {
        currentCowVariant = variant;
        Entity cached = MorphState.getCachedEntity();
        if (cached instanceof Cow c) c.setVariant(variant);
    }
    public static Holder<CowVariant> getCowVariant() { return currentCowVariant; }
    public static List<Holder.Reference<CowVariant>> getCowVariantList() {
        return Minecraft.getInstance().level.registryAccess()
                .lookupOrThrow(Registries.COW_VARIANT).listElements().toList();
    }

    // Sheep
    public static void setSheepColor(net.minecraft.world.item.DyeColor color) {
        currentSheepColor = color;
        Entity cached = MorphState.getCachedEntity();
        if (cached instanceof Sheep s) s.setColor(color);
    }
    public static net.minecraft.world.item.DyeColor getSheepColor() { return currentSheepColor; }
}