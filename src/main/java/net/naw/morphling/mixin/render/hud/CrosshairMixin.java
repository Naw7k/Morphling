package net.naw.morphling.mixin.render.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.naw.morphling.client.debug.HandPlacementDebugScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class CrosshairMixin {

    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void morphling$hideCrosshairOnHandTuner(CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof HandPlacementDebugScreen) {
            ci.cancel();
        }
    }
}