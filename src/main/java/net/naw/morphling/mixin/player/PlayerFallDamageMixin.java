package net.naw.morphling.mixin.player;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.naw.morphling.client.core.MorphState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerFallDamageMixin {

    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void morphling$noFallDamage(double fallDistance, float damageModifier, net.minecraft.world.damagesource.DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        var morph = MorphState.getCurrentMorph();
        if (morph == EntityType.CHICKEN) {
            cir.setReturnValue(false);
        } else if (morph == EntityType.PARROT) {
            cir.setReturnValue(false);
        } else if (morph == EntityType.IRON_GOLEM) {
            cir.setReturnValue(false);

        }
    }
}