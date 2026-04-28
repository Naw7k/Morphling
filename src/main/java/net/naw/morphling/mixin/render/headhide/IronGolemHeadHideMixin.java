package net.naw.morphling.mixin.render.headhide;

import net.minecraft.client.model.animal.golem.IronGolemModel;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.client.model.geom.ModelPart;
import net.naw.morphling.client.compat.FpmCompat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IronGolemModel.class)
public class IronGolemHeadHideMixin {

    @Shadow @Final private ModelPart head;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/IronGolemRenderState;)V", at = @At("TAIL"))
    private void morphling$hideHead(IronGolemRenderState state, CallbackInfo ci) {
        if (!FpmCompat.shouldHideHeadNow()) {
            head.visible = true;
            return;
        }
        if (state.lightCoords == 15728880) {
            head.visible = true;
            return;
        }
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            head.visible = true;
            return;
        }
        boolean isAtPlayer = Math.abs(state.x - player.getX()) < 0.5
                && Math.abs(state.z - player.getZ()) < 0.5;
        head.visible = !isAtPlayer;
    }
}