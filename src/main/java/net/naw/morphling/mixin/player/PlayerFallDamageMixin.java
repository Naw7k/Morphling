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

        // Roulette: brief immunity after morph change prevents unfair fall damage
        if (net.naw.morphling.client.games.MorphRoulette.MorphRouletteGame.getInstance().hasFallDamageImmunity()) {
            cir.setReturnValue(false);
            return;
        }

        boolean isClientThread = Thread.currentThread().getName().equals("Render thread");

        if (isClientThread) {
            var morph = MorphState.getCurrentMorph();
            // Full immunity morphs
            if (morph == EntityType.CHICKEN || morph == EntityType.PARROT || morph == EntityType.IRON_GOLEM || morph == EntityType.SLIME || morph == EntityType.BEE) {
                cir.setReturnValue(false);
            }
            // Fox — only cancel small falls (pounce lands under 6 blocks), real cliff drops still hurt
            if (morph == EntityType.FOX && fallDistance < 6.0) {
                cir.setReturnValue(false);
            }
            // Frog — vanilla reduces fall damage by 5, so cancel falls under 5 blocks
            if (morph == EntityType.FROG && fallDistance < 5.0) {
                cir.setReturnValue(false);
            }
            return;
        }

        // Server-side (dedicated server)
        String morphTypeId = MorphlingNetworking.playerMorphMap.get(self.getUUID());
        if (morphTypeId == null || morphTypeId.isEmpty()) return;
        // Full immunity morphs
        if (morphTypeId.contains("chicken") || morphTypeId.contains("parrot") || morphTypeId.contains("iron_golem") || morphTypeId.contains("slime") || morphTypeId.contains("bee")) {
            cir.setReturnValue(false);
        }
        // Fox — only cancel small falls (pounce), real drops still hurt
        if (morphTypeId.contains("fox") && fallDistance < 6.0) {
            cir.setReturnValue(false);
        }
        // Frog — vanilla reduces fall damage by 5, so cancel falls under 5 blocks
        if (morphTypeId.contains("frog") && fallDistance < 5.0) {
            cir.setReturnValue(false);
        }
    }
}
