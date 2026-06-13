package net.naw.morphling.mixin.player;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.naw.morphling.network.MorphlingNetworking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Player.class)
public class PlayerDamageScaleMixin {

    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, name = "damage")
    private float morphling$scaleDamage(float damage, ServerLevel level, DamageSource source, float origDamage) {
        Player self = (Player)(Object)this;

        // ── Mob Brawl: Equal Damage mode ──────────────────────────────────────
        // Caps damage between two players in the same active brawl to a flat 2f
        // (one heart). Done here as a damage-arg override so it flows through normal
        // hurtServer — NOT via a re-entrant hurtServer call, which would double-fire
        // the brawl damage mixin and bypass lethal-death handling.
        if (source.getEntity() instanceof Player attacker) {
            var victimGame   = net.naw.morphling.client.games.MobBrawl.MobBrawlServerGame.getByPlayer(self.getUUID());
            var attackerGame = net.naw.morphling.client.games.MobBrawl.MobBrawlServerGame.getByPlayer(attacker.getUUID());
            boolean sameBrawl = victimGame != null && attackerGame != null
                    && victimGame.getPhase() == net.naw.morphling.client.games.MobBrawl.MobBrawlServerGame.Phase.FIGHTING
                    && victimGame.roomId.equals(attackerGame.roomId);
            if (sameBrawl && victimGame.getDamageMode() == 1) {
                return Math.min(damage, 2.0F);
            }
        }

        // Don't scale damage from other players — only from mobs/environment
        if (source.getEntity() instanceof Player) return damage;

        String morphTypeId = MorphlingNetworking.playerMorphMap.get(self.getUUID());
        if (morphTypeId == null || morphTypeId.isEmpty()) return damage;

        try {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(morphTypeId));
            if (type != null) {
                var entity = type.create(level, EntitySpawnReason.LOAD);
                if (entity instanceof LivingEntity le) {
                    float morphMax = le.getMaxHealth();
                    if (morphMax > 0) return damage * (20.0F / morphMax);
                }
            }
        } catch (Exception ignored) {}

        return damage;
    }
}
