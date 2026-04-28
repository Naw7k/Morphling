package net.naw.morphling.mixin.player;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.naw.morphling.client.core.MorphState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class CameraCrouchMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void morphling$crouchOffset(CallbackInfo ci) {
        if (!MorphState.isMorphed()) return;
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        if (!player.isCrouching()) return;

        // Get morph height to scale offset
        var morph = MorphState.getCachedEntity();
        float offset = 0.15F;
        if (morph instanceof net.minecraft.world.entity.LivingEntity le) {
            // Smaller mobs get smaller offset
            if (le.getBbHeight() < 1.0F) offset = 0.05F;
        }

        try {
            java.lang.reflect.Field eyeField = Camera.class.getDeclaredField("eyeHeight");
            eyeField.setAccessible(true);
            float current = eyeField.getFloat(this);
            eyeField.setFloat(this, current - offset);
        } catch (Exception ignored) {}
    }
}