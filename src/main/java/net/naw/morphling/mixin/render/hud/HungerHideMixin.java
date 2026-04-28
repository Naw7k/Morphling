package net.naw.morphling.mixin.render.hud;

import net.minecraft.client.gui.Gui;
import net.naw.morphling.client.health.HungerSync;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class HungerHideMixin {

    @Inject(method = "extractFood", at = @At("HEAD"), cancellable = true)
    private void morphling$hideHunger(CallbackInfo ci) {
        if (HungerSync.shouldHideHunger()) {
            ci.cancel();
        }
    }
}