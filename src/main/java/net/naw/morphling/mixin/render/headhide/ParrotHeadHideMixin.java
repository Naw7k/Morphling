package net.naw.morphling.mixin.render.headhide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.animal.parrot.ParrotModel;
import net.minecraft.client.renderer.entity.state.ParrotRenderState;
import net.naw.morphling.client.compat.FpmCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParrotModel.class)
public class ParrotHeadHideMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/ParrotRenderState;)V", at = @At("TAIL"))
    private void morphling$hideHead(ParrotRenderState state, CallbackInfo ci) {
        try {
            java.lang.reflect.Field headField = this.getClass().getDeclaredField("head");
            headField.setAccessible(true);
            net.minecraft.client.model.geom.ModelPart head = (net.minecraft.client.model.geom.ModelPart) headField.get(this);

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