package net.naw.morphling.mixin.accessors;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Mob.class)
public interface LivingEntityAccessor {
    @Invoker("getAmbientSound")
    SoundEvent morphling$getAmbientSound();
}