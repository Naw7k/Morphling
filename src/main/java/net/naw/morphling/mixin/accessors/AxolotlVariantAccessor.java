package net.naw.morphling.mixin.accessors;

import net.minecraft.world.entity.animal.axolotl.Axolotl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Axolotl.class)
public interface AxolotlVariantAccessor {
    @Invoker("setVariant")
    void morphling$setVariant(Axolotl.Variant variant);
}