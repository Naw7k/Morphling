package net.naw.morphling.mixin.render.headhide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.monster.creeper.CreeperModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import net.naw.morphling.client.compat.FpmCompat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreeperModel.class)
public class CreeperHeadHideMixin {

    @Shadow @Final public ModelPart head;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/CreeperRenderState;)V", at = @At("TAIL"))
    private void morphling$hideHead(CreeperRenderState state, CallbackInfo ci) {
        if (!FpmCompat.shouldHideHeadNow()) { head.visible = true; return; }
        if (state.lightCoords == 15728880) { head.visible = true; return; }
        var player = Minecraft.getInstance().player;
        if (player == null) { head.visible = true; return; }
        boolean isAtPlayer = Math.abs(state.x - player.getX()) < 0.5
                && Math.abs(state.z - player.getZ()) < 0.5;
        head.visible = !isAtPlayer;
    }
}