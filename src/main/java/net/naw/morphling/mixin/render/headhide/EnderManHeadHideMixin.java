package net.naw.morphling.mixin.render.headhide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.naw.morphling.client.compat.FpmCompat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndermanModel.class)
public class EnderManHeadHideMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/EndermanRenderState;)V", at = @At("TAIL"))
    private void morphling$hideHead(EndermanRenderState state, CallbackInfo ci) {
        // Cast "this" to HumanoidModel to access the head field safely
        var model = (HumanoidModel<?>)(Object)this;

        if (!FpmCompat.shouldHideHeadNow()) { model.head.visible = true; return; }
        if (state.lightCoords == 15728880) { model.head.visible = true; return; }

        var player = Minecraft.getInstance().player;
        if (player == null) { model.head.visible = true; return; }

        boolean isAtPlayer = Math.abs(state.x - player.getX()) < 0.5
                && Math.abs(state.z - player.getZ()) < 0.5;

        model.head.visible = !isAtPlayer;
    }
}
