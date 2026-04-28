package net.naw.morphling.mixin.dolphin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.naw.morphling.client.core.MorphState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class DolphinNoDrownMixin {

    @Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
    private void morphling$dolphinNoDrown(ServerLevel level, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (!MorphState.isMorphed()) return;
        var morph = MorphState.getCachedEntity();
        if (morph != null && morph.getType() == EntityType.DOLPHIN) {
            if (source.is(DamageTypeTags.IS_DROWNING)) {
                cir.setReturnValue(true);
            }
        }
    }
}