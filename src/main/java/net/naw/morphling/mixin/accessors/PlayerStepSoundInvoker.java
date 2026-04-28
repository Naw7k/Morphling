package net.naw.morphling.mixin.accessors;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface PlayerStepSoundInvoker {
    @Invoker("playStepSound")
    void morphling$playStepSound(BlockPos pos, BlockState state);
}