package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.naw.morphling.client.MorphlingClient;
import net.naw.morphling.client.core.MorphState;

public class ZombieAbility {

    private static final int BREAK_TIME_TICKS = 240;
    private static final double MAX_REACH = 3.0;

    private static int breakProgress = 0;
    private static int lastVisualProgress = -1;
    private static BlockPos currentDoorPos = null;
    private static boolean wasPressed = false;

    public static void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            resetProgress(client);
            return;
        }

        if (MorphState.getCurrentMorph() != EntityType.ZOMBIE) {
            resetProgress(client);
            return;
        }

        Player player = client.player;
        Level level = client.level;

        boolean isPressed = MorphlingClient.abilityKey.isDown();

        if (!isPressed) {
            if (wasPressed) resetProgress(client);
            wasPressed = false;
            return;
        }

        BlockPos lookedAtDoor = findTargetDoor(player, level);

        if (lookedAtDoor == null) {
            if (wasPressed) resetProgress(client);
            wasPressed = false;
            return;
        }

        if (currentDoorPos != null && !currentDoorPos.equals(lookedAtDoor)) {
            resetProgress(client);
        }

        currentDoorPos = lookedAtDoor;
        wasPressed = true;

        if (player.getRandom().nextInt(20) == 0) {
            level.levelEvent(1019, currentDoorPos, 0);
            if (!player.swinging) {
                player.swing(player.getUsedItemHand());
            }
        }

        breakProgress++;

        int visualProgress = (int) ((float) breakProgress / (float) BREAK_TIME_TICKS * 10.0F);
        if (visualProgress != lastVisualProgress) {
            level.destroyBlockProgress(player.getId(), currentDoorPos, visualProgress);
            lastVisualProgress = visualProgress;
        }

        if (breakProgress >= BREAK_TIME_TICKS) {
            breakDoor(client, currentDoorPos);
            resetProgress(client);
        }
    }

    private static BlockPos findTargetDoor(Player player, Level level) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(MAX_REACH));

        BlockHitResult hit = level.clip(new ClipContext(
                eyePos, endPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));

        if (hit.getType() != HitResult.Type.BLOCK) return null;

        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);

        if (!(state.getBlock() instanceof DoorBlock door)) return null;
        if (!door.type().canOpenByHand()) return null;

        return pos;
    }

    private static void breakDoor(Minecraft client, BlockPos pos) {
        Player player = client.player;
        if (player == null || client.level == null) return;

        Level level = client.level;
        BlockState state = level.getBlockState(pos);

        level.levelEvent(1021, pos, 0);
        level.levelEvent(2001, pos, Block.getId(state));

        var server = client.getSingleplayerServer();
        if (server != null) {
            final BlockPos finalPos = pos;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayer(player.getUUID());
                if (sp != null) {
                    sp.level().removeBlock(finalPos, false);
                }
            });
        } else {
            MorphState.sendAbilityAction("zombie_break_door", pos.getX() + "," + pos.getY() + "," + pos.getZ());
        }
    }

    private static void resetProgress(Minecraft client) {
        if (currentDoorPos != null && client.player != null && client.level != null) {
            client.level.destroyBlockProgress(client.player.getId(), currentDoorPos, -1);
        }
        breakProgress = 0;
        lastVisualProgress = -1;
        currentDoorPos = null;
    }
}
