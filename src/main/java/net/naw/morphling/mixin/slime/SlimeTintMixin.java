package net.naw.morphling.mixin.slime;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EntityType;
import net.naw.morphling.client.core.MorphState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class SlimeTintMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void morphling$slimeTint(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (MorphState.getCurrentMorph() != EntityType.SLIME) return;
        if (mc.options.getCameraType() != net.minecraft.client.CameraType.FIRST_PERSON) return;
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0x40003300);
    }
}