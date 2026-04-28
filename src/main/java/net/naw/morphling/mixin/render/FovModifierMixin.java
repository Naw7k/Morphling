package net.naw.morphling.mixin.render;

import net.minecraft.client.player.AbstractClientPlayer;
import net.naw.morphling.client.core.MorphState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class FovModifierMixin {

    @Inject(method = "getFieldOfViewModifier", at = @At("HEAD"), cancellable = true)
    private void morphling$lockFovDuringFlight(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> cir) {

        if (MorphState.isFlightActive()) {
            cir.setReturnValue(1.0F);
        }
    }
}