package net.naw.morphling.mixin.accessors;

import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EnderMan.class)
public interface EndermanCreepyAccessor {
    @Accessor("DATA_CREEPY")
    static net.minecraft.network.syncher.EntityDataAccessor<Boolean> morphling$getDataCreepy() {
        throw new AssertionError();
    }
}