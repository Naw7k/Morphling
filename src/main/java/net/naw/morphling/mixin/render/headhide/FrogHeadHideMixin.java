package net.naw.morphling.mixin.render.headhide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.animal.frog.FrogModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.FrogRenderState;
import net.naw.morphling.client.compat.FpmCompat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FrogModel.class)
public class FrogHeadHideMixin {

    @Shadow @Final private ModelPart head;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/FrogRenderState;)V", at = @At("TAIL"))
    private void morphling$hideHead(FrogRenderState state, CallbackInfo ci) {
        try {
            if (!FpmCompat.shouldHideHeadNow()) { this.head.visible = true; return; }
            var player = Minecraft.getInstance().player;
            if (player == null) { this.head.visible = true; return; }
            boolean isAtPlayer = Math.abs(state.x - player.getX()) < 0.5
                    && Math.abs(state.z - player.getZ()) < 0.5;
            this.head.visible = !isAtPlayer;
        } catch (Exception ignored) {}
    }
}