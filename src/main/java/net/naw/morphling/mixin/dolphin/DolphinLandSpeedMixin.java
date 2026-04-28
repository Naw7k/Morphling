package net.naw.morphling.mixin.dolphin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.naw.morphling.client.core.MorphState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class DolphinLandSpeedMixin {

    @Inject(method = "getSpeed", at = @At("HEAD"), cancellable = true)
    private void morphling$slowDolphinOnLand(CallbackInfoReturnable<Float> cir) {
        if (!MorphState.isMorphed()) return;
        var morph = MorphState.getCachedEntity();
        if (morph == null || morph.getType() != EntityType.DOLPHIN) return;

        Player self = (Player)(Object)this;
        if (self.isInWater()) return;
        if (!self.onGround()) return;

        cir.setReturnValue(0.02F);
    }
}