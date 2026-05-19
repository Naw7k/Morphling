package net.naw.morphling.mixin.player;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EntityType;
import net.naw.morphling.client.core.MorphState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class NoCrouchMixin {

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void morphling$beeNoCrouch(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer)(Object)this;
        if (MorphState.getCurrentMorph() == EntityType.BEE && MorphState.isFlightActive()) {
            ((net.naw.morphling.mixin.accessors.LocalPlayerCrouchAccessor) self).morphling$setCrouching(false);
        }
    }
}