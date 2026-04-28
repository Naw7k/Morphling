package net.naw.morphling.mixin.player;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.naw.morphling.client.core.MorphState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerAttackMixin {

    @Inject(method = "attack", at = @At("TAIL"))
    private void morphling$ironGolemAttackEffects(Entity target, CallbackInfo ci) {
        Player self = (Player)(Object)this;
        if (Minecraft.getInstance().player == null) return;
        if (!self.getUUID().equals(Minecraft.getInstance().player.getUUID())) return;
        if (MorphState.getCurrentMorph() != EntityType.IRON_GOLEM) return;

        // Trigger arm-slam animation + attack sound on the cached golem
        if (MorphState.getCachedEntity() instanceof IronGolem golem) {
            golem.handleEntityEvent((byte) 4);
        }

        // Apply vanilla-style upward knockback to living targets
        if (target instanceof LivingEntity livingTarget) {
            double knockbackResistance = livingTarget.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            double scale = Math.max(0.0, 1.0 - knockbackResistance);
            target.setDeltaMovement(
                    target.getDeltaMovement().add(0.0, 0.4 * scale, 0.0)
            );
        }
    }
}