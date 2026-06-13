package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.naw.morphling.client.core.MorphState;

public class EndermanAbility {

    // ── R teleport ──────────────────────────────────────────────────────────
    private static final double MAX_TELEPORT_DISTANCE = 32.0;
    private static long lastTeleportTime = 0L;
    private static final long TELEPORT_COOLDOWN_MS = 500;

    // ── Water teleport ───────────────────────────────────────────────────────
    private static int waterDamageCooldown = 0;
    private static int waterTouchTicks = 0;

    /**
     * R key teleport — shoots a ray along the look direction and teleports
     * the player to the first non-solid block along it.
     */
    public static void trigger(Minecraft client) {
        Player player = client.player;
        Level level = client.level;
        if (player == null || level == null) return;

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
        if (!stateAtTarget.getCollisionShape(level, BlockPos.containing(targetPos)).isEmpty()) return;

        level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F, false);
        MorphState.broadcastSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);

        spawnPortalParticles(level, rng, player.getX(), player.getY(), player.getZ(),
                player.getBbWidth(), player.getBbHeight());

        teleportServerPlayer(client, targetPos);
        player.setPos(targetPos.x, targetPos.y, targetPos.z);

        spawnPortalParticles(level, rng, targetPos.x, targetPos.y, targetPos.z,
                player.getBbWidth(), player.getBbHeight());

        level.playLocalSound(targetPos.x, targetPos.y, targetPos.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F, false);
    }

    /**
     * Called every tick while the player is in water as enderman.
     * Deals damage for the first few ticks, then teleports to nearby land.
     * If no land is found within 64 blocks, continues dealing damage.
     * Mirrors vanilla EnderMan.hurtServer() projectile handling.
     */
    public static void tickWaterTeleport(Minecraft client) {
        Player player = client.player;
        Level level = client.level;
        if (player == null || level == null) return;
        if (player.isCreative() || player.isSpectator()) return;

        waterTouchTicks++;

        // Deal damage for first 3 ticks before attempting teleport
        if (waterTouchTicks < 4) {
            if (waterDamageCooldown <= 0) {
                var server = client.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> {
                        var serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
                        if (serverPlayer != null) {
                            serverPlayer.hurtServer(server.overworld(), server.overworld().damageSources().drown(), 4.0F);
                        }
                    });
                } else {
                    MorphState.sendAbilityAction("enderman_water_damage", "");
                }
                waterDamageCooldown = 0;
            } else {
                waterDamageCooldown--;
            }
            return;
        }

        RandomSource rng = player.getRandom();

        // Try 64 random positions within 64x32x64 blocks — matches vanilla
        for (int i = 0; i < 64; i++) {
            double tx = player.getX() + (rng.nextDouble() - 0.5) * 64.0;
            double ty = player.getY() + (rng.nextInt(64) - 32);
            double tz = player.getZ() + (rng.nextDouble() - 0.5) * 64.0;

            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(tx, ty, tz);

            // Drop down to solid ground
            while (pos.getY() > level.getMinY() && !level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)) {
                pos.move(Direction.DOWN);
            }

            BlockState ground = level.getBlockState(pos);
            boolean isWet = ground.getFluidState().is(FluidTags.WATER);
            BlockState above1 = level.getBlockState(pos.above());
            BlockState above2 = level.getBlockState(pos.above(2));

            if (ground.isFaceSturdy(level, pos, Direction.UP) && !isWet && above1.isAir() && above2.isAir()) {
                final double finalTx = tx;
                final double finalTy = pos.getY() + 1;
                final double finalTz = tz;

                level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F, false);

                var server = client.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> {
                        var serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
                        if (serverPlayer != null) {
                            serverPlayer.randomTeleport(finalTx, finalTy, finalTz, true);
                        }
                    });
                } else {
                    MorphState.sendAbilityAction("enderman_teleport",
                            finalTx + "," + finalTy + "," + finalTz);
                }
                player.setPos(finalTx, finalTy, finalTz);

                level.playLocalSound(finalTx, finalTy, finalTz,
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F, false);
                waterTouchTicks = 0;
                waterDamageCooldown = 0;
                return;
            }
        }

        // No valid land found — deal damage and reset
        if (waterDamageCooldown <= 0) {
            var server = client.getSingleplayerServer();
            if (server != null) {
                server.execute(() -> {
                    var serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
                    if (serverPlayer != null) {
                        serverPlayer.hurtServer(server.overworld(), server.overworld().damageSources().drown(), 4.0F);
                    }
                });
            } else {
                MorphState.sendAbilityAction("enderman_water_damage", "");
            }
            waterDamageCooldown = 10;
        } else {
            waterDamageCooldown--;
        }
        waterTouchTicks = 0;
    }

    /**
     * Teleports the server-side player to the given position.
     * Singleplayer: executes directly on integrated server.
     * Multiplayer: sends ability action payload.
     */
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
        } else {
            MorphState.sendAbilityAction("enderman_teleport",
                    targetPos.x + "," + targetPos.y + "," + targetPos.z);
        }
    }

    /** Spawns portal particles at the given position. */
    private static void spawnPortalParticles(Level level, RandomSource rng,
                                             double x, double y, double z,
                                             float width, float height) {
        for (int i = 0; i < 32; i++) {
            level.addParticle(ParticleTypes.PORTAL,
                    x + (rng.nextDouble() - 0.5) * width * 2,
                    y + rng.nextDouble() * height,
                    z + (rng.nextDouble() - 0.5) * width * 2,
                    (rng.nextDouble() - 0.5) * 2,
                    -rng.nextDouble(),
                    (rng.nextDouble() - 0.5) * 2);
        }
    }

    /** Spawns ambient portal particles around the player every tick. */
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