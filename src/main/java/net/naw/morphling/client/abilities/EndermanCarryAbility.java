package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.naw.morphling.client.core.MorphState;

public class EndermanCarryAbility {

    private static long lastActionTime = 0L;
    private static final long ACTION_COOLDOWN_MS = 300;

    public static void trigger(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.ENDERMAN) return;
        if (client.player == null || client.level == null) return;

        // Cooldown to prevent spam
        long now = System.currentTimeMillis();
        if (now - lastActionTime < ACTION_COOLDOWN_MS) return;
        lastActionTime = now;

        Player player = client.player;
        Level level = client.level;

        if (!(MorphState.getCachedEntity() instanceof EnderMan enderman)) return;

        BlockState carried = enderman.getCarriedBlock();

        if (carried == null) {
            // Not carrying — try to pick up a block
            tryPickup(client, player, level, enderman);
        } else {
            // Carrying — try to place the block
            tryPlace(client, player, level, enderman, carried);
        }
    }

    private static void tryPickup(Minecraft client, Player player, Level level, EnderMan enderman) {
        // Raycast up to 4 blocks away from where player is looking
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(4.0));

        BlockHitResult hit = level.clip(new ClipContext(
                eyePos, endPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));

        if (hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);

        // Only pickup holdable blocks (same rule as vanilla enderman)
        if (!state.is(BlockTags.ENDERMAN_HOLDABLE)) return;

        // Set carried block on the cached enderman (triggers visual via vanilla renderer)
        enderman.setCarriedBlock(state);

        // Remove block from world (server-side)
        var server = client.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                var serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
                if (serverPlayer != null) {
                    serverPlayer.level().destroyBlock(pos, false);
                }
            });
        }

        // Sound feedback
        level.playLocalSound(
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                0.5F, 1.2F, false
        );
    }

    private static void tryPlace(Minecraft client, Player player, Level level, EnderMan enderman, BlockState carried) {
        // Raycast to find where to place
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(4.0));

        BlockHitResult hit = level.clip(new ClipContext(
                eyePos, endPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));

        BlockPos placePos;
        if (hit.getType() == HitResult.Type.BLOCK) {
            placePos = hit.getBlockPos().relative(hit.getDirection());
        } else {
            // No block hit — place at end of look
            placePos = BlockPos.containing(endPos);
        }

        // Check that target spot is empty
        if (!level.getBlockState(placePos).isAir()) return;

        // Don't place inside player
        if (placePos.equals(player.blockPosition()) || placePos.equals(player.blockPosition().above())) return;

        final BlockPos finalPos = placePos;
        final BlockState toPlace = carried;

        // Place block server-side
        var server = client.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                var serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
                if (serverPlayer != null) {
                    serverPlayer.level().setBlock(finalPos, toPlace, 3);
                }
            });
        }

        // Clear carried state
        enderman.setCarriedBlock(null);

        level.playLocalSound(
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                0.5F, 0.8F, false
        );
    }
}