package net.naw.morphling.client.debug;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.naw.morphling.client.debug.DebugSettings;

public class DamageIndicator {

    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!DebugSettings.isDamageIndicatorEnabled()) return InteractionResult.PASS;
            if (world.isClientSide() && entity instanceof LivingEntity target) {
                double attackDamage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
                float targetHealthBefore = target.getHealth();
                net.minecraft.client.Minecraft.getInstance().gui.setOverlayMessage(
                        Component.literal("Hit " + entity.getType().getDescription().getString()
                                + " for " + String.format("%.1f", attackDamage)
                                + " (target HP: " + String.format("%.1f", targetHealthBefore) + ")"),
                        false
                );
            }
            return InteractionResult.PASS;
        });
    }
}