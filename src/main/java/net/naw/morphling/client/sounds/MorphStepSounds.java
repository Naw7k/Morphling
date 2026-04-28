package net.naw.morphling.client.sounds;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.naw.morphling.mixin.accessors.CowSoundAccessor;

import java.util.HashMap;
import java.util.Map;

public class MorphStepSounds {
    private static final Map<EntityType<?>, SoundEvent> STEP_SOUNDS = new HashMap<>();
    private static final Map<EntityType<?>, Float> STEP_VOLUMES = new HashMap<>();

    static {
        STEP_SOUNDS.put(EntityType.CHICKEN, SoundEvents.CHICKEN_STEP.value());
        STEP_SOUNDS.put(EntityType.PIG, SoundEvents.PIG_STEP.value());
        STEP_SOUNDS.put(EntityType.SHEEP, SoundEvents.SHEEP_STEP);
        STEP_SOUNDS.put(EntityType.HORSE, SoundEvents.HORSE_STEP);
        STEP_SOUNDS.put(EntityType.WOLF, SoundEvents.WOLF_STEP.value());
        STEP_SOUNDS.put(EntityType.ZOMBIE, SoundEvents.ZOMBIE_STEP);
        STEP_SOUNDS.put(EntityType.ZOMBIE_VILLAGER, SoundEvents.ZOMBIE_VILLAGER_STEP);
        STEP_SOUNDS.put(EntityType.SKELETON, SoundEvents.SKELETON_STEP);
        STEP_SOUNDS.put(EntityType.IRON_GOLEM, SoundEvents.IRON_GOLEM_STEP);

        // Per-mob volume overrides — anything not listed uses the default (0.15F)
        STEP_VOLUMES.put(EntityType.IRON_GOLEM, 1.0F);
    }

    /**
     * Get step sound for a morph. Falls back to pulling from mob's SoundVariant for mobs
     * like Cow that store sounds per-variant.
     */
    public static SoundEvent getStepSound(Entity morphEntity) {
        if (morphEntity == null) return null;

        // Direct map hit
        SoundEvent direct = STEP_SOUNDS.get(morphEntity.getType());
        if (direct != null) return direct;

        // Cow's step sound lives in its sound variant
        if (morphEntity instanceof net.minecraft.world.entity.animal.cow.Cow cow) {
            return ((CowSoundAccessor)(Object) cow).morphling$getSoundSet().stepSound().value();
        }

        return null;
    }

    /**
     * Get volume multiplier for a morph's step sound. Returns -1 if no override (use default).
     */
    public static float getStepVolume(Entity morphEntity) {
        if (morphEntity == null) return -1F;
        return STEP_VOLUMES.getOrDefault(morphEntity.getType(), -1F);
    }
}