package net.naw.morphling.mixin.accessors;

import net.minecraft.world.entity.animal.golem.IronGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(IronGolem.class)
public interface IronGolemAttackAccessor {
    @Accessor("attackAnimationTick")
    int morphling$getAttackAnimationTick();

    @Accessor("attackAnimationTick")
    void morphling$setAttackAnimationTick(int value);
}