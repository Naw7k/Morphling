package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.naw.morphling.client.core.MorphState;

public class WolfAbility {

    private static long lastActionTime = 0L;
    private static final long ACTION_COOLDOWN_MS = 300;

    private static boolean sitting = false;
    private static boolean headTilted = false;
    private static boolean shaking = false;

    public static void toggleSit(Minecraft client) {
        if (!checkReady(client)) return;
        if (!(MorphState.getCachedEntity() instanceof Wolf wolf)) return;
        sitting = !sitting;
        wolf.setInSittingPose(sitting);
    }

    public static void triggerShake(Minecraft client) {
        if (!checkReady(client)) return;
        if (!(MorphState.getCachedEntity() instanceof Wolf wolf)) return;

        // Set wet + shaking flags so the tick logic plays the animation
        var accessor = (net.naw.morphling.mixin.accessors.WolfShakeAccessor)(Object) wolf;
        accessor.morphling$setIsWet(true);
        accessor.morphling$setIsShaking(true);
        accessor.morphling$setShakeAnim(0.0F);
        accessor.morphling$setShakeAnimO(0.0F);
        shaking = true;

        if (client.level != null && client.player != null) {
            client.level.playLocalSound(
                    client.player.getX(), client.player.getY(), client.player.getZ(),
                    SoundEvents.WOLF_SHAKE, SoundSource.PLAYERS,
                    0.5F, 1.0F, false
            );
        }
    }

    public static void toggleHeadTilt(Minecraft client) {
        if (!checkReady(client)) return;
        if (!(MorphState.getCachedEntity() instanceof Wolf wolf)) return;
        headTilted = !headTilted;
        wolf.setIsInterested(headTilted);
    }

    public static void playPant(Minecraft client) {
        if (!checkReady(client)) return;
        if (!(MorphState.getCachedEntity() instanceof Wolf wolf)) return;
        if (client.level == null || client.player == null) return;

        var soundSet = ((net.naw.morphling.mixin.accessors.WolfSoundAccessor)(Object) wolf).morphling$getSoundSet();
        if (soundSet != null) {
            client.level.playLocalSound(
                    client.player.getX(), client.player.getY(), client.player.getZ(),
                    soundSet.pantSound().value(), SoundSource.PLAYERS,
                    1.0F, 1.0F, false
            );
        }
    }

    private static boolean checkReady(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.WOLF) return false;
        if (client.player == null) return false;
        long now = System.currentTimeMillis();
        if (now - lastActionTime < ACTION_COOLDOWN_MS) return false;
        lastActionTime = now;
        return true;
    }

    public static void tick(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.WOLF) {
            sitting = false;
            headTilted = false;
            shaking = false;
            return;
        }
        if (client.player == null) return;
        if (!(MorphState.getCachedEntity() instanceof Wolf wolf)) return;

        // Cancel sit on movement
        if (sitting) {
            double speedSqr = client.player.getDeltaMovement().horizontalDistanceSqr();
            if (speedSqr > 0.001) {
                sitting = false;
                wolf.setInSittingPose(false);
            }
        }

        // Manually animate head tilt (mirrors wolf.tick() logic)
        var tickAccessor = (net.naw.morphling.mixin.accessors.WolfTickAccessor)(Object) wolf;
        float current = tickAccessor.morphling$getInterestedAngle();
        tickAccessor.morphling$setInterestedAngleO(current);
        if (wolf.isInterested()) {
            tickAccessor.morphling$setInterestedAngle(current + (1.0F - current) * 0.4F);
        } else {
            tickAccessor.morphling$setInterestedAngle(current + (0.0F - current) * 0.4F);
        }

        // Manually animate shake (mirrors wolf.tick() logic — shakeAnim increments 0.05 per tick to 2.0)
        if (shaking) {
            var shakeAccessor = (net.naw.morphling.mixin.accessors.WolfShakeAccessor)(Object) wolf;
            float shakeAnim = shakeAccessor.morphling$getShakeAnim();
            shakeAccessor.morphling$setShakeAnimO(shakeAnim);
            shakeAnim += 0.05F;
            shakeAccessor.morphling$setShakeAnim(shakeAnim);

            if (shakeAnim >= 2.0F) {
                shaking = false;
                shakeAccessor.morphling$setIsWet(false);
                shakeAccessor.morphling$setIsShaking(false);
                shakeAccessor.morphling$setShakeAnim(0.0F);
                shakeAccessor.morphling$setShakeAnimO(0.0F);
            }
        }
    }
}