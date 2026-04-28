package net.naw.morphling.mixin.dolphin;

import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.naw.morphling.client.core.MorphState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEffectUtil.class)
public class DolphinWaterBreathingMixin {

    @Inject(method = "hasWaterBreathing", at = @At("HEAD"), cancellable = true)
    private static void morphling$dolphinBreathe(LivingEntity mob, CallbackInfoReturnable<Boolean> cir) {
        if (!MorphState.isMorphed()) return;
        var morph = MorphState.getCachedEntity();
        if (morph != null && morph.getType() == EntityType.DOLPHIN) {
            cir.setReturnValue(true);
        }
    }
}