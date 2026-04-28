package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.naw.morphling.client.MorphlingClient;
import net.naw.morphling.client.core.MorphState;

public class CreeperAbility {

    private static final int SWELL_TICKS = 30; // ~1.5 seconds
    private static int tickCounter = 0;
    private static boolean wasPressed = false;

    /**
     * Called every tick — handles press/hold/release logic for the swell.
     */
    public static void tick(Minecraft client) {
        if (client.player == null) return;

        // Only tick if morphed as creeper
        if (!MorphState.isMorphed() ||
                MorphState.getCurrentMorph() != net.minecraft.world.entity.EntityType.CREEPER) {
            tickCounter = 0;
            wasPressed = false;
            return;
        }

        boolean isPressed = MorphlingClient.abilityKey.isDown();

        if (isPressed) {
            if (!wasPressed) {
                Level level = client.level;
                if (level != null) {
                    level.playLocalSound(
                            client.player.getX(), client.player.getY(), client.player.getZ(),
                            SoundEvents.CREEPER_PRIMED,
                            SoundSource.PLAYERS,
                            1.0F, 0.5F, false
                    );
                }
            }

            tickCounter++;
            if (tickCounter >= SWELL_TICKS) {
                explode(client);
                tickCounter = 0;
            }
        } else {
            tickCounter = 0;
        }

        wasPressed = isPressed;
    }

    private static void explode(Minecraft client) {
        Player player = client.player;
        if (player == null) return;

        var server = client.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                var serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
                if (serverPlayer != null) {
                    serverPlayer.level().explode(
                            serverPlayer,
                            serverPlayer.getX(),
                            serverPlayer.getY(),
                            serverPlayer.getZ(),
                            3.0F,
                            Level.ExplosionInteraction.MOB
                    );
                }
            });
        }
    }

    /** For the renderer to drive the swell visual (0.0 - 1.0). */
    public static float getSwellProgress() {
        return (float) tickCounter / SWELL_TICKS;
    }

}