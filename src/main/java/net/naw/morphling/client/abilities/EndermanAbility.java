package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class EndermanAbility {

    private static final double MAX_TELEPORT_DISTANCE = 32.0;
    private static long lastTeleportTime = 0L;
    private static final long TELEPORT_COOLDOWN_MS = 500;

    public static void trigger(Minecraft client) {
        Player player = client.player;
        Level level = client.level;
        if (player == null || level == null) return;

        // Cooldown
        long now = System.currentTimeMillis();
        if (now - lastTeleportTime < TELEPORT_COOLDOWN_MS) return;
        lastTeleportTime = now;

        RandomSource rng = player.getRandom();

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(MAX_TELEPORT_DISTANCE));

        BlockHitResult hit = level.clip(new ClipContext(
                eyePos, endPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));

        Vec3 targetPos;
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos blockPos = hit.getBlockPos().relative(hit.getDirection());
            targetPos = new Vec3(blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 0.5);
        } else {
            targetPos = endPos;
        }

        BlockState stateAtTarget = level.getBlockState(BlockPos.containing(targetPos));
        if (stateAtTarget.blocksMotion()) return;

        level.playLocalSound(
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                1.0F, 1.0F, false
        );

        for (int i = 0; i < 32; i++) {
            level.addParticle(ParticleTypes.PORTAL,
                    player.getX() + (rng.nextDouble() - 0.5) * player.getBbWidth() * 2,
                    player.getY() + rng.nextDouble() * player.getBbHeight(),
                    player.getZ() + (rng.nextDouble() - 0.5) * player.getBbWidth() * 2,
                    (rng.nextDouble() - 0.5) * 2,
                    -rng.nextDouble(),
                    (rng.nextDouble() - 0.5) * 2);
        }

        teleportServerPlayer(client, targetPos);
        player.setPos(targetPos.x, targetPos.y, targetPos.z);

        for (int i = 0; i < 32; i++) {
            level.addParticle(ParticleTypes.PORTAL,
                    targetPos.x + (rng.nextDouble() - 0.5) * player.getBbWidth() * 2,
                    targetPos.y + rng.nextDouble() * player.getBbHeight(),
                    targetPos.z + (rng.nextDouble() - 0.5) * player.getBbWidth() * 2,
                    (rng.nextDouble() - 0.5) * 2,
                    -rng.nextDouble(),
                    (rng.nextDouble() - 0.5) * 2);
        }

        level.playLocalSound(
                targetPos.x, targetPos.y, targetPos.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                1.0F, 1.0F, false
        );
    }

    private static void teleportServerPlayer(Minecraft client, Vec3 targetPos) {
        Player player = client.player;
        if (player == null) return;
        var server = client.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                var serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
                if (serverPlayer != null) {
                    serverPlayer.teleportTo(targetPos.x, targetPos.y, targetPos.z);
                }
            });
        }
    }

    /** Called every tick — spawns portal particles around player while morphed as enderman. */
    public static void tickParticles(Minecraft client) {
        Player player = client.player;
        Level level = client.level;
        if (player == null || level == null) return;

        RandomSource rng = player.getRandom();
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.PORTAL,
                    player.getX() + (rng.nextDouble() - 0.5) * player.getBbWidth(),
                    player.getY() + rng.nextDouble() * player.getBbHeight() - 0.25,
                    player.getZ() + (rng.nextDouble() - 0.5) * player.getBbWidth(),
                    (rng.nextDouble() - 0.5) * 2,
                    -rng.nextDouble(),
                    (rng.nextDouble() - 0.5) * 2);
        }
    }
}