package net.naw.morphling.mixin.accessors;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WalkAnimationState.class)
public interface WalkAnimationStateAccessor {
    @Accessor("speed")
    void morphling$setSpeed(float speed);

    @Accessor("speedOld")
    void morphling$setSpeedOld(float speedOld);

    @Accessor("position")
    void morphling$setPosition(float position);

    @Accessor("speed")
    float morphling$getSpeed();

    @Accessor("speedOld")
    float morphling$getSpeedOld();

    @Accessor("position")
    float morphling$getPosition();
}