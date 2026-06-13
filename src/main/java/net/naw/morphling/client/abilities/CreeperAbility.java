package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.naw.morphling.client.MorphlingClient;
import net.naw.morphling.client.core.MorphState;

public class CreeperAbility {

    private static final int SWELL_TICKS = 30;
    private static int tickCounter = 0;
    private static boolean wasPressed = false;

    public static void tick(Minecraft client) {
        if (client.player == null) return;

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
                    MorphState.broadcastSound(SoundEvents.CREEPER_PRIMED, 1.0F, 0.5F);
                }
            }

            tickCounter++;

            if (tickCounter % 3 == 0) {
                MorphState.sendAbilityState("creeper_swell", String.valueOf(getSwellProgress()));
            }

            if (tickCounter >= SWELL_TICKS) {
                explode(client);
                tickCounter = 0;
                MorphState.sendAbilityState("creeper_swell", "0.0");
            }
        } else {
            if (tickCounter > 0) {
                MorphState.sendAbilityState("creeper_swell", "0.0");
            }
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
                            serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                            3.0F, net.naw.morphling.client.games.MobBrawl.BrawlDimension.isBrawlDimension(serverPlayer.level())
                                    ? Level.ExplosionInteraction.NONE
                                    : Level.ExplosionInteraction.MOB
                    );
                }
            });
        } else {
            MorphState.sendAbilityAction("creeper_explode", "");
        }
    }

    public static float getSwellProgress() {
        return (float) tickCounter / SWELL_TICKS;
    }
}
