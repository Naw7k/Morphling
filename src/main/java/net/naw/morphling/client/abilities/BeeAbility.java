package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.phys.Vec3;
import net.naw.morphling.client.core.MorphState;
import net.naw.morphling.mixin.accessors.BeeOnGroundAccessor;

public class BeeAbility {

    private static final long STING_COOLDOWN_MS = 2000;
    private static long lastStingTime = 0L;

    private static boolean angry = false;
    private static boolean hasStung = false;
    public static net.minecraft.client.resources.sounds.AbstractTickableSoundInstance activeBeeSound = null;
    private static long lastPollinateTime = 0L;
    private static final long POLLINATE_COOLDOWN_MS = 1500;

    /** R — sting nearby entity, poison them */
    public static void triggerSting(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.BEE) return;
        if (client.player == null) return;
        if (hasStung) return; // bee can only sting once
        long now = System.currentTimeMillis();
        if (now - lastStingTime < STING_COOLDOWN_MS) return;
        lastStingTime = now;

        // Find nearest entity in range and sting it
        if (client.level == null) return;
        net.minecraft.world.entity.Entity nearest = null;
        double nearestDist = 3.0;
        for (net.minecraft.world.entity.Entity entity : client.level.getEntities(client.player,
                client.player.getBoundingBox().inflate(3.0))) {
            if (entity instanceof net.minecraft.world.entity.LivingEntity
                    && !entity.getUUID().equals(client.player.getUUID())) {
                double dist = entity.distanceTo(client.player);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = entity;
                }
            }
        }

        if (nearest != null) {
            MorphState.sendAbilityAction("bee_sting", nearest.getUUID().toString());
            hasStung = true;
            // Play sting sound
            client.level.playLocalSound(
                    client.player.getX(), client.player.getY(), client.player.getZ(),
                    SoundEvents.BEE_STING, SoundSource.PLAYERS, 1.0F, 1.0F, false
            );
            MorphState.broadcastSound(SoundEvents.BEE_STING, 1.0F, 1.0F);
        }
    }

    /** F — toggle angry mode */
    public static void toggleAngry() {
        angry = !angry;
        MorphState.sendAbilityState("bee_angry", String.valueOf(angry));

        // Stop current sound and start the right one
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (activeBeeSound != null) {
            mc.getSoundManager().stop(activeBeeSound);
            activeBeeSound = null;
        }
        if (MorphState.isFlightActive()) {
            if (angry) {
                activeBeeSound = new net.naw.morphling.client.sounds.PlayerBeeAggressiveSoundInstance(mc.player);
            } else {
                activeBeeSound = new net.naw.morphling.client.sounds.PlayerBeeFlyingSoundInstance(mc.player);
            }
            mc.getSoundManager().queueTickingSound(activeBeeSound);
        }
    }

    private static boolean hasNectar = false;

    public static boolean hasNectar() { return hasNectar; }

    public static void toggleNectar() {
        hasNectar = !hasNectar;
        MorphState.sendAbilityState("bee_nectar", String.valueOf(hasNectar));
    }

    private static float rollAmount = 0.0F;
    private static int rollTicks = 0;

    public static float getRollAmount() { return rollAmount; }

    public static void triggerRoll() {
        if (angry) {
            rollTicks = 20;
        }
    }

    public static boolean isAngry() { return angry; }

    public static void tick(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.BEE) {
            if (angry) {
                angry = false;
                MorphState.sendAbilityState("bee_angry", "false");
            }
            hasStung = false;
            hasNectar = false;
            return;
        }

        if (rollTicks > 0) {
            rollTicks--;
            rollAmount = Math.min(1.0F, rollAmount + 0.2F);
        } else {
            rollAmount = Math.max(0.0F, rollAmount - 0.24F);
        }

        if (rollTicks > 0 || rollAmount > 0) {
            MorphState.sendAbilityState("bee_roll", String.valueOf(rollAmount));
        }

        if (client.player == null) return;
        if (!(MorphState.getCachedEntity() instanceof Bee bee)) return;

        // Tick cached entity for wing animation
        bee.tickCount = client.player.tickCount;
        bee.setPos(client.player.getX(), client.player.getY(), client.player.getZ());
        try {
            bee.tick();
            bee.setPos(client.player.getX(), client.player.getY(), client.player.getZ());
            bee.setDeltaMovement(0, 0, 0);
        } catch (Exception ignored) {}
        if (client.player.onGround()) {
            ((BeeOnGroundAccessor) bee).morphling$setOnGroundDirect(true);
        }

        // Bee-specific flight adjustments — slower and hovery
        if (MorphState.isFlightActive()) {
            Vec3 velocity = client.player.getDeltaMovement();

            // Hover — dampen Y velocity more aggressively when not pressing sprint/shift
            boolean ascending = client.options.keySprint.isDown();
            boolean descending = client.options.keyShift.isDown();
            if (!ascending && !descending) {
                // Hover in place — strongly dampen vertical movement
                double newY = velocity.y * 0.6;
                client.player.setDeltaMovement(velocity.x, newY, velocity.z);
            }

            // Cap speed lower than parrot
            Vec3 current = client.player.getDeltaMovement();
            double maxSpeed = 0.25;
            double horizSpeedSq = current.x * current.x + current.z * current.z;
            if (horizSpeedSq > maxSpeed * maxSpeed) {
                double scale = maxSpeed / Math.sqrt(horizSpeedSq);
                client.player.setDeltaMovement(current.x * scale, current.y, current.z * scale);
            }
        }
    }

    public static void triggerPollinate(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.BEE) return;
        if (client.level == null || client.player == null) return;

        long now = System.currentTimeMillis();
        if (now - lastPollinateTime < POLLINATE_COOLDOWN_MS) return;
        lastPollinateTime = now;

        // Play sound
        client.level.playLocalSound(
                client.player.getX(), client.player.getY(), client.player.getZ(),
                SoundEvents.BEE_POLLINATE, SoundSource.PLAYERS, 1.0F, 1.0F, false
        );
        MorphState.broadcastSound(SoundEvents.BEE_POLLINATE, 1.0F, 1.0F);
        MorphState.sendAbilityState("bee_pollinate", "true");

        // Spawn nectar particles around player
        for (int i = 0; i < 8; i++) {
            double offsetX = (client.level.getRandom().nextDouble() - 0.5) * 0.6;
            double offsetZ = (client.level.getRandom().nextDouble() - 0.5) * 0.6;
            double offsetY = client.level.getRandom().nextDouble() * 0.5;
            client.level.addParticle(
                    net.minecraft.core.particles.ParticleTypes.FALLING_NECTAR,
                    client.player.getX() + offsetX,
                    client.player.getY() + offsetY,
                    client.player.getZ() + offsetZ,
                    0, 0, 0
            );
        }
    }
}