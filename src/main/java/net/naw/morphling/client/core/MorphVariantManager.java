package net.naw.morphling.client.core;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.chicken.ChickenVariant;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.cow.CowVariant;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.CatVariant;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.pig.PigVariant;
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
    private static Holder<PigVariant> currentPigVariant = null;
    private static Holder<ChickenVariant> currentChickenVariant = null;
    private static net.minecraft.world.item.DyeColor currentSheepColor = net.minecraft.world.item.DyeColor.WHITE;

    // Horse uses old-style enum for color + markings (not registry-based)
    private static net.minecraft.world.entity.animal.equine.Variant currentHorseColor = net.minecraft.world.entity.animal.equine.Variant.WHITE;
    private static net.minecraft.world.entity.animal.equine.Markings currentHorseMarkings = net.minecraft.world.entity.animal.equine.Markings.NONE;

    // Returns true if this mob type has selectable variants
    public static boolean hasVariants(EntityType<?> type) {
        return type == EntityType.PARROT
                || type == EntityType.CAT
                || type == EntityType.WOLF
                || type == EntityType.COW
                || type == EntityType.SHEEP
                || type == EntityType.PIG
                || type == EntityType.CHICKEN
                || type == EntityType.HORSE
                || type == EntityType.VILLAGER
                || type == EntityType.SLIME;
    }

    /** Serialize all current variants to a single comma-separated string for NBT storage */
    public static String serializeVariants() {
        String parrot = currentParrotVariant != null ? currentParrotVariant.name() : "RED_BLUE";
        String cat = currentCatVariant != null ? currentCatVariant.unwrapKey().orElseThrow().identifier().toString() : "";
        String wolf = currentWolfVariant != null ? currentWolfVariant.unwrapKey().orElseThrow().identifier().toString() : "";
        String cow = currentCowVariant != null ? currentCowVariant.unwrapKey().orElseThrow().identifier().toString() : "";
        String sheep = currentSheepColor != null ? currentSheepColor.name() : "WHITE";
        String pig = currentPigVariant != null ? currentPigVariant.unwrapKey().orElseThrow().identifier().toString() : "";
        String chicken = currentChickenVariant != null ? currentChickenVariant.unwrapKey().orElseThrow().identifier().toString() : "";
        String horseColor = currentHorseColor != null ? currentHorseColor.name() : "WHITE";
        String horseMarkings = currentHorseMarkings != null ? currentHorseMarkings.name() : "NONE";
        String villagerProf = currentVillagerProfession != null ? currentVillagerProfession.unwrapKey().orElseThrow().identifier().toString() : "";
        String villagerType = currentVillagerType != null ? currentVillagerType.unwrapKey().orElseThrow().identifier().toString() : "";
        String slime = String.valueOf(currentSlimeSize);
        return String.join("|", parrot, cat, wolf, cow, sheep, pig, chicken, horseColor, horseMarkings, villagerProf, villagerType, slime);
    }


    /** Apply a serialized variants string back to MorphVariantManager */
    public static void deserializeVariants(String data) {
        if (data == null || data.isEmpty()) return;
        String[] parts = data.split("\\|", -1);
        if (parts.length < 12) return;

        try {
            // Parrot
            currentParrotVariant = Parrot.Variant.valueOf(parts[0]);
        } catch (Exception ignored) {}

        try {
            // Cat
            if (!parts[1].isEmpty() && Minecraft.getInstance().level != null) {
                currentCatVariant = Minecraft.getInstance().level.registryAccess()
                        .lookupOrThrow(Registries.CAT_VARIANT)
                        .get(net.minecraft.resources.ResourceKey.create(Registries.CAT_VARIANT, Identifier.parse(parts[1])))
                        .orElse(null);
            }
        } catch (Exception ignored) {}

        try {
            // Wolf
            if (!parts[2].isEmpty() && Minecraft.getInstance().level != null) {
                currentWolfVariant = Minecraft.getInstance().level.registryAccess()
                        .lookupOrThrow(Registries.WOLF_VARIANT)
                        .get(net.minecraft.resources.ResourceKey.create(Registries.WOLF_VARIANT, Identifier.parse(parts[2])))
                        .orElse(null);
            }
        } catch (Exception ignored) {}

        try {
            // Cow
            if (!parts[3].isEmpty() && Minecraft.getInstance().level != null) {
                currentCowVariant = Minecraft.getInstance().level.registryAccess()
                        .lookupOrThrow(Registries.COW_VARIANT)
                        .get(net.minecraft.resources.ResourceKey.create(Registries.COW_VARIANT, Identifier.parse(parts[3])))
                        .orElse(null);
            }
        } catch (Exception ignored) {}

        try {
            // Sheep
            if (!parts[4].isEmpty()) currentSheepColor = net.minecraft.world.item.DyeColor.valueOf(parts[4]);
        } catch (Exception ignored) {}

        try {
            // Pig
            if (!parts[5].isEmpty() && Minecraft.getInstance().level != null) {
                currentPigVariant = Minecraft.getInstance().level.registryAccess()
                        .lookupOrThrow(Registries.PIG_VARIANT)
                        .get(net.minecraft.resources.ResourceKey.create(Registries.PIG_VARIANT, Identifier.parse(parts[5])))
                        .orElse(null);
            }
        } catch (Exception ignored) {}

        try {
            // Chicken
            if (!parts[6].isEmpty() && Minecraft.getInstance().level != null) {
                currentChickenVariant = Minecraft.getInstance().level.registryAccess()
                        .lookupOrThrow(Registries.CHICKEN_VARIANT)
                        .get(net.minecraft.resources.ResourceKey.create(Registries.CHICKEN_VARIANT, Identifier.parse(parts[6])))
                        .orElse(null);
            }
        } catch (Exception ignored) {}

        try {
            // Horse color
            if (!parts[7].isEmpty()) currentHorseColor = net.minecraft.world.entity.animal.equine.Variant.valueOf(parts[7]);
        } catch (Exception ignored) {}

        try {
            // Horse markings
            if (!parts[8].isEmpty()) currentHorseMarkings = net.minecraft.world.entity.animal.equine.Markings.valueOf(parts[8]);
        } catch (Exception ignored) {}

        try {
            // Villager profession
            if (!parts[9].isEmpty() && Minecraft.getInstance().level != null) {
                currentVillagerProfession = Minecraft.getInstance().level.registryAccess()
                        .lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_PROFESSION)
                        .get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.VILLAGER_PROFESSION, Identifier.parse(parts[9])))
                        .orElse(null);
            }
        } catch (Exception ignored) {}

        try {
            // Villager type
            if (!parts[10].isEmpty() && Minecraft.getInstance().level != null) {
                currentVillagerType = Minecraft.getInstance().level.registryAccess()
                        .lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_TYPE)
                        .get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.VILLAGER_TYPE, Identifier.parse(parts[10])))
                        .orElse(null);
            }
        } catch (Exception ignored) {}

        try {
            // Slime size
            if (!parts[11].isEmpty()) currentSlimeSize = Integer.parseInt(parts[11]);
        } catch (Exception ignored) {}
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
        } else if (entity instanceof net.minecraft.world.entity.animal.pig.Pig pig && currentPigVariant != null) {
            ((net.naw.morphling.mixin.accessors.PigVariantAccessor) pig).morphling$setVariant(currentPigVariant);
        } else if (entity instanceof net.minecraft.world.entity.animal.chicken.Chicken chicken && currentChickenVariant != null) {
            chicken.setVariant(currentChickenVariant);
        } else if (entity instanceof Horse horse) {
            // Horse packs color + markings into a single int internally
            ((net.naw.morphling.mixin.accessors.HorseVariantAccessor) horse).morphling$setVariantAndMarkings(currentHorseColor, currentHorseMarkings);
        } else if (entity instanceof net.minecraft.world.entity.npc.villager.Villager v) {
            if (currentVillagerProfession != null) v.setVillagerData(v.getVillagerData().withProfession(currentVillagerProfession));
            if (currentVillagerType != null) v.setVillagerData(v.getVillagerData().withType(currentVillagerType));
        } else if (entity instanceof net.minecraft.world.entity.monster.Slime slime) {
            slime.setSize(currentSlimeSize, false);
        }
    }

    // Plays a UI feedback sound when the user changes a variant
    // (e.g. cycling through cat colors). Low volume + slightly higher pitch
    // so it's smooth and unobtrusive.
    private static void playVariantChangeSound() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        mc.level.playLocalSound(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),
                net.minecraft.sounds.SoundSource.PLAYERS,
                0.3F, 1.5F, false
        );
        MorphState.broadcastSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.3F, 1.5F);
    }

    // Parrot
    public static void setParrotVariant(Parrot.Variant variant) {
        currentParrotVariant = variant;
        Entity cached = MorphState.getCachedEntity();
        if (cached instanceof Parrot p) ((ParrotVariantAccessor) p).morphling$setVariant(variant);
        playVariantChangeSound();
    }
    public static Parrot.Variant getParrotVariant() { return currentParrotVariant; }

    // Cat
    public static void setCatVariant(Holder<CatVariant> variant) {
        currentCatVariant = variant;
        Entity cached = MorphState.getCachedEntity();
        if (cached instanceof Cat c) ((CatVariantAccessor) c).morphling$setVariant(variant);
        playVariantChangeSound();
    }
    public static Holder<CatVariant> getCatVariant() { return currentCatVariant; }
    public static List<Holder.Reference<CatVariant>> getCatVariantList() {
        assert Minecraft.getInstance().level != null;
        return Minecraft.getInstance().level.registryAccess()
                .lookupOrThrow(Registries.CAT_VARIANT).listElements().toList();
    }

    // Wolf
    public static void setWolfVariant(Holder<WolfVariant> variant) {
        currentWolfVariant = variant;
        Entity cached = MorphState.getCachedEntity();
        if (cached instanceof Wolf w) ((net.naw.morphling.mixin.accessors.WolfVariantAccessor) w).morphling$setVariant(variant);
        playVariantChangeSound();
    }
    public static Holder<WolfVariant> getWolfVariant() { return currentWolfVariant; }
    public static List<Holder.Reference<WolfVariant>> getWolfVariantList() {
        assert Minecraft.getInstance().level != null;
        return Minecraft.getInstance().level.registryAccess()
                .lookupOrThrow(Registries.WOLF_VARIANT).listElements().toList();
    }

    // Cow
    public static void setCowVariant(Holder<CowVariant> variant) {
        currentCowVariant = variant;
        Entity cached = MorphState.getCachedEntity();
        if (cached instanceof Cow c) c.setVariant(variant);
        playVariantChangeSound();
    }
    public static Holder<CowVariant> getCowVariant() { return currentCowVariant; }
    public static List<Holder.Reference<CowVariant>> getCowVariantList() {
        assert Minecraft.getInstance().level != null;
        return Minecraft.getInstance().level.registryAccess()
                .lookupOrThrow(Registries.COW_VARIANT).listElements().toList();
    }

    // Sheep
    public static void setSheepColor(net.minecraft.world.item.DyeColor color) {
        currentSheepColor = color;
        Entity cached = MorphState.getCachedEntity();
        if (cached instanceof Sheep s) s.setColor(color);
        playVariantChangeSound();
    }
    public static net.minecraft.world.item.DyeColor getSheepColor() { return currentSheepColor; }

    // Pig
    public static void setPigVariant(Holder<net.minecraft.world.entity.animal.pig.PigVariant> variant) {
        currentPigVariant = variant;
        Entity cached = MorphState.getCachedEntity();
        if (cached instanceof net.minecraft.world.entity.animal.pig.Pig p)
            ((net.naw.morphling.mixin.accessors.PigVariantAccessor) p).morphling$setVariant(variant);
        playVariantChangeSound();
    }
    public static Holder<net.minecraft.world.entity.animal.pig.PigVariant> getPigVariant() { return currentPigVariant; }
    public static List<Holder.Reference<net.minecraft.world.entity.animal.pig.PigVariant>> getPigVariantList() {
        assert Minecraft.getInstance().level != null;
        return Minecraft.getInstance().level.registryAccess()
                .lookupOrThrow(Registries.PIG_VARIANT).listElements().toList();
    }

    // Chicken
    public static void setChickenVariant(Holder<net.minecraft.world.entity.animal.chicken.ChickenVariant> variant) {
        currentChickenVariant = variant;
        Entity cached = MorphState.getCachedEntity();
        if (cached instanceof net.minecraft.world.entity.animal.chicken.Chicken c) c.setVariant(variant);
        playVariantChangeSound();
    }
    public static Holder<net.minecraft.world.entity.animal.chicken.ChickenVariant> getChickenVariant() { return currentChickenVariant; }
    public static List<Holder.Reference<net.minecraft.world.entity.animal.chicken.ChickenVariant>> getChickenVariantList() {
        assert Minecraft.getInstance().level != null;
        return Minecraft.getInstance().level.registryAccess()
                .lookupOrThrow(Registries.CHICKEN_VARIANT).listElements().toList();
    }

    // Horse — color and markings are separate enums packed into one int internally
    public static void setHorseColor(net.minecraft.world.entity.animal.equine.Variant color) {
        currentHorseColor = color;
        Entity cached = MorphState.getCachedEntity();
        if (cached instanceof Horse h) ((net.naw.morphling.mixin.accessors.HorseVariantAccessor) h).morphling$setVariantAndMarkings(currentHorseColor, currentHorseMarkings);
        playVariantChangeSound();
    }
    public static net.minecraft.world.entity.animal.equine.Variant getHorseColor() { return currentHorseColor; }
    public static net.minecraft.world.entity.animal.equine.Variant[] getHorseColors() { return net.minecraft.world.entity.animal.equine.Variant.values(); }

    public static void setHorseMarkings(net.minecraft.world.entity.animal.equine.Markings markings) {
        currentHorseMarkings = markings;
        Entity cached = MorphState.getCachedEntity();
        if (cached instanceof Horse h) ((net.naw.morphling.mixin.accessors.HorseVariantAccessor) h).morphling$setVariantAndMarkings(currentHorseColor, currentHorseMarkings);
        playVariantChangeSound();
    }
    public static net.minecraft.world.entity.animal.equine.Markings getHorseMarkings() { return currentHorseMarkings; }
    public static net.minecraft.world.entity.animal.equine.Markings[] getHorseMarkingsList() { return net.minecraft.world.entity.animal.equine.Markings.values(); }


    // Villager — profession + type (biome) stored via VillagerData
    private static net.minecraft.core.Holder<net.minecraft.world.entity.npc.villager.VillagerProfession> currentVillagerProfession = null;
    private static net.minecraft.core.Holder<net.minecraft.world.entity.npc.villager.VillagerType> currentVillagerType = null;

    public static void setVillagerProfession(net.minecraft.core.Holder<net.minecraft.world.entity.npc.villager.VillagerProfession> profession) {
        currentVillagerProfession = profession;
        Entity cached = MorphState.getCachedEntity();
        if (cached instanceof net.minecraft.world.entity.npc.villager.Villager v && currentVillagerProfession != null) {
            v.setVillagerData(v.getVillagerData().withProfession(currentVillagerProfession));
        }
        playVariantChangeSound();
    }
    public static net.minecraft.core.Holder<net.minecraft.world.entity.npc.villager.VillagerProfession> getVillagerProfession() { return currentVillagerProfession; }
    public static java.util.List<net.minecraft.core.Holder.Reference<net.minecraft.world.entity.npc.villager.VillagerProfession>> getVillagerProfessionList() {
        assert Minecraft.getInstance().level != null;
        return Minecraft.getInstance().level.registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_PROFESSION).listElements().toList();
    }

    public static void setVillagerType(net.minecraft.core.Holder<net.minecraft.world.entity.npc.villager.VillagerType> type) {
        currentVillagerType = type;
        Entity cached = MorphState.getCachedEntity();
        if (cached instanceof net.minecraft.world.entity.npc.villager.Villager v && currentVillagerType != null) {
            v.setVillagerData(v.getVillagerData().withType(currentVillagerType));
        }
        playVariantChangeSound();
    }
    public static net.minecraft.core.Holder<net.minecraft.world.entity.npc.villager.VillagerType> getVillagerType() { return currentVillagerType; }
    public static java.util.List<net.minecraft.core.Holder.Reference<net.minecraft.world.entity.npc.villager.VillagerType>> getVillagerTypeList() {
        assert Minecraft.getInstance().level != null;
        return Minecraft.getInstance().level.registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_TYPE).listElements().toList();
    }

    // Slime — size variant (1 = tiny, 2 = small, 4 = big)
    private static int currentSlimeSize = 2;

    public static void setSlimeSize(int size) {
        currentSlimeSize = size;
        Entity cached = MorphState.getCachedEntity();
        if (cached instanceof net.minecraft.world.entity.monster.Slime slime) {
            slime.setSize(size, false);
        }
        playVariantChangeSound();
    }
    public static int getSlimeSize() { return currentSlimeSize; }
}
