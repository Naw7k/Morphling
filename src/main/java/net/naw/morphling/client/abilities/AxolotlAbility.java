package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.naw.morphling.client.core.MorphState;
import net.naw.morphling.client.core.RemoteMorphState;

/**
 * Axolotl morph ability.

 * R — toggle play dead. Lasts 10 seconds (200 ticks) like vanilla, granting
 * Regeneration I for the duration, then auto-revives. Pressing R again or moving
 * cancels early. Pressing R after it ends starts a fresh 10s cycle (no cooldown).

 * Animations are driven from AxolotlAbility.tickAnimators by ticking the cached
 * axolotl entity's BinaryAnimators at the client tick rate.
 */
@SuppressWarnings("unused")
public class AxolotlAbility {

    private static final int PLAY_DEAD_TICKS = 200; // 10 seconds, matches vanilla

    private static boolean playingDead = false;
    private static int playDeadTimer = 0;          // counts down while playing dead
    private static int dryTimer = 6000;            // 5 minutes out of water before dry damage

    /** R — toggle play dead */
    public static void togglePlayDead(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.AXOLOTL) return;
        if (client.player == null) return;
        // Don't allow STARTING play dead while moving (re-press to cancel is always allowed)
        if (!playingDead && client.player.getDeltaMovement().horizontalDistanceSqr() > 0.003) return;

        playingDead = !playingDead;
        playDeadTimer = playingDead ? PLAY_DEAD_TICKS : 0;

        // Apply play dead state to cached entity
        if (MorphState.getCachedEntity() instanceof Axolotl axolotl) {
            axolotl.setPlayingDead(playingDead);
        }

        // Regen while playing dead, remove when stopping — sent to server so it works on dedicated servers too
        if (playingDead) {
            MorphState.sendAbilityAction("axolotl_playdead_on", "");
        } else {
            MorphState.sendAbilityAction("axolotl_playdead_off", "");
        }

        // Play splash sound as feedback
        if (client.level != null && client.player != null) {
            client.level.playLocalSound(
                    client.player.getX(), client.player.getY(), client.player.getZ(),
                    SoundEvents.AXOLOTL_SPLASH, SoundSource.PLAYERS,
                    0.8F, 1.0F, false
            );
            MorphState.broadcastSound(SoundEvents.AXOLOTL_SPLASH, 0.8F, 1.0F);
        }

        MorphState.sendAbilityState("axolotl_playdead", String.valueOf(playingDead));
    }

    /** Centralized stop — clears state, entity pose, regen, and re-syncs. */
    private static void stopPlayDead() {
        playingDead = false;
        playDeadTimer = 0;
        if (MorphState.getCachedEntity() instanceof Axolotl axolotl) {
            axolotl.setPlayingDead(false);
        }
        MorphState.sendAbilityState("axolotl_playdead", "false");
        MorphState.sendAbilityAction("axolotl_playdead_off", "");
    }

    public static void tick(Minecraft client) {
        if (client.player == null) return;

        if (MorphState.getCurrentMorph() != EntityType.AXOLOTL) {
            dryTimer = 6000;
            if (playingDead) stopPlayDead();
            return;
        }

        if (playingDead) {
            // Cancel if the player moves
            if (client.player.getDeltaMovement().horizontalDistanceSqr() > 0.003) {
                stopPlayDead();
            } else {
                // 10-second auto-revive
                playDeadTimer--;
                if (playDeadTimer <= 0) {
                    stopPlayDead();
                }
            }
        }

        // Dry damage — axolotl takes damage after 5 minutes out of water
        if (client.player.isInWaterOrRain()) {
            dryTimer = 6000;
        } else {
            dryTimer--;
            if (dryTimer <= 0) {
                dryTimer = 0;
                var server = client.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> {
                        var sp = server.getPlayerList().getPlayer(client.player.getUUID());
                        if (sp != null) {
                            //noinspection deprecation
                            sp.hurt(sp.damageSources().dryOut(), 2.0F);
                        }
                    });
                } else {
                    MorphState.sendAbilityAction("axolotl_dry_damage", "");
                }
            }
        }

        // Reset dry timer on death
        if (client.player.getHealth() <= 0 || client.player.isDeadOrDying()) {
            dryTimer = 6000;
        }
    }

    public static void tickAnimators(Minecraft client) {
        if (client.player == null) return;

        // Local
        if (MorphState.getCurrentMorph() == EntityType.AXOLOTL
                && MorphState.getCachedEntity() instanceof Axolotl axo) {
            driveAnimators(axo, client.player, isPlayingDead());
        }

        // Remote
        if (client.level != null) {
            for (var p : client.level.players()) {
                var data = RemoteMorphState.get(p.getUUID());
                if (data != null && data.cachedEntity instanceof Axolotl axo) {
                    driveAnimators(axo, p, data.axolotlPlayingDead);
                }
            }
        }
    }

    private static void driveAnimators(Axolotl axo, net.minecraft.world.entity.player.Player p, boolean playingDead) {
        boolean inWater  = p.isInWater();
        boolean onGround = p.onGround();
        float speed = ((net.naw.morphling.mixin.accessors.WalkAnimationStateAccessor) p.walkAnimation).morphling$getSpeed();
        boolean moving = speed > 0.01F;

        axo.tickCount = p.tickCount;
        axo.setPlayingDead(playingDead);
        axo.playingDeadAnimator.tick(playingDead);
        if (playingDead) {
            axo.inWaterAnimator.tick(false);
            axo.onGroundAnimator.tick(false);
            axo.movingAnimator.tick(false);
        } else {
            axo.inWaterAnimator.tick(inWater);
            axo.onGroundAnimator.tick(onGround);
            axo.movingAnimator.tick(moving);
        }
    }

    public static boolean isPlayingDead() { return playingDead; }
}