package net.naw.morphling.mixin.accessors;

import net.minecraft.world.entity.animal.rabbit.Rabbit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Rabbit.class)
public interface RabbitVariantAccessor {
    @Invoker("setVariant")
    void morphling$setVariant(Rabbit.Variant variant);
}