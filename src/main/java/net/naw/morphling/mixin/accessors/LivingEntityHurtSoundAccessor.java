package net.naw.morphling.mixin.accessors;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityHurtSoundAccessor {
    @Invoker("getHurtSound")
    SoundEvent morphling$getHurtSound(DamageSource source);
}