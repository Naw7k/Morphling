package net.naw.morphling.mixin.accessors;

import net.minecraft.world.entity.animal.wolf.Wolf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Wolf.class)
public interface WolfShakeAccessor {
    @Accessor("isWet")
    void morphling$setIsWet(boolean value);

    @Accessor("isShaking")
    void morphling$setIsShaking(boolean value);

    @Accessor("shakeAnim")
    float morphling$getShakeAnim();

    @Accessor("shakeAnim")
    void morphling$setShakeAnim(float value);

    @Accessor("shakeAnimO")
    void morphling$setShakeAnimO(float value);
}