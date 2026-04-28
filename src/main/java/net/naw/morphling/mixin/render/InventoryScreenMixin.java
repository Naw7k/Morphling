package net.naw.morphling.mixin.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.naw.morphling.client.core.MorphState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractContainerScreen<net.minecraft.world.inventory.InventoryMenu> {

    // Dummy constructor — Mixin targets don't use this
    private InventoryScreenMixin() {
        super(null, null, null);
    }

    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void morphling$replaceMorphPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!MorphState.isMorphed()) return;

        Entity morph = MorphState.getCachedEntity();
        if (!(morph instanceof LivingEntity livingMorph)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int xo = this.leftPos;
        int yo = this.topPos;
        int x0 = xo + 26;
        int y0 = yo + 8;
        int x1 = xo + 75;
        int y1 = yo + 78;

        // Cover up the vanilla player render with black so it's not visible underneath
        graphics.fill(x0, y0, x1, y1, 0xFF000000);

        // Scale based on morph size — same formula as our menu tiles
        float maxDim = Math.max(livingMorph.getBbHeight(), livingMorph.getBbWidth());
        int size = Math.max(8, (int)(55.0F / Math.max(2F, maxDim)));

        try {
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    graphics,
                    x0, y0, x1, y1,
                    size,
                    0.0625F,
                    mouseX, mouseY,
                    livingMorph
            );
        } catch (Exception ignored) {}
    }
}