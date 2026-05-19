package net.naw.morphling.mixin.accessors;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.pig.PigVariant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Pig.class)
public interface PigVariantAccessor {
    @Invoker("setVariant")
    void morphling$setVariant(Holder<PigVariant> variant);
}