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
import net.minecraft.core.registries.BuiltInRegistries;

public class EndermanCarryAbility {

    private static long lastActionTime = 0L;
    private static final long ACTION_COOLDOWN_MS = 300;

    public static void trigger(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.ENDERMAN) return;
        if (client.player == null || client.level == null) return;

        long now = System.currentTimeMillis();
        if (now - lastActionTime < ACTION_COOLDOWN_MS) return;
        lastActionTime = now;

        Player player = client.player;
        Level level = client.level;

        if (!(MorphState.getCachedEntity() instanceof EnderMan enderman)) return;

        BlockState carried = enderman.getCarriedBlock();

        if (carried == null) {
            tryPickup(client, player, level, enderman);
        } else {
            tryPlace(client, player, level, enderman, carried);
        }
    }

    private static void tryPickup(Minecraft client, Player player, Level level, EnderMan enderman) {
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

        if (!state.is(BlockTags.ENDERMAN_HOLDABLE)) return;

        enderman.setCarriedBlock(state);

        // Sync carried block to other players
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        MorphState.sendAbilityState("enderman_carried", blockId);

        var server = client.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                var serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
                if (serverPlayer != null) {
                    serverPlayer.level().destroyBlock(pos, false);
                }
            });
        } else {
            MorphState.sendAbilityAction("enderman_pickup",
                    pos.getX() + "," + pos.getY() + "," + pos.getZ());
        }

        level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5F, 1.2F, false);
    }

    private static void tryPlace(Minecraft client, Player player, Level level, EnderMan enderman, BlockState carried) {
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
            placePos = BlockPos.containing(endPos);
        }

        if (!level.getBlockState(placePos).isAir()) return;
        if (placePos.equals(player.blockPosition()) || placePos.equals(player.blockPosition().above())) return;

        final BlockPos finalPos = placePos;
        final BlockState toPlace = carried;

        var server = client.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                var serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
                if (serverPlayer != null) {
                    serverPlayer.level().setBlock(finalPos, toPlace, 3);
                }
            });
        } else {
            String blockId = BuiltInRegistries.BLOCK.getKey(toPlace.getBlock()).toString();
            MorphState.sendAbilityAction("enderman_place",
                    finalPos.getX() + "," + finalPos.getY() + "," + finalPos.getZ() + "," + blockId);
        }

        enderman.setCarriedBlock(null);

        // Sync cleared carried block
        MorphState.sendAbilityState("enderman_carried", "");

        level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5F, 0.8F, false);
    }
}
