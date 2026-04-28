package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.naw.morphling.client.core.MorphState;

public class SheepAbility {

    private static int eatTicksRemaining = 0;
    private static long lastEatTime = 0L;
    private static final long EAT_COOLDOWN_MS = 3000;
    private static final int EAT_DURATION_TICKS = 40;
    private static BlockPos pendingGrassPos = null;

    public static boolean isEating() {
        return eatTicksRemaining > 0 && MorphState.getCurrentMorph() == EntityType.SHEEP;
    }

    public static void trigger(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.SHEEP) return;
        if (client.player == null || client.level == null) return;

        long now = System.currentTimeMillis();
        if (now - lastEatTime < EAT_COOLDOWN_MS) return;
        lastEatTime = now;

        eatTicksRemaining = EAT_DURATION_TICKS;

        if (MorphState.getCachedEntity() instanceof Sheep sheep) {
            sheep.handleEntityEvent((byte) 10);
        }

        client.level.playLocalSound(
                client.player.getX(), client.player.getY(), client.player.getZ(),
                SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS,
                0.5F, 1.0F, false
        );

        // Remember the grass below for later consumption
        BlockPos below = client.player.blockPosition().below();
        if (client.level.getBlockState(below).is(Blocks.GRASS_BLOCK)) {
            pendingGrassPos = below;
        } else {
            pendingGrassPos = null;
        }
    }

    public static void tick(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.SHEEP) {
            eatTicksRemaining = 0;
            pendingGrassPos = null;
            return;
        }

        if (eatTicksRemaining <= 0) return;
        eatTicksRemaining--;

        if (MorphState.getCachedEntity() instanceof Sheep sheep) {
            var accessor = (net.naw.morphling.mixin.accessors.SheepEatAccessor)(Object) sheep;
            int current = accessor.morphling$getEatAnimationTick();
            if (current > 0) {
                accessor.morphling$setEatAnimationTick(current - 1);
            }
        }

        Player player = client.player;
        if (player == null) return;

        if (eatTicksRemaining % 20 == 0) {
            var server = client.getSingleplayerServer();
            if (server != null) {
                server.execute(() -> {
                    var sp = server.getPlayerList().getPlayer(player.getUUID());
                    if (sp != null && sp.getHealth() < sp.getMaxHealth()) {
                        sp.heal(0.5F);
                    }
                });
            }
        }

        // Restore a tiny bit of hunger
        if (eatTicksRemaining % 50 == 0) {
            var server = client.getSingleplayerServer();
            if (server != null) {
                server.execute(() -> {
                    var sp = server.getPlayerList().getPlayer(player.getUUID());
                    if (sp != null) {
                        var food = sp.getFoodData();
                        food.setFoodLevel(Math.min(food.getFoodLevel() + 1, 20));
                    }
                });
            }
        }

        // On animation complete — turn grass to dirt
        if (eatTicksRemaining == 0 && pendingGrassPos != null) {
            final BlockPos pos = pendingGrassPos;
            pendingGrassPos = null;
            var server = client.getSingleplayerServer();
            if (server != null) {
                server.execute(() -> {
                    var sp = server.getPlayerList().getPlayer(player.getUUID());
                    if (sp != null && sp.level().getBlockState(pos).is(Blocks.GRASS_BLOCK)) {
                        sp.level().setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
                        sp.level().levelEvent(2001, pos, Block.getId(Blocks.GRASS_BLOCK.defaultBlockState()));
                    }
                });
            }
        }
    }
}