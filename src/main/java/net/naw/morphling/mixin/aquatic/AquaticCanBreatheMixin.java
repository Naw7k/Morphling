package net.naw.morphling.mixin.aquatic;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.naw.morphling.client.core.MorphState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.world.entity.LivingEntity.class)
public class AquaticCanBreatheMixin {

    /**
     * Makes aquatic morphs (dolphin, axolotl, frog) able to breathe underwater — stops air bubbles from
     * depleting. Dual-pathed:
     *   - Client: checks MorphState (singleplayer / LAN host)
     *   - Server: checks playerMorphMap (dedicated server authoritative path)
     * Only applies while actively morphed — un-morphing restores normal air depletion immediately.
     */
    @Inject(method = "canBreatheUnderwater", at = @At("HEAD"), cancellable = true)
    private void morphling$dolphinCanBreathe(CallbackInfoReturnable<Boolean> cir) {
        net.minecraft.world.entity.LivingEntity self = (net.minecraft.world.entity.LivingEntity)(Object)this;
        if (!(self instanceof Player player)) return;
        if (player.level().isClientSide()) {
            if (!MorphState.isMorphed()) return;
            var morph = MorphState.getCachedEntity();
            if (morph != null && (morph.getType() == EntityType.DOLPHIN || morph.getType() == EntityType.AXOLOTL || morph.getType() == EntityType.FROG)) {
                cir.setReturnValue(true);
            }
        } else {
            String morphId = net.naw.morphling.network.MorphlingNetworking.playerMorphMap.get(player.getUUID());
            if ("minecraft:dolphin".equals(morphId) || "minecraft:axolotl".equals(morphId) || "minecraft:frog".equals(morphId)) {
                cir.setReturnValue(true);
            }
        }
    }
}