package net.naw.morphling.mixin.accessors;

import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.cow.CowSoundVariant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Cow.class)
public interface CowSoundAccessor {
    @Invoker("getSoundSet")
    CowSoundVariant morphling$getSoundSet();
}