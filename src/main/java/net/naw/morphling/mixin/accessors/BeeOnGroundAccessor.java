package net.naw.morphling.mixin.accessors;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface BeeOnGroundAccessor {
    @Mutable
    @Accessor("onGround")
    void morphling$setOnGroundDirect(boolean onGround);
}