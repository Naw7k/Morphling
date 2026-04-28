package net.naw.morphling.mixin.render.headhide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.animal.dolphin.DolphinModel;
import net.minecraft.client.renderer.entity.state.DolphinRenderState;
import net.naw.morphling.client.compat.FpmCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DolphinModel.class)
public class DolphinHeadHideMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/DolphinRenderState;)V", at = @At("TAIL"))
    private void morphling$hideHead(DolphinRenderState state, CallbackInfo ci) {
        try {
            java.lang.reflect.Field bodyField = DolphinModel.class.getDeclaredField("body");
            bodyField.setAccessible(true);
            net.minecraft.client.model.geom.ModelPart body = (net.minecraft.client.model.geom.ModelPart) bodyField.get(this);
            net.minecraft.client.model.geom.ModelPart head = body.getChild("head");

            if (!FpmCompat.shouldHideHeadNow()) { head.visible = true; return; }
            if (state.lightCoords == 15728880) { head.visible = true; return; }
            var player = Minecraft.getInstance().player;
            if (player == null) { head.visible = true; return; }
            boolean isAtPlayer = Math.abs(state.x - player.getX()) < 0.5
                    && Math.abs(state.z - player.getZ()) < 0.5;
            head.visible = !isAtPlayer;
        } catch (Exception ignored) {}
    }
}