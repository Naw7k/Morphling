package net.naw.morphling.mixin.player;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.naw.morphling.client.core.MorphState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerAttackMixin {

    @Inject(method = "attack", at = @At("TAIL"))
    private void morphling$ironGolemAttackEffects(Entity entity, CallbackInfo ci) {
        Player self = (Player)(Object)this;
        if (Minecraft.getInstance().player == null) return;
        if (!self.getUUID().equals(Minecraft.getInstance().player.getUUID())) return;
        if (!Minecraft.getInstance().isSameThread()) return;

        // Fox — play bite sound on attack
        if (MorphState.getCurrentMorph() == EntityType.FOX) {
            if (self.level() != null) {
                self.level().playLocalSound(
                        self.getX(), self.getY(), self.getZ(),
                        net.minecraft.sounds.SoundEvents.FOX_BITE,
                        net.minecraft.sounds.SoundSource.PLAYERS,
                        1.0F, 1.0F, false
                );
                MorphState.broadcastSound(net.minecraft.sounds.SoundEvents.FOX_BITE, 1.0F, 1.0F);
            }
            return;
        }
        if (MorphState.getCurrentMorph() != EntityType.IRON_GOLEM) return;

        // Trigger arm-slam animation + attack sound on the cached golem
        if (MorphState.getCachedEntity() instanceof IronGolem golem) {
            golem.handleEntityEvent((byte) 4);
        }

        // Sync knockback to server — server applies it for all players including host
        if (entity instanceof LivingEntity livingTarget) {
            double knockbackResistance = livingTarget.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            double scale = Math.max(0.0, 1.0 - knockbackResistance);
            MorphState.sendAbilityAction("irongolem_knockback", entity.getId() + "," + scale);
            MorphState.sendAbilityState("irongolem_attack", "true");
        }
    }

    @Inject(method = "killedEntity", at = @At("HEAD"))
    private void morphling$rouletteKillScore(ServerLevel level, LivingEntity entity, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player)(Object)this;
        if (Minecraft.getInstance().player == null) return;
        if (!self.getUUID().equals(Minecraft.getInstance().player.getUUID())) return;
        if (!net.naw.morphling.client.games.MorphRoulette.MorphRouletteGame.getInstance().isRunning()) return;

        // Award points based on mob difficulty
        int points = getRouletteKillPoints(entity);
        net.naw.morphling.client.games.MorphRoulette.MorphRouletteGame.getInstance().addScore(points);
    }

    /** Returns roulette kill points based on mob type — harder mobs = more points */
    @Unique
    private static int getRouletteKillPoints(LivingEntity entity) {
        EntityType<?> type = entity.getType();
        // 5 points — tough neutrals/hostiles
        if (type == EntityType.ENDERMAN || type == EntityType.IRON_GOLEM) return 5;
        // 3 points — basic hostiles
        if (type == EntityType.ZOMBIE || type == EntityType.SKELETON || type == EntityType.CREEPER
                || type == EntityType.SPIDER || type == EntityType.SLIME) return 3;
        // 2 points — passive but harder to find/kill
        if (type == EntityType.CAT || type == EntityType.WOLF || type == EntityType.PARROT
                || type == EntityType.VILLAGER || type == EntityType.DOLPHIN || type == EntityType.BEE
                || type == EntityType.FOX) return 2;
        // 1 point — easy passive mobs (chicken, cow, pig, sheep, horse, etc.)
        return 1;
    }
}
