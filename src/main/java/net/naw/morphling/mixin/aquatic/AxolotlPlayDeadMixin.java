package net.naw.morphling.mixin.aquatic;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.naw.morphling.client.abilities.AxolotlAbility;
import net.naw.morphling.client.core.MorphState;
import net.naw.morphling.network.MorphlingNetworking;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class AxolotlPlayDeadMixin {

    @Inject(method = "canBeSeenAsEnemy", at = @At("HEAD"), cancellable = true)
    private void morphling$axolotlPlayDead(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (!(self instanceof Player player)) return;

        if (player.level().isClientSide()) {
            if (MorphState.getCurrentMorph() == EntityType.AXOLOTL && AxolotlAbility.isPlayingDead()) {
                cir.setReturnValue(false);
            }
        } else {
            String morphId = MorphlingNetworking.playerMorphMap.get(player.getUUID());
            if ("minecraft:axolotl".equals(morphId) && MorphlingNetworking.axolotlPlayingDead.contains(player.getUUID())) {
                cir.setReturnValue(false);
            }
        }
    }
}