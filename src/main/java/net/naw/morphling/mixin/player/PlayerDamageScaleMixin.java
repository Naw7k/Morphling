package net.naw.morphling.mixin.player;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.naw.morphling.client.core.MorphState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Player.class)
public class PlayerDamageScaleMixin {

    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, name = "damage")
    private float morphling$scaleDamage(float damage, net.minecraft.server.level.ServerLevel level, DamageSource source, float origDamage) {
        if (!MorphState.isMorphed()) return damage;
        var morph = MorphState.getCachedEntity();
        if (!(morph instanceof LivingEntity le)) return damage;
        float morphMax = le.getMaxHealth();
        if (morphMax <= 0) return damage;
        return damage * (20.0F / morphMax);
    }
}