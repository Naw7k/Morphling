package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.phys.Vec3;
import net.naw.morphling.client.core.MorphState;
import net.naw.morphling.client.core.MorphVariantManager;

public class SlimeAbility {

    private static final long BIG_JUMP_COOLDOWN_MS = 1000;
    private static long lastBigJumpTime = 0L;

    private static long lastSmallJumpTime = 0L;
    private static final long SMALL_JUMP_COOLDOWN_MS = 600;

    /** R — big manual jump in look direction */
    public static void triggerBigJump(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.SLIME) return;
        if (client.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastBigJumpTime < BIG_JUMP_COOLDOWN_MS) return;
        lastBigJumpTime = now;

        int size = MorphVariantManager.getSlimeSize();
        double jumpPower = 0.4 + size * 0.1;

        Vec3 look = client.player.getLookAngle();
        Vec3 current = client.player.getDeltaMovement();
        client.player.setDeltaMovement(
                current.x + look.x * 0.3 * size,
                jumpPower,
                current.z + look.z * 0.3 * size
        );

        playJumpSound(client, size);
    }

    /** Space — small hop with slime sound */
    public static void triggerSmallJump(Minecraft client) {
        if (client.player == null) return;
        if (!client.player.onGround()) return;
        long now = System.currentTimeMillis();
        if (now - lastSmallJumpTime < SMALL_JUMP_COOLDOWN_MS) return;
        lastSmallJumpTime = now;

        int size = MorphVariantManager.getSlimeSize();
        double jumpPower = 0.3 + size * 0.05;
        Vec3 look = client.player.getLookAngle();
        Vec3 current = client.player.getDeltaMovement();
        client.player.setDeltaMovement(
                current.x + look.x * 0.5,
                jumpPower,
                current.z + look.z * 0.5
        );
        playJumpSound(client, size);
    }

    /** Called every tick */
    public static void tick(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.SLIME) return;

        if (client.player == null) return;
        if (!(MorphState.getCachedEntity() instanceof Slime slime)) return;

        // Slow walk speed
        client.player.setSpeed(0.08F);

        int size = MorphVariantManager.getSlimeSize();

// Contact damage — size 2+ only, same as vanilla
        if (size >= 2 && client.player.onGround()) {
            if (client.level != null) {
                for (net.minecraft.world.entity.Entity entity : client.level.getEntities(client.player,
                        client.player.getBoundingBox().inflate(0.8))) {

                    if (entity instanceof net.minecraft.world.entity.LivingEntity
                            && !entity.getUUID().equals(client.player.getUUID())) {
                        MorphState.sendAbilityAction("slime_contact_damage",
                                entity.getUUID() + "," + size);
                    }
                }
            }
        }

        // Tick the cached entity for squish animation
        slime.tickCount = client.player.tickCount;
        slime.setPos(client.player.getX(), client.player.getY(), client.player.getZ());
        try {
            slime.tick();
            slime.setPos(client.player.getX(), client.player.getY(), client.player.getZ());
            slime.setDeltaMovement(0, 0, 0);
        } catch (Exception ignored) {}
    }

    private static void playJumpSound(Minecraft client, int size) {
        if (client.level == null || client.player == null) return;
        boolean tiny = size <= 1;
        client.level.playLocalSound(
                client.player.getX(), client.player.getY(), client.player.getZ(),
                tiny ? SoundEvents.SLIME_JUMP_SMALL : SoundEvents.SLIME_JUMP,
                SoundSource.PLAYERS,
                0.4F * size, 1.0F, false
        );
        MorphState.broadcastSound(
                tiny ? SoundEvents.SLIME_JUMP_SMALL : SoundEvents.SLIME_JUMP,
                0.4F * size, 1.0F
        );
    }
}
