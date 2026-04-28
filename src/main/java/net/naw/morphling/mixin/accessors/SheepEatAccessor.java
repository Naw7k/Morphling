package net.naw.morphling.mixin.accessors;

import net.minecraft.world.entity.animal.sheep.Sheep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Sheep.class)
public interface SheepEatAccessor {
    @Accessor("eatAnimationTick")
    int morphling$getEatAnimationTick();

    @Accessor("eatAnimationTick")
    void morphling$setEatAnimationTick(int value);
}