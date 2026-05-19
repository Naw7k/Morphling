package net.naw.morphling.mixin.player;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.naw.morphling.client.core.MorphState;
import net.naw.morphling.network.MorphlingNetworking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerFallDamageMixin {

    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void morphling$noFallDamage(double fallDistance, float damageModifier, net.minecraft.world.damagesource.DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player)(Object)this;

        boolean isClientThread = Thread.currentThread().getName().equals("Render thread");

        if (isClientThread) {
            var morph = MorphState.getCurrentMorph();
            if (morph == EntityType.CHICKEN || morph == EntityType.PARROT || morph == EntityType.IRON_GOLEM || morph == EntityType.SLIME || morph == EntityType.BEE) {
                cir.setReturnValue(false);
            }
            return;
        }

        String morphTypeId = MorphlingNetworking.playerMorphMap.get(self.getUUID());
        if (morphTypeId == null || morphTypeId.isEmpty()) return;
        if (morphTypeId.contains("chicken") || morphTypeId.contains("parrot") || morphTypeId.contains("iron_golem") || morphTypeId.contains("slime") || morphTypeId.contains("bee")) {
            cir.setReturnValue(false);
        }
    }
}