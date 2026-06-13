package net.naw.morphling.mixin.aquatic;

import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.naw.morphling.client.core.MorphState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Grants water breathing to aquatic morphs (dolphin, axolotl, frog).
 * Dual-pathed: client checks MorphState, server checks playerMorphMap.
 */
@Mixin(MobEffectUtil.class)
public class AquaticWaterBreathingMixin {

    @Inject(method = "hasWaterBreathing", at = @At("HEAD"), cancellable = true)
    private static void morphling$dolphinBreathe(LivingEntity mob, CallbackInfoReturnable<Boolean> cir) {
        if (mob.level().isClientSide()) {
            if (!MorphState.isMorphed()) return;
            var morph = MorphState.getCachedEntity();
            if (morph != null && (morph.getType() == EntityType.DOLPHIN || morph.getType() == EntityType.AXOLOTL || morph.getType() == EntityType.FROG)) {
                cir.setReturnValue(true);
            }
        } else {
            String morphId = net.naw.morphling.network.MorphlingNetworking.playerMorphMap.get(mob.getUUID());
            if ("minecraft:dolphin".equals(morphId) || "minecraft:axolotl".equals(morphId) || "minecraft:frog".equals(morphId)) {
                cir.setReturnValue(true);
            }
        }
    }
}