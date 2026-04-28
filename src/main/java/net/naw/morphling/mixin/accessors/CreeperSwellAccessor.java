package net.naw.morphling.mixin.accessors;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Creeper.class)
public interface CreeperSwellAccessor {
    @Accessor("swell")
    void morphling$setSwell(int swell);

    @Accessor("swell")
    int morphling$getSwell();

    @Accessor("oldSwell")
    void morphling$setOldSwell(int oldSwell);

    @Accessor("oldSwell")
    int morphling$getOldSwell();
}
