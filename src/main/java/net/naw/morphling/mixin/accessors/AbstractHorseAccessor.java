package net.naw.morphling.mixin.accessors;

import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractHorse.class)
public interface AbstractHorseAccessor {
    @Invoker("setStanding")
    void morphling$setStanding(int ticks);

    @Invoker("setEating")
    void morphling$setEating(boolean eating);
}