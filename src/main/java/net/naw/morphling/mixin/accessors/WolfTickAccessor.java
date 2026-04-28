package net.naw.morphling.mixin.accessors;

import net.minecraft.world.entity.animal.wolf.Wolf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Wolf.class)
public interface WolfTickAccessor {
    @Accessor("interestedAngle")
    float morphling$getInterestedAngle();

    @Accessor("interestedAngle")
    void morphling$setInterestedAngle(float value);

    @Accessor("interestedAngleO")
    void morphling$setInterestedAngleO(float value);
}