package net.naw.morphling.client.core;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class MorphTransition {

    private static final int TRANSITION_TICKS = 14;
    private static int ticksRemaining = 0;

    public static void trigger() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) return;

        ticksRemaining = TRANSITION_TICKS;

        // Initial burst of particles
        spawnParticles(player, level, 1);

        // Morph sound
        level.playLocalSound(
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                0.2F, 1.3F, false
        );
    }

    public static void tick() {
        if (ticksRemaining <= 0) return;
        ticksRemaining--;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) return;

        // Continuous particle spawn during transition
        spawnParticles(player, level, 1);

    }

    public static boolean isActive() {
        return ticksRemaining > 0;
    }

    /** 0.0 at end, 1.0 at start — useful for scaling visuals. */
    public static float getProgress() {
        return (float) ticksRemaining / TRANSITION_TICKS;
    }

    private static void spawnParticles(Player player, Level level, int count) {
        RandomSource rng = player.getRandom();
        for (int i = 0; i < count; i++) {
            double ox = (rng.nextDouble() - 0.5) * player.getBbWidth() * 2;
            double oy = rng.nextDouble() * player.getBbHeight();
            double oz = (rng.nextDouble() - 0.5) * player.getBbWidth() * 2;
            double vx = (rng.nextDouble() - 0.5) * 0.3;
            double vy = (rng.nextDouble() - 0.5) * 0.3;
            double vz = (rng.nextDouble() - 0.5) * 0.3;

            level.addParticle(ParticleTypes.PORTAL,
                    player.getX() + ox, player.getY() + oy, player.getZ() + oz,
                    vx, vy, vz);


        }
    }
}