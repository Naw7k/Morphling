package net.naw.morphling.mixin.player;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.naw.morphling.client.core.MorphState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class PlayerEyeHeightMixin {

    @Inject(method = "getEyeHeight()F", at = @At("HEAD"), cancellable = true)
    private void morphling$overrideEyeHeight(CallbackInfoReturnable<Float> cir) {
        if (!MorphState.isMorphed()) return;

        // Only affect the local player
        Entity self = (Entity)(Object)this;
        if (self != Minecraft.getInstance().player) return;

        Entity morphEntity = MorphState.getCachedEntity();
        if (morphEntity == null) return;

        cir.setReturnValue(morphEntity.getEyeHeight());
    }
}