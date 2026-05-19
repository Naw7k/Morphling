package net.naw.morphling.mixin.accessors;

import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Markings;
import net.minecraft.world.entity.animal.equine.Variant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Horse.class)
public interface HorseVariantAccessor {
    @Invoker("setVariantAndMarkings")
    void morphling$setVariantAndMarkings(Variant variant, Markings markings);
}