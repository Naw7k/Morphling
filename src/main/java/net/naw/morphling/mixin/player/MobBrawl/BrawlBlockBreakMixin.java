package net.naw.morphling.mixin.player.MobBrawl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.naw.morphling.client.games.MobBrawl.BrawlDimension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents block breaking inside Mob Brawl arena dimensions (client-side).
 * Applies only in morphling:brawl_arena and morphling:brawl_arena_night.
 * No effect in any other dimension.
 */
@Mixin(MultiPlayerGameMode.class)
public class BrawlBlockBreakMixin {

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void morphling$cancelArenaBlockBreak(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var dim = mc.level.dimension();
        if (dim.equals(BrawlDimension.DIMENSION_KEY) || dim.equals(BrawlDimension.NIGHT_DIMENSION_KEY)) {
            cir.setReturnValue(false);
        }
    }
}