package net.naw.morphling.mixin.accessors;

import net.minecraft.world.entity.animal.fox.Fox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Fox.class)
public interface FoxVariantAccessor {

    @Invoker("setVariant")
    void morphling$setVariant(Fox.Variant variant);

    @Invoker("setSleeping")
    void morphling$setSleeping(boolean sleeping);

    @Invoker("setSitting")
    void morphling$setSitting(boolean sitting);
}