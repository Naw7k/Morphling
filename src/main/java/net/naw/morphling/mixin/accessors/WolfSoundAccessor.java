package net.naw.morphling.mixin.accessors;

import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Wolf.class)
public interface WolfSoundAccessor {
    @Invoker("getSoundSet")
    WolfSoundVariant.WolfSoundSet morphling$getSoundSet();
}