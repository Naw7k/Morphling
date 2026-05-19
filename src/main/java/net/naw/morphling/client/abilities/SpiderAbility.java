package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.phys.Vec3;
import net.naw.morphling.client.core.MorphState;

public class SpiderAbility {

    private static final long LEAP_COOLDOWN_MS = 800;
    private static long lastLeapTime = 0L;

    // Wall climbing state
    private static boolean isClimbing = false;

    /** R — leap forward in look direction */
    public static void triggerLeap(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.SPIDER) return;
        if (client.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastLeapTime < LEAP_COOLDOWN_MS) return;
        lastLeapTime = now;

        Vec3 look = client.player.getLookAngle();
        Vec3 current = client.player.getDeltaMovement();
        // Leap in look direction with upward boost
        client.player.setDeltaMovement(
                current.x + look.x * 0.6,
                0.4,
                current.z + look.z * 0.6
        );
        client.player.hurtMarked = true;

        if (client.level != null) {
            client.level.playLocalSound(
                    client.player.getX(), client.player.getY(), client.player.getZ(),
                    SoundEvents.SPIDER_STEP, SoundSource.PLAYERS,
                    0.5F, 1.2F, false
            );
        }
        MorphState.broadcastSound(SoundEvents.SPIDER_STEP, 0.5F, 1.2F);
    }

    /** Tick — handles wall climbing detection and physics */
    public static void tick(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.SPIDER) {
            if (isClimbing) {
                isClimbing = false;
                MorphState.sendAbilityState("spider_climbing", "false");
            }
            return;
        }

        if (client.player == null) return;
        if (!(MorphState.getCachedEntity() instanceof Spider spider)) return;

        // Detect horizontal collision (walking into a wall)
        boolean touching = client.player.horizontalCollision;

        if (touching && !client.player.onGround()) {
            // Climbing — cancel gravity and push against the wall
            isClimbing = true;
            Vec3 velocity = client.player.getDeltaMovement();

            double climbSpeed = 0.0;
            if (client.options.keyUp.isDown()) climbSpeed = 0.15; // W = climb up
            if (client.options.keyShift.isDown()) climbSpeed = -0.10; // Shift = slide down

            client.player.setDeltaMovement(velocity.x, climbSpeed, velocity.z);
            client.player.resetFallDistance();
            MorphState.sendAbilityState("spider_climbing", "true");
        } else {
            if (isClimbing) {
                isClimbing = false;
                MorphState.sendAbilityState("spider_climbing", "false");
            }
        }

        // Sync climbing state to cached entity
        spider.setClimbing(isClimbing);
    }

}