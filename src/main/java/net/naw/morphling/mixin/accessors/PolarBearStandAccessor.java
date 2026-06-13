package net.naw.morphling.mixin.accessors;

import net.minecraft.world.entity.animal.polarbear.PolarBear;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes PolarBear's private stand-animation fields so PolarBearAbility can
 * drive the rear-up animation on a cached entity at tick rate, without calling
 * bear.tick() (which calls refreshDimensions() and thrashes the hitbox).
 * Same pattern as WalkAnimationStateAccessor.
 */
@Mixin(PolarBear.class)
public interface PolarBearStandAccessor {

    @Accessor("clientSideStandAnimation")
    float morphling$getStandAnimation();

    @Accessor("clientSideStandAnimation")
    void morphling$setStandAnimation(float value);

    @Accessor("clientSideStandAnimationO")
    void morphling$setStandAnimationO(float value);
}