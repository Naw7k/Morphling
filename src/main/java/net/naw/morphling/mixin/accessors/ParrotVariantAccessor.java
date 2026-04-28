package net.naw.morphling.mixin.accessors;

import net.minecraft.world.entity.animal.parrot.Parrot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Parrot.class)
public interface ParrotVariantAccessor {
    @Invoker("setVariant")
    void morphling$setVariant(Parrot.Variant variant);
}