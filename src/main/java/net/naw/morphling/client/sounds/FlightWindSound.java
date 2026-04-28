package net.naw.morphling.client.sounds;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

public class FlightWindSound extends AbstractTickableSoundInstance {
    private static final float TARGET_VOLUME = 0.02F;
    private static final int FADE_IN_TICKS = 20; // ~1 second fade

    private final Player player;
    private int ticksPlayed = 0;

    public FlightWindSound(Player player) {
        super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, RandomSource.create());
        this.player = player;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.005F;
        this.pitch = 1.0F;
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
    }

    @Override
    public void tick() {

        if (player.isRemoved() || player.onGround()) {
            this.stop();
            return;
        }

        ticksPlayed++;
        if (ticksPlayed < FADE_IN_TICKS) {
            this.volume = TARGET_VOLUME * ((float) ticksPlayed / FADE_IN_TICKS);
        } else {
            this.volume = TARGET_VOLUME;
        }

        // Follow player
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
    }
}