package net.naw.morphling.mixin.render;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.entity.EntityType;
import net.naw.morphling.client.core.MorphState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class HideHandMixin {

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void morphling$hideSlimeHand(CallbackInfo ci) {
        if (MorphState.getCurrentMorph() == EntityType.SLIME
                || MorphState.getCurrentMorph() == EntityType.BEE) {
            ci.cancel();
        }
    }
}