package net.naw.morphling.mixin.accessors;

import net.minecraft.world.entity.animal.feline.Cat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Cat.class)
public interface CatTickAccessor {
    @Invoker("handleLieDown")
    void morphling$handleLieDown();
}