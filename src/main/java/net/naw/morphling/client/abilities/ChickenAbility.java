package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.naw.morphling.client.core.MorphState;

import java.util.Random;

public class ChickenAbility {

    private static long lastLayTime = 0L;
    private static final long LAY_COOLDOWN_MS = 30_000;
    private static final Random RANDOM = new Random();

    public static void layEgg(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.CHICKEN) return;
        if (client.player == null || client.level == null) return;

        long now = System.currentTimeMillis();
        if (now - lastLayTime < LAY_COOLDOWN_MS) return;
        lastLayTime = now;

        Player player = client.player;
        Level level = client.level;

        level.playLocalSound(
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS,
                1.0F, (RANDOM.nextFloat() - RANDOM.nextFloat()) * 0.2F + 1.0F, false
        );

        // Try singleplayer/LAN host
        var server = client.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                var serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
                if (serverPlayer != null) {
                    Level serverLevel = serverPlayer.level();
                    Vec3 lookDir = serverPlayer.getLookAngle();
                    double behindX = serverPlayer.getX() - lookDir.x;
                    double behindZ = serverPlayer.getZ() - lookDir.z;
                    ItemEntity egg = new ItemEntity(
                            serverLevel,
                            behindX, serverPlayer.getY() + 0.1, behindZ,
                            new ItemStack(Items.EGG)
                    );
                    egg.setDeltaMovement(
                            (RANDOM.nextDouble() - 0.5) * 0.05, 0.05,
                            (RANDOM.nextDouble() - 0.5) * 0.05
                    );
                    serverLevel.addFreshEntity(egg);
                }
            });
        } else {
            // Dedicated server
            MorphState.sendAbilityAction("chicken_egg", "");
        }
    }
}
