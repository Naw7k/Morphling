package net.naw.morphling.mixin.player.MobBrawl;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.naw.morphling.client.games.MobBrawl.MobBrawlNetworkingServer;
import net.naw.morphling.client.games.MobBrawl.MobBrawlServerGame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MobBrawlDamageMixin {

    /**
     * HEAD inject — cancels damage between brawl and non-brawl players.
     * Fires before any damage is applied.
     */
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void morphling$blockCrossBrawlDamage(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (!(self instanceof ServerPlayer victim)) return;
        if (!(source.getEntity() instanceof ServerPlayer attacker)) return;

        MobBrawlServerGame victimGame   = MobBrawlServerGame.getByPlayer(victim.getUUID());
        MobBrawlServerGame attackerGame = MobBrawlServerGame.getByPlayer(attacker.getUUID());
        boolean victimInBrawl   = victimGame   != null && victimGame.getPhase()   == MobBrawlServerGame.Phase.FIGHTING;
        boolean attackerInBrawl = attackerGame != null && attackerGame.getPhase() == MobBrawlServerGame.Phase.FIGHTING;

        // One is in brawl, other isn't — or they're in different brawls
        if (victimInBrawl != attackerInBrawl ||
                (victimInBrawl && !victimGame.roomId.equals(attackerGame.roomId))) {
            cir.setReturnValue(false);
            return;
        }

        // ── Abilities mode ────────────────────────────────────────────────────
        // Both players are in the SAME active brawl from here on. Cancel (no damage)
        // happens BEFORE the lethal check below, so a blocked hit can never trigger a
        // brawl death. The ability/attack still plays visually — only damage is denied.
        if (victimInBrawl) {
            int abilitiesMode = victimGame.getAbilitiesMode();
            if (abilitiesMode == 1) {
                // No Weapons — block damage when the attacker holds a sword or axe
                net.minecraft.world.item.ItemStack held = attacker.getMainHandItem();
                if (held.is(net.minecraft.tags.ItemTags.SWORDS) || held.is(net.minecraft.tags.ItemTags.AXES)) {
                    cir.setReturnValue(false);
                    return;
                }
            } else if (abilitiesMode == 2) {
                // No Abilities — only allow direct melee (the attacker hitting in person).
                // Blocks creeper explosions, skeleton arrows, etc. (anything where the
                // thing actually dealing the damage isn't the attacker player themselves).
                if (source.getDirectEntity() != attacker) {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }

        // Lethal-damage interception — both players are in the SAME active brawl here.
        // If this hit would kill the victim, DON'T let vanilla death run (that would
        // dump them to the overworld via the death screen and skip the brawl respawn).
        // Instead cancel the damage and drive the brawl death/respawn server-side,
        // synchronously in this same hit — so even a one-shot can't outrun it.
        if (victimInBrawl
                && !victim.hasEffect(net.minecraft.world.effect.MobEffects.RESISTANCE)
                && amount >= victim.getHealth() + victim.getAbsorptionAmount()) {
            // Count the damage that would have been dealt, for end-screen stats
            victimGame.trackDamage(attacker.getUUID(), victim.getHealth());
            MobBrawlNetworkingServer.handleBrawlDeath(victimGame, victim, level.getServer());
            cir.setReturnValue(false);
        }
    }

    /**
     * TAIL inject — tracks damage and broadcasts health for brawl players.
     * Fires after damage is confirmed applied.
     */
    @Inject(method = "hurtServer", at = @At("TAIL"))
    private void morphling$mobBrawlHurt(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (!(self instanceof ServerPlayer victim)) return;
        if (amount <= 0) return;
        if (victim.hasEffect(net.minecraft.world.effect.MobEffects.RESISTANCE)) return;
        if (!(source.getEntity() instanceof ServerPlayer attackerPlayer)) return;

        MobBrawlServerGame game = MobBrawlServerGame.getByPlayer(victim.getUUID());
        if (game == null) return;
        if (game.getPhase() != MobBrawlServerGame.Phase.FIGHTING) return;

        MobBrawlServerGame attackerGame = MobBrawlServerGame.getByPlayer(attackerPlayer.getUUID());
        if (attackerGame == null || !attackerGame.roomId.equals(game.roomId)) return;

        // Track damage dealt for end screen stats
        game.trackDamage(attackerPlayer.getUUID(), amount);

        // Broadcast real health from server
        MobBrawlNetworkingServer.broadcastHealth(game, level.getServer());
    }
}