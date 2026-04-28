package net.naw.morphling.mixin.player;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.naw.morphling.client.debug.DebugSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class SpeedometerMixin {

    @Unique
    private Vec3 morphling$previousPosition = null;

    @Unique
    private double morphling$smoothKmh = 0.0;

    @Unique
    private boolean morphling$lastWasVisible = false;

    @Inject(method = "tick", at = @At("HEAD"))
    private void morphling$tickSpeedometer(CallbackInfo ci) {
        Player player = (Player)(Object)this;

        // Only affect the local player
        if (Minecraft.getInstance().player == null) return;
        if (!player.getUUID().equals(Minecraft.getInstance().player.getUUID())) return;

        if (DebugSettings.isSpeedometerEnabled()) {
            Vec3 currentPosition = new Vec3(player.getX(), player.getY(), player.getZ());

            if (morphling$previousPosition != null) {
                double deltaX = currentPosition.x - morphling$previousPosition.x;
                double deltaZ = currentPosition.z - morphling$previousPosition.z;
                morphling$smoothKmh = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ) * 20 * 3.6;
            }
            morphling$previousPosition = currentPosition;

            String state = player.isSprinting() ? "Sprinting" : (player.isCrouching() ? "Sneaking" : "Walking");

            int tempColor = 0xFFFFFF;
            if (morphling$smoothKmh > 30) tempColor = 0xFF5555;
            else if (morphling$smoothKmh > 20) tempColor = 0xFFFF55;
            final int finalColor = tempColor;

            double speedAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) != null
                    ? player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getBaseValue()
                    : 0.1;

            String content = String.format("Speed attr: %.3f | %.1f km/h (%s)", speedAttr, morphling$smoothKmh, state);

            Minecraft.getInstance().gui.setOverlayMessage(
                    Component.literal(content).withStyle(style -> style.withColor(finalColor)),
                    false
            );
            morphling$lastWasVisible = true;
        } else if (morphling$lastWasVisible) {
            Minecraft.getInstance().gui.setOverlayMessage(Component.literal(""), false);
            morphling$lastWasVisible = false;
        }
    }
}